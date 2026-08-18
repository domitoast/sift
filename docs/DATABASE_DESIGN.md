# 資料庫設計

**資料庫**：PostgreSQL
**命名慣例**：資料表與欄位一律 `snake_case`、資料表用**單數**（`document` 而非 `documents`）
**主鍵**：`BIGSERIAL`（自動遞增數字）
**時間欄位**：一律 `TIMESTAMPTZ`（含時區），預設 `now()`

---

## 通用欄位

以下欄位出現在所有資料表，不再逐一說明：

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | 主鍵 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 預設 `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | 預設 `now()`，更新時一併修改 |

---

## 1. `user`

> ⚠️ `user` 是 PostgreSQL 的保留字，實際建表時需加雙引號，
> 或改名為 `app_user`。**本專案採用 `app_user`** 以免每次查詢都要加引號。

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `email` | VARCHAR(255) | NOT NULL | 登入帳號 |
| `password_hash` | VARCHAR(60) | NOT NULL | BCrypt 輸出固定 60 字元 |
| `llm_api_key_encrypted` | TEXT | NULL | 加密後的 API key；未設定時為 null（ADR-003） |
| `daily_llm_call_count` | INTEGER | NOT NULL | 預設 0，用於防爆量 |
| `daily_llm_count_date` | DATE | NULL | 上述計數對應的日期，跨日時歸零 |

**設計說明**

- `password_hash` 長度 60：BCrypt 輸出格式固定，不需要多給
- `llm_api_key_encrypted` 用 TEXT：加密後長度會膨脹，不好預估上限
- 計數用「計數 + 日期」兩欄而非每次查 log 表：查詢成本 O(1)，跨日時比對日期歸零即可

---

## 2. `source`（訂閱來源）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `user_id` | BIGINT | NOT NULL | 外鍵 → `app_user.id` |
| `name` | VARCHAR(200) | NOT NULL | 顯示名稱，例如「Hacker News」 |
| `url` | VARCHAR(1000) | NOT NULL | RSS / Atom 位址 |
| `type` | VARCHAR(20) | NOT NULL | `RSS` / `ATOM`，加 CHECK 約束 |
| `enabled` | BOOLEAN | NOT NULL | 預設 true |
| `fetch_interval_minutes` | INTEGER | NOT NULL | 預設 1440（一天一次） |
| `last_success_at` | TIMESTAMPTZ | NULL | 最後一次成功抓取時間 |
| `consecutive_failure_count` | INTEGER | NOT NULL | 預設 0，連續失敗次數 |
| `deleted_at` | TIMESTAMPTZ | NULL | soft delete；null 表示未刪除（ADR-005） |

**設計說明**

- `url` 給 1000 字元：URL 理論上可以很長，實務上 2048 是常見上限，1000 對 RSS 位址足夠
- `consecutive_failure_count`：用來判斷「這個來源是不是已經死了」。
  例如連續失敗 10 次就自動停用並通知，避免每天無謂重試
- **不用 PostgreSQL 原生 ENUM 型別**：原生 enum 要新增一個值必須跑 `ALTER TYPE`，
  在 migration 中很麻煩。用 `VARCHAR + CHECK` 彈性較高，這是多數團隊的做法

---

## 3. `fetch_job`（抓取任務）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `source_id` | BIGINT | NOT NULL | 外鍵 → `source.id` |
| `status` | VARCHAR(20) | NOT NULL | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `started_at` | TIMESTAMPTZ | NULL | 轉為 RUNNING 的時間 |
| `finished_at` | TIMESTAMPTZ | NULL | 轉為 SUCCESS / FAILED 的時間 |
| `retry_count` | INTEGER | NOT NULL | 預設 0 |
| `next_retry_at` | TIMESTAMPTZ | NULL | 下次重試時間，由 exponential backoff 計算 |
| `failure_type` | VARCHAR(20) | NULL | `TRANSIENT` / `PERMANENT` |
| `failure_reason` | TEXT | NULL | 錯誤訊息，供除錯 |
| `item_count` | INTEGER | NOT NULL | 預設 0，本次抓到幾筆 |

**設計說明**

- `failure_type` 是這張表最重要的欄位。它決定要不要 retry：
  - `TRANSIENT`（暫時性）：timeout、連線失敗、對方回 500 → 值得 retry
  - `PERMANENT`（永久性）：URL 404、回應不是合法 XML → retry 一百次也不會成功
- `next_retry_at` 存「下次何時重試」而非「已等待多久」：
  排程只要查 `next_retry_at <= now()` 就能撈出該重試的任務，邏輯簡單

---

## 4. `fetched_item`（staging：抓取項目）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `source_id` | BIGINT | NOT NULL | 外鍵 → `source.id` |
| `fetch_job_id` | BIGINT | NOT NULL | 外鍵 → `fetch_job.id`，用於追查是哪次抓的 |
| `external_url` | VARCHAR(1000) | NOT NULL | 文章原始網址 |
| `content_hash` | CHAR(64) | NOT NULL | SHA-256 十六進位，**dedup 的依據** |
| `title` | VARCHAR(500) | NOT NULL | 文章標題 |
| `raw_content` | TEXT | NULL | 原始內文；30 天後由清除排程設為 null |
| `published_at` | TIMESTAMPTZ | NULL | 原文發布時間，來源不一定提供 |
| `status` | VARCHAR(20) | NOT NULL | `NEW` / `SUMMARIZING` / `READY` / `PROMOTED` / `FAILED` / `DISCARDED` |
| `retry_count` | INTEGER | NOT NULL | 預設 0 |
| `next_retry_at` | TIMESTAMPTZ | NULL | 摘要失敗後的下次重試時間 |
| `failure_type` | VARCHAR(20) | NULL | `TRANSIENT` / `PERMANENT` |
| `failure_reason` | TEXT | NULL | 摘要失敗原因 |
| `summary` | TEXT | NULL | LLM 產生的摘要 |
| `summarized_at` | TIMESTAMPTZ | NULL | 摘要完成時間 |
| `promoted_at` | TIMESTAMPTZ | NULL | 晉升為 Document 的時間；30 天清除以此計算 |

**設計說明**

- `content_hash` 用 `CHAR(64)`：SHA-256 轉成十六進位字串固定 64 字元，
  用 CHAR 而非 VARCHAR 可省下長度欄位的空間
- `raw_content` 可為空的原因：30 天後清除策略只清內文，
  保留 metadata（網址、標題、hash、失敗原因）以便日後追查
- `summary` 直接放在本表而非另開 `summary` 表：
  一對一關係且必定同時查詢，拆表只會增加 join，無正規化上的必要

---

## 5. `document`（知識庫）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `user_id` | BIGINT | NOT NULL | 外鍵 → `app_user.id` |
| `origin` | VARCHAR(10) | NOT NULL | `MANUAL` / `FETCHED` |
| `fetched_item_id` | BIGINT | NULL | 外鍵 → `fetched_item.id`；`MANUAL` 時為 null |
| `title` | VARCHAR(500) | NOT NULL | |
| `content` | TEXT | NOT NULL | Markdown |
| `note` | TEXT | NULL | 使用者對抓取內容的註解（原文不可改，註解可以） |
| `version` | BIGINT | NOT NULL | 預設 0，**optimistic lock 用**，使用者看不到 |
| `deleted_at` | TIMESTAMPTZ | NULL | soft delete；null 表示未刪除 |

**設計說明**

- `version` 與 `document_version` 表**完全不同**，這是本專案最容易混淆的一組名詞：
  - `document.version`：Hibernate `@Version` 欄位，用於併發控制
  - `document_version`：使用者可見的歷史快照
- 採用 **soft delete**（軟刪除）：刪除時只填 `deleted_at`，不真的移除資料列。
  理由是誤刪可救回；代價是每次查詢都要加 `WHERE deleted_at IS NULL`

---

## 6. `document_version`（文件歷史版本）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `document_id` | BIGINT | NOT NULL | 外鍵 → `document.id` |
| `version_number` | INTEGER | NOT NULL | 第幾版，從 1 開始 |
| `title` | VARCHAR(500) | NOT NULL | 該版本當時的標題 |
| `content` | TEXT | NOT NULL | 該版本當時的內容 |

**設計說明**

- 儲存**完整快照**而非差異（diff）：實作簡單、還原快。
  代價是空間。以本專案的資料量（1 萬筆文件）完全可接受
- 只有 `origin = MANUAL` 的 Document 會產生版本紀錄

---

## 7. `tag` 與 `document_tag`（範圍內但優先度低）

`tag`

| 欄位 | 型別 | 可否為空 |
|---|---|---|
| `user_id` | BIGINT | NOT NULL |
| `name` | VARCHAR(50) | NOT NULL |

`document_tag`（多對多的中介表）

| 欄位 | 型別 | 可否為空 |
|---|---|---|
| `document_id` | BIGINT | NOT NULL |
| `tag_id` | BIGINT | NOT NULL |

**設計說明**：一份 Document 可以有多個 Tag，一個 Tag 可以貼在多份 Document 上，
這是典型的多對多關係，必須用中介表（junction table）表達。

---

## 8. 索引與約束

> **原則**：每一個索引都必須說得出「它服務哪一個查詢」。
> 說不出來的索引就是純成本——它會拖慢寫入、佔用空間，卻沒有加速任何事。

### Unique constraints（唯一約束）

| 資料表 | 約束 | 用途 |
|---|---|---|
| `app_user` | UNIQUE (`email`) | 帳號不可重複 |
| `source` | UNIQUE (`user_id`, `url`) **WHERE `deleted_at` IS NULL** | 同一使用者不可重複訂閱同一網址。<br>加上條件是為了讓刪除後能重新訂閱同一網址（partial unique index） |
| `fetched_item` | **UNIQUE (`source_id`, `content_hash`)** | **dedup 的實作點（ADR-004）** |
| `document` | UNIQUE (`fetched_item_id`) | 一筆 FetchedItem 只能 promote 一次 |
| `document_version` | UNIQUE (`document_id`, `version_number`) | 同一文件不可有兩個第 3 版 |
| `tag` | UNIQUE (`user_id`, `name`) | 同一使用者的標籤名稱不重複 |
| `document_tag` | PRIMARY KEY (`document_id`, `tag_id`) | 同一標籤不可重複貼同一文件 |

`document` 的 UNIQUE (`fetched_item_id`) 是 **idempotency 的第二道防線**：
即使 promote 邏輯因為 retry 被執行兩次，資料庫也只會讓第一次成功。

### 查詢索引

| 資料表 | 索引 | 服務的查詢 |
|---|---|---|
| `source` | (`enabled`, `last_success_at`) | 排程撈出「啟用中且該抓了」的來源 |
| `fetch_job` | (`source_id`, `created_at` DESC) | 顯示某來源最近 N 次抓取結果（FR-2.4） |
| `fetch_job` | (`status`, `next_retry_at`) | 排程撈出「該重試的任務」 |
| `fetched_item` | (`status`, `next_retry_at`) | 撈出待摘要 / 待重試的項目 |
| `fetched_item` | (`promoted_at`) | 30 天清除排程（FR-3.12） |
| `document` | (`user_id`, `deleted_at`, `created_at` DESC) | 使用者的文件列表 |
| `document_version` | (`document_id`, `version_number` DESC) | 版本歷史列表 |

### 全文搜尋索引

PostgreSQL 的全文搜尋需要 GIN 索引，於 Day 10 實作時再加：

```sql
CREATE INDEX idx_document_fts ON document
USING GIN (to_tsvector('simple', title || ' ' || content));
```

暫時延後的原因：中文分詞需要額外設定，Day 10 再一併處理。

### 外鍵的 ON DELETE 行為

**決定：一律使用 `ON DELETE RESTRICT`，並以 soft delete 取代實體刪除（ADR-005）。**

| 資料表 | 刪除策略 |
|---|---|
| `app_user` | soft delete（`deleted_at`） |
| `source` | soft delete（`deleted_at`）→ 停止排程、列表不顯示，歷史資料保留 |
| `document` | soft delete（`deleted_at`） |
| `fetch_job` | 不刪除（本身即為歷史紀錄） |
| `fetched_item` | 30 天後僅清除 `raw_content`，資料列保留 |
| `document_version` | 不刪除 |
| `tag` / `document_tag` | 可實體刪除（資料量小、無歷史價值） |

`ON DELETE RESTRICT` 的作用是**最後一道保險**：
若有人繞過應用程式直接下 SQL 刪除，資料庫會拒絕，避免產生孤兒資料。

**代價**：所有查詢都必須加上 `WHERE deleted_at IS NULL`。
遺漏時會查到已刪除的資料。Day 8 會用 JPA 的機制統一處理，避免逐一手寫。

### 需要額外索引

soft delete 使得 `deleted_at` 出現在幾乎所有查詢條件中，
因此 `source` 的排程查詢索引調整為：

| 資料表 | 索引 | 服務的查詢 |
|---|---|---|
| `source` | (`deleted_at`, `enabled`, `last_success_at`) | 排程撈出「未刪除、啟用中、該抓了」的來源 |

---

## 待辦

- [ ] 中文全文搜尋的分詞方案（Day 10）
- [ ] 欄位加密演算法（Day 6）
