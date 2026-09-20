-- Persist refresh tokens so they can be revoked. Only the hash is stored.

CREATE TABLE refresh_token (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,

    token_hash CHAR(64)    NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    CONSTRAINT ck_refresh_token_expiry
        CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX uq_refresh_token_hash
    ON refresh_token (token_hash);

CREATE INDEX idx_refresh_token_user_active
    ON refresh_token (user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_refresh_token_expires
    ON refresh_token (expires_at);
