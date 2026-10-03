ALTER TABLE payment_attempts ADD COLUMN provider_entity_payment_id VARCHAR(128);
CREATE UNIQUE INDEX uq_attempt_provider_entity ON payment_attempts(provider, provider_entity_payment_id) WHERE provider_entity_payment_id IS NOT NULL;
