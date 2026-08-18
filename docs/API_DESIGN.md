# API 設計

**風格**：REST
**基底路徑**：`/api/v1`
**認證**：Bearer token（JWT）置於 `Authorization` 標頭
**內容型別**：`application/json`
**最後更新**：Day 3

---

## 命名慣例

- 路徑用**名詞複數**：`/documents` 而非 `/document` 或 `/getDocument`
- 路徑用 **kebab-case**：`/fetched-items` 而非 `/fetchedItems`
- JSON 欄位用 **camelCase**：`createdAt` 而非 `created_at`
  （資料庫用 snake_case，API 用 camelCase，由 DTO 負責轉換）
- 動作型操作包裝成子資源：`POST /sources/{id}/fetches`

---

## 狀態碼使用規則

| 狀態碼 | 使用時機 |
|---|---|
| 200 OK | 查詢成功、更新成功 |
| 201 Created | 建立成功，回應標頭附 `Location` |
| 202 Accepted | 已接受但尚未完成（非同步作業，例如手動觸發抓取） |
| 204 No Content | 成功且無回應內容（刪除） |
| 400 Bad Request | 請求格式或參數錯誤 |
| 401 Unauthorized | 未提供 token 或 token 無效 / 過期 |
| 403 Forbidden | 已認證但無權限 |
| 404 Not Found | 資源不存在 |
| 409 Conflict | 狀態衝突（optimistic lock、email 重複、狀態機不允許的轉移） |
| 422 Unprocessable Entity | 格式正確但違反業務規則 |
| 429 Too Many Requests | 超過 rate limit |
| 500 Internal Server Error | 未預期的例外 |

### 400 與 422 的分野

- **400**：連格式都不對。`title` 應為字串卻收到數字
- **422**：格式沒問題，但業務規則不允許。
  例如對狀態為 `NEW` 的 FetchedItem 執行 promote
  （只有 `READY` 可以 promote）

---

## 錯誤回應格式（RFC 7807 Problem Details）

所有錯誤回應統一格式：

```json
{
  "type": "https://sift.dev/errors/document-conflict",
  "title": "文件已被修改",
  "status": 409,
  "detail": "這份文件在你編輯期間已被更新，請重新載入後再試",
  "instance": "/api/v1/documents/42",
  "currentVersion": 7
}
```

| 欄位 | 意義 |
|---|---|
| `type` | 錯誤類型的識別字串（前端據此判斷） |
| `title` | 人類可讀的簡短說明 |
| `status` | HTTP 狀態碼 |
| `detail` | 具體說明，可包含這次請求的細節 |
| `instance` | 發生錯誤的資源路徑 |

驗證錯誤額外附 `errors` 陣列：

```json
{
  "type": "https://sift.dev/errors/validation",
  "title": "輸入驗證失敗",
  "status": 400,
  "instance": "/api/v1/documents",
  "errors": [
    { "field": "title", "message": "標題不可為空" },
    { "field": "content", "message": "內容長度不可超過 100000 字元" }
  ]
}
```

**採用理由**：前端只需實作一套錯誤處理邏輯。
若每個 endpoint 的錯誤格式各異，前端必須為每個 API 寫專屬處理。

---

## 分頁格式

採 **offset-based**（`page` + `size`）：

```
GET /api/v1/documents?page=0&size=20&sort=createdAt,desc
```

回應：

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

**選擇理由**：Spring Data 原生支援，實作成本近乎為零，
且本專案資料量（1 萬筆以內）不會遇到 offset 分頁的效能問題。

**已知限制**：資料量極大時 `OFFSET 100000` 會很慢，
且分頁期間有新資料插入會導致項目重複或遺漏。
屆時應改用 cursor-based 分頁。此限制記錄於此，供日後參考。

---

## Endpoint 清單

### 認證

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| POST | `/auth/register` | 註冊 | 201 |
| POST | `/auth/login` | 登入，回傳 access + refresh token | 200 |
| POST | `/auth/refresh` | 以 refresh token 換發新 access token | 200 |
| POST | `/auth/logout` | 使 refresh token 失效 | 204 |

**錯誤情境**

- 409：email 已被註冊
- 401：帳號或密碼錯誤（**不區分是帳號錯還是密碼錯**，避免帳號列舉攻擊）

### 使用者設定

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| GET | `/me` | 取得自己的資料（API key 以遮罩形式回傳） | 200 |
| PUT | `/me/llm-api-key` | 設定 / 更新 LLM API key | 204 |
| DELETE | `/me/llm-api-key` | 移除 API key | 204 |

