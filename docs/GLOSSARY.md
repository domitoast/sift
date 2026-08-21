# GLOSSARY.md — 名詞表（Ubiquitous Language）

> 這是本專案所有 domain 名詞的**唯一定義**。
> **規則**：程式碼的類別名稱、資料表名稱、API 欄位名稱必須直接對應本表。
> 要新增或修改名詞，先改本表，再改程式碼。

**最後更新**：Day 2

---

## 核心資料模型（兩層設計）

```
Source（訂閱來源）
   │
   │ 排程觸發
   ▼
FetchJob（一次抓取任務）
   │
   │ 產生 0..N 筆
   ▼
FetchedItem（staging：髒資料工作區）
   │
   │ dedup 通過 + 摘要成功 → promote（晉升）
   ▼
Document（知識庫：乾淨成品，origin = FETCHED）
   ▲
   │ 使用者手寫
   └── Document（origin = MANUAL）
```

**設計原則**：staging 層負責「髒活」（重複、失敗、retry），curated 層只放「成品」。
詳見 ADR-002。

---

## A. 使用者與認證

### User（使用者）

- **定義**：系統的帳號擁有者。本專案為單人使用，但架構上仍支援多帳號。
- **英文命名**：`User`
- **它不是什麼**：不是「訂閱者」——本系統不對外推播。
- **關鍵規則**：所有 Document、Source 都屬於某一個 User。

---

## B. 內容管線（Pipeline）

### Source（訂閱來源）

- **定義**：一個可被定期抓取的外部內容位址，例如某個部落格的 RSS feed。
- **英文命名**：`Source`
- **它不是什麼**：不是單篇文章，是**文章的產地**。
- **關鍵欄位**：URL、類型（RSS / API）、抓取頻率、是否啟用、最後成功抓取時間。
- **關鍵規則**：同一個 User 底下，同一個 URL 只能有一筆 Source。

### FetchJob（抓取任務）

- **定義**：對某個 Source 執行「一次」抓取的紀錄。一次排程觸發產生一筆 FetchJob。
- **英文命名**：`FetchJob`
- **它不是什麼**：不是抓回來的內容本身，是**這次抓取這個動作的紀錄**。
- **狀態機**：`PENDING` → `RUNNING` → `SUCCESS` / `FAILED`
- **關鍵欄位**：所屬 Source、狀態、開始/結束時間、失敗分類、失敗原因。
- **關鍵規則**：同一個 Source 在同一時間只能有一個 RUNNING 的 FetchJob（靠 distributed lock 保證）。
- **數量關係**：**一次抓取動作 = 一筆 FetchJob，但通常產生數十筆 FetchedItem。**
  這是本專案最容易誤解的數量關係。
- **無 retry 欄位**（ADR-008）：本系統為每日排程，抓取失敗等下次排程即可。
  分鐘級 retry 只在 FetchedItem 的 LLM 摘要上有意義。

### FetchedItem（抓取項目 / staging）

- **定義**：一次抓取所取回的**單一原始內容**，尚未進入知識庫。這是管線的工作區。
- **英文命名**：`FetchedItem`
- **它不是什麼**：**不是 Document**。它可能是重複的、可能摘要失敗、可能是垃圾內容。
- **狀態機**：`NEW` → `SUMMARIZING` → `READY` → `PROMOTED`，或任一步 → `DISCARDED` / `FAILED`
- **關鍵欄位**：所屬 Source、原始網址、標題、原始內容、發布時間、內容雜湊值（dedup 用）、狀態、retry 次數。
- **關鍵規則**：
  - 內容雜湊值（或原始網址）加 unique constraint，這是 **dedup 的實作點**
  - **只有 READY 狀態的 FetchedItem 才能 promote 成 Document**

### Summary（摘要）

- **定義**：由 LLM 針對 FetchedItem 產生的濃縮內容，通常包含三行重點與「為什麼重要」。
- **英文命名**：`Summary`
- **它不是什麼**：不是使用者寫的筆記。
- **關鍵規則**：摘要產生失敗不影響 FetchedItem 本身的保存，只是無法 promote。

### Digest（每日摘要頁）

- **定義**：把某一天所有 READY / PROMOTED 的內容彙整成一份可閱讀的清單。
- **英文命名**：`Digest`
- **它不是什麼**：不是一個實體資料表（可由查詢動態產生），是一個**呈現概念**。

---

## C. 知識庫（Knowledge Base）

### Document（文件）

- **定義**：知識庫中的一則內容。這是使用者最終會閱讀、搜尋、標註的東西。
- **英文命名**：`Document`
- **它不是什麼**：不是 FetchedItem。**Document 一律是「成品」**——不會有摘要失敗、待 retry 的 Document。
- **來源區分**：`origin` 欄位
  - `MANUAL`：使用者自己寫的筆記
  - `FETCHED`：由 FetchedItem 晉升而來
- **關鍵欄位**：標題、內容（Markdown）、origin、來源 FetchedItem（可為空）、version、建立/更新時間。
- **關鍵規則**：
  - `origin = FETCHED` 的 Document，其原文內容**不可編輯**，但使用者可加註解欄位
  - 使用 `@Version` 做 optimistic lock

### DocumentVersion（文件版本）

- **定義**：Document 每次被編輯後保留的歷史快照。
- **英文命名**：`DocumentVersion`
- **它不是什麼**：不是 optimistic lock 的版本號。**兩者名字像但用途完全不同**：
  - `Document.version`（`@Version`）：給 optimistic lock 用的併發控制欄位
  - `DocumentVersion`：給使用者看的歷史紀錄
  - ⚠️ 這是本專案最容易搞混的一組名詞
- **關鍵規則**：只有 `origin = MANUAL` 的 Document 會產生版本歷史。

### ~~Tag（標籤）~~ / ~~Attachment（附件）~~

**已於 Day 5 移除**（ADR-008）。

`PROJECT_RULES.md` 明訂不做標籤管理介面——沒有介面即無法建立標籤，
該資料表永遠為空。需要時再以 V2 migration 加回。

附件功能位於停損順序第 3 位，尚未實作，因此資料模型也不預先建立。

---

## D. 容易搞混的名詞對照

| 容易混淆 | 差別 |
|---|---|
| `Source` vs `FetchedItem` | Source 是產地，FetchedItem 是產物 |
| `FetchJob` vs `FetchedItem` | FetchJob 是「抓取這個動作」，FetchedItem 是「抓到的東西」 |
| `FetchedItem` vs `Document` | staging（髒）vs curated（乾淨） |
| `Document.version` vs `DocumentVersion` | 併發控制欄位 vs 使用者可見的歷史紀錄 |
| `Summary` vs `Digest` | 單篇的摘要 vs 一整天的彙整清單 |

---

## E. 動詞（domain 行為）

| 動詞 | 意義 |
|---|---|
| **fetch** | 從 Source 抓取內容，產生 FetchedItem |
| **dedup** | 判斷 FetchedItem 是否已存在 |
| **summarize** | 對 FetchedItem 呼叫 LLM 產生 Summary |
| **promote** | 把 READY 的 FetchedItem 晉升為 Document |
| **discard** | 丟棄不需要的 FetchedItem（重複、垃圾） |

> 這些動詞會直接對應到 Service 的方法名稱。
> 例如 `FetchedItemService.promote(...)`，而不是 `saveToDocument(...)`。
