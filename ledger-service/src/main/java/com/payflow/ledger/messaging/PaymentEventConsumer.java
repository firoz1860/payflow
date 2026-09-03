package com.payflow.ledger.messaging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.money.Money;
import com.payflow.common.web.CorrelationId;
import com.payflow.ledger.service.LedgerService;
import com.payflow.ledger.service.PaymentPostingBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final LedgerService ledgerService;
    private final PaymentPostingBuilder postingBuilder;
    private final ObjectMapper objectMapper;
    private final BigDecimal defaultFeePercentage;
    public PaymentEventConsumer(LedgerService ledgerService, PaymentPostingBuilder postingBuilder,
                                ObjectMapper objectMapper,
                                @Value("${payflow.ledger.default-fee-percentage:2.00}")
                                BigDecimal defaultFeePercentage) {
        this.ledgerService = ledgerService;
        this.postingBuilder = postingBuilder;
        this.objectMapper = objectMapper;
        this.defaultFeePercentage = defaultFeePercentage;
    }
    @KafkaListener(topics = "payment.captured", groupId = "ledger-service.payments",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCaptured(ConsumerRecord<String, String> record) {
        withCorrelation(record, () -> {
            JsonNode data = readData(record.value());
            String paymentReference = data.path("paymentReference").asText();
            String merchantId = data.path("merchantId").asText();
            String currency = data.path("currency").asText();
            BigDecimal amount = new BigDecimal(data.path("amount").asText());
            BigDecimal fee = Money.percentageOf(amount, defaultFeePercentage, currency);
            ledgerService.post(postingBuilder.capture(
                    paymentReference, merchantId, currency, amount, fee, BigDecimal.ZERO));
        });
    }
    @KafkaListener(topics = "refund.completed", groupId = "ledger-service.refunds",
            containerFactory = "kafkaListenerContainerFactory")
    public void onRefundCompleted(ConsumerRecord<String, String> record) {
        withCorrelation(record, () -> {
            JsonNode data = readData(record.value());
            String refundReference = data.path("refundReference").asText();
            String paymentReference = data.path("paymentReference").asText();
            String merchantId = data.path("merchantId").asText();
            String currency = data.path("currency").asText();
            BigDecimal amount = new BigDecimal(data.path("refundAmount").asText());
            ledgerService.post(postingBuilder.refund(refundReference, paymentReference,
                    merchantId, currency, amount, BigDecimal.ZERO));
        });
    }
    @KafkaListener(topics = "settlement.completed", groupId = "ledger-service.settlements",
            containerFactory = "kafkaListenerContainerFactory")
    public void onSettlementCompleted(ConsumerRecord<String, String> record) {
        withCorrelation(record, () -> {
            JsonNode data = readData(record.value());
            ledgerService.post(postingBuilder.settlement(
                    data.path("settlementReference").asText(),
                    data.path("merchantId").asText(),
                    data.path("currency").asText(),
                    new BigDecimal(data.path("netAmount").asText())));
        });
    }
    private JsonNode readData(String value) {
        try {
            return objectMapper.readTree(value).path("data");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unparseable ledger event", ex);
        }
    }
    private void withCorrelation(ConsumerRecord<String, String> record, Runnable action) {
        var header = record.headers().lastHeader(CorrelationId.HEADER);
        if (header != null) {
            CorrelationId.set(new String(header.value(), StandardCharsets.UTF_8));
        }
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error("Ledger event from {} offset {} failed: {}",
                    record.topic(), record.offset(), ex.getMessage(), ex);
            throw ex;
        } finally {
            CorrelationId.clear();
        }
    }
}
