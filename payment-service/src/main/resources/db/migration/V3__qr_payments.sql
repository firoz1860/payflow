-- ---------------------------------------------------------------------------
-- QR / UPI-intent payments.
--   * QR becomes an accepted payment method.
--   * A QR payment carries a scannable payload (a upi://pay intent, or a provider
--     QR string/URL) and a rendered image the checkout can display directly.
-- The image is a data: URI (or a provider image URL), so TEXT rather than a bounded
-- VARCHAR: a base64 PNG comfortably exceeds any sensible VARCHAR limit.
-- ---------------------------------------------------------------------------

ALTER TABLE payments ADD COLUMN qr_code_data  TEXT;
ALTER TABLE payments ADD COLUMN qr_code_image TEXT;

-- Widen the payment-method allow-list to include QR. A DROP + re-ADD is required
-- because PostgreSQL cannot alter a CHECK constraint in place.
ALTER TABLE payment_attempts DROP CONSTRAINT chk_attempt_method;
ALTER TABLE payment_attempts ADD CONSTRAINT chk_attempt_method
    CHECK (payment_method IS NULL OR payment_method IN
        ('CARD','UPI','QR','NET_BANKING','WALLET'));
