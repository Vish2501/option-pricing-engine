package com.optionpricing.entity;

import com.optionpricing.model.OptionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "pricing_requests",
        indexes = {
                @Index(name = "idx_pricing_requests_ticker", columnList = "ticker"),
                @Index(name = "idx_pricing_requests_requested_at", columnList = "requested_at")
        }
)
public class PricingRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private double spotPrice;

    @Column(nullable = false)
    private double strike;

    @Column(nullable = false)
    private LocalDate expiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptionType optionType;

    @Column(nullable = false)
    private double riskFreeRate;

    @Column(nullable = false)
    private double volatility;

    @Column(nullable = false)
    private double blackScholesPrice;

    @Column(nullable = false)
    private double monteCarloPrice;

    @Column(nullable = false)
    private double binomialTreePrice;

    private Double impliedVolatility;

    @Column(nullable = false)
    private double delta;

    @Column(nullable = false)
    private double gamma;

    @Column(nullable = false)
    private double vega;

    @Column(nullable = false)
    private double theta;

    @Column(nullable = false)
    private double rho;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @PrePersist
    void prePersist() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
