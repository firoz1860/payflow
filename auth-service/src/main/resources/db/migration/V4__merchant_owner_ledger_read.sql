-- Merchant owners need read-only visibility into their own ledger in the dashboard.
-- The Ledger Service still enforces merchant scope server-side.
INSERT INTO role_permissions (role_id, permission)
VALUES ('22222222-2222-2222-2222-222222222222', 'LEDGER_READ')
ON CONFLICT DO NOTHING;
