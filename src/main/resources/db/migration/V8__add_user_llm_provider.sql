-- Record which LLM provider each key belongs to, with a check constraint keeping
-- provider and key either both set or both null.

ALTER TABLE app_user
    ADD COLUMN llm_provider VARCHAR(20);

UPDATE app_user
SET llm_provider = 'GEMINI'
WHERE llm_api_key_encrypted IS NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT ck_user_llm_consistency
        CHECK (
            (llm_api_key_encrypted IS NULL AND llm_provider IS NULL) OR
            (llm_api_key_encrypted IS NOT NULL AND llm_provider IS NOT NULL)
        );

ALTER TABLE app_user
    ADD CONSTRAINT ck_user_llm_provider
        CHECK (llm_provider IS NULL OR llm_provider IN ('GEMINI', 'FAKE'));
