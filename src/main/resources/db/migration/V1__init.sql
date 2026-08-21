-- =============================================================================
-- V1__init.sql
--
-- 建立 Sift 的初始資料庫結構。
-- 依據：docs/DATABASE_DESIGN.md、docs/ER_DIAGRAM.md
-- 相關決策：
--   ADR-002（staging vs curated）
--   ADR-004（dedup 依據）
--   ADR-005（soft delete）
--   ADR-008（Day 5 schema 精簡）
--
-- ⚠️ 本檔案一經推送至遠端或被他人執行，即為唯讀。
--    屆時任何結構調整請新增 V2__xxx.sql。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- updated_at 自動更新機制
--
-- created_at 用 DEFAULT now() 即可，但 updated_at 不行——
-- DEFAULT 只在 INSERT 時生效，UPDATE 時不會重新計算。
--
-- 選擇由資料庫的 trigger 維護而非應用層，
-- 理由是「不依賴呼叫者記得做」：即使有人直接下 SQL 更新，值仍然正確。
--
-- 注意：document_version 沒有 updated_at，因為快照永遠不會被修改。
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- 1. app_user
--
-- 命名為 app_user 而非 user，因為 user 是 PostgreSQL 保留字。
-- =============================================================================
CREATE TABLE app_user (
    id                    BIGSERIAL   PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    -- BCrypt 的輸出長度固定為 60 字元
    password_hash         VARCHAR(60)  NOT NULL,
    -- 加密後的 LLM API key（ADR-003 BYOK）。未設定時為 NULL。
    llm_api_key_encrypted TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);

-- partial unique index：soft delete 之下，唯一約束必須加條件，
-- 否則帳號刪除後該 email 永遠無法重新註冊（ADR-005）。
CREATE UNIQUE INDEX uq_app_user_email
    ON app_user (email)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 2. source（訂閱來源）
-- =============================================================================
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

    -- 不使用 PostgreSQL 原生 ENUM：新增值需 ALTER TYPE，在 migration 中不易管理。
    -- VARCHAR + CHECK 效果相同但彈性更高。
    CONSTRAINT ck_source_type
        CHECK (type IN ('RSS', 'ATOM'))
);

CREATE UNIQUE INDEX uq_source_user_url
    ON source (user_id, url)
    WHERE deleted_at IS NULL;

-- 服務排程查詢：「撈出未刪除且啟用中的來源」
CREATE INDEX idx_source_scheduling
    ON source (deleted_at, enabled);

CREATE TRIGGER trg_source_updated_at
    BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 3. fetch_job（抓取任務）
--
-- 一次排程觸發、對一個 source 執行一次抓取 = 一筆紀錄。
-- 注意：一筆 fetch_job 通常會產生數十筆 fetched_item。
--
-- 本表不含 retry 欄位（ADR-008）：
-- 本系統為每日排程，來源抓取失敗時等待下次排程即可，
-- 不需要在同一天內做 exponential backoff。
-- 真正需要分鐘級 retry 的是 LLM 摘要，設計於 fetched_item。
-- =============================================================================
CREATE TABLE fetch_job (
    id             BIGSERIAL   PRIMARY KEY,
    source_id      BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    -- 失敗分類，供診斷與統計：
    --   TRANSIENT：timeout、連線失敗、對方回 5xx（下次排程可望成功）
    --   PERMANENT：404、回應非合法 XML（來源本身有問題，需人工處理）
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

    -- 狀態與時間戳的一致性：只有離開 PENDING 之後才會有 started_at
    CONSTRAINT ck_fetch_job_started
        CHECK (status = 'PENDING' OR started_at IS NOT NULL),

    -- 失敗時必須說明原因，否則這筆紀錄沒有診斷價值
    CONSTRAINT ck_fetch_job_failure_reason
        CHECK (status <> 'FAILED' OR failure_type IS NOT NULL)
);

-- 服務 FR-2.4：「顯示某來源最近 N 次抓取結果」
-- 同時也是「這個來源最後一次成功是什麼時候」的查詢依據
--（因此 source 不需要 last_success_at 欄位）
CREATE INDEX idx_fetch_job_source_created
    ON fetch_job (source_id, created_at DESC);

CREATE TRIGGER trg_fetch_job_updated_at
    BEFORE UPDATE ON fetch_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 4. fetched_item（staging：抓取項目）
