package com.payflow.ledger.controller;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/internal/ai/ledger")
@Hidden
public class InternalAiLedgerController {
    private final LedgerPostingRepository postingRepository;
    private final LedgerEntryRepository entryRepository;
    private final LedgerAccountRepository accountRepository;
    public InternalAiLedgerController(LedgerPostingRepository postingRepository,
                                      LedgerEntryRepository entryRepository,
                                      LedgerAccountRepository accountRepository) {
        this.postingRepository = postingRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
    }
    @GetMapping("/payments/{paymentReference}")
    public PaymentLedgerEvidence byPayment(@PathVariable String paymentReference) {
        List<PostingEvidence> postings = postingRepository
                .findBySourceTypeAndSourceId(LedgerPosting.SourceType.PAYMENT, paymentReference)
                .map(this::toPostingEvidence)
                .map(List::of)
                .orElseGet(List::of);
        return new PaymentLedgerEvidence(paymentReference, postings);
    }
    private PostingEvidence toPostingEvidence(LedgerPosting posting) {
        List<EntryEvidence> entries = entryRepository.findByPostingId(posting.getId()).stream()
                .map(this::toEntryEvidence)
                .toList();
        return new PostingEvidence(
                posting.getId(),
                posting.getSourceType().name(),
                posting.getSourceId(),
                posting.getMerchantId(),
                posting.getCurrency(),
                posting.getTotalDebit(),
                posting.getTotalCredit(),
                posting.getTotalDebit().compareTo(posting.getTotalCredit()) == 0,
                posting.getDescription(),
                posting.getCorrelationId(),
                posting.getCreatedAt(),
                entries);
    }
    private EntryEvidence toEntryEvidence(LedgerEntry entry) {
        String accountType = accountRepository.findById(entry.getAccountId())
                .map(account -> account.getAccountType().name())
                .orElse(null);
        return new EntryEvidence(
                entry.getId(),
                entry.getAccountId(),
                accountType,
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getCurrency());
    }
    public record PaymentLedgerEvidence(
            String paymentReference,
            List<PostingEvidence> postings
    ) {
    }
    public record PostingEvidence(
            UUID postingId,
            String sourceType,
            String sourceId,
            String merchantId,
            String currency,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            String description,
            String correlationId,
            Instant createdAt,
            List<EntryEvidence> entries
    ) {
    }
    public record EntryEvidence(
            UUID entryId,
            UUID accountId,
            String accountType,
            String entryType,
            BigDecimal amount,
            String currency
    ) {
    }
}
