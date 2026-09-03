package com.payflow.ledger.repository;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public final class LedgerRepositories {
    private LedgerRepositories() {
    }
    @Repository
    public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
        Optional<LedgerAccount> findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(
                LedgerAccount.OwnerType ownerType, String ownerId,
                LedgerAccount.AccountType accountType, String currency);
        List<LedgerAccount> findByOwnerId(String ownerId);
    }
    @Repository
    public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, UUID> {
        Optional<LedgerPosting> findBySourceTypeAndSourceId(LedgerPosting.SourceType sourceType,
                                                            String sourceId);
        boolean existsBySourceTypeAndSourceId(LedgerPosting.SourceType sourceType, String sourceId);
        List<LedgerPosting> findByMerchantIdOrderByCreatedAtDesc(String merchantId,
                                                                 org.springframework.data.domain.Pageable pageable);
    }
    @Repository
    public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
        List<LedgerEntry> findByPostingId(UUID postingId);
        List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId,
                                                             org.springframework.data.domain.Pageable pageable);
        @Query("""
                select coalesce(sum(case when e.entryType = 'DEBIT'
                                         then e.amount else -e.amount end), 0)
                from LedgerEntry e where e.accountId = :accountId
                """)
        BigDecimal debitMinusCredit(@Param("accountId") UUID accountId);
        @Query("""
                select coalesce(sum(case when e.entryType = 'DEBIT'
                                         then e.amount else -e.amount end), 0)
                from LedgerEntry e where e.currency = :currency
                """)
        BigDecimal globalImbalance(@Param("currency") String currency);
        @Query("select distinct e.currency from LedgerEntry e")
        List<String> distinctCurrencies();
    }
}
