-- PayFlow Copilot owns ai_agent_db. Privacy-aware by design: NO secret is ever
-- stored here — not LLM/BYOK keys, API-key secrets, provider secrets, JWTs,
-- refresh tokens, DB credentials, PAN or CVV. Tool executions store references
-- and status, never raw tool output.

CREATE TABLE ai_conversations (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    -- null only for a platform-admin (cross-tenant) conversation
    merchant_id   UUID,
    title         VARCHAR(200) NOT NULL,
    mode          VARCHAR(24)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_conv_mode   CHECK (mode IN ('MERCHANT', 'PLATFORM')),
    CONSTRAINT chk_ai_conv_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'))
);
CREATE INDEX idx_ai_conv_user    ON ai_conversations (user_id, updated_at DESC);
CREATE INDEX idx_ai_conv_merchant ON ai_conversations (merchant_id, updated_at DESC);
CREATE INDEX idx_ai_conv_created ON ai_conversations (created_at);

CREATE TABLE ai_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    -- deterministic answer metadata (evidence/confidence/warnings/toolCalls), never secrets
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_msg_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);
CREATE INDEX idx_ai_msg_conversation ON ai_messages (conversation_id, created_at);

CREATE TABLE ai_tool_executions (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    message_id      UUID        REFERENCES ai_messages (id) ON DELETE SET NULL,
    tool_name       VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    -- a safe, redacted reference to what was fetched (e.g. "payment:pay_xxx"), never raw output
    resource_ref    VARCHAR(128),
    latency_ms      INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_tool_status CHECK (status IN ('SUCCESS', 'FAILURE', 'DENIED', 'TIMEOUT'))
);
CREATE INDEX idx_ai_tool_conversation ON ai_tool_executions (conversation_id, created_at);

CREATE TABLE ai_approvals (
    id                    UUID PRIMARY KEY,
    conversation_id       UUID         REFERENCES ai_conversations (id) ON DELETE SET NULL,
    requested_action      VARCHAR(64)  NOT NULL,
    -- already-sanitized parameters (redaction applied before persistence)
    sanitized_params      JSONB        NOT NULL,
    requesting_user_id    UUID         NOT NULL,
    merchant_id           UUID,
    required_permission   VARCHAR(64)  NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at            TIMESTAMPTZ  NOT NULL,
    approving_user_id     UUID,
    execution_result_ref  VARCHAR(128),
    CONSTRAINT chk_ai_appr_status CHECK (status IN
        ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'EXECUTED', 'FAILED'))
);
CREATE INDEX idx_ai_appr_status ON ai_approvals (status, expires_at);
CREATE INDEX idx_ai_appr_merchant ON ai_approvals (merchant_id, created_at DESC);

CREATE TABLE ai_feedback (
    id          UUID PRIMARY KEY,
    message_id  UUID        NOT NULL REFERENCES ai_messages (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL,
    rating      VARCHAR(8)  NOT NULL,
    comment     VARCHAR(2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_feedback_rating CHECK (rating IN ('UP', 'DOWN')),
    CONSTRAINT uk_ai_feedback_message_user UNIQUE (message_id, user_id)
);
