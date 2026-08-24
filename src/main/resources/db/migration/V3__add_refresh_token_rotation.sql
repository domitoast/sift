-- V3: 支援 refresh token rotation（ADR-011）
--
-- rotation 採「原地更新」：換發時不新增列，而是把目前的 token_hash
-- 移到 previous_token_hash，再寫入新的 token_hash。
--
-- ⚠️ V2 已經執行過，絕對不可以回頭修改它。
--    Flyway 會比對已套用 migration 的 checksum，改了就再也啟動不了。
--    要改變既有結構，只能新增一份 migration。

ALTER TABLE refresh_token
    ADD COLUMN previous_token_hash CHAR(64);

COMMENT ON COLUMN refresh_token.previous_token_hash IS
    '上一代的 token 雜湊值。若收到的 token 與此欄位相符，代表一張已被換掉的票再次被使用，判定為盜用（ADR-011）';

-- 盜用偵測時需要用這個欄位查詢，因此建立索引。
--
-- 加上 WHERE 條件做成 partial index：只有換發過至少一次的列才有值，
-- 剛登入尚未換發過的列不會進入索引，索引體積因此更小。
CREATE INDEX idx_refresh_token_previous_hash
    ON refresh_token (previous_token_hash)
    WHERE previous_token_hash IS NOT NULL;
