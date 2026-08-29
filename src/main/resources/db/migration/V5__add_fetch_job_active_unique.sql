-- =============================================================================
-- V5__add_fetch_job_active_unique.sql
--
-- 一個 source 同一時間只能有一筆「進行中」的抓取任務。
--
-- 背景（Day 15，使用者決定）：
--   排程每次執行都會為每個啟用中的來源建立一筆 fetch_job。
--   若上一筆卡在 RUNNING（例如程式在抓取途中被中止），
--   下一次排程仍會再建一筆，數小時後同一個來源會累積多筆未結束的任務。
--
--   使用者的判斷：「不希望同一個訂閱來源的抓取任務同時有好幾個存在，
--   會把資料搞得很混亂。」
--
-- 為什麼是 partial index（帶 WHERE）而不是一般的 UNIQUE：
--   ADR-008 決定失敗不重跑，等下次排程開一筆新的，
--   因此一個 source 累積下來本來就會有很多筆 fetch_job。
--   要限制的是「同時存在的活躍任務」，不是「歷史總數」。
--
--   已結束的（SUCCESS / FAILED）不參與這個唯一性檢查。
--
--   PostgreSQL 的 UNIQUE 約束不支援 WHERE，
--   因此只能寫成 CREATE UNIQUE INDEX。
-- =============================================================================

CREATE UNIQUE INDEX uq_fetch_job_active
    ON fetch_job (source_id)
    WHERE status IN ('PENDING', 'RUNNING');
