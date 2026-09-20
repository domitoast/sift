-- Rotation support: link each token to the one that replaced it, so replaying a
-- revoked token can be detected as theft.

ALTER TABLE refresh_token
    ADD COLUMN previous_token_hash CHAR(64);

COMMENT ON COLUMN refresh_token.previous_token_hash IS
    'Hash of the token this one replaced. A match means an already-rotated token was replayed, which is treated as theft (ADR-011)';

CREATE INDEX idx_refresh_token_previous_hash
    ON refresh_token (previous_token_hash)
    WHERE previous_token_hash IS NOT NULL;