--
-- 管線的工作區。此處的資料可能重複、可能摘要失敗、可能是垃圾內容。
-- 唯有狀態為 READY 者可 promote 為 document（ADR-002）。
--
-- retry 相關欄位保留於此表：LLM API 的失敗（rate limit、timeout）
-- 通常在數分鐘內恢復，exponential backoff 在此有實際意義。
-- =============================================================================
CREATE TABLE fetched_item (
    id             BIGSERIAL     PRIMARY KEY,
    source_id      BIGINT        NOT NULL,
    fetch_job_id   BIGINT        NOT NULL,
    external_url   VARCHAR(1000) NOT NULL,
    -- SHA-256 轉十六進位固定 64 字元。dedup 的依據（ADR-004）。
    content_hash   CHAR(64)      NOT NULL,
    title          VARCHAR(500)  NOT NULL,
    -- 原始內文。promote 後 30 天由清除排程設為 NULL，
    -- 僅保留 metadata 供追查（FR-3.12）。
    raw_content    TEXT,
    published_at   TIMESTAMPTZ,
    status         VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    retry_count    INTEGER       NOT NULL DEFAULT 0,
    -- 存「下次何時重試」而非「已等待多久」：
    -- 排程只需查 next_retry_at <= now()，且服務重啟不會遺失計時狀態。
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

    -- READY 之後的狀態必須已有摘要。
    -- 少了這條，程式邏輯出錯時會產生「狀態說好了但摘要是空的」的矛盾資料。
    CONSTRAINT ck_fetched_item_summary
        CHECK (status NOT IN ('READY', 'PROMOTED') OR summary IS NOT NULL),

    -- 已 promote 者必須有時間戳，否則 30 天清除排程無法判斷
    CONSTRAINT ck_fetched_item_promoted
        CHECK (status <> 'PROMOTED' OR promoted_at IS NOT NULL)
);

-- ★ dedup 的實作點（ADR-004）。
-- 寫入採 insert-or-ignore：直接 INSERT，
-- 收到 unique violation 即視為重複並跳過。
-- 不採「先查再寫」——後者在並發下有 race condition。
CREATE UNIQUE INDEX uq_fetched_item_source_hash
    ON fetched_item (source_id, content_hash);

-- 服務摘要工作者：「撈出待摘要或待重試的項目」
CREATE INDEX idx_fetched_item_processing
    ON fetched_item (status, next_retry_at);

-- 服務 30 天清除排程（FR-3.12）
CREATE INDEX idx_fetched_item_promoted_at
    ON fetched_item (promoted_at)
    WHERE promoted_at IS NOT NULL;

CREATE TRIGGER trg_fetched_item_updated_at
    BEFORE UPDATE ON fetched_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 5. document（知識庫）
--
-- curated 層：一律為成品，不含半成品或失敗中的資料。
--
-- ADR-008 移除了 note 欄位。連帶影響：
-- 由 FETCHED 晉升的文件，其 content 也可自由編輯。
-- 原文若需追溯，30 天內可於 fetched_item.raw_content 查得。
-- 兩種 origin 的編輯行為因此一致，不再有特例。
-- =============================================================================
CREATE TABLE document (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    origin          VARCHAR(10)  NOT NULL,
    -- 由哪筆 fetched_item promote 而來。MANUAL 時為 NULL。
    fetched_item_id BIGINT,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,
    -- ⚠️ 此欄位供 optimistic lock 使用（Hibernate @Version），
    --    與 document_version 表完全無關：
    --      document.version → 併發控制，使用者看不到
    --      document_version → 使用者可見的歷史快照
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

    -- origin 與 fetched_item_id 必須一致：
    -- FETCHED 一定有來源，MANUAL 一定沒有。
    -- 少了這條，會產生「來源不明的抓取文件」這種矛盾資料且無錯誤提示。
    CONSTRAINT ck_document_origin_consistency
        CHECK (
            (origin = 'FETCHED' AND fetched_item_id IS NOT NULL) OR
            (origin = 'MANUAL'  AND fetched_item_id IS NULL)
        )
);

-- idempotency 的第二道防線：
-- 即使 promote 邏輯因 retry 執行兩次，資料庫也只允許第一次成功。
CREATE UNIQUE INDEX uq_document_fetched_item
    ON document (fetched_item_id)
    WHERE fetched_item_id IS NOT NULL;

-- 服務使用者的文件列表查詢
CREATE INDEX idx_document_user_list
    ON document (user_id, deleted_at, created_at DESC);

CREATE TRIGGER trg_document_updated_at
    BEFORE UPDATE ON document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 6. document_version（文件歷史版本）
--
-- 儲存完整快照而非差異（diff）：實作簡單、還原快，
-- 代價是空間。以本專案的資料量（1 萬筆以內）完全可接受。
--
-- 本表沒有 updated_at 也沒有 trigger：
-- 快照一旦建立就不會被修改，加上該欄位等於在說謊。
-- =============================================================================
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

-- 同一份文件不可有兩個相同的版本號
CREATE UNIQUE INDEX uq_document_version
    ON document_version (document_id, version_number);

-- 服務版本歷史列表（新版在前）
CREATE INDEX idx_document_version_history
    ON document_version (document_id, version_number DESC);
