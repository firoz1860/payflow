package com.payflow.ledger.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ledger.service.LedgerService;
import com.payflow.ledger.service.PaymentPostingBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
class SharedTopicFilteringTest {
    @Test
    void unrelatedSharedEventsAreNotPostedToLedger() {
        var ledger = mock(LedgerService.class);
        var builder = mock(PaymentPostingBuilder.class);
        var consumer = new PaymentEventConsumer(ledger, builder, new ObjectMapper(), new BigDecimal("2"));
        var record = new ConsumerRecord<String,String>("payment.created", 0, 1, "key", "not-a-refund");
        record.headers().add("eventType", "audit.event".getBytes());
        consumer.onRefundCompleted(record);
        consumer.onSettlementCompleted(record);
        verifyNoInteractions(ledger, builder);
    }
}
