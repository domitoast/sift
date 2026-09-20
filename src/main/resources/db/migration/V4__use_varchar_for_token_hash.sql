-- CHAR pads with spaces, which broke hash comparisons. Switch to VARCHAR.

ALTER TABLE refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64),
    ALTER COLUMN previous_token_hash TYPE VARCHAR(64);
