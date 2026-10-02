-- Persist merchant-owner onboarding across sessions and devices.
-- Existing users are treated as already onboarded to avoid forcing a new
-- first-run flow on established accounts. New users start at NOT_STARTED.

ALTER TABLE users
    ADD COLUMN onboarding_status VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN onboarding_last_step INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN onboarding_dismissed_at TIMESTAMPTZ,
    ADD COLUMN onboarding_completed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT chk_users_onboarding_status
        CHECK (onboarding_status IN ('NOT_STARTED', 'IN_PROGRESS', 'DISMISSED', 'COMPLETED')),
    ADD CONSTRAINT chk_users_onboarding_last_step
        CHECK (onboarding_last_step BETWEEN 1 AND 5);

ALTER TABLE users ALTER COLUMN onboarding_status SET DEFAULT 'NOT_STARTED';
ALTER TABLE users ALTER COLUMN onboarding_last_step SET DEFAULT 1;
ALTER TABLE users ALTER COLUMN onboarding_completed_at DROP DEFAULT;
