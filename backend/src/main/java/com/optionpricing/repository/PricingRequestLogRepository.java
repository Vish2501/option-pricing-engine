package com.optionpricing.repository;

import com.optionpricing.entity.PricingRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PricingRequestLogRepository extends JpaRepository<PricingRequestLog, Long> {
    Page<PricingRequestLog> findByTicker(String ticker, Pageable pageable);

    // Aggregated in the database so the table never has to be loaded into memory.
    // Model spread is the gap between the highest and lowest of the three model prices.
    @Query("""
            select count(l) as totalRequests,
                   count(distinct l.ticker) as uniqueTickers,
                   avg(l.blackScholesPrice) as averageBlackScholesPrice,
                   avg(l.monteCarloPrice) as averageMonteCarloPrice,
                   avg(l.binomialTreePrice) as averageBinomialTreePrice,
                   avg(greatest(l.blackScholesPrice, l.monteCarloPrice, l.binomialTreePrice)
                       - least(l.blackScholesPrice, l.monteCarloPrice, l.binomialTreePrice)) as averageModelSpread
            from PricingRequestLog l
            """)
    AnalyticsSummary summarize();

    @Query("""
            select l.ticker as ticker, count(l) as requests
            from PricingRequestLog l
            group by l.ticker
            order by count(l) desc, l.ticker
            """)
    List<TickerRequestCount> countRequestsByTicker();

    // Averages are null when the table is empty.
    interface AnalyticsSummary {
        long getTotalRequests();

        long getUniqueTickers();

        Double getAverageBlackScholesPrice();

        Double getAverageMonteCarloPrice();

        Double getAverageBinomialTreePrice();

        Double getAverageModelSpread();
    }

    interface TickerRequestCount {
        String getTicker();

        long getRequests();
    }
}
