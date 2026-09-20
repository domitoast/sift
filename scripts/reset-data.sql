-- =============================================================================
-- 清空「內容」資料，保留帳號與訂閱來源。
--
-- 【為什麼需要這個檔案】
--
-- 開發時常常要把資料退回乾淨狀態重跑管線。每次手打 DELETE 有兩個問題：
--
--   1. 順序會打錯。外鍵是 ON DELETE RESTRICT，順序錯就整批失敗
--   2. 容易多刪或少刪（例如忘了 llm_usage，配額還是滿的）
--
-- 寫成檔案就只有一份正確的順序，而且它進版控——
-- 下次 schema 多一張表，這裡沒改的話會立刻爆，而不是靜靜地漏掉。
--
-- 【怎麼跑】
--
--   方法 A（IntelliJ）：打開這個檔案，右上角選 sift 資料庫，按執行
--
--   方法 B（PowerShell 一行）：
--     docker exec -i sift-postgres psql -U sift -d sift < scripts\reset-data.sql
--
-- ⚠️ 這個檔案「不會」刪掉 app_user 與 source——
--    帳號和訂閱清單重建很麻煩，而且它們不是測試髒資料。
--    要連那些一起清，用下面註解掉的那一段。
-- =============================================================================

BEGIN;

-- 刪除順序 = 外鍵依賴的反方向，從最末端往回刪。
--
--   app_user ← source ← fetch_job ← fetched_item ← document ← document_version
--   app_user ← refresh_token
--   app_user ← llm_usage

DELETE FROM document_version;
DELETE FROM document;
DELETE FROM fetched_item;
DELETE FROM fetch_job;

-- 今日的 LLM 用量歸零。
-- 不清的話，反覆測試很快就會撞到 daily-quota，
-- 然後你會以為是摘要壞了——實際上是額度用完。
DELETE FROM llm_usage;

COMMIT;

-- 確認結果。前四個應該是 0，後兩個保持原樣。
SELECT
    (SELECT count(*) FROM fetched_item)     AS 文章,
    (SELECT count(*) FROM document)         AS 知識庫,
    (SELECT count(*) FROM fetch_job)        AS 工作卡,
    (SELECT count(*) FROM llm_usage)        AS 今日用量,
    (SELECT count(*) FROM source)           AS 來源,
    (SELECT count(*) FROM app_user)         AS 使用者;


-- =============================================================================
-- 需要「全部」清掉時（連帳號與訂閱來源），把下面這段取消註解。
--
-- ⚠️ 清掉之後要重新註冊、重新設 API key、重新訂閱。
-- =============================================================================
--
-- BEGIN;
-- DELETE FROM document_version;
-- DELETE FROM document;
-- DELETE FROM fetched_item;
-- DELETE FROM fetch_job;
-- DELETE FROM llm_usage;
-- DELETE FROM refresh_token;
-- DELETE FROM source;
-- DELETE FROM app_user;
-- COMMIT;
