# ADR-005: 一律採用 soft delete，外鍵設為 RESTRICT

## 狀態

已接受（Day 3）

## 背景

資料表之間存在外鍵關聯，例如 `fetched_item.source_id → source.id`。
必須決定：當使用者刪除一個 Source 時，底下數千筆 FetchJob 與 FetchedItem 該如何處理？

## 考慮過的選項

### 選項 A：`ON DELETE CASCADE`（連帶刪除）

刪除 Source 時，資料庫自動刪除所有關聯的 FetchJob 與 FetchedItem。

- 優點：不會產生孤兒資料，實作最簡單
- 缺點：破壞力極大。誤刪一個訂閱半年的來源，數千筆歷史資料瞬間消失且無法復原

### 選項 B：`ON DELETE RESTRICT`（禁止刪除）

只要底下仍有關聯資料就不允許刪除 Source。

- 優點：最安全
- 缺點：使用者必須先手動清空所有關聯資料才能刪除來源，操作繁瑣

### 選項 C：soft delete + RESTRICT（採用）

Source 不實體刪除，僅將 `deleted_at` 設為當前時間。
效果為：停止排程抓取、不再出現於列表，但歷史資料完整保留。
外鍵仍設為 RESTRICT 作為底層保險。

- 優點：誤刪可復原；歷史資料保留；與 `document` 的既有策略一致
- 缺點：所有查詢都必須加上 `WHERE deleted_at IS NULL`

## 決定

採用選項 C。所有具有業務意義的資料表一律採 soft delete，
外鍵一律設為 `ON DELETE RESTRICT`。

## 理由

1. **語意上正確**：不再追蹤某個部落格，不代表要丟棄過去存下的文章
2. **與既有設計一致**：`document` 已採 soft delete，兩張表行為統一可降低心智負擔
3. **RESTRICT 作為第二道防線**：若有人繞過應用程式直接下 SQL 刪除，
   資料庫會拒絕，避免產生孤兒資料

## 後果

### 好處

- 誤刪可復原
- 歷史資料完整，利於日後追查與統計

### 代價（重要）

**每一次查詢都必須加上 `WHERE deleted_at IS NULL`。遺漏即為 bug，
且此類 bug 不會拋出錯誤，只會靜默地回傳已刪除的資料。**

緩解方式：Day 8 導入 JPA 的統一過濾機制（例如 `@SQLRestriction`），
由框架自動附加條件，而非在每個查詢手寫。

### 額外影響

- 唯一約束需要調整：`source` 的 `UNIQUE (user_id, url)` 會導致
  「刪除後無法重新訂閱同一網址」。需改為 partial unique index，
  僅對 `deleted_at IS NULL` 的資料列生效
- 索引需納入 `deleted_at` 欄位

### 重新評估的時機

若資料量成長到 soft delete 導致查詢明顯變慢，
可考慮定期將已刪除資料搬移至封存表（archive table）。
