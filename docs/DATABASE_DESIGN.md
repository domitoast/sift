# 資料庫設計

**資料庫**：PostgreSQL 17
**命名慣例**：資料表與欄位一律 `snake_case`、資料表用**單數**
**主鍵**：`BIGSERIAL`（自動遞增數字）
**時間欄位**：一律 `TIMESTAMPTZ`（含時區），以 UTC 儲存
**最後更新**：Day 10

---

## 現行 migration 一覽

| 檔案 | 內容 |
|---|---|
| `V1__init.sql` | 6 張業務表（Day 4） |
| `V2__add_refresh_token.sql` | `refresh_token` 表（Day 6） |
| `V3__add_refresh_token_rotation.sql` | 加 `previous_token_hash`（Day 8，ADR-011） |
| `V4__use_varchar_for_token_hash.sql` | `CHAR(64)` → `VARCHAR(64)`（Day 8，修正 V2/V3） |

> V2、V3 的型別錯誤刻意留在歷史裡。
> **已執行的 migration 不能修改**，只能往後新增一份——V4 就是那份。

## `refresh_token`（V2 建立，V3/V4 修改）

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | `BIGSERIAL` | 主鍵 |
| `user_id` | `BIGINT NOT NULL` | 擁有者，FK → `app_user`，`ON DELETE RESTRICT` |
| `token_hash` | `VARCHAR(64) NOT NULL` | 目前有效 token 的 SHA-256 雜湊。**不存 token 本身** |
| `previous_token_hash` | `VARCHAR(64)` | 上一代的雜湊。**盜用偵測用**（ADR-011） |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | 絕對到期時間，**rotation 不會延長它** |
| `revoked_at` | `TIMESTAMPTZ` | 撤銷時間。登出、盜用偵測、登出所有裝置都會填 |
| `created_at` | `TIMESTAMPTZ NOT NULL` | `DEFAULT now()` |

**索引**：

| 索引 | 服務哪個查詢 |
|---|---|
| `uq_refresh_token_hash`（UNIQUE） | 正常換發：依 `token_hash` 查 |
| `idx_refresh_token_previous_hash`（partial，非 NULL 才進） | 盜用偵測：依 `previous_token_hash` 查 |
| `idx_refresh_token_user_active`（partial，未撤銷才進） | 作廢某使用者的所有憑證 |
| `idx_refresh_token_expires` | 未來的過期清除排程（Day 12） |

**一列 = 一個裝置的一次登入。** 同一使用者可同時有多列，
因此手機登出不會影響筆電。rotation 採原地更新，不新增列（ADR-011）。

### 待辦

- [ ] 過期紀錄的清除排程（Day 12）
- [ ] 已撤銷紀錄保留 24 小時後刪除

---

> **欄位存在的三個判準**（ADR-008）
> 1. 現在有哪個 FR / User Story 需要它？
> 2. 它能不能從別的資料算出來？
> 3. 刪掉它，什麼會壞掉？
>
> 三題都答不出來的欄位，不該存在。

---

## 通用欄位

| 欄位 | 型別 | 出現於 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | 全部 6 張表 | 主鍵 |
| `created_at` | TIMESTAMPTZ | 全部 6 張表 | 預設 `now()` |
| `updated_at` | TIMESTAMPTZ | 除 `document_version` 外 | 由 trigger `set_updated_at()` 維護 |
| `deleted_at` | TIMESTAMPTZ | `app_user`、`source`、`document` | soft delete（ADR-005） |

**`document_version` 為何沒有 `updated_at`**：快照建立後永遠不會被修改，
加上該欄位等於在說謊。

**哪些表有 `deleted_at`**：

| 資料表 | soft delete | 理由 |
|---|---|---|
| `app_user` | ✅ | 帳號誤刪需可復原 |
| `source` | ✅ | 停止訂閱不應丟棄歷史文章 |
| `document` | ✅ | 筆記誤刪需可復原 |
| `fetch_job` | ❌ | 本身即為歷史紀錄 |
| `fetched_item` | ❌ | 30 天後僅清 `raw_content`，資料列保留 |
| `document_version` | ❌ | 歷史快照 |

> ⚠️ **有 `deleted_at` 的表，唯一約束必須加 `WHERE deleted_at IS NULL`**，
> 否則刪除後該值永遠無法重新使用。

---

## 資料表總覽

```
app_user ──┬── source ── fetch_job ── fetched_item ──┐
           │                                          │ promote
           └── document ── document_version           │
                  ▲                                   │
                  └───────────────────────────────────┘
```

