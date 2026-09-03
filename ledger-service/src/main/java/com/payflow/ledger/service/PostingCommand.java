package com.payflow.ledger.service;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerPosting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
public record PostingCommand(
        LedgerPosting.SourceType sourceType,
        String sourceId,
        String merchantId,
        String currency,
        String description,
        List<Line> lines
) {
    public record Line(
            LedgerAccount.OwnerType ownerType,
            String ownerId,
            LedgerAccount.AccountType accountType,
            LedgerEntry.EntryType entryType,
            BigDecimal amount,
            String description
    ) {
    }
    public static Builder builder(LedgerPosting.SourceType sourceType, String sourceId,
                                  String merchantId, String currency) {
        return new Builder(sourceType, sourceId, merchantId, currency);
    }
    public static final class Builder {
        private final LedgerPosting.SourceType sourceType;
        private final String sourceId;
        private final String merchantId;
        private final String currency;
        private final List<Line> lines = new ArrayList<>();
        private String description;
        private Builder(LedgerPosting.SourceType sourceType, String sourceId,
                        String merchantId, String currency) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.merchantId = merchantId;
            this.currency = currency;
        }
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        public Builder debit(LedgerAccount.OwnerType ownerType, String ownerId,
                             LedgerAccount.AccountType accountType, BigDecimal amount, String memo) {
            lines.add(new Line(ownerType, ownerId, accountType,
                    LedgerEntry.EntryType.DEBIT, amount, memo));
            return this;
        }
        public Builder credit(LedgerAccount.OwnerType ownerType, String ownerId,
                              LedgerAccount.AccountType accountType, BigDecimal amount, String memo) {
            lines.add(new Line(ownerType, ownerId, accountType,
                    LedgerEntry.EntryType.CREDIT, amount, memo));
            return this;
        }
        public PostingCommand build() {
            return new PostingCommand(sourceType, sourceId, merchantId, currency, description,
                    List.copyOf(lines));
        }
    }
}
