# ADR-007: 選用 PostgreSQL 而非 MySQL

## 狀態

已接受（Day 4）

## 背景

開發者本機已安裝 MySQL，因此提出「為何不直接使用 MySQL」的疑問。

需要說明的是：資料庫執行於 Docker 容器中，
本機是否已安裝任何資料庫與本決策無關。
真正的判準是「哪一套支援本專案的設計」。

## 考慮過的選項

### 選項 A：PostgreSQL（採用）

- 優點：**支援 partial index**（`CREATE UNIQUE INDEX ... WHERE 條件`）。
  ADR-005 決定全面採用 soft delete，唯一約束必須加上
  `WHERE deleted_at IS NULL` 才能讓資料在刪除後可重新建立。
  **這是決定性因素——MySQL 不支援此語法**
- 優點：`TIMESTAMPTZ` 為真正帶時區的型別，一律以 UTC 儲存
- 優點：`CHECK` 約束、`GIN` 索引、`JSONB` 等功能較完整
- 缺點：在台灣傳統企業的普及度略低於 MySQL

### 選項 B：MySQL

- 優點：台灣就業市場普及度高
- 優點：MySQL 8 的 ngram parser 對中文全文檢索的支援反而優於
  PostgreSQL 內建方案
- 缺點：**無 partial index**。若採用，ADR-005 的 soft delete 設計
  需改用變通做法——例如新增一個欄位，刪除時填入該列 id 以湊出唯一性。
  可行但不直觀，且後續維護者難以理解該欄位存在的理由
- 缺點：無真正帶時區的時間型別。`TIMESTAMP` 會隱式轉換為 UTC，
  `DATETIME` 則完全不處理時區，跨時區部署時容易產生難以追查的錯誤

## 決定

採用 PostgreSQL 17。

## 理由

**設計已經依賴 PostgreSQL 的功能。** 改用 MySQL 並非「換一個資料庫」，
而是要回頭修改 ADR-005 的 soft delete 策略，並為此接受一個不直觀的變通做法。

在「配合既有設計」與「配合市場普及度」之間，選擇前者——
因為前者影響程式碼的正確性，後者只影響履歷上的關鍵字。

## 後果

### 好處

- soft delete 的唯一約束可直接以 partial index 正確表達
- 時間欄位的時區處理正確且不需額外注意事項
- CHECK 約束可用於保護狀態機的一致性

### 代價

- 若應徵以 MySQL 為主的公司，需自行說明兩者差異
- **緩解**：這反而是面試素材——能說明「為什麼這個專案選 PostgreSQL」
  比「我用過 MySQL」更能展現判斷力

### 重新評估的時機

若日後需整合僅支援 MySQL 的既有系統，
則需重新檢視 ADR-005 的 soft delete 實作方式。
