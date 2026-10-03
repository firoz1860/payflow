package com.payflow.ledger.integration;
import com.payflow.ledger.messaging.PaymentEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Testcontainers @ActiveProfiles("test")
class CaptureLedgerIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PaymentEventConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Test void redeliveredCaptureCreatesOneBalancedPosting() {
        String reference="pay_"+UUID.randomUUID();
        String body="{\"data\":{\"paymentReference\":\""+reference+"\",\"merchantId\":\""+UUID.randomUUID()+"\",\"amount\":\"1.00\",\"currency\":\"INR\"}}";
        var record=new ConsumerRecord<String,String>("payment.captured",0,1,reference,body);
        consumer.onPaymentCaptured(record);consumer.onPaymentCaptured(record);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ledger_postings WHERE source_id=?",Integer.class,reference));
        BigDecimal debit=jdbc.queryForObject("SELECT total_debit FROM ledger_postings WHERE source_id=?",BigDecimal.class,reference);
        BigDecimal credit=jdbc.queryForObject("SELECT total_credit FROM ledger_postings WHERE source_id=?",BigDecimal.class,reference);
        assertEquals(0,debit.compareTo(credit));assertEquals(0,debit.compareTo(new BigDecimal("1.00")));
    }
}
