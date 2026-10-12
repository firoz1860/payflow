CREATE TABLE site_hero (
    id integer PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0,
    badge varchar(100) NOT NULL,
    title varchar(100) NOT NULL,
    accent varchar(140) NOT NULL,
    description varchar(600) NOT NULL,
    supporting_text varchar(300) NOT NULL,
    primary_label varchar(60) NOT NULL,
    secondary_label varchar(60) NOT NULL,
    trust_points text NOT NULL
);

INSERT INTO site_hero (id, badge, title, accent, description, supporting_text, primary_label, secondary_label, trust_points)
VALUES (1, 'Payment processing with a clear audit trail', 'Accept payments.', 'Keep your records in balance.',
    'PayFlow brings payment collection, transaction tracking, and ledger records into one merchant workspace. Create payments through an API, offer hosted or UPI QR checkout, and follow each transaction from initiation to its recorded outcome.',
    'Built to prevent duplicate payment creation during retries, verify provider updates, and keep financial entries traceable.',
    'Create merchant account', 'See how payments work',
    E'Retry-safe payment APIs\nVerified provider updates\nBalanced ledger entries\nMerchant dashboard');
