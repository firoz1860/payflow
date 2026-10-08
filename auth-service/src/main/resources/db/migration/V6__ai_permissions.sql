-- ---------------------------------------------------------------------------
-- Stage 8: PayFlow Copilot RBAC. The AI assistant is gated by permissions,
-- not by role. ai:use grants interactive access; ai:admin adds administrative
-- control. Granted to platform operators and to merchant roles that own or
-- build the integration. Finance and support roles are intentionally excluded.
-- Permissions are stored as the enum constant name (see Permission).
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission) VALUES
    -- PAYFLOW_ADMIN: full AI access
    ('11111111-1111-1111-1111-111111111111', 'AI_USE'),
    ('11111111-1111-1111-1111-111111111111', 'AI_ADMIN'),

    -- MERCHANT_OWNER: interactive AI access
    ('22222222-2222-2222-2222-222222222222', 'AI_USE'),

    -- MERCHANT_DEVELOPER: interactive AI access
    ('33333333-3333-3333-3333-333333333333', 'AI_USE');
