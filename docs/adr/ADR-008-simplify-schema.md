# ADR-008: Day 5 schema 精簡

## 狀態

已接受（Day 5）

## 背景

Day 5 開始撰寫 Entity 之前，開發者主動要求逐欄審視 schema，
指出 `app_user` 上的 LLM 配額欄位「沒有意義」。

該質疑成立，並促成一次完整的欄位審查。
審查基準為三個問題：

1. 現在有哪個需求（FR / User Story）需要它？
2. 它能不能從別的資料算出來？
3. 刪掉它，什麼會壞掉？

答不出來的欄位即為「為想像中的未來而加」，屬 YAGNI 違反。

**時機說明**：此時尚未撰寫任何 Entity、DTO 或測試。
在此階段刪欄位的成本接近零；等程式碼寫完再刪，成本是數十倍。

## 決定

### 移除的欄位

| 表 | 欄位 | 理由 |
|---|---|---|
| `app_user` | `daily_llm_call_count` | 高頻變動的計數器不應放在幾乎不變的身分表上。<br>兩者生命週期不同，且 `app_user` 是被讀最頻繁的表 |
| `app_user` | `daily_llm_count_date` | 同上 |
| `source` | `fetch_interval_minutes` | 無任何需求要求「每個來源不同頻率」；<br>現行設計為每日統一排程 |
| `source` | `consecutive_failure_count` | 為「連續失敗自動停用」而加，但該功能不在任何 FR 中 |
| `source` | `last_success_at` | 可由 `fetch_job` 算出：<br>`max(finished_at) WHERE status = 'SUCCESS'` |
| `fetch_job` | `item_count` | 可由 `count(*) FROM fetched_item WHERE fetch_job_id = ?` 算出 |
| `fetch_job` | `retry_count`、`next_retry_at` | 見下方「retry 設計的重新定位」 |
| `fetched_item` | `summarized_at` | `summary IS NOT NULL` 已表達相同資訊 |
| `document` | `note` | 連帶影響見下方 |
| `document_version` | `updated_at` | 快照不會被修改，此欄位在說謊 |

### 移除的資料表

| 表 | 理由 |
|---|---|
| `tag` | `PROJECT_RULES.md` 明訂不做標籤管理介面。<br>沒有介面即無法建立標籤，此表永遠為空 |
| `document_tag` | 同上 |

需要時再以 V2 migration 加回，成本極低。

## retry 設計的重新定位（本次最重要的判斷）

開發者原本提議連 `fetched_item` 的 retry 欄位一併刪除。
此提議被拒絕——`PROJECT_RULES.md` 第 5 節「永不砍」明訂包含
「管線的可靠性設計（retry、idempotency、狀態機）」，
且該設計正是本專案的核心定位「我知道系統在真實世界會壞，而且我處理了」。

但檢視過程中發現，**原本的設計確實在兩張表上都放了 retry，屬於慣例照抄**。
兩者的真實需求並不相同：

| 場景 | 失敗特性 | 合理的 retry 策略 |
|---|---|---|
| RSS 抓取失敗 | 來源網站維護、網路問題 | **等下次排程（明天）即可**。<br>本系統為每日抓取，在同一天內做秒級 backoff 意義不大 |
| LLM 摘要失敗 | rate limit（429）、timeout | **分鐘級 retry 有實際價值**。<br>API 限流通常數分鐘內恢復 |

因此：

- `fetch_job`：移除 `retry_count`、`next_retry_at`，
  **保留** `failure_type`、`failure_reason`（診斷與統計必需）
- `fetched_item`：retry 相關欄位**完整保留**，
  Day 13 的 exponential backoff 在此實作

## note 欄位移除的連帶影響

原設計中，`origin = FETCHED` 的文件其 `content` 不可修改，
使用者只能在 `note` 欄位加註解。

移除 `note` 後，此規則失去意義，因此一併調整為：
**兩種 origin 的文件皆可自由編輯 `content`，行為一致，不再有特例。**

原文若需追溯，30 天內可於 `fetched_item.raw_content` 查得。

受影響的需求：**FR-4.5 移除**。

## 後果

### 好處

- 資料表由 8 張減為 6 張，欄位減少 11 個
- 每個保留下來的欄位都能對應到具體需求
- retry 設計出現在真正需要它的地方，而非每張表抄一份——
  這比原設計更能展現判斷力
- Entity 與測試的撰寫量同步下降

### 代價

- LLM 防爆量機制延至 Day 16 再設計（屆時才知道真實需求）
- 失去「每個來源不同抓取頻率」的能力（目前無此需求）
- 失去「連續失敗自動停用來源」的能力（目前無此需求）

### 實作方式

由於 V1 尚未推送至遠端、未被他人執行，
本次直接改寫 `V1__init.sql` 而非新增 V2。

**規則的完整版本**：migration 一旦在自己控制範圍外的環境執行過
（同事的電腦、測試環境、正式環境），即永久唯讀。
在此之前仍可改寫。

**這是最後一次改寫 V1。**

### 重新評估的時機

- Day 16 實作 LLM 呼叫時，重新設計配額機制
- 若日後真的需要標籤功能，以 V2 加回 `tag` 與 `document_tag`
