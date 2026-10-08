-- Owners manage their own merchant and must be able to inspect its ledger.
-- Ledger endpoints retain their merchant ownership checks; other roles are unchanged.
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'LEDGER_READ' FROM roles WHERE name = 'MERCHANT_OWNER'
ON CONFLICT (role_id, permission) DO NOTHING;
