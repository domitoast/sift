# SRS — Sift 軟體需求規格書（草案 v0.1）

**版本**：0.1（Day 2 草案）
**相關文件**：`PROJECT_RULES.md`（範圍界線）、`docs/GLOSSARY.md`（名詞定義）、`docs/adr/`

---

## 1. 系統概述

### 1.1 定位

> 為了需要持續吸收技術與新聞資訊、但沒時間逐篇閱讀的個人使用者，
> Sift 是一個內建自動內容管線的個人知識庫，
> 能定時從指定來源抓取文章、自動 dedup、產生摘要，並沉澱為可搜尋的個人知識。

### 1.2 使用者角色

| 角色 | 說明 |
|---|---|
| User | 系統唯一角色。擁有自己的 Source 與 Document，管理訂閱、閱讀摘要、撰寫筆記 |

> 本專案不做多角色 RBAC（見 PROJECT_RULES out-of-scope）。

### 1.3 系統邊界

**系統內**：帳號認證、來源管理、排程抓取、dedup、LLM 摘要、知識庫 CRUD、版本、搜尋

**系統外**：外部 RSS / 網站（不可控，可能掛掉）、LLM API（不可控，可能 timeout、可能限流）

> **關鍵假設**：所有系統外的依賴都會失敗。這是本專案可靠性設計的前提。

---

## 2. User Story

### Epic 1：帳號與認證

| ID | Story |
|---|---|
| US-01 | 身為 User，我想註冊帳號，以便擁有自己的知識庫 |
| US-02 | 身為 User，我想登入取得 token，以便存取受保護的資源 |
| US-03 | 身為 User，我想在 access token 過期時用 refresh token 換新的，以免頻繁重新登入 |

### Epic 2：訂閱來源管理

| ID | Story |
|---|---|
| US-04 | 身為 User，我想新增一個 RSS 來源，以便系統自動幫我追蹤它 |
| US-05 | 身為 User，我想暫停某個來源，以便在不想看它時停止抓取但保留設定 |
| US-06 | 身為 User，我想看到每個來源最近的抓取狀況，以便知道它是否正常運作 |

### Epic 3：自動內容管線

| ID | Story |
|---|---|
| US-07 | 身為 User，我想讓系統每天定時自動抓取所有啟用中的來源，以免我手動觸發 |
| US-08 | 身為 User，我不想看到重複的文章，即使系統重試過很多次 |
| US-09 | 身為 User，我想在來源暫時掛掉時系統會自動重試，而不是直接放棄 |
| US-10 | 身為 User，我想讓系統自動為每篇文章產生摘要，以便快速判斷要不要細讀 |
| US-11 | 身為 User，我想在摘要失敗時知道原因，以便判斷是暫時性問題還是設定錯誤 |
| US-12 | 身為 User，我想看到今天的摘要清單（Digest），以便一次讀完當日內容 |

### Epic 4：知識庫

| ID | Story |
|---|---|
| US-13 | 身為 User，我想建立自己的 Markdown 筆記，以便記錄想法 |
| US-14 | 身為 User，我想把讀到的好文章存進知識庫並加上自己的註解 |
| US-15 | 身為 User，我想看到筆記的修改歷史，以便回溯之前的版本 |
| US-16 | 身為 User，我想在兩個分頁同時編輯同一篇筆記時收到衝突提示，而不是默默覆蓋掉其中一份 |
| US-17 | 身為 User，我想用關鍵字搜尋知識庫，以便找回之前存過的內容 |

---

## 3. Use Case（主要情境）

### UC-01：每日自動抓取

**觸發**：排程時間到（預設每日 07:00）

**主流程**

1. Scheduler 觸發，取得所有啟用中的 Source
2. 取得 distributed lock（避免多實例重複執行）
3. 對每個 Source 建立一筆 FetchJob，狀態 `PENDING`
4. FetchJob 轉為 `RUNNING`，向來源發出請求
5. 解析回應，對每一筆內容計算 content hash
6. 若 hash 已存在 → 略過（dedup）；否則建立 FetchedItem，狀態 `NEW`
7. FetchJob 轉為 `SUCCESS`，更新 Source 的最後成功時間

**例外流程**

- **E1 取不到 lock**：本次執行直接跳過，不視為失敗（另一個實例正在跑）
- **E2 來源 timeout**：FetchJob 轉 `FAILED`，記錄原因，依 exponential backoff 排入 retry
- **E3 超過最大 retry 次數**：FetchJob 停留在 `FAILED`，進入 DLQ 供人工查看，不再自動重試
- **E4 回應格式無法解析**：視為永久性失敗，不 retry（retry 也不會變好）

