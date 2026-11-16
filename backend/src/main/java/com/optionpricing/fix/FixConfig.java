package com.optionpricing.fix;

import com.optionpricing.service.OptionPricingService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import quickfix.Acceptor;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.MemoryStoreFactory;
import quickfix.SLF4JLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wires the FIX acceptor into the Spring Boot app. Nothing in this class exists unless
 * fix.enabled=true, so with the property off the app is the REST API it always was.
 */
@Configuration
@ConditionalOnProperty(name = "fix.enabled", havingValue = "true")
public class FixConfig {

    @Bean
    FixApplication fixApplication(OptionPricingService pricingService,
                                  Validator validator,
                                  @Value("${fix.spread-bps}") double spreadBps) {
        return new FixApplication(pricingService, validator, spreadBps);
    }

    // Spring calls start() once the bean is built (the port opens) and stop() on shutdown
    // (clients are logged out and the port is closed).
    @Bean(initMethod = "start", destroyMethod = "stop")
    Acceptor fixAcceptor(FixApplication application,
                         @Value("${fix.config-file}") Resource configFile,
                         @Value("${fix.port}") int port) throws ConfigError, IOException {
        SessionSettings settings;
        try (InputStream in = configFile.getInputStream()) {
            settings = new SessionSettings(in);
        }
        // The port lives in application.yml so it can be set per environment like server.port.
        settings.setLong(Acceptor.SETTING_SOCKET_ACCEPT_PORT, port);

        return new SocketAcceptor(
                application,
                // Sequence numbers and sent messages are kept in memory, not on disk.
                new MemoryStoreFactory(),
                settings,
                // FIX session events and messages go to the same SLF4J logs as the rest of the app.
                new SLF4JLogFactory(settings),
                // Builds typed FIX 4.4 message objects (QuoteRequest, Quote...) from raw text.
                new DefaultMessageFactory());
    }
}