| # | 資料表 | 欄位數 | 層 |
|---|---|---|---|
| 1 | `app_user` | 7 | 身分 |
| 2 | `source` | 9 | pipeline |
| 3 | `fetch_job` | 9 | pipeline |
| 4 | `fetched_item` | 17 | pipeline（staging） |
| 5 | `document` | 10 | 知識庫（curated） |
| 6 | `document_version` | 6 | 知識庫 |

---

## 1. `app_user`

> `user` 是 PostgreSQL 保留字，故命名為 `app_user`。

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `email` | VARCHAR(255) | NOT NULL | 登入帳號 |
| `password_hash` | VARCHAR(60) | NOT NULL | BCrypt 輸出固定 60 字元 |
| `llm_api_key_encrypted` | TEXT | NULL | 加密後的 API key（ADR-003） |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `deleted_at` | TIMESTAMPTZ | NULL | |

**約束**

- `UNIQUE (email) WHERE deleted_at IS NULL`

**設計說明**

- `password_hash` 長度 60：BCrypt 輸出格式固定
- `llm_api_key_encrypted` 用 TEXT：加密後長度會膨脹，不好預估上限
- **LLM 配額欄位已於 ADR-008 移除**。防爆量機制延至 Day 16 設計——
  屆時才知道真實需求（獨立表？Redis？）

---

## 2. `source`（訂閱來源）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `user_id` | BIGINT | NOT NULL | FK → `app_user.id` |
| `name` | VARCHAR(200) | NOT NULL | 顯示名稱 |
| `url` | VARCHAR(1000) | NOT NULL | RSS / Atom 位址 |
| `type` | VARCHAR(20) | NOT NULL | `RSS` / `ATOM` |
| `enabled` | BOOLEAN | NOT NULL | 預設 true（US-05 暫停來源） |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `deleted_at` | TIMESTAMPTZ | NULL | |

**約束**

- `UNIQUE (user_id, url) WHERE deleted_at IS NULL`
- `CHECK (type IN ('RSS', 'ATOM'))`

**索引**

- `(deleted_at, enabled)` — 排程撈出待抓取的來源

**設計說明**

- **`last_success_at` 已移除**：可由 `fetch_job` 算出
  `max(finished_at) WHERE status = 'SUCCESS'`
- **`fetch_interval_minutes` 已移除**：無需求要求各來源不同頻率
- **`consecutive_failure_count` 已移除**：其目的功能不在任何 FR 中

---

## 3. `fetch_job`（抓取任務）

> **一次抓取動作 = 一筆紀錄**，不是一篇文章一筆。
> 一筆 `fetch_job` 通常產生數十筆 `fetched_item`。

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `source_id` | BIGINT | NOT NULL | FK → `source.id` |
| `status` | VARCHAR(20) | NOT NULL | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `started_at` | TIMESTAMPTZ | NULL | 轉為 RUNNING 的時間 |
| `finished_at` | TIMESTAMPTZ | NULL | 結束時間（可算耗時，NFR-4.1） |
| `failure_type` | VARCHAR(20) | NULL | `TRANSIENT` / `PERMANENT` |
| `failure_reason` | TEXT | NULL | 錯誤訊息 |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |

**約束**

- `CHECK (status IN (...))`
- `CHECK (failure_type IS NULL OR failure_type IN ('TRANSIENT', 'PERMANENT'))`
- `CHECK (status = 'PENDING' OR started_at IS NOT NULL)`
- `CHECK (status <> 'FAILED' OR failure_type IS NOT NULL)` — 失敗必須說明原因

**索引**

- `(source_id, created_at DESC)` — FR-2.4 顯示最近 N 次抓取結果

**設計說明**

- **本表無 retry 欄位**（ADR-008）。本系統為每日排程，
  來源抓取失敗時等下次排程即可，同一天內做秒級 backoff 意義不大
- `failure_type` 保留：它是診斷的關鍵，
  也是「暫時性 vs 永久性失敗」這個核心觀念的載體

---

## 4. `fetched_item`（staging）

> 管線的工作區。資料可能重複、可能摘要失敗、可能是垃圾。
> 唯有 `READY` 可 promote 為 document（ADR-002）。

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `source_id` | BIGINT | NOT NULL | FK → `source.id`（刻意反正規化） |
| `fetch_job_id` | BIGINT | NOT NULL | FK → `fetch_job.id`（追查用） |
| `external_url` | VARCHAR(1000) | NOT NULL | 原文網址 |
| `content_hash` | CHAR(64) | NOT NULL | SHA-256，**dedup 依據**（ADR-004） |
| `title` | VARCHAR(500) | NOT NULL | |
| `raw_content` | TEXT | NULL | 30 天後清空，保留 metadata |
| `published_at` | TIMESTAMPTZ | NULL | 來源不一定提供 |
| `status` | VARCHAR(20) | NOT NULL | 見下方狀態機 |
| `retry_count` | INTEGER | NOT NULL | 預設 0 |
| `next_retry_at` | TIMESTAMPTZ | NULL | exponential backoff 計算結果 |
| `failure_type` | VARCHAR(20) | NULL | `TRANSIENT` / `PERMANENT` |
| `failure_reason` | TEXT | NULL | |
| `summary` | TEXT | NULL | LLM 產生 |
| `promoted_at` | TIMESTAMPTZ | NULL | 30 天清除以此計算 |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |

