package com.optionpricing.repository;

import com.optionpricing.entity.PricingRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRequestLogRepository extends JpaRepository<PricingRequestLog, Long> {
}
