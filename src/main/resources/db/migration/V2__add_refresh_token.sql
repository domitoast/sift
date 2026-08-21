-- =============================================================================
-- V2__add_refresh_token.sql
--
-- 新增 refresh_token 表，讓「登出」與「撤銷」成為可能。
--
-- 背景：JWT 一經簽發即無法撤銷——伺服器沒有記錄它，也就無從讓它失效。
-- 但 API_DESIGN.md 承諾了 POST /auth/logout 會「使 refresh token 失效」。
-- 若不儲存 token，該承諾無法兌現。
--
-- 相關決策：ADR-010
--
-- ⚠️ V1 已在資料庫執行過（flyway_schema_history 有其 checksum 紀錄），
--    因此不可修改 V1，只能新增本檔案。
-- =============================================================================


CREATE TABLE refresh_token (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,

    -- ★ 存的是 token 的 SHA-256 雜湊值，不是 token 本身。
    --
    -- 為什麼：若資料庫外洩，攻擊者拿到的是雜湊值，無法反推出原始 token，
    -- 也就無法冒用任何人的身分。
    --
    -- 這與密碼儲存是同一個原則——凡是「能證明身分的字串」都不該明文存放。
    --
    -- 驗證流程：使用者送 token 過來 → 系統計算其雜湊 → 比對資料庫。
    -- 與密碼的差別在於這裡用 SHA-256 而非 BCrypt：
    -- BCrypt 刻意很慢（100ms）是為了防暴力破解「人類記得住的短密碼」；
    -- refresh token 是 256 位元的隨機值，暴力破解本來就不可行，
    -- 用快速的 SHA-256 即可，否則每次 refresh 都要多等 100 毫秒。
    token_hash CHAR(64)    NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    -- 撤銷時間。NULL 表示仍然有效。
    --
    -- 使用「時間戳」而非布林值 is_revoked 的理由：
    -- 時間戳同時回答了「有沒有被撤銷」與「什麼時候被撤銷」兩個問題，
    -- 成本相同但資訊更多。這與 deleted_at 的設計思路一致。
    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,

    -- 過期時間必須晚於建立時間，否則這筆資料一出生就沒意義
    CONSTRAINT ck_refresh_token_expiry
        CHECK (expires_at > created_at)
);


-- 同一個雜湊值不可重複。
-- 這也是「同一個 token 被簽發兩次」這種程式錯誤的最後防線。
CREATE UNIQUE INDEX uq_refresh_token_hash
    ON refresh_token (token_hash);

-- 服務「撤銷某使用者的所有 token」（登出所有裝置）。
-- 條件限定未撤銷者，因為已撤銷的不需要再處理。
CREATE INDEX idx_refresh_token_user_active
    ON refresh_token (user_id)
    WHERE revoked_at IS NULL;

-- 服務清除排程：刪除過期已久的紀錄，避免資料無限增長。
CREATE INDEX idx_refresh_token_expires
    ON refresh_token (expires_at);


-- 本表沒有 updated_at 也沒有 trigger。
--
-- 理由與 document_version 相同：refresh token 一旦建立就不會被修改，
-- 唯一可能的變化是「被撤銷」，而那有專屬的 revoked_at 欄位記錄時間。
-- 加上 updated_at 只會多一個沒有額外資訊的欄位。
