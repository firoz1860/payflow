-- ---------------------------------------------------------------------------
-- auth-service schema. Constraints live in the database, not only in Java:
-- a bug in the service layer must not be able to create bad data.
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id    UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE users (
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    full_name              VARCHAR(160) NOT NULL,
    merchant_id            UUID,
    status                 VARCHAR(32)  NOT NULL,
    email_verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    credentials_valid_from TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DISABLED')),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_login_attempts >= 0)
);

-- Case-insensitive uniqueness enforced by the database, not by application code.
CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email));
CREATE INDEX idx_users_merchant ON users (merchant_id) WHERE merchant_id IS NOT NULL;

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex, never the raw token
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    status     VARCHAR(16) NOT NULL,
    user_agent VARCHAR(255),
    ip_address VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_refresh_status CHECK (status IN ('ACTIVE','ROTATED','REVOKED'))
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at);

CREATE TABLE one_time_tokens (
    id          UUID PRIMARY KEY,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(32) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ott_purpose CHECK (purpose IN ('EMAIL_VERIFICATION','PASSWORD_RESET'))
);
CREATE INDEX idx_one_time_tokens_user ON one_time_tokens (user_id);
