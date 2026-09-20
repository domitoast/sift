-- Per-user daily call counter backing the LLM quota.

CREATE TABLE llm_usage (
    user_id    BIGINT  NOT NULL,

    usage_date DATE    NOT NULL,
    call_count INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (user_id, usage_date),

    CONSTRAINT fk_llm_usage_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    CONSTRAINT ck_llm_usage_count
        CHECK (call_count >= 0)
);

CREATE INDEX idx_llm_usage_date ON llm_usage (usage_date);