**狀態機**

```
NEW → SUMMARIZING → READY → PROMOTED
 │         │
 └─────────┴──→ FAILED（retry 耗盡後進 DLQ）
 │
 └──→ DISCARDED（人工判定為垃圾）
```

**約束**

- `UNIQUE (source_id, content_hash)` ← **dedup 的實作點**
- `CHECK (status NOT IN ('READY','PROMOTED') OR summary IS NOT NULL)`
- `CHECK (status <> 'PROMOTED' OR promoted_at IS NOT NULL)`

**索引**

- `(status, next_retry_at)` — 撈出待摘要 / 待重試
- `(promoted_at) WHERE promoted_at IS NOT NULL` — 30 天清除排程

**設計說明**

- **retry 欄位保留於此表**：LLM 的 rate limit 與 timeout
  通常數分鐘內恢復，分鐘級 retry 有實際價值
- **`summarized_at` 已移除**：`summary IS NOT NULL` 已表達相同資訊
- dedup 寫入採 **insert-or-ignore**，不採「先查再寫」（有 race condition）

---

## 5. `document`（知識庫）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `user_id` | BIGINT | NOT NULL | FK → `app_user.id` |
| `origin` | VARCHAR(10) | NOT NULL | `MANUAL` / `FETCHED` |
| `fetched_item_id` | BIGINT | NULL | FK；`MANUAL` 時為 NULL |
| `title` | VARCHAR(500) | NOT NULL | |
| `content` | TEXT | NOT NULL | Markdown |
| `version` | BIGINT | NOT NULL | **optimistic lock 用**，使用者看不到 |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `deleted_at` | TIMESTAMPTZ | NULL | |

**約束**

- `UNIQUE (fetched_item_id) WHERE fetched_item_id IS NOT NULL`
  ← idempotency 第二道防線
- `CHECK (origin IN ('MANUAL', 'FETCHED'))`
- `CHECK ((origin='FETCHED' AND fetched_item_id IS NOT NULL) OR
   (origin='MANUAL' AND fetched_item_id IS NULL))`

**索引**

- `(user_id, deleted_at, created_at DESC)` — 文件列表

**設計說明**

- ⚠️ `document.version`（併發控制）與 `document_version` 表（歷史快照）
  **完全無關**。這是本專案最容易混淆的一組名詞
- **`note` 已移除**（ADR-008）。連帶影響：
  兩種 origin 的文件皆可自由編輯 `content`，不再有特例。
  原文追溯改由 `fetched_item.raw_content` 提供（30 天內）

---

## 6. `document_version`（歷史版本）

| 欄位 | 型別 | 可否為空 | 說明 |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | |
| `document_id` | BIGINT | NOT NULL | FK → `document.id` |
| `version_number` | INTEGER | NOT NULL | 從 1 開始 |
| `title` | VARCHAR(500) | NOT NULL | 該版當時的標題 |
| `content` | TEXT | NOT NULL | 該版當時的內容 |
| `created_at` | TIMESTAMPTZ | NOT NULL | |

**約束**

- `UNIQUE (document_id, version_number)`
- `CHECK (version_number >= 1)`

**索引**

- `(document_id, version_number DESC)` — 版本歷史列表

**設計說明**

- 儲存**完整快照**而非 diff：實作簡單、還原快，代價是空間。
  以 1 萬筆文件的規模完全可接受
- **無 `updated_at`、無 trigger**：快照不會被修改

---

## 外鍵刪除行為

**一律 `ON DELETE RESTRICT`**，搭配 soft delete（ADR-005）。

RESTRICT 是最後一道保險：若有人繞過應用程式直接下 SQL 刪除，
資料庫會拒絕，避免產生孤兒資料。

---

## 已移除的資料表

| 表 | 移除理由 | 何時可能加回 |
|---|---|---|
| `tag` | PROJECT_RULES 明訂不做標籤管理介面，此表永遠為空 | 真的要做標籤時，V2 migration |
| `document_tag` | 同上 | 同上 |

---

## 待辦

- [ ] 中文全文搜尋的分詞方案（Day 10）
- [ ] 欄位加密演算法（Day 6）
- [ ] LLM 配額機制（Day 16）
