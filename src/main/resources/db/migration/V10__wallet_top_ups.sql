-- Wallet top-ups a company makes: bank transfers with a receipt (reviewed by Intact), and later card, M-Pesa and
-- Airtel Money payments through the payment partner. The wallet is credited in billing once a top-up is approved
-- or paid; billing's external reference (topup-<id>) makes that credit happen once.
CREATE TABLE IF NOT EXISTS wallet_top_ups
(
    id                   VARCHAR(255)   NOT NULL PRIMARY KEY,
    created_at           TIMESTAMP(6),
    updated_at           TIMESTAMP(6),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    company_id           VARCHAR(255)   NOT NULL,
    -- BANK_RECEIPT, CARD, MPESA, AIRTEL_MONEY
    method               VARCHAR(20)    NOT NULL CHECK (method IN ('BANK_RECEIPT', 'CARD', 'MPESA', 'AIRTEL_MONEY')),
    -- Bank receipts: PENDING_REVIEW -> APPROVED | REJECTED | CANCELLED. Partner payments: PENDING_PAYMENT -> PAID | FAILED
    status               VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED',
                                                                   'PENDING_PAYMENT', 'PAID', 'FAILED')),
    -- What the company says it paid, and what was credited (a reviewer may credit what the bank actually shows)
    amount               NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    credited_amount      NUMERIC(19, 4),
    currency             VARCHAR(3)     NOT NULL,
    -- Bank transfer: the bank, its reference and the transfer date. Partner: its payment id in reference.
    bank_name            VARCHAR(255),
    reference            VARCHAR(255)   NOT NULL,
    paid_on              DATE,
    receipt_storage_key  VARCHAR(512),
    receipt_filename     VARCHAR(255),
    receipt_content_type VARCHAR(100),
    receipt_size         BIGINT,
    submitted_by         VARCHAR(255)   NOT NULL,
    note                 VARCHAR(1000),
    reviewed_by          VARCHAR(255),
    reviewed_at          TIMESTAMP(6),
    review_note          VARCHAR(1000),
    credited_at          TIMESTAMP(6)
);
CREATE INDEX IF NOT EXISTS idx_wallet_top_ups_company ON wallet_top_ups (company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_top_ups_status ON wallet_top_ups (status, created_at);
-- One live claim per bank reference: a rejected or cancelled one can be submitted again
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_top_ups_reference ON wallet_top_ups (method, LOWER(reference))
    WHERE status NOT IN ('REJECTED', 'CANCELLED', 'FAILED');
