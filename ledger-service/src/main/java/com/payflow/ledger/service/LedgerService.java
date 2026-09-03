package com.payflow.ledger.service;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.money.Money;
import com.payflow.common.web.CorrelationId;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Service
public class LedgerService {
    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);
    public static final String PLATFORM_OWNER = "platform";
    private final LedgerAccountRepository accountRepository;
    private final LedgerPostingRepository postingRepository;
    private final LedgerEntryRepository entryRepository;
    private final Counter postedCounter;
    private final Counter unbalancedCounter;
    private final Counter duplicateCounter;
    public LedgerService(LedgerAccountRepository accountRepository,
                         LedgerPostingRepository postingRepository,
                         LedgerEntryRepository entryRepository,
                         MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
        this.entryRepository = entryRepository;
        this.postedCounter = Counter.builder("payflow.ledger.posted").register(meterRegistry);
        this.unbalancedCounter = Counter.builder("payflow.ledger.unbalanced").register(meterRegistry);
        this.duplicateCounter = Counter.builder("payflow.ledger.duplicate").register(meterRegistry);
    }
    @Transactional
    public LedgerPosting post(PostingCommand command) {
        var existing = postingRepository.findBySourceTypeAndSourceId(
                command.sourceType(), command.sourceId());
        if (existing.isPresent()) {
            duplicateCounter.increment();
            log.info("Posting for {} {} already exists, returning it unchanged",
                    command.sourceType(), command.sourceId());
            return existing.get();
        }
        if (command.lines().isEmpty()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A posting must contain at least one debit and one credit");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (PostingCommand.Line line : command.lines()) {
            BigDecimal amount = Money.normalize(line.amount(), command.currency());
            if (amount.signum() <= 0) {
                throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Ledger entry amounts must be strictly positive; use entryType for direction");
            }
            if (line.entryType() == LedgerEntry.EntryType.DEBIT) {
                totalDebit = totalDebit.add(amount);
            } else {
                totalCredit = totalCredit.add(amount);
            }
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            unbalancedCounter.increment();
            log.error("Rejected unbalanced posting for {} {}: debits {} != credits {}",
                    command.sourceType(), command.sourceId(), totalDebit, totalCredit);
            throw PayFlowException.unprocessable(ErrorCode.LEDGER_UNBALANCED,
                    "Posting does not balance: debits " + totalDebit + " != credits " + totalCredit);
        }
        if (totalDebit.signum() == 0) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A posting must move a non-zero amount");
        }
        LedgerPosting posting = new LedgerPosting(command.sourceType(), command.sourceId(),
                command.merchantId(), command.currency(), totalDebit, totalCredit,
                command.description(), CorrelationId.get());
        try {
            postingRepository.saveAndFlush(posting);
        } catch (DataIntegrityViolationException ex) {
            duplicateCounter.increment();
            return postingRepository.findBySourceTypeAndSourceId(
                            command.sourceType(), command.sourceId())
                    .orElseThrow(() -> ex);
        }
        List<LedgerEntry> entries = new ArrayList<>(command.lines().size());
        for (PostingCommand.Line line : command.lines()) {
            LedgerAccount account = resolveAccount(line.ownerType(), line.ownerId(),
                    line.accountType(), command.currency());
            if (!account.acceptsPostings()) {
                throw PayFlowException.unprocessable(ErrorCode.CONFLICT,
                        "Ledger account " + account.getId() + " is " + account.getStatus());
            }
            entries.add(new LedgerEntry(posting.getId(), account.getId(), line.entryType(),
                    Money.normalize(line.amount(), command.currency()),
                    command.currency(), line.description()));
        }
        entryRepository.saveAll(entries);
        postedCounter.increment();
        log.info("Posted {} {} for merchant {}: {} {} across {} entries",
                command.sourceType(), command.sourceId(), command.merchantId(),
                totalDebit, command.currency(), entries.size());
        return posting;
    }
    @Transactional
    public LedgerPosting reverse(UUID postingId, String reason) {
        LedgerPosting original = postingRepository.findById(postingId)
                .orElseThrow(() -> PayFlowException.notFound("Posting not found"));
        String reversalSourceId = "rev_" + original.getSourceId();
        var alreadyReversed = postingRepository.findBySourceTypeAndSourceId(
                LedgerPosting.SourceType.REVERSAL, reversalSourceId);
        if (alreadyReversed.isPresent()) {
            return alreadyReversed.get();
        }
        List<LedgerEntry> originalEntries = entryRepository.findByPostingId(postingId);
        if (originalEntries.isEmpty()) {
            throw PayFlowException.unprocessable(ErrorCode.CONFLICT,
                    "Posting has no entries to reverse");
        }
        LedgerPosting reversal = new LedgerPosting(LedgerPosting.SourceType.REVERSAL,
                reversalSourceId, original.getMerchantId(), original.getCurrency(),
                original.getTotalCredit(), original.getTotalDebit(),
                "Reversal of " + original.getSourceId() + ": " + reason, CorrelationId.get());
        reversal.markReverses(postingId);
        postingRepository.saveAndFlush(reversal);
        List<LedgerEntry> reversalEntries = originalEntries.stream()
                .map(entry -> new LedgerEntry(reversal.getId(), entry.getAccountId(),
                        entry.getEntryType() == LedgerEntry.EntryType.DEBIT
                                ? LedgerEntry.EntryType.CREDIT
                                : LedgerEntry.EntryType.DEBIT,
                        entry.getAmount(), entry.getCurrency(),
                        "Reversal: " + entry.getDescription()))
                .toList();
        entryRepository.saveAll(reversalEntries);
        log.warn("Reversed posting {} ({}): {}", postingId, original.getSourceId(), reason);
        return reversal;
    }
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(UUID accountId) {
        LedgerAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> PayFlowException.notFound("Ledger account not found"));
        BigDecimal debitMinusCredit = entryRepository.debitMinusCredit(accountId);
        return account.getAccountType().normalBalance() == LedgerAccount.AccountType.Normal.DEBIT
                ? debitMinusCredit
                : debitMinusCredit.negate();
    }
    @Transactional
    public LedgerAccount resolveAccount(LedgerAccount.OwnerType ownerType, String ownerId,
                                        LedgerAccount.AccountType accountType, String currency) {
        String normalisedCurrency = currency.toUpperCase();
        return accountRepository
                .findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(
                        ownerType, ownerId, accountType, normalisedCurrency)
                .orElseGet(() -> {
                    try {
                        return accountRepository.saveAndFlush(
                                new LedgerAccount(ownerType, ownerId, accountType, normalisedCurrency));
                    } catch (DataIntegrityViolationException ex) {
                        return accountRepository
                                .findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(
                                        ownerType, ownerId, accountType, normalisedCurrency)
                                .orElseThrow(() -> ex);
                    }
                });
    }
}
