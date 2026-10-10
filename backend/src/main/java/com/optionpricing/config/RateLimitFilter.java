package com.optionpricing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionpricing.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-window rate limiting for the public API. The API has no logins, so this is what
 * stops a script from filling the database or hammering Yahoo Finance through us.
 * <p>
 * Three limits apply: every client gets a general per-minute budget, a tighter per-minute
 * budget for POST /price (the only endpoint that writes a row and may call Yahoo), and all
 * clients together share an hourly cap on POST /price. Clients are told apart by IP address
 * taken from X-Forwarded-For, which a caller can forge - the shared hourly cap is the
 * backstop for that.
 * <p>
 * Counters live in memory, which is enough for a single instance; running several instances
 * would need a shared store such as Redis.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long MINUTE_MILLIS = 60_000;
    private static final long HOUR_MILLIS = 3_600_000;
    private static final String ALL_CLIENTS = "*";

    private final int requestsPerMinute;
    private final int pricingPerMinute;
    private final int pricingPerHourAllClients;
    private final ObjectMapper objectMapper;

    private final Window requestsWindow = new Window(MINUTE_MILLIS);
    private final Window pricingWindow = new Window(MINUTE_MILLIS);
    private final Window pricingAllClientsWindow = new Window(HOUR_MILLIS);

    public RateLimitFilter(
            @Value("${rate-limit.requests-per-minute:60}") int requestsPerMinute,
            @Value("${rate-limit.pricing-per-minute:10}") int pricingPerMinute,
            @Value("${rate-limit.pricing-per-hour-all-clients:300}") int pricingPerHourAllClients,
            ObjectMapper objectMapper
    ) {
        this.requestsPerMinute = requestsPerMinute;
        this.pricingPerMinute = pricingPerMinute;
        this.pricingPerHourAllClients = pricingPerHourAllClients;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String client = clientAddress(request);
        long now = System.currentTimeMillis();

        long retryAfterSeconds = requestsWindow.retryAfterSeconds(client, requestsPerMinute, now);
        if (retryAfterSeconds == 0 && isPricingRequest(request)) {
            retryAfterSeconds = pricingWindow.retryAfterSeconds(client, pricingPerMinute, now);
            if (retryAfterSeconds == 0) {
                retryAfterSeconds = pricingAllClientsWindow.retryAfterSeconds(ALL_CLIENTS, pricingPerHourAllClients, now);
            }
        }

        if (retryAfterSeconds > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                    "Too many requests. Try again in " + retryAfterSeconds + " seconds."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPricingRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/price");
    }

    // Behind Render's proxy the socket address is the proxy, so the caller is the first X-Forwarded-For entry.
    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Counts requests per key within the current fixed window, forgetting everything when the window rolls over. */
    private static final class Window {
        private final long lengthMillis;
        private final Map<String, Integer> counts = new HashMap<>();
        private long currentWindow = -1;

        private Window(long lengthMillis) {
            this.lengthMillis = lengthMillis;
        }

        // Records the request and returns 0 if it is within the limit, otherwise the seconds until the window resets.
        private synchronized long retryAfterSeconds(String key, int limit, long now) {
            long window = now / lengthMillis;
            if (window != currentWindow) {
                currentWindow = window;
                counts.clear();
            }
            if (counts.merge(key, 1, Integer::sum) <= limit) {
                return 0;
            }
            return Math.max(1, (lengthMillis - now % lengthMillis + 999) / 1000);
        }
    }
}
