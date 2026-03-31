CREATE TABLE cards
(
    id               UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    encrypted_number VARCHAR(512)   NOT NULL,
    masked_number    VARCHAR(19)    NOT NULL,
    owner_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expiration_date  DATE           NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    balance          NUMERIC(19, 4) NOT NULL DEFAULT 0
);

CREATE INDEX idx_cards_owner_id ON cards (owner_id);
CREATE INDEX idx_cards_status ON cards (status);
