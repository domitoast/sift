-- =============================================================================
-- V1__init.sql
--
-- 建立 Sift 的初始資料庫結構。
-- 依據：docs/DATABASE_DESIGN.md、docs/ER_DIAGRAM.md
-- 相關決策：ADR-002（staging vs curated）、ADR-004（dedup）、ADR-005（soft delete）
--
-- ⚠️ 本檔案一經執行即為唯讀。任何結構調整請新增 V2__xxx.sql，
--    切勿修改此檔——Flyway 會以 checksum 偵測並拒絕啟動。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- updated_at 自動更新機制
--
-- created_at 可以用 DEFAULT now() 解決，但 updated_at 不行——
-- DEFAULT 只在 INSERT 時生效，UPDATE 時不會重新計算。
--
-- 有兩種做法：
--   (a) 應用層：由 Hibernate 的 @UpdateTimestamp 負責
--   (b) 資料庫層：用 trigger，由資料庫自己維護
--
-- 這裡選 (b)。理由是「不依賴呼叫者記得做」——
-- 即使有人繞過應用程式直接下 SQL 更新，updated_at 仍然正確。
-- 這與 unique constraint 的思路一致：把保證放在資料庫層。
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
-- 命名為 app_user 而非 user，因為 user 是 PostgreSQL 保留字，
-- 直接使用需要每次加雙引號。
-- =============================================================================
CREATE TABLE app_user (
    id                    BIGSERIAL   PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    -- BCrypt 的輸出長度固定為 60 字元，不需要更長
    password_hash         VARCHAR(60)  NOT NULL,
    -- 加密後的 LLM API key（ADR-003 BYOK）。未設定時為 NULL。
    -- 加密後長度會膨脹且不易預估上限，故用 TEXT。
    llm_api_key_encrypted TEXT,
    -- 每日 LLM 呼叫次數與其對應日期。
    -- 用「計數 + 日期」兩欄而非查詢紀錄表，查詢成本為 O(1)；
    -- 跨日時比對日期不同即歸零。
    daily_llm_call_count  INTEGER      NOT NULL DEFAULT 0,
    daily_llm_count_date  DATE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);

-- soft delete 之下，唯一約束必須加條件，
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
    id                        BIGSERIAL     PRIMARY KEY,
    user_id                   BIGINT        NOT NULL,
    name                      VARCHAR(200)  NOT NULL,
    url                       VARCHAR(1000) NOT NULL,
    type                      VARCHAR(20)   NOT NULL,
    enabled                   BOOLEAN       NOT NULL DEFAULT TRUE,
    -- 預設 1440 分鐘 = 一天一次
    fetch_interval_minutes    INTEGER       NOT NULL DEFAULT 1440,
    last_success_at           TIMESTAMPTZ,
    -- 連續失敗次數。用於判斷來源是否已永久失效，
    -- 例如連續失敗達一定次數即自動停用，避免每天無謂重試。
    consecutive_failure_count INTEGER       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ,

    CONSTRAINT fk_source_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    -- 不使用 PostgreSQL 原生 ENUM 型別：
    -- 原生 enum 新增值需執行 ALTER TYPE，在 migration 中不易管理。
    -- VARCHAR + CHECK 效果相同但彈性更高，為業界常見做法。
    CONSTRAINT ck_source_type
        CHECK (type IN ('RSS', 'ATOM')),

    CONSTRAINT ck_source_interval
        CHECK (fetch_interval_minutes >= 5)
);

-- partial unique index：僅約束未刪除的資料列，
-- 使得使用者刪除某來源後仍可重新訂閱同一網址（ADR-005）。
CREATE UNIQUE INDEX uq_source_user_url
    ON source (user_id, url)
    WHERE deleted_at IS NULL;

-- 服務排程查詢：「撈出未刪除、啟用中、且該抓取的來源」
CREATE INDEX idx_source_scheduling
    ON source (deleted_at, enabled, last_success_at);

CREATE TRIGGER trg_source_updated_at
    BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 3. fetch_job（抓取任務）
--
-- 一次排程觸發對一個 source 產生一筆紀錄。
-- =============================================================================
CREATE TABLE fetch_job (
    id             BIGSERIAL   PRIMARY KEY,
    source_id      BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    retry_count    INTEGER     NOT NULL DEFAULT 0,
    -- 存「下次何時重試」而非「已等待多久」。
    -- 排程只需查 next_retry_at <= now() 即可撈出待重試任務，
    -- 且服務重啟後不會遺失計時狀態。
    next_retry_at  TIMESTAMPTZ,
    -- 失敗分類，決定是否值得 retry：
    --   TRANSIENT（暫時性）：timeout、連線失敗、對方回 5xx → 值得 retry
    --   PERMANENT（永久性）：404、回應非合法 XML → retry 無意義
    failure_type   VARCHAR(20),
    failure_reason TEXT,
    item_count     INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_fetch_job_source
        FOREIGN KEY (source_id) REFERENCES source (id) ON DELETE RESTRICT,

    CONSTRAINT ck_fetch_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),

    CONSTRAINT ck_fetch_job_failure_type
        CHECK (failure_type IS NULL OR failure_type IN ('TRANSIENT', 'PERMANENT')),

    -- 狀態與時間戳的一致性：只有 RUNNING 之後才可能有 started_at。
    -- 這類約束能在資料層擋掉狀態機被錯誤操作的情況。
    CONSTRAINT ck_fetch_job_started
        CHECK (status = 'PENDING' OR started_at IS NOT NULL)
);

