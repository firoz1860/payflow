package com.payflow.ledger.controller;

import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.common.security.TenantGuard;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger")
public class MerchantLedgerController {
    private final LedgerAccountRepository accountRepository;
    private final LedgerPostingRepository postingRepository;
    private final LedgerEntryRepository entryRepository;

    public MerchantLedgerController(LedgerAccountRepository accountRepository,
                                    LedgerPostingRepository postingRepository,
                                    LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
        this.entryRepository = entryRepository;
    }

    public record AccountView(UUID accountId, String accountType, String currency, BigDecimal balance) {}
    public record PostingView(UUID id, String sourceType, String sourceId, String currency,
                              BigDecimal totalDebit, BigDecimal totalCredit,
                              String description, Instant createdAt) {}
    public record EntryView(UUID id, UUID postingId, UUID accountId, String entryType,
                            BigDecimal amount, String currency, String description, Instant createdAt) {}

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('ledger:read')")
    @Operation(summary = "List ledger accounts owned by the authenticated merchant")
    public ResponseEntity<List<AccountView>> accounts(@AuthenticationPrincipal PayFlowPrincipal principal) {
        String merchantId = TenantGuard.requireMerchant(principal).toString();
        List<AccountView> result = accountRepository.findByOwnerId(merchantId).stream()
                .map(account -> new AccountView(
                        account.getId(),
                        account.getAccountType().name(),
                        account.getCurrency(),
                        balanceOf(account)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/postings")
    @PreAuthorize("hasAuthority('ledger:read')")
    @Operation(summary = "List recent ledger postings for the authenticated merchant")
    public ResponseEntity<List<PostingView>> postings(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @RequestParam(defaultValue = "50") int limit) {
        String merchantId = TenantGuard.requireMerchant(principal).toString();
        int capped = Math.min(Math.max(limit, 1), 200);
        List<PostingView> result = postingRepository
                .findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, capped))
                .stream()
                .map(this::toPostingView)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/postings/{postingId}/entries")
    @PreAuthorize("hasAuthority('ledger:read')")
    @Operation(summary = "List entries for one merchant-owned ledger posting")
    public ResponseEntity<List<EntryView>> entries(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable UUID postingId) {
        String merchantId = TenantGuard.requireMerchant(principal).toString();
        LedgerPosting posting = postingRepository.findById(postingId)
                .filter(p -> merchantId.equals(p.getMerchantId()))
                .orElseThrow(() -> PayFlowException.notFound("Ledger posting not found"));

        List<EntryView> result = entryRepository.findByPostingId(posting.getId()).stream()
                .map(this::toEntryView)
                .toList();
        return ResponseEntity.ok(result);
    }

    private PostingView toPostingView(LedgerPosting posting) {
        return new PostingView(
                posting.getId(),
                posting.getSourceType().name(),
                posting.getSourceId(),
                posting.getCurrency(),
                posting.getTotalDebit(),
                posting.getTotalCredit(),
                posting.getDescription(),
                posting.getCreatedAt());
    }

    private EntryView toEntryView(LedgerEntry entry) {
        return new EntryView(
                entry.getId(),
                entry.getPostingId(),
                entry.getAccountId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getCreatedAt());
    }

    private BigDecimal balanceOf(LedgerAccount account) {
        BigDecimal debitMinusCredit = entryRepository.debitMinusCredit(account.getId());
        return account.getAccountType().normalBalance() == LedgerAccount.AccountType.Normal.DEBIT
                ? debitMinusCredit
                : debitMinusCredit.negate();
    }
}
