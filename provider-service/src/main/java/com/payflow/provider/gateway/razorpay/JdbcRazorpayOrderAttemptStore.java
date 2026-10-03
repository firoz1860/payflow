package com.payflow.provider.gateway.razorpay;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
@Repository
public class JdbcRazorpayOrderAttemptStore implements RazorpayOrderAttemptStore {
    private final JdbcTemplate jdbc;
    public JdbcRazorpayOrderAttemptStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Claim claim(String keyHash, String reference, String requestHash) {
        int inserted = jdbc.update("INSERT INTO razorpay_order_attempts(key_hash,payment_reference,request_hash,state) VALUES (?,?,?,'CREATING') ON CONFLICT (key_hash) DO NOTHING", keyHash, reference, requestHash);
        if (inserted == 1) return new Claim(true, null);
        return jdbc.queryForObject("SELECT payment_reference,request_hash,state,response_json FROM razorpay_order_attempts WHERE key_hash=?", (rs, row) -> {
            if (!reference.equals(rs.getString("payment_reference")) || !requestHash.equals(rs.getString("request_hash"))) {
                throw PayFlowException.conflict(ErrorCode.CONFLICT, "Provider request already belongs to another payment; reconcile the original reference");
            }
            if ("CREATED".equals(rs.getString("state"))) return new Claim(false, rs.getString("response_json"));
            throw new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Provider order outcome requires reconciliation; no new order was created");
        }, keyHash);
    }
    @Override public void complete(String keyHash, String responseJson) {
        if (jdbc.update("UPDATE razorpay_order_attempts SET state='CREATED',response_json=?,updated_at=now() WHERE key_hash=? AND state='CREATING'", responseJson, keyHash) != 1)
            throw new IllegalStateException("Provider attempt cannot be completed");
    }
    @Override public void uncertain(String keyHash) {
        jdbc.update("UPDATE razorpay_order_attempts SET state='RECONCILIATION_REQUIRED',updated_at=now() WHERE key_hash=? AND state='CREATING'", keyHash);
    }
}
