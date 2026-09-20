-- Store how many new articles a fetch produced.
-- Once fetching became asynchronous the response is sent before the work starts,
-- so the result has to live in a column instead of a return value.
--
-- Also relaxes ck_fetch_job_started: a job rejected while still queued never
-- started, so it legitimately has no started_at.

ALTER TABLE fetch_job
    ADD COLUMN new_item_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN fetch_job.new_item_count IS
    'New articles stored by this fetch, after deduplication';

ALTER TABLE fetch_job
    DROP CONSTRAINT ck_fetch_job_started;

ALTER TABLE fetch_job
    ADD CONSTRAINT ck_fetch_job_started
        CHECK (status NOT IN ('RUNNING', 'SUCCESS') OR started_at IS NOT NULL);
