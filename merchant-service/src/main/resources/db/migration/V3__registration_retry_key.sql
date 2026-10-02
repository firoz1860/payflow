ALTER TABLE merchants ADD COLUMN registration_key VARCHAR(64);
CREATE UNIQUE INDEX uk_merchants_registration_key ON merchants (registration_key) WHERE registration_key IS NOT NULL;
