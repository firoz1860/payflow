package com.payflow.ledger.service;
import com.payflow.common.money.Money;
import com.payflow.ledger.domain.LedgerAccount;
import com.payflow.ledger.domain.LedgerPosting;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
@Component
public class PaymentPostingBuilder {
    public PostingCommand capture(String paymentReference, String merchantId, String currency,
                                  BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal taxAmount) {
        BigDecimal gross = Money.normalize(grossAmount, currency);
        BigDecimal fee = Money.normalize(feeAmount == null ? BigDecimal.ZERO : feeAmount, currency);
        BigDecimal tax = Money.normalize(taxAmount == null ? BigDecimal.ZERO : taxAmount, currency);
        BigDecimal netToMerchant = gross.subtract(fee).subtract(tax);
        if (netToMerchant.signum() < 0) {
            throw com.payflow.common.error.PayFlowException.unprocessable(
                    com.payflow.common.error.ErrorCode.LEDGER_UNBALANCED,
                    "Fees (" + fee.add(tax) + ") exceed the captured amount (" + gross + ")");
        }
        PostingCommand.Builder builder = PostingCommand
                .builder(LedgerPosting.SourceType.PAYMENT, paymentReference, merchantId, currency)
                .description("Capture of " + paymentReference)
                .debit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                        LedgerAccount.AccountType.PAYMENT_CLEARING, gross,
                        "Funds held at provider for " + paymentReference)
                .credit(LedgerAccount.OwnerType.MERCHANT, merchantId,
                        LedgerAccount.AccountType.MERCHANT_PAYABLE, netToMerchant,
                        "Net payable to merchant for " + paymentReference);
        if (fee.signum() > 0) {
            builder.credit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                    LedgerAccount.AccountType.FEE_REVENUE, fee,
                    "PayFlow fee for " + paymentReference);
        }
        if (tax.signum() > 0) {
            builder.credit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                    LedgerAccount.AccountType.TAX_PAYABLE, tax,
                    "Tax on fee for " + paymentReference);
        }
        return builder.build();
    }
    public PostingCommand refund(String refundReference, String paymentReference, String merchantId,
                                 String currency, BigDecimal refundAmount, BigDecimal feeReturned) {
        BigDecimal amount = Money.normalize(refundAmount, currency);
        BigDecimal returnedFee = Money.normalize(
                feeReturned == null ? BigDecimal.ZERO : feeReturned, currency);
        PostingCommand.Builder builder = PostingCommand
                .builder(LedgerPosting.SourceType.REFUND, refundReference, merchantId, currency)
                .description("Refund " + refundReference + " against " + paymentReference)
                .debit(LedgerAccount.OwnerType.MERCHANT, merchantId,
                        LedgerAccount.AccountType.MERCHANT_PAYABLE, amount.subtract(returnedFee),
                        "Merchant payable reduced by refund " + refundReference)
                .credit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                        LedgerAccount.AccountType.PAYMENT_CLEARING, amount,
                        "Funds returned to customer for " + refundReference);
        if (returnedFee.signum() > 0) {
            builder.debit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                    LedgerAccount.AccountType.FEE_REVENUE, returnedFee,
                    "Fee returned on refund " + refundReference);
        }
        return builder.build();
    }
    public PostingCommand settlement(String settlementReference, String merchantId, String currency,
                                     BigDecimal netAmount) {
        BigDecimal net = Money.normalize(netAmount, currency);
        return PostingCommand
                .builder(LedgerPosting.SourceType.SETTLEMENT, settlementReference, merchantId, currency)
                .description("Payout " + settlementReference)
                .debit(LedgerAccount.OwnerType.MERCHANT, merchantId,
                        LedgerAccount.AccountType.MERCHANT_PAYABLE, net,
                        "Payable discharged by settlement " + settlementReference)
                .credit(LedgerAccount.OwnerType.PLATFORM, LedgerService.PLATFORM_OWNER,
                        LedgerAccount.AccountType.PLATFORM_CASH, net,
                        "Cash paid out for settlement " + settlementReference)
                .build();
    }
}