> **設計重點**：E2 是暫時性失敗（transient），要 retry；E4 是永久性失敗（permanent），不該 retry。
> 區分這兩者是可靠性設計的核心。

### UC-02：摘要與晉升

**觸發**：存在狀態為 `NEW` 的 FetchedItem

**主流程**

1. 取出 `NEW` 的 FetchedItem，轉為 `SUMMARIZING`
2. 檢查今日 LLM 用量是否超過配額；超過則保持 `NEW`，延後處理
3. 呼叫 LLM API 產生 Summary（設定 timeout）
4. 成功 → 儲存 Summary，狀態轉 `READY`
5. Promote：建立對應的 Document（`origin = FETCHED`），FetchedItem 狀態轉 `PROMOTED`

**例外流程**

- **E1 LLM timeout / 5xx**：狀態轉 `FAILED`，retry 次數 +1，依 backoff 重試
- **E2 超過最大 retry**：停留 `FAILED`，進 DLQ
- **E3 內容過長超過 token 上限**：截斷後重試一次；仍失敗則標為永久性失敗

### UC-03：編輯筆記時發生衝突

**觸發**：User 儲存 Document 的修改

**主流程**

1. 讀取 Document（含 `version` 欄位）
2. User 修改後送出，請求中帶回原本的 `version`
3. 資料庫比對 version 相符 → 更新成功，version +1，建立一筆 DocumentVersion

**例外流程**

- **E1 version 不符**：代表這份文件在你編輯期間已被他人（或你的另一個分頁）改過
  → 回傳 `409 Conflict`，附上最新版本內容，由 User 決定如何處理
  → **不可默默覆蓋**

---

## 4. Functional Requirements（FR）

### FR-1 認證

| ID | 需求 |
|---|---|
| FR-1.1 | 系統應提供註冊 API，密碼以 BCrypt 雜湊儲存 |
| FR-1.2 | 系統應提供登入 API，回傳 access token 與 refresh token |
| FR-1.3 | access token 有效期 15 分鐘；refresh token 有效期 7 天 |
| FR-1.4 | 系統應提供 refresh API 以換發新的 access token |
| FR-1.5 | 所有非認證 API 皆須帶有效 access token |

### FR-2 來源管理

| ID | 需求 |
|---|---|
| FR-2.1 | User 可新增、查詢、修改、刪除自己的 Source |
| FR-2.2 | 同一 User 下不可有重複 URL 的 Source |
| FR-2.3 | Source 可被啟用 / 停用，停用時不參與排程 |
| FR-2.4 | 系統應顯示每個 Source 最近 N 次 FetchJob 的結果 |

### FR-3 內容管線

| ID | 需求 |
|---|---|
| FR-3.1 | 系統應依排程自動對所有啟用中的 Source 執行抓取 |
| FR-3.2 | 多實例環境下，同一次排程只能有一個實例執行（distributed lock） |
| FR-3.3 | 系統應以 content hash 進行 dedup，重複內容不建立新的 FetchedItem |
| FR-3.4 | 暫時性失敗應以 exponential backoff 自動 retry，最多 N 次 |
| FR-3.5 | 永久性失敗不應 retry |
| FR-3.6 | 超過最大 retry 次數的項目應進入 DLQ，可被查詢 |
| FR-3.7 | 所有外部呼叫皆須設定 timeout |
| FR-3.8 | 系統應為每筆 FetchedItem 產生 LLM 摘要 |
| FR-3.9 | LLM 呼叫使用該 User 自己設定的 API key（BYOK，見 ADR-003） |
| FR-3.9a | User 可設定、更新、刪除自己的 LLM API key |
| FR-3.9b | 系統應限制每個 User 每日最多 200 次 LLM 呼叫，用途為防止排程設定錯誤導致爆量，超過時延後處理而非失敗 |
| FR-3.9c | User 未設定 API key 時，FetchedItem 停留在 `NEW` 狀態，不視為失敗 |
| FR-3.12 | 已 promote 的 FetchedItem 於 30 天後由排程任務清除（見 ADR-002） |
| FR-3.10 | 僅 `READY` 狀態的 FetchedItem 可 promote 為 Document |
| FR-3.11 | User 可手動觸發某個 Source 的抓取 |

### FR-4 知識庫

| ID | 需求 |
|---|---|
| FR-4.1 | User 可建立、查詢、修改、刪除自己的 Document |
| FR-4.2 | Document 內容以 Markdown 儲存 |
| FR-4.3 | 修改 `origin = MANUAL` 的 Document 時應建立 DocumentVersion 快照 |
| FR-4.4 | 更新 Document 應使用 optimistic lock，衝突時回傳 409 |
| FR-4.5 | `origin = FETCHED` 的 Document 其原文內容不可修改，但可加註解 |
| FR-4.6 | User 可依關鍵字搜尋自己的 Document |
| FR-4.7 | User 只能存取屬於自己的 Document 與 Source |

