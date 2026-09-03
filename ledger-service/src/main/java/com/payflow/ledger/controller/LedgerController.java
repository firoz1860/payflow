package com.payflow.ledger.controller;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import com.payflow.ledger.service.LedgerService;
import com.payflow.ledger.service.PostingCommand;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/internal/ledger")
@Hidden
public class LedgerController {
    private final LedgerService ledgerService;
    private final LedgerAccountRepository accountRepository;
    private final LedgerPostingRepository postingRepository;
    private final LedgerEntryRepository entryRepository;
    public LedgerController(LedgerService ledgerService, LedgerAccountRepository accountRepository,
                            LedgerPostingRepository postingRepository,
                            LedgerEntryRepository entryRepository) {
        this.ledgerService = ledgerService;
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
        this.entryRepository = entryRepository;
    }
    @PostMapping("/postings")
    public ResponseEntity<LedgerPosting> post(@Valid @RequestBody PostingCommand command) {
        return ResponseEntity.ok(ledgerService.post(command));
    }
    public record ReverseRequest(String reason) {
    }
    @PostMapping("/postings/{postingId}/reverse")
    public ResponseEntity<LedgerPosting> reverse(@PathVariable UUID postingId,
                                                 @RequestBody ReverseRequest request) {
        return ResponseEntity.ok(ledgerService.reverse(postingId,
                request.reason() == null ? "unspecified" : request.reason()));
    }
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> balance(@PathVariable UUID accountId) {
        BigDecimal balance = ledgerService.balanceOf(accountId);
        LedgerAccount account = accountRepository.findById(accountId).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "accountType", account.getAccountType().name(),
                "currency", account.getCurrency(),
                "normalBalance", account.getAccountType().normalBalance().name(),
                "balance", balance));
    }
    @GetMapping("/merchants/{merchantId}/accounts")
    public ResponseEntity<List<Map<String, Object>>> merchantAccounts(@PathVariable String merchantId) {
        List<Map<String, Object>> accounts = accountRepository.findByOwnerId(merchantId).stream()
                .map(account -> Map.<String, Object>of(
                        "accountId", account.getId(),
                        "accountType", account.getAccountType().name(),
                        "currency", account.getCurrency(),
                        "balance", ledgerService.balanceOf(account.getId())))
                .toList();
        return ResponseEntity.ok(accounts);
    }
    @GetMapping("/postings/{postingId}/entries")
    public ResponseEntity<List<LedgerEntry>> entries(@PathVariable UUID postingId) {
        return ResponseEntity.ok(entryRepository.findByPostingId(postingId));
    }
    @GetMapping("/merchants/{merchantId}/postings")
    public ResponseEntity<List<LedgerPosting>> merchantPostings(
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(postingRepository.findByMerchantIdOrderByCreatedAtDesc(
                merchantId, PageRequest.of(0, Math.min(limit, 200))));
    }
}
