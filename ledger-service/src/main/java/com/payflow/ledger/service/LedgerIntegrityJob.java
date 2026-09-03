package com.payflow.ledger.service;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
@Component
public class LedgerIntegrityJob {
    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityJob.class);
    private final LedgerEntryRepository entryRepository;
    private final AtomicInteger imbalancedCurrencies = new AtomicInteger();
    public LedgerIntegrityJob(LedgerEntryRepository entryRepository, MeterRegistry meterRegistry) {
        this.entryRepository = entryRepository;
        meterRegistry.gauge("payflow.ledger.imbalanced.currencies", imbalancedCurrencies);
    }
    @Scheduled(cron = "${payflow.ledger.integrity-cron:0 */10 * * * *}")
    @Transactional(readOnly = true)
    public void verifyGlobalBalance() {
        int imbalanced = 0;
        for (String currency : entryRepository.distinctCurrencies()) {
            BigDecimal imbalance = entryRepository.globalImbalance(currency);
            if (imbalance.signum() != 0) {
                imbalanced++;
                log.error("LEDGER INTEGRITY FAILURE: {} is out of balance by {}. "
                        + "Debits do not equal credits. Halt settlements and investigate.",
                        currency, imbalance);
            } else {
                log.debug("Ledger balanced for {}", currency);
            }
        }
        imbalancedCurrencies.set(imbalanced);
    }
}
