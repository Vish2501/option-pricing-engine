package com.optionpricing.repository;

import com.optionpricing.entity.PricingRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingRequestLogRepository extends JpaRepository<PricingRequestLog, Long> {
    List<PricingRequestLog> findTop50ByOrderByRequestedAtDesc();
}
