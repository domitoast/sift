-- V4: 把 token 雜湊欄位從 CHAR(64) 改為 VARCHAR(64)
--
-- 修正 V2 與 V3 的型別選擇錯誤。
--
-- 【為什麼要改】
--
-- 一、與 JPA entity 對不起來。
--     RefreshToken 的欄位宣告為 @Column(length = 64) 的 String，
--     Hibernate 據此預期 VARCHAR(64)，但資料庫是 CHAR(64)（PostgreSQL 內部型別 bpchar）。
--     ddl-auto: validate 會在啟動時擋下來：
--       "found [bpchar], but expecting [varchar(64)]"
--
-- 二、PostgreSQL 本來就不建議使用 CHAR(n)。
--     官方文件明確指出：char(n) 相較 varchar 沒有任何效能優勢，
--     而且會自動補空白到固定長度，反而多佔空間、比較時容易出意外。
--     三種字串型別中，char(n) 是最不推薦的。
--
-- 【為什麼不直接改 V2 / V3】
--
-- 它們已經執行過了。Flyway 會比對已套用 migration 的 checksum，
-- 一旦內容變動就拒絕啟動。要修正既有結構，唯一的方式是新增一份 migration。
--
-- 【資料安全性】
--
-- 執行當下 refresh_token 表是空的，因此不需要處理既有資料的空白補齊問題。
-- （bpchar 轉 varchar 時，PostgreSQL 不會自動去除尾端空白。）
--
-- 唯一索引 uq_refresh_token_hash 會由 PostgreSQL 自動重建，不需另外處理。

ALTER TABLE refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64),
    ALTER COLUMN previous_token_hash TYPE VARCHAR(64);
