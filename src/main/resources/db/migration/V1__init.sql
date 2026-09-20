-- Initial schema: users, sources, fetch jobs, fetched items, documents and
-- version history, plus the updated_at trigger shared by every table.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE app_user (
    id                    BIGSERIAL   PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,

    password_hash         VARCHAR(60)  NOT NULL,

    llm_api_key_encrypted TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_app_user_email
    ON app_user (email)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE source (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    name       VARCHAR(200)  NOT NULL,
    url        VARCHAR(1000) NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_source_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    CONSTRAINT ck_source_type
        CHECK (type IN ('RSS', 'ATOM'))
);

CREATE UNIQUE INDEX uq_source_user_url
    ON source (user_id, url)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_source_scheduling
    ON source (deleted_at, enabled);

CREATE TRIGGER trg_source_updated_at
    BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE fetch_job (
    id             BIGSERIAL   PRIMARY KEY,
    source_id      BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,

    failure_type   VARCHAR(20),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_fetch_job_source
        FOREIGN KEY (source_id) REFERENCES source (id) ON DELETE RESTRICT,

    CONSTRAINT ck_fetch_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),

    CONSTRAINT ck_fetch_job_failure_type
        CHECK (failure_type IS NULL OR failure_type IN ('TRANSIENT', 'PERMANENT')),

    CONSTRAINT ck_fetch_job_started
        CHECK (status = 'PENDING' OR started_at IS NOT NULL),

    CONSTRAINT ck_fetch_job_failure_reason
        CHECK (status <> 'FAILED' OR failure_type IS NOT NULL)
);

CREATE INDEX idx_fetch_job_source_created
    ON fetch_job (source_id, created_at DESC);

CREATE TRIGGER trg_fetch_job_updated_at
    BEFORE UPDATE ON fetch_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE fetched_item (
    id             BIGSERIAL     PRIMARY KEY,
    source_id      BIGINT        NOT NULL,
    fetch_job_id   BIGINT        NOT NULL,
    external_url   VARCHAR(1000) NOT NULL,

    content_hash   CHAR(64)      NOT NULL,
    title          VARCHAR(500)  NOT NULL,

    raw_content    TEXT,
    published_at   TIMESTAMPTZ,
    status         VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    retry_count    INTEGER       NOT NULL DEFAULT 0,

    next_retry_at  TIMESTAMPTZ,
    failure_type   VARCHAR(20),
    failure_reason TEXT,
    summary        TEXT,
    promoted_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_fetched_item_source
        FOREIGN KEY (source_id) REFERENCES source (id) ON DELETE RESTRICT,

    CONSTRAINT fk_fetched_item_fetch_job
        FOREIGN KEY (fetch_job_id) REFERENCES fetch_job (id) ON DELETE RESTRICT,

    CONSTRAINT ck_fetched_item_status
        CHECK (status IN ('NEW', 'SUMMARIZING', 'READY', 'PROMOTED', 'FAILED', 'DISCARDED')),

    CONSTRAINT ck_fetched_item_failure_type
        CHECK (failure_type IS NULL OR failure_type IN ('TRANSIENT', 'PERMANENT')),

    CONSTRAINT ck_fetched_item_summary
        CHECK (status NOT IN ('READY', 'PROMOTED') OR summary IS NOT NULL),

    CONSTRAINT ck_fetched_item_promoted
        CHECK (status <> 'PROMOTED' OR promoted_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_fetched_item_source_hash
    ON fetched_item (source_id, content_hash);

CREATE INDEX idx_fetched_item_processing
    ON fetched_item (status, next_retry_at);

CREATE INDEX idx_fetched_item_promoted_at
    ON fetched_item (promoted_at)
    WHERE promoted_at IS NOT NULL;

CREATE TRIGGER trg_fetched_item_updated_at
    BEFORE UPDATE ON fetched_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE document (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    origin          VARCHAR(10)  NOT NULL,

    fetched_item_id BIGINT,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,

    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT fk_document_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    CONSTRAINT fk_document_fetched_item
        FOREIGN KEY (fetched_item_id) REFERENCES fetched_item (id) ON DELETE RESTRICT,

    CONSTRAINT ck_document_origin
        CHECK (origin IN ('MANUAL', 'FETCHED')),

    CONSTRAINT ck_document_origin_consistency
        CHECK (
            (origin = 'FETCHED' AND fetched_item_id IS NOT NULL) OR
            (origin = 'MANUAL'  AND fetched_item_id IS NULL)
        )
);

CREATE UNIQUE INDEX uq_document_fetched_item
    ON document (fetched_item_id)
    WHERE fetched_item_id IS NOT NULL;

CREATE INDEX idx_document_user_list
    ON document (user_id, deleted_at, created_at DESC);

CREATE TRIGGER trg_document_updated_at
    BEFORE UPDATE ON document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE document_version (
    id             BIGSERIAL    PRIMARY KEY,
    document_id    BIGINT       NOT NULL,
    version_number INTEGER      NOT NULL,
    title          VARCHAR(500) NOT NULL,
    content        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_document_version_document
        FOREIGN KEY (document_id) REFERENCES document (id) ON DELETE RESTRICT,

    CONSTRAINT ck_document_version_number
        CHECK (version_number >= 1)
);

CREATE UNIQUE INDEX uq_document_version
    ON document_version (document_id, version_number);

CREATE INDEX idx_document_version_history
    ON document_version (document_id, version_number DESC);
