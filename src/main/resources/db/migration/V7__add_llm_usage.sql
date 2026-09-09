-- V7: 每日 LLM 呼叫量，用於配額控制
--
-- 【背景】
--
-- ADR-003（BYOK）把配額的角色定義為「防止爆量」而非「限制使用者」：
--   避免排程設錯而狂打 API —— 花的是使用者自己的錢。
--
-- ADR-008（Day 5）刪掉了 app_user 上的 daily_llm_call_count，
-- 理由是「高頻變動的計數器不該放在幾乎不變的身分表上」。
-- 那個判斷是對的，但它沒有指定替代方案——所以到 Day 20 為止，
-- 配額沒有地方可以記。這份 migration 補上那個空白。
--
-- 【為什麼是獨立的表，不是加回 app_user】
--
-- 一、生命週期不同。app_user 一年可能改不到一次，
--     這張表每次呼叫 LLM 都要寫。放在一起會讓最常被讀的表一直被寫。
--
-- 二、每天一列，天然帶著歷史。「上個月用了多少」直接查得到，
--     而單一欄位的計數器只知道「今天」。
--
-- 三、清理容易。要刪掉半年前的紀錄，DELETE 一張小表即可。
--
-- 【為什麼不記在記憶體】
--
-- 配額的目的是「防止失控」，而失控最常見的形態就是當掉重啟迴圈。
-- 記憶體版本一重啟就歸零——它在最需要它的時候剛好失效。

CREATE TABLE llm_usage (
    user_id    BIGINT  NOT NULL,
    -- 用 DATE 而非 TIMESTAMPTZ：配額是「以天為單位」的概念，
    -- 不需要記到秒。日界由資料庫的 CURRENT_DATE 決定（容器時區為 UTC）。
    usage_date DATE    NOT NULL,
    call_count INTEGER NOT NULL DEFAULT 0,

    -- 複合主鍵：一個使用者一天只有一列。
    -- 這同時是 UPSERT（ON CONFLICT）要用的衝突目標。
    PRIMARY KEY (user_id, usage_date),

    CONSTRAINT fk_llm_usage_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    -- 計數不可能是負的。這條約束擋的是程式的 bug，不是使用者的輸入。
    CONSTRAINT ck_llm_usage_count
        CHECK (call_count >= 0)
);

-- 服務日後的清理排程：「刪掉 N 天前的紀錄」
CREATE INDEX idx_llm_usage_date ON llm_usage (usage_date);
