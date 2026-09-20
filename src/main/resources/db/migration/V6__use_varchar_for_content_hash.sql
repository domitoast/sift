-- Same CHAR padding problem as V4, this time for the dedup fingerprint.

ALTER TABLE fetched_item
    ALTER COLUMN content_hash TYPE VARCHAR(64);
