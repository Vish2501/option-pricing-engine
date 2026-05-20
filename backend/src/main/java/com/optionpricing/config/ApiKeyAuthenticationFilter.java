package com.optionpricing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String apiKey;

    public ApiKeyAuthenticationFilter(@Value("${security.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedEndpoint = path.matches("^/api(/v1)?/history(/analytics)?$");
        return !StringUtils.hasText(apiKey) || !protectedEndpoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(providedKey)) {
            providedKey = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        }

        if (!StringUtils.hasText(providedKey) || !MessageDigest.isEqual(bytes(apiKey), bytes(providedKey))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Missing or invalid API key.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