**安全規則**：`GET /me` 回傳的 `llmApiKeyMasked` 僅顯示如 `sk-...c123`，
完整值永不回傳（NFR-3.6）。

### 訂閱來源

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| GET | `/sources` | 列出自己的來源（分頁） | 200 |
| POST | `/sources` | 新增來源 | 201 |
| GET | `/sources/{id}` | 查詢單一來源 | 200 |
| PATCH | `/sources/{id}` | 修改（名稱、啟用狀態、抓取頻率） | 200 |
| DELETE | `/sources/{id}` | soft delete | 204 |
| GET | `/sources/{id}/fetch-jobs` | 該來源最近的抓取紀錄（FR-2.4） | 200 |
| POST | `/sources/{id}/fetches` | **手動觸發一次抓取** | 202 |

`POST /sources/{id}/fetches` 回傳 202 而非 201，
因為抓取是非同步執行——請求已被接受，但尚未完成。
回應內容為建立的 FetchJob，前端可據其 id 輪詢狀態。

**錯誤情境**

- 409：該來源已有一個 `RUNNING` 中的 FetchJob
- 409：新增時網址與既有來源重複

### 抓取項目（staging）

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| GET | `/fetched-items` | 列出（可依 `status` 篩選） | 200 |
| GET | `/fetched-items/{id}` | 查詢單筆（含失敗原因、retry 次數） | 200 |
| POST | `/fetched-items/{id}/promotion` | **promote 為 Document** | 201 |
| DELETE | `/fetched-items/{id}` | 標記為 `DISCARDED` | 204 |
| GET | `/fetched-items/failed` | **DLQ 檢視**：撈出 retry 耗盡的項目（FR-3.6） | 200 |

**錯誤情境**

- 422：狀態不是 `READY` 時執行 promote
- 409：已經 promote 過（由 `document.fetched_item_id` 的唯一約束保證）

### 知識庫文件

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| GET | `/documents` | 列出（分頁、可搜尋） | 200 |
| POST | `/documents` | 建立手寫筆記 | 201 |
| GET | `/documents/{id}` | 查詢單篇 | 200 |
| PATCH | `/documents/{id}` | 修改（**需帶 version**） | 200 |
| DELETE | `/documents/{id}` | soft delete | 204 |
| GET | `/documents/{id}/versions` | 版本歷史列表 | 200 |
| GET | `/documents/{id}/versions/{n}` | 查詢特定版本內容 | 200 |
| GET | `/documents/search?q=關鍵字` | 全文搜尋 | 200 |

**PATCH 的請求格式**

```json
{
  "title": "新標題",
  "content": "新內容",
  "version": 6
}
```

`version` 為必填。若與資料庫現值不符，回傳 409 並附上目前版本內容。
這是 optimistic lock 在 API 層的呈現方式。

**錯誤情境**

- 409：version 不符（編輯衝突）
- 422：嘗試修改 `origin = FETCHED` 的文件之 `content`
  （原文不可改，僅 `note` 欄位可改）

### 每日摘要

| Method | 路徑 | 說明 | 成功狀態碼 |
|---|---|---|---|
| GET | `/digest?date=2026-08-13` | 指定日期的摘要清單 | 200 |

---

## 已決事項（Day 3）

### 存取他人資源一律回 404，不回 403

當使用者嘗試存取不屬於自己的資源時，回傳 **404 Not Found**，
而非語意上更精確的 403 Forbidden。

**理由**：403 會洩漏「該資源存在」這項資訊。
攻擊者可掃描 `/documents/1` 至 `/documents/9999`，
依回應碼區分哪些 id 存在，藉此推知系統規模與 id 分布。
GitHub 對無權限的私有 repo 同樣回傳 404。

**代價**：自行除錯時無法從回應碼區分「資源不存在」與「無權限」。
**緩解**：伺服器端日誌必須明確記錄實際原因，
但回傳給客戶端的一律是 404。

### API key 使用獨立 endpoint

採用 `PUT /me/llm-api-key` 與 `DELETE /me/llm-api-key`，
不併入通用的 `PATCH /me`。

**理由**：敏感資料的處理路徑應窄而明確。
日後若要為「修改 API key」加上額外保護
（重新驗證密碼、單獨記錄稽核日誌、加上 rate limit），
獨立 endpoint 可直接套用；
若混在通用 endpoint 中，則須在共用邏輯內寫特例，容易出錯或遺漏。