-- 服務 FR-2.4：「顯示某來源最近 N 次抓取結果」
CREATE INDEX idx_fetch_job_source_created
    ON fetch_job (source_id, created_at DESC);

-- 服務 retry 排程：「撈出該重試的任務」
CREATE INDEX idx_fetch_job_retry
    ON fetch_job (status, next_retry_at);

CREATE TRIGGER trg_fetch_job_updated_at
    BEFORE UPDATE ON fetch_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 4. fetched_item（staging：抓取項目）
--
-- 管線的工作區。此處的資料可能重複、可能摘要失敗、可能是垃圾內容。
-- 唯有狀態為 READY 者可 promote 為 document（ADR-002）。
-- =============================================================================
CREATE TABLE fetched_item (
    id             BIGSERIAL     PRIMARY KEY,
    source_id      BIGINT        NOT NULL,
    fetch_job_id   BIGINT        NOT NULL,
    external_url   VARCHAR(1000) NOT NULL,
    -- SHA-256 轉十六進位字串固定 64 字元，故用 CHAR(64)。
    -- 這是 dedup 的依據（ADR-004）。
    content_hash   CHAR(64)      NOT NULL,
    title          VARCHAR(500)  NOT NULL,
    -- 原始內文。promote 後 30 天由清除排程設為 NULL，
    -- 僅保留 metadata 供追查（FR-3.12）。
    raw_content    TEXT,
    published_at   TIMESTAMPTZ,
    status         VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    retry_count    INTEGER       NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMPTZ,
    failure_type   VARCHAR(20),
    failure_reason TEXT,
    summary        TEXT,
    summarized_at  TIMESTAMPTZ,
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

    -- READY 以後的狀態必須已有摘要
    CONSTRAINT ck_fetched_item_summary
        CHECK (status NOT IN ('READY', 'PROMOTED') OR summary IS NOT NULL)
);

-- ★ dedup 的實作點（ADR-004）。
-- 這條約束是「同一來源內不得有相同內容」的唯一可靠保證。
-- 寫入時採 insert-or-ignore：直接 INSERT，
-- 若收到 unique violation 即視為重複並跳過，
-- 而非「先查再寫」——後者在並發下有 race condition。
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
-- =============================================================================
CREATE TABLE document (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    origin          VARCHAR(10)  NOT NULL,
    -- 由哪筆 fetched_item promote 而來。MANUAL 時為 NULL。
    fetched_item_id BIGINT,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,
    -- 使用者對抓取內容的註解。原文不可改，註解可以。
    note            TEXT,
    -- ⚠️ 此欄位供 optimistic lock 使用（Hibernate @Version），
    --    與 document_version 表完全無關。
    --    document.version    → 併發控制，使用者看不到
    --    document_version    → 使用者可見的歷史快照
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
    --   FETCHED 一定有來源，MANUAL 一定沒有。
    -- 少了這條，程式碼寫錯時會產生「來源不明的抓取文件」這種矛盾資料，
    -- 而且不會有任何錯誤提示。
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
-- =============================================================================
CREATE TABLE document_version (
    id             BIGSERIAL    PRIMARY KEY,
    document_id    BIGINT       NOT NULL,
    version_number INTEGER      NOT NULL,
    title          VARCHAR(500) NOT NULL,
    content        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

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

CREATE TRIGGER trg_document_version_updated_at
    BEFORE UPDATE ON document_version
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- 7. tag 與 document_tag
--
-- 資料模型建立，但不做標籤管理介面（見 PROJECT_RULES out-of-scope）。
-- =============================================================================
CREATE TABLE tag (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_tag_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_tag_user_name
    ON tag (user_id, name);

CREATE TRIGGER trg_tag_updated_at
    BEFORE UPDATE ON tag
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 多對多的中介表（junction table）。
-- 一份 document 可有多個 tag，一個 tag 可貼在多份 document 上。
-- 複合主鍵同時扮演唯一約束，避免同一標籤重複貼同一文件。
CREATE TABLE document_tag (
    document_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,

    CONSTRAINT pk_document_tag
        PRIMARY KEY (document_id, tag_id),

    CONSTRAINT fk_document_tag_document
        FOREIGN KEY (document_id) REFERENCES document (id) ON DELETE RESTRICT,

    CONSTRAINT fk_document_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE RESTRICT
);

-- 反向查詢「這個標籤有哪些文件」時使用。
-- 複合主鍵的索引只對 (document_id, tag_id) 這個方向有效，
-- 單獨用 tag_id 查詢無法利用它，因此需要這條。
CREATE INDEX idx_document_tag_tag
    ON document_tag (tag_id);
