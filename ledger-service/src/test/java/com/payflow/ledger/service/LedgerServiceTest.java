package com.payflow.ledger.service;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock
    private LedgerAccountRepository accountRepository;
    @Mock
    private LedgerPostingRepository postingRepository;
    @Mock
    private LedgerEntryRepository entryRepository;
    private LedgerService ledgerService;
    private PaymentPostingBuilder postingBuilder;
    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(accountRepository, postingRepository, entryRepository,
                new SimpleMeterRegistry());
        postingBuilder = new PaymentPostingBuilder();
    }
    @Test
    @DisplayName("a 1000 capture with a 20 fee produces balanced debits and credits")
    void captureBalances() {
        PostingCommand command = postingBuilder.capture("pay_1", "merchant-1", "INR",
                new BigDecimal("1000.00"), new BigDecimal("20.00"), BigDecimal.ZERO);
        BigDecimal debits = sum(command, LedgerEntry.EntryType.DEBIT);
        BigDecimal credits = sum(command, LedgerEntry.EntryType.CREDIT);
        assertThat(debits).isEqualByComparingTo("1000.00");
        assertThat(credits).isEqualByComparingTo("1000.00");
        BigDecimal merchantPayable = command.lines().stream()
                .filter(l -> l.accountType() == LedgerAccount.AccountType.MERCHANT_PAYABLE)
                .map(PostingCommand.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(merchantPayable).isEqualByComparingTo("980.00");
    }
    @Test
    @DisplayName("an unbalanced posting is rejected entirely and writes nothing")
    void unbalancedPostingRejected() {
        PostingCommand command = PostingCommand
                .builder(LedgerPosting.SourceType.PAYMENT, "pay_bad", "merchant-1", "INR")
                .debit(LedgerAccount.OwnerType.PLATFORM, "platform",
                        LedgerAccount.AccountType.PAYMENT_CLEARING, new BigDecimal("1000.00"), "in")
                .credit(LedgerAccount.OwnerType.MERCHANT, "merchant-1",
                        LedgerAccount.AccountType.MERCHANT_PAYABLE, new BigDecimal("999.00"), "out")
                .build();
        when(postingRepository.findBySourceTypeAndSourceId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ledgerService.post(command))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.LEDGER_UNBALANCED);
        verify(postingRepository, never()).saveAndFlush(any());
        verify(entryRepository, never()).saveAll(any());
    }
    @Test
    @DisplayName("negative amounts are rejected: direction is expressed by entry type")
    void negativeAmountsRejected() {
        PostingCommand command = PostingCommand
                .builder(LedgerPosting.SourceType.ADJUSTMENT, "adj_1", "merchant-1", "INR")
                .debit(LedgerAccount.OwnerType.PLATFORM, "platform",
                        LedgerAccount.AccountType.PAYMENT_CLEARING, new BigDecimal("-50.00"), "in")
                .credit(LedgerAccount.OwnerType.MERCHANT, "merchant-1",
                        LedgerAccount.AccountType.MERCHANT_PAYABLE, new BigDecimal("-50.00"), "out")
                .build();
        when(postingRepository.findBySourceTypeAndSourceId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ledgerService.post(command))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("strictly positive");
    }
    @Test
    @DisplayName("re-posting the same source event returns the original posting")
    void postingIsIdempotent() {
        LedgerPosting existing = new LedgerPosting(LedgerPosting.SourceType.PAYMENT, "pay_1",
                "merchant-1", "INR", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                "original", null);
        when(postingRepository.findBySourceTypeAndSourceId(
                LedgerPosting.SourceType.PAYMENT, "pay_1")).thenReturn(Optional.of(existing));
        LedgerPosting result = ledgerService.post(postingBuilder.capture("pay_1", "merchant-1",
                "INR", new BigDecimal("1000.00"), new BigDecimal("20.00"), BigDecimal.ZERO));
        assertThat(result).isSameAs(existing);
        verify(entryRepository, never()).saveAll(any());
    }
    @Test
    @DisplayName("fees exceeding the captured amount are refused")
    void feesCannotExceedCapture() {
        assertThatThrownBy(() -> postingBuilder.capture("pay_1", "merchant-1", "INR",
                new BigDecimal("10.00"), new BigDecimal("15.00"), BigDecimal.ZERO))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("exceed");
    }
    @Test
    @DisplayName("a reversal mirrors every entry and leaves the original untouched")
    void reversalMirrorsEntries() {
        var postingId = java.util.UUID.randomUUID();
        LedgerPosting original = new LedgerPosting(LedgerPosting.SourceType.PAYMENT, "pay_1",
                "merchant-1", "INR", new BigDecimal("1000.00"), new BigDecimal("1000.00"), "x", null);
        when(postingRepository.findById(postingId)).thenReturn(Optional.of(original));
        when(postingRepository.findBySourceTypeAndSourceId(
                LedgerPosting.SourceType.REVERSAL, "rev_pay_1")).thenReturn(Optional.empty());
        when(entryRepository.findByPostingId(postingId)).thenReturn(List.of(
                new LedgerEntry(postingId, java.util.UUID.randomUUID(), LedgerEntry.EntryType.DEBIT,
                        new BigDecimal("1000.00"), "INR", "in"),
                new LedgerEntry(postingId, java.util.UUID.randomUUID(), LedgerEntry.EntryType.CREDIT,
                        new BigDecimal("1000.00"), "INR", "out")));
        ledgerService.reverse(postingId, "duplicate capture");
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(entryRepository).saveAll(captor.capture());
        List<LedgerEntry> reversal = captor.getValue();
        assertThat(reversal).hasSize(2);
        assertThat(reversal.get(0).getEntryType()).isEqualTo(LedgerEntry.EntryType.CREDIT);
        assertThat(reversal.get(1).getEntryType()).isEqualTo(LedgerEntry.EntryType.DEBIT);
    }
    private BigDecimal sum(PostingCommand command, LedgerEntry.EntryType type) {
        return command.lines().stream()
                .filter(l -> l.entryType() == type)
                .map(PostingCommand.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
