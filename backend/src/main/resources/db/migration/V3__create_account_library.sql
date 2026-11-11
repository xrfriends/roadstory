CREATE TABLE account_saved_item (
    account_id VARCHAR(255) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('STORY', 'COURSE')),
    item_id VARCHAR(100) NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, kind, item_id)
);

CREATE TABLE account_listening_history (
    account_id VARCHAR(255) NOT NULL,
    story_id VARCHAR(100) NOT NULL,
    played_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, story_id)
);

CREATE INDEX idx_account_listening_history_recent
    ON account_listening_history (account_id, played_at DESC, story_id);

CREATE TABLE account_preferences (
    account_id VARCHAR(255) PRIMARY KEY,
    categories JSONB NOT NULL CHECK (jsonb_typeof(categories) = 'object'),
    autoplay BOOLEAN NOT NULL,
    family_mode BOOLEAN NOT NULL
);