---

## 5. Non-functional Requirements（NFR）

> **原則**：每一條都必須可測量。「要很快」不是需求，「p95 < 200ms」才是。

### NFR-1 效能

| ID | 需求 | 驗證方式 |
|---|---|---|
| NFR-1.1 | 單篇 Document 查詢 p95 延遲 < 200ms（1 萬筆資料下） | 壓測 |
| NFR-1.2 | 搜尋 p95 延遲 < 500ms（1 萬筆資料下） | 壓測 |
| NFR-1.3 | 單一 Source 的抓取應在 30 秒內完成或 timeout | 整合測試 |

### NFR-2 可靠性

| ID | 需求 | 驗證方式 |
|---|---|---|
| NFR-2.1 | 任一外部來源失敗不得導致其他來源的抓取失敗 | 整合測試（模擬失敗） |
| NFR-2.2 | 重複執行同一次抓取不得產生重複 Document（idempotent） | 整合測試（重複觸發） |
| NFR-2.3 | 排程在多實例環境下不得重複執行 | 整合測試（模擬雙實例） |
| NFR-2.4 | 所有外部呼叫的 timeout 不得超過 10 秒 | 設定檢查 |

### NFR-3 安全

| ID | 需求 | 驗證方式 |
|---|---|---|
| NFR-3.1 | 密碼不得以明文儲存或出現在日誌中 | Code review + 日誌檢查 |
| NFR-3.2 | User 不得存取他人的資料 | Security test |
| NFR-3.3 | 所有輸入須經過驗證，拒絕不合法請求並回傳 400 | 整合測試 |
| NFR-3.4 | 加密金鑰不得寫死在程式碼中，須由環境變數提供 | Code review |
| NFR-3.5 | User 的 LLM API key 須加密後儲存，不得明文入庫 | 資料庫檢查 |
| NFR-3.6 | API key 回傳前端時須遮罩（例：`sk-...c123`），不得回傳完整值 | 整合測試 |
| NFR-3.7 | API key 不得出現在任何日誌中 | 日誌檢查 |

### NFR-4 可觀測性

| ID | 需求 | 驗證方式 |
|---|---|---|
| NFR-4.1 | 每次 FetchJob 執行須留下結構化日誌（來源、耗時、結果） | 日誌檢查 |
| NFR-4.2 | 每個請求須有 correlation ID 可貫穿日誌 | 日誌檢查 |
| NFR-4.3 | 失敗的 FetchedItem 須可查詢失敗原因與 retry 次數 | API 提供 |

### NFR-5 可維護性

| ID | 需求 | 驗證方式 |
|---|---|---|
| NFR-5.1 | 核心路徑測試覆蓋率 ≥ 60% | CI 報告 |
| NFR-5.2 | 資料庫變更一律透過 Flyway migration | Code review |
| NFR-5.3 | 新開發者能在 5 分鐘內以 `docker compose up` 啟動環境 | 實測 |

---

## 6. 假設與限制

### 假設

- 單一使用者使用，資料量在 1 萬筆 Document 以內
- 外部 RSS 來源提供標準格式
- LLM API 有免費或低成本額度可用

### 限制

- 開發時程 21 天
- 不做前端 SPA，以 API + Swagger 為主要介面
- 不做多租戶、不做即時協作

---

## 7. 已決事項（Day 2 收斂）

| 項目 | 決定 | 依據 |
|---|---|---|
| FetchedItem 保留期限 | promote 後 30 天，由排程任務清除 | ADR-002 |
| Retry 參數 | 初始等待 1 秒、倍數 2、上限 60 秒、最多 3 次 | 暫定值，Day 13 實測後調整 |
| LLM 額度來源 | BYOK，使用者自帶 API key | ADR-003 |
| 每日呼叫上限 | 每 User 200 次，用途為防爆量 | ADR-003 |
| 初始訂閱來源 | ① GitHub `anthropics/claude-code` releases（`releases.atom`）<br>② Hacker News | 見下方說明 |

### 初始來源選擇理由

先只放兩個來源，避免第一次跑管線就抓回數百篇造成除錯困難。

- **GitHub releases.atom**：格式標準、內容短、更新頻率低，
  是最適合當第一個測試來源的對象
- **Hacker News**：涵蓋 AI 產業重大消息，直接對應核心使用情境
  （「不想再因為不知道業界發生什麼事而接不上話」）

管線穩定後再逐步增加來源。

## 8. 待決事項

- [ ] 加密演算法選擇（AES-GCM vs 資料庫層級加密）
- [ ] Digest 的呈現時間範圍（當日 / 近 7 天）
