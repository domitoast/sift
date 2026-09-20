-- At most one unfinished job per source.
-- Partial index rather than a plain UNIQUE: finished jobs accumulate by design,
-- so the constraint has to apply only to active ones.

CREATE UNIQUE INDEX uq_fetch_job_active
    ON fetch_job (source_id)
    WHERE status IN ('PENDING', 'RUNNING');
