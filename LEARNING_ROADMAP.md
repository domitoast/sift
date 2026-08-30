# LEARNING_ROADMAP.md — 學習地圖

> 每學會一項就更新一次。掌握度定義：
> - ⬜ 沒概念
> - 🟡 聽過 / 看得懂別人寫的
> - 🟢 能自己寫出來
> - ⭐ 能講清楚為什麼，並說出替代方案與取捨

**最後更新**：Day 16（補洞：測試、autodiscovery、抓取紀錄 API）

> 📖 **參考文件**
> - [`docs/ANNOTATIONS.md`](docs/ANNOTATIONS.md) — 本專案用到的所有註解
> - [`docs/TESTING.md`](docs/TESTING.md) — 怎麼讀懂這個專案的測試

---

## 已建立的工程觀念（非技術項目）

| 觀念 | 掌握度 | 重點 |
|---|---|---|
| Positioning Statement | 🟢 | 定位的一半價值在劃 out-of-scope |
| ADR | 🟢 | 最有價值的是「被否決的選項」與「後果」 |
| Ubiquitous Language | 🟢 | 定義「它不是什麼」比定義本身有用 |
| Staging vs Curated | 🟢 | 生命週期不同的資料不該同表 |
| 可測量的 NFR | 🟡 | 「要很快」不是需求，「p95 < 200ms」才是 |
| Transient vs Permanent failure | 🟡 | 只有暫時性失敗值得 retry |
| Normalization（正規化） | 🟡 | 同一件事實只存一次。判斷法：「這個值要改，我要改幾個地方？」 |
| Denormalization（反正規化） | 🟡 | 正規化是預設，反正規化是需要理由的例外 |
| Race condition | 🟡 | 「先查再寫」之間有空隙，並發時兩個 thread 會同時通過檢查 |
| Unique constraint | 🟡 | 資料庫層的檢查沒有空隙，是並發下唯一可靠的保證 |
| Insert-or-ignore | 🟡 | 不先查，直接寫，失敗當重複——dedup 的標準寫法 |
| Soft delete | 🟡 | 誤刪可救；代價是每個查詢都要加 `WHERE deleted_at IS NULL` |
| Partial unique index | 🟡 | 唯一約束只對符合條件的資料列生效，解決 soft delete 後無法重新建立的問題 |
| REST | 🟡 | 網址是名詞，HTTP method 是動詞。是風格不是規範 |
| HTTP 狀態碼 | 🟡 | 401 是「你是誰」，403 是「我知道你是誰但你不能碰」 |
| 資訊洩漏（information leakage） | 🟡 | 對無權限資源回 404 而非 403，避免洩漏資源是否存在 |
| RFC 7807 Problem Details | 🟡 | 統一錯誤格式，前端只需一套處理邏輯 |
| ER 圖的關聯基數 | 🟡 | `\|\|`＝恰好一個、`o{`＝零或多、`\|o`＝零或一 |
| IDOR / Broken Access Control | 🟢 | 把 `user_id` 放進查詢條件，而非查出來再比對。<br>OWASP Top 10 第一名 |
| Secure by construction | 🟢 | 把安全規則做成「不可能忘記」的形式，<br>而非「記得要做」的形式。Repository 只提供 `findByIdAndUserId` |
| CHECK constraint | 🟢 | 自訂條件，不符合就拒絕寫入。已親手驗證過小寫 `rss` 被擋下 |
| Database trigger | 🟢 | 某事發生時資料庫自動執行一段程式。已親手驗證 `updated_at` 自動更新 |
| Invariant 下推到資料層 | 🟢 | 不變條件放在資料庫，所有路徑都躲不掉；<br>放在程式碼則會被新的 endpoint 繞過 |
| TIMESTAMPTZ 與 UTC | 🟢 | 一律以 UTC 儲存，顯示時才轉當地時間。<br>用不帶時區的型別，機房搬遷後資料無法救回 |
| Walking skeleton | 🟡 | 先讓最小但完整的系統跑通，再往上長肉 |
| Connection pool | 🟡 | 預先建立幾條資料庫連線重複使用，避免每次查詢都重新握手 |
| Dependency management | 🟡 | `spring-boot-starter-parent` 統一決定版本，避免函式庫互相衝突 |
| Docker 的兩種用途 | 🟢 | 開發時「拿別人做好的 image 來用」；<br>部署時「把自己的程式打包成 image」。多數人只知道後者 |
| 分層架構的職責 | 🟡 | Controller 只接請求、Service 放邏輯、Repository 只存取。<br>DTO 與 Entity 不是「層」，是層與層之間傳遞的資料 |
| DTO 存在的理由 | 🟢 | 直接回傳 Entity 會把 passwordHash 一起送出去。<br>DTO 是「預設不外洩」，`@JsonIgnore` 是「記得要排除」 |
| 在邊界正規化資料 | 🟢 | 驗證發生在 Controller 之前，所以清理必須更早。<br>由整合測試發現的設計缺陷 |
| Defence in depth | 🟢 | 程式檢查給友善訊息，資料庫約束才是保證。<br>DTO 與 Service 都做正規化也是同一思路 |
| Mock 與可測試性 | 🟡 | 能把 Repository 換成假的，是因為當初用了建構子注入 |
| 機密不進版控 | 🟢 | 判斷法：「這個值外洩會怎樣？」會出事就是機密。<br>Git 是永久的——改掉那行沒用，必須換掉金鑰 |
| Fail fast 的不對稱設計 | 🟢 | 機密不給預設值（沒設就啟動失敗）；<br>非機密給預設值。最糟的情況是「用預設弱金鑰默默跑起來」 |
| JWT 是簽章不是加密 | 🟢 | 任何人都能解開讀內容（已在 jwt.io 親眼驗證）。<br>簽章防竄改，不防偷看 |
| HMAC 簽章原理 | 🟡 | 訊息＋金鑰 → 指紋。金鑰不同或訊息改一個字，指紋完全不同。<br>驗證＝用自己的金鑰重算一次比對 |
| 對稱式 vs 非對稱式 | 🟡 | HS512 用同一把鑰匙簽與驗；RS256 用私鑰簽、公鑰驗。<br>多服務共用發證中心時才需要後者 |
| access / refresh 的搭配原理 | 🟢 | 暴露頻率與有效期要「反向」搭配。<br>每次請求都帶的要短命，很少用的可以長命 |
| SecureRandom vs Random | 🟢 | `java.util.Random` 可預測——觀察幾個輸出就能推算後續。<br>安全相關的隨機值一律用 SecureRandom |
| 雜湊演算法的選擇原則 | 🟢 | 取決於「被保護的值有多容易猜」。<br>人類密碼用慢的 BCrypt，256-bit 隨機值用快的 SHA-256 |
| User enumeration | 🟢 | 登入失敗不區分「帳號不存在」與「密碼錯誤」，<br>否則等於送給攻擊者一份有效帳號清單 |
| Auto-configuration | 🟡 | 加 starter 不只是多一些 import——Spring 會掃 classpath，<br>依條件自動建立 Bean。安全標頭就是這樣自己出現的 |
| Classpath | 🟡 | 「Java 去哪裡找 class」的清單。<br>就是每次啟動 log 第一行那一長串 |
| Migration 是增量的 | 🟢 | `CREATE TABLE` / `ALTER TABLE` 在活的資料庫上動手術，<br>不需要重建。正式環境永遠不可能 `down -v` |
| 測試「沒做某件事」 | 🟡 | `verify(repo, never()).save(any())`——<br>少了它，「先存再丟例外」的錯誤寫法也會通過測試<br>⚠️ Day 5 抽問答錯，降級。Day 7 空手日重測 |
| 誰守得住哪一側？ | 🟡 | Java↔資料庫：`ddl-auto: validate` 與 Spring Data 在**啟動時**就爆<br>Java↔JSON：**只有整合測試**。框架完全不把關<br>⚠️ Day 5 抽問答錯（選了「編譯不過」），需重測 |

---

## 進度總覽

| 分類 | 項目數 | 🟡 以上 | 🟢 以上 |
|---|---|---|---|
| Spring Boot 核心 | 11 | 10 | 3 |
| 資料層 | 12 | 11 | 4 |
| Reliability engineering（深度戰場） | 8 | 2 | 0 |
| 安全 | 7 | **7** | 3 |
| 測試 | 6 | 4 | 1 |
| 基礎設施 | 6 | 3 | 2 |

> **Day 14 現況（強制檢查點）**
>
> **深度戰場（C 類）到今天為止仍然幾乎空白。** 這是本專案最大的一個問題——
> `CLAUDE.md` 開宗明義寫「知識庫是外殼，抓取管線是深度戰場」，
> 而 14 天過去，外殼做完了，戰場一行都沒寫。
>
> Day 15–18 全部是 C 類，**不可再砍**。詳見 `ROADMAP_21D.md` 的 Day 14 檢查點結論。
>
> **空手日 #2 的結果：部分完成，比 Day 7 進步。**
>
> | | Day 7 | Day 14 |
> |---|---|---|
> | 實作 | ❌ 卡住，Claude 代寫 | ✅ 四個檔案全部自己寫，方向全對 |
> | 測試 | ❌ | ❌ 沒寫 |
> | 設計決定 | ✅ 說得出理由 | ✅ 決定 2 事前想過並答對 |
> | 過程紀錄 | ❌ | ❌ 卡住紀錄空白 |
>
> **量到的進步**：從「無法動手」變成「能動手且方向正確」。
> **量到的缺口**：**寫完就停了，沒有補測試的習慣。** 這是 Day 18 要處理的。
>
> ⚠️ **Day 6 的節奏警訊仍然有效**：一天內改動十個檔案且中途沒有可運作的成果，
> 會導致理解斷裂。每個工作段落都應該以「有東西能跑」作為結束條件。

---

## A. Spring Boot 核心

### A1. Dependency Injection / IoC 容器
- **掌握度**：🟡（已教學，尚未實際使用）
- **核心概念**：物件不自己 new 依賴，由容器注入
- **本專案用途**：所有 Service、Repository 的組裝
- **常見錯誤**：用欄位注入而非建構子注入；循環依賴
- **面試考點**：為什麼要 DI？建構子注入 vs 欄位注入的差別？

### A2. Bean 與生命週期
- **掌握度**：🟡（已看過 @Service / @Bean / @Configuration 的實例）
- **核心概念**：由 Spring 容器管理的物件
- **常見錯誤**：以為每次拿到的都是新物件（預設是 singleton）
- **面試考點**：Bean scope 有哪些？singleton 有什麼陷阱？

### A3. 分層架構（Controller / Service / Repository）
- **掌握度**：🟡（已完整走過一輪，但尚未自己寫）
- **常見錯誤**：業務邏輯寫在 Controller；Service 直接回傳 Entity

### A4. DTO vs Entity
- **掌握度**：🟢（能說出為什麼不能直接回傳 Entity）
- **面試考點**：為什麼不直接回傳 Entity？

### A5. Validation（Bean Validation）
- **掌握度**：🟡
- **關鍵**：沒有 `@Valid` 就完全不會生效，且不會有任何警告
- **順序**：Jackson 建物件 → 驗證 → Controller。清理資料必須更早

### A5.1 PUT vs PATCH，以及包裝類別的必要性（Day 13）
- **掌握度**：🟢（自己判斷對了）
- **`PUT`** = 「用我給的內容**整個取代**」→ 所有欄位必填
- **`PATCH`** = 「**只改我有給的**」→ 欄位可省略，沒給的維持原樣
- **⚠️ PATCH 的 DTO 一定要用包裝類別**：`Boolean` 而不是 `boolean`
- **為什麼**：原始型別沒有「沒給」這個狀態。JSON 沒帶 `enabled` 時<br>Jackson 會填入預設值 `false`——使用者只是改個名字，來源卻被靜靜停用
- **這種 bug 不會報錯**，只能靠一個專門的測試抓（本專案測試 7）
- **本專案用法**：文件編輯用 `PUT`（整篇取代），來源修改用 `PATCH`

### A6. 全域例外處理（@ControllerAdvice）
- **掌握度**：🟡
- **關鍵**：`Exception.class` 的 fallback 必須存在，且不可把堆疊回傳給客戶端

### A7. Configuration & Profiles
- **掌握度**：🟡
- **`@ConfigurationProperties`**：把一段 YAML 綁成型別安全的 Java 物件
- **relaxed binding**：`access-token-ttl-minutes` 自動對應 `accessTokenTtlMinutes`
- **`${VAR}` 有無預設值**：機密不給（fail fast），非機密給

### A8. @Transactional 與 transaction 邊界
- **掌握度**：🟡（已使用，尚未遇到 rollback 情境）
- **邊界放 Service**：Repository 太細、Controller 太粗
- **面試考點**：傳播行為有哪些？為什麼同類內部呼叫會失效？
- **⚠️ 本專案重點**

### A9. @Async 與執行緒池
- **掌握度**：⬜
- **⚠️ 本專案重點**

### A10. 排程（@Scheduled）
- **掌握度**：⬜
- **⚠️ 本專案重點**

---

## B. 資料層

### B1. JPA / Hibernate 基礎
- **掌握度**：🟡
- **機制**：靠 reflection 建立「Java 欄位 ↔ 資料表欄位」對照表，**按名字對應，順序無關**
- **JPA 是規範，Hibernate 是實作**

### B2. 實體關聯映射（一對多、多對多）
- **掌握度**：🟡（懂原理與取捨，但本專案刻意不用，沒有實作經驗）
- **兩種寫法產生的資料庫結構完全相同**，差別只在 Java 這一側
- **判斷問題**：「有任何一個地方需要從 A 拿到 B 的資料嗎？」沒有就別建關聯
- **本專案決定**：跨聚合只存 id（ADR-012）
- **面試考點**：`@ManyToOne` 的 fetch 預設是 EAGER，`@OneToMany` 預設 LAZY——<br>兩者不一致，是常見的效能陷阱來源

### B3. N+1 查詢問題
- **掌握度**：🟡（看得懂，也親眼在日誌裡看過「該省沒省」的版本）
- **定義**：1 次主查詢 + N 次附帶查詢。列 20 筆 = 21 次查詢
- **本機測不出來**：資料少、延遲低。上線才爆
- **怎麼發現**：`show-sql: true`，打一支 API 數日誌裡有幾行 SQL
- **怎麼解**：`JOIN FETCH`、`@EntityGraph`、或改用 projection
- **Day 9 的實際案例**：列表用了精簡 DTO，但 SQL 仍 `select content`——<br>**省到 HTTP 回應，沒省到資料庫傳輸**。正解是 projection
- **面試考點**：怎麼發現？怎麼解？

### B3.1 分頁（Day 9）
- **掌握度**：🟢（自己驗證過三種情境）
- **判斷原則**：**回傳清單的 API，答不出「最多幾筆」就必須分頁**
- **一定要配排序**：沒有 ORDER BY 的分頁，同一筆可能出現在兩頁，<br>也可能永遠不出現——而且資料少時重現不出來
- **`size` 必須有上限**：否則 `?size=999999999` 等於分頁不存在
- **`Page` vs `Slice`**：前者多一次 COUNT 查詢換到總頁數
- **不要直接回傳 Spring 的 `Page`**：它的 JSON 結構由框架決定且曾隨版本變動
- **⚠️ Day 9 抽問答錯的地方**：以為「把分頁拿掉」測試不會紅。<br>實際會紅 3 個，但**紅的原因是 JSON 形狀變了，不是因為測試發現了危險**。<br>若改成「保留外殼但內部撈全部」，12 個測試只有 1 個會發現。
- **核心教訓**：**效能問題幾乎測不出來**——沒有人會為了測試先建 40 萬筆資料。<br>防線只有兩條：code review，以及「回傳清單一律分頁」這條不用每次重議的規則。

### B4. optimistic lock（@Version）
- **掌握度**：🟡（親手驗證過兩次，但程式碼是 Claude 寫的）
- **核心機制**：在 UPDATE 的 WHERE 多一個條件 →<br>`WHERE id=? AND version=?`。影響 0 列就代表有人先改過
- **要防的現象叫 lost update**：不需要「同時」，兩人隔五分鐘也會發生
- **⚠️ `@Version` 單獨用擋不住 HTTP 情境**：每個請求都重新載入，<br>載到的一定是最新版本，過期的版本號在呼叫端手上
- **所以要兩層**：① Service 明確比對呼叫端送回的 version（防「五分鐘前讀的」）<br>② `@Version`（防「同一毫秒的兩個請求」）
- **`version` 必須是必填欄位**，否則呼叫端不送就能繞過偵測
- **見 ADR-014**
- **面試考點**：為什麼不用 pessimistic lock？（鎖要跨越使用者思考的時間，<br>而 HTTP 無狀態，伺服器不知道他何時會送出、會不會直接關掉分頁）
- **面試考點**：衝突發生時該回什麼狀態碼？（409，不是 400 也不是 403）

### B4.1 Hibernate 的 flush 與 dirty checking（Day 10）
- **掌握度**：🟡
- **flush = 「現在就把累積的改動送到資料庫」**，不等交易提交
- **購物車比喻**：改欄位 = 放進購物車；flush = 現在結帳；提交 = 離場自動結帳
- **為什麼需要提前 flush**：`version` 與 `updatedAt` 是「UPDATE 執行後才確定」的值，<br>不送 UPDATE 就讀不到 → 回應會帶著舊值
- **flush 之後不會重複送**：Hibernate 會重新拍一張快照，<br>提交時比對發現沒差異就不送 SQL
- **flush ≠ commit**：flush 之後拋例外仍然會 rollback
- **面試考點**：什麼時候需要手動 flush？

### B4.2 版本歷史的快照策略（Day 11）
- **掌握度**：🟡（四個設計決定都自己做過，程式碼是 Claude 寫的）
- **完整快照 vs 存差異**：前者一次查詢就拿到，後者要從第 1 版套用到第 N 版。<br>選哪個看數字——本專案 100 篇 × 20 版 ≈ 10 MB，不值得換複雜度
- **存舊的還是存新的**：存新的（歷史包含現在這一版），<br>否則使用者打開版本列表會覺得少一個
- **⚠️ 兩個決定會互相撞**：「建立時存初版」＋「編輯前存舊的」＝ 第 1、2 版重複。<br>這個衝突是**追蹤實際資料時才發現的**，討論時看不出來
- **保留上限的算術容易差一**：存完第 21 版 → 刪 `<= 1` → 剩 2–21 共 20 筆
- **見 ADR-015**
- **面試考點**：版本歷史要存完整內容還是差異？（看資料量算數字）

### B5. pessimistic lock
- **掌握度**：🟡（懂它與 optimistic 的取捨，未實作）
- **做法**：`SELECT ... FOR UPDATE`，讀取時就鎖住資料列，其他人必須等
- **什麼時候該用**：衝突「很常發生」且交易「很短」——例如扣庫存
- **什麼時候不該用**：鎖要跨越「使用者思考的時間」時。<br>HTTP 無狀態，伺服器不知道他何時送出，也不知道他是不是關掉分頁走了
- **代價**：deadlock、連線池耗盡

### B5.1 EXPLAIN：看資料庫「打算怎麼查」（Day 12）
- **掌握度**：🟢（自己灌 5 萬筆資料實測過）
- **`EXPLAIN`** = 只看計畫；**`EXPLAIN ANALYZE`** = 真的跑一次並回報實際耗時
- **⚠️ 最重要的一課：看到 `Index Scan` 不代表有效率**
- **要看的數字是 `Rows Removed by Filter`**——它代表「撈出來之後又丟掉幾筆」<br>Day 12 實測：撈 50,031 筆、丟掉 50,030 筆、留 1 筆
- **為什麼要用它**：資料少的時候什麼查詢都快。<br>**不能看「現在跑多久」，要看「它用什麼方式找」**
- **面試考點**：你怎麼確認一個查詢有沒有效率？

### B6. 索引設計
- **掌握度**：🟡
- **⚠️ `LIKE '%關鍵字%'` 用不到 B-tree 索引**——開頭是萬用字元，<br>符合的目標可能散在任何位置。`LIKE '關鍵字%'` 則可以
- **字典比喻**：找「以蘋開頭」的詞很快；找「中間含果」的詞只能一頁一頁翻
- **`pg_trgm`（trigram）**：把字串切成三字一組建索引，<br>讓 `LIKE '%x%'` 也能走索引。**對中文可用**，因為它不需要斷詞
- **本專案決定不建**：算過，實際規模下小於 1 ms（ADR-016）
- **核心概念**：每個索引都必須說得出「它服務哪一個查詢」；說不出來的索引是純成本
- **本專案用途**：排程撈取待抓來源、DLQ 查詢、文件列表
- **常見錯誤**：亂加索引拖慢寫入；忘記 soft delete 後 `deleted_at` 也要進索引
- **面試考點**：你為什麼加這個索引？加了索引為什麼寫入會變慢？

### B6.1 唯一性的兩道防線與 race condition（Day 14，空手日 #2）
- **掌握度**：🟢（自己寫出第一道防線，第二道由 Claude 補上並解說）
- **先看情境**：使用者連點兩下「新增來源」

  | 時間 | 請求 A | 請求 B |
  |---|---|---|
  | 0.000s | `existsBy...` → false | |
  | 0.001s | | `existsBy...` → **也是 false** |
  | 0.002s | INSERT 成功 | |
  | 0.003s | | INSERT → 撞到 unique index |

  **兩個請求都通過了程式的 `if` 檢查**，因為 B 檢查時 A 還沒寫進去。
  **這叫 race condition。**
- **兩道防線的分工**：

  | | 負責 |
  |---|---|
  | 程式的 `existsBy` 查詢 | **給友善的錯誤訊息**（99.9% 的情況走這裡） |
  | 資料庫的 partial unique index | **真正的正確性保證**（並發時唯一擋得住的） |

- **關鍵**：兩道防線要丟出**同一個 exception**，呼叫端看到的行為才一致。
  沒有第二道的 catch → 並發時使用者拿到 500
- **⚠️ 陷阱：`save()` 不夠，要 `saveAndFlush()`**——
  `save()` 之後 Hibernate 可能把 INSERT 攢到 commit 才送，
  而 commit 發生在方法回傳之後，**那個 catch 永遠不會被觸發**。
  這與 B4.1 的 flush 是同一件事
- **partial（帶 `WHERE deleted_at IS NULL`）是 soft delete 的必要條件**：
  少了它，資料刪除後那個值永遠無法重複使用
- **面試考點**：「你怎麼保證 email 不重複？」——
  只答「程式先查一次」是不及格的答案

### B7. Flyway 資料庫版本控制
- **掌握度**：🟡（已看過完整 migration 並成功執行，尚未自己從零寫）
- **核心概念**：讓資料庫結構變成可以進 Git 的東西
- **鐵律**：檔案一經執行即為唯讀，要改就寫新的一份（checksum 會擋）
- **常見錯誤**：檔名用一個底線（必須兩個）；用 `ddl-auto: update` 取代 migration
- **面試考點**：你們怎麼管理資料庫變更？

---

## C. Reliability engineering（本專案的深度戰場）

### C1. Idempotency（冪等）
- **掌握度**：🟡（懂概念，尚未實作）
- **核心概念**：同一個操作做一次和做十次，結果相同
- **本專案用途**：抓取管線 retry 時不能產生重複文章
- **面試考點**：怎麼設計 idempotency key？

### C2. Retry 與 exponential backoff
- **掌握度**：⬜
- **常見錯誤**：無上限重試；固定間隔重試造成雪崩

### C3. Distributed lock（Day 15）
- **掌握度**：🟡（懂問題與解法，**刻意沒有實作**，見 ADR-018）
- **問題**：兩台機器跑同一份程式，各自的 `@Scheduled` 互不知情 → 每小時抓兩次
- **解法**：在共用資料庫插旗。ShedLock 的 `@SchedulerLock` 幫你做這件事
- **`lockAtMostFor` 是關鍵參數**：拿到鎖的機器當機時，鎖必須會自己過期，
  否則永遠沒有人能再跑那個排程
- **本專案的判斷**：`uq_fetch_job_active` 已經擋住了重複抓取——
  兩台同時 INSERT，一台撞唯一約束後跳過。**想防的事已經被防住了**
- **重新評估的條件**（ADR-018）：部署超過一個實例；
  或**新增任何一個「重複執行會造成傷害」的排程**（寄信、扣款、呼叫收費 API）——
  那類工作沒有 unique index 可以依靠
- **面試考點**：「排程在多台機器上會怎樣」是必考題。
  能答出「我先確認既有的約束擋不擋得住」比背出 ShedLock 更好

### C4. Task state machine（Day 15）
- **掌握度**：🟢（`FetchJob` 自己寫過，9 個 unit test）
- **定義只有兩句**：① 只能處於幾個明確狀態之一 ② 狀態之間只有特定幾條路合法
- **本專案**：`PENDING → RUNNING → SUCCESS / FAILED`，兩個終點都回不來
- **關鍵設計：沒有 `setStatus()`**——只有 `start()` `succeed()` `fail()`，
  每個方法自己檢查來源狀態，並同時更新該轉換必須更新的欄位。
  **「換了狀態卻忘記記時間」變成做不到的事，而不是要記得的事**
- **⚠️ 抽問答錯過一次**：狀態機**不會自己動**。
  一個卡在 `RUNNING` 的任務不會因為你多定義一個 `TIMEOUT` 狀態就自己脫困——
  要有程式定時去掃、去 `UPDATE` 它（業界叫 reaper）
- **同型錯誤**：加了 `deleted_at` 欄位 ≠ 刪掉的就查不到；
  設了 `expires_at` ≠ 過期的會自動失效。
  **資料庫欄位只是「記下來」，不是「會發生」**

### C5. Timeout（Day 15）
- **掌握度**：🟢（`FetchClient` 兩種都設了）
- **只設 connect timeout 不夠**：

  | | 擋什麼 |
  |---|---|
  | `connectTimeout(5s)` | 對方防火牆把封包丟掉，**不回應也不拒絕** |
  | `.timeout(10s)`（整個請求） | 對方**每秒吐一個位元組**，連線正常但傳不完（slowloris） |

- **回應大小也要設上限**：`readNBytes(5MB + 1)` 而不是 `readAllBytes()`——
  後者會把 10 GB 全部吃進記憶體，然後你才發現太大
- **面試考點**：「你怎麼防止一個慢的外部服務拖垮整個系統」

### C6. Dead letter queue（DLQ）
- **掌握度**：⬜

### C7. Dedup 策略
- **掌握度**：🟡（已決定用 `(source_id, content_hash)`，見 ADR-004；尚未實作）

### C8. Rate limiting 與配額控制
- **掌握度**：⬜
- **本專案用途**：控制 LLM API 呼叫成本

---

## D. 安全

### D1. Spring Security 架構（Filter Chain）
- **掌握度**：🟡（已教學，filter 尚未實作 → Day 7）
- **核心**：filter 在 Controller 之前執行，可直接擋下請求
- **SecurityContext**：底層是 ThreadLocal，存「這個請求是誰」
- **常見錯誤**：加了 starter 沒設定 → 所有 endpoint 變 401

### D2. JWT
- **掌握度**：🟢（已能簽發並在 jwt.io 驗證內容）
- **三段**：header.payload.signature，前兩段是 Base64，任何人可讀
- **payload 只放 userId**：JWT 公開可讀，且每個請求都要傳輸
- **面試考點**：JWT 的缺點？（無法撤銷）怎麼登出？（存 refresh token）
- **面試考點**：JWT 的缺點是什麼？怎麼登出？

### D3. Refresh Token
- **掌握度**：🟢（Day 8 完整實作：換發、rotation、logout、盜用偵測，11 個測試）
- **刻意不用 JWT**：反正要查資料庫，JWT 的無狀態優勢用不上
- **存雜湊不存原文**：外洩時攻擊者拿不到可用的 token
- **SHA-256 而非 BCrypt**：雜湊演算法的選擇取決於<br>「被保護的值有多容易猜」。人想的密碼要慢，256 bits 亂數不需要
- **兩張票的本質差異**：access token 自己說自己是誰（不查 DB、快、不可撤銷）；<br>refresh token 什麼都不說（要查 DB、慢、可撤銷）
- **rotation 買到的唯一東西是盜用偵測**：一張票只能用一次，<br>用第二次 = 一定有兩個人持有過 → 全部作廢
- **原地更新 vs 每次新增列**：資料量差 100 倍，見 ADR-011
- **見 ADR-010、ADR-011**
- **面試考點**：為什麼需要兩個 token？為什麼不能讓 access token 自己續期？<br>（小偷可無限續命，15 分鐘保護失效）
- **面試考點**：rotation 怎麼抓到盜用？誤判怎麼辦？（寬限期）

### D3.1 Token 撤銷與交易邊界（Day 8）
- **掌握度**：🟡（懂了原理，但只實作過一次）
- **`noRollbackFor` 的必要性**：偵測到盜用 → 作廢 → 拋例外。<br>預設 rollback 會把作廢一起撤銷，變成「偵測到但沒防住」——比沒偵測更糟
- **logout 必須 idempotent**：無效 token 也回 204。<br>否則等於送攻擊者一支「這張 token 是不是真的」查詢 API
- **面試考點**：在同一個交易裡「先寫入再拋例外」會發生什麼事？

### D4. 密碼雜湊（BCrypt）
- **掌握度**：🟢
- **核心**：雜湊不可逆；BCrypt 刻意很慢（cost factor 10 ≈ 100ms）；自動加 salt
- **輸出格式**：`$2a$10$<22字元salt><31字元hash>`，共 60 字元
- **面試考點**：為什麼不用 MD5/SHA-256？（太快，方便暴力破解）

### D4.1 SSRF（Server-Side Request Forgery，Day 13）
- **掌握度**：🟡
- **一句話**：**讓伺服器幫你去存取它不該存取的東西**
- **本專案的情境**：使用者填一個訂閱網址 → 排程會去「抓」它。<br>如果他填 `file:///etc/passwd`，抓的就是伺服器本機的檔案
- **也可以打內網**：填 `http://169.254.169.254/...`（雲端的 metadata 服務）<br>就能拿到伺服器的憑證——那是真實發生過的事故形態
- **防法**：限定協定（只允許 `http` / `https`）。進階做法還要擋內網 IP 網段
- **關鍵判斷**：**只要「伺服器會依使用者給的網址發出請求」，就要想到 SSRF**
- **面試考點**：使用者可以輸入網址的功能，你會做什麼防護？

### D5. 常見漏洞防護（SQL Injection / XSS / CSRF）
- **掌握度**：🟡
- **CSRF 為何可停用**：攻擊前提是「瀏覽器自動附帶 cookie」，<br>JWT 由前端主動放進標頭，此攻擊面不存在。改用 cookie 就必須開回來
- **Spring Security 自動加的標頭**：X-Frame-Options（防點擊劫持）、<br>X-Content-Type-Options（防型別猜測）、Cache-Control: no-store（防 token 被快取）

---

## E. 測試

### E1. 單元測試（JUnit 5）
- **掌握度**：🟡

### E2. Mockito
- **掌握度**：🟡
- **ArgumentCaptor**：攔截傳給 save() 的物件，檢查實際要寫入的內容
- **常見錯誤**：mock 太多，測到的是 mock 不是邏輯

### E3. Spring Boot Test 分層
- **掌握度**：🟡（單元 50ms vs 整合 5s；MockMvc 模擬 HTTP 不開 port）

### E3.1 一個測試對應一個設計決定（Day 14）
- **掌握度**：🟢
- **具體例子**：`existsByUrlAndUserIdAndDeletedAtIsNull` 這個方法名字有三段條件，
  就寫三個測試，一段一題：

  | 測試 | 拿掉哪一段會變紅 |
  |---|---|
  | 9. 同一人重複 → 409 | `Url` |
  | 10. 不同人同 url → 都成功 | `AndUserId` |
  | 11. 刪掉後可重新訂閱 | `AndDeletedAtIsNull` |

- **判斷一個測試有沒有價值的方法**：
  **把對應的那段程式碼註解掉，它會不會紅？** 不會紅的測試是假的
- **反例**：只寫「重複 → 409」一題，那 `AndUserId` 和 `AndDeletedAtIsNull`
  被誰刪掉都不會有人發現

### E3.2 怎麼測「會連外網」的程式碼（Day 16）
- **掌握度**：🟡（看得懂，尚未自己寫過）
- **核心手法：不要連外網，自己架一個**

  ```java
  HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
  //                                                          ↑ 0 = 隨便挑一個沒被佔用的 port
  ```
  JDK 內建，不需要任何依賴。想讓它回 404 就回 404，想讓它慢十秒就慢十秒
- **這些情況用真實網站重現不了**：你沒辦法叫 Hacker News 回你一個 500
- **拆分原則**：把「不穩定的部分」縮到最小
  - `FeedParser`、`FeedDiscoverer`、`InternalAddressChecker` → 完全不碰網路 → 純 unit test
  - `FetchClient` → 碰網路 → 只有它需要假伺服器
- **⚠️ 撞到的真實矛盾**：安全檢查擋住了自己的測試。
  假伺服器只能架在 localhost，而 localhost 正是 SSRF 防護要擋的東西。
  解法是「可以關掉，但關掉要很明顯」——加一個設定開關，
  **打開時啟動 log 印出警告**，而且有一題測試專門驗證「關回去時真的擋得住」

### E3.3 mock 的判斷（Day 16）
- **掌握度**：🟢
- **原則：只 mock 你控制不了的東西**

  | 該 mock | 不該 mock |
  |---|---|
  | 外部網路、寄信、付款 | 你自己的 Repository / Service / 資料庫 |

- **mock 太多的後果**：把 repository mock 掉之後，測到的是
  「我叫 mock 回 false，然後它回了 false」——**方法名字對不對、
  SQL 產得對不對，全部沒測到**
- **`@Mock` vs `@MockitoBean`**：前者不啟動 Spring、只有你手動塞的地方拿到假的；
  後者換掉整個容器裡的那一個
- **本專案的實例**：`SourceFlowIntegrationTest` 換掉 `FeedResolver`，
  因為 Day 16 起新增來源會真的連網路，而測試用的是 `mine.example.com` 這種假網址

### E4. Testcontainers
- **掌握度**：⬜
- **核心概念**：測試時用真的資料庫（跑在 Docker 裡）

### E5. 測試覆蓋率與其陷阱
- **掌握度**：⬜
- **面試考點**：覆蓋率 100% 代表沒 bug 嗎？

---

## F. 基礎設施

### F1. Docker 基礎
- **掌握度**：🟢
- **核心概念**：image 是模板，container 是執行中的實例
- **關鍵**：資料要用 volume 保存，否則容器一刪就沒
- **常見錯誤**：以為容器是虛擬機；用 `latest` 標籤導致版本漂移
- **面試考點**：你怎麼用 Docker？（能區分「開發時用現成 image」與「部署時打包自己的程式」是關鍵）

### F2. Dockerfile 最佳化（多階段建置）
- **掌握度**：⬜

### F3. Docker Compose
- **掌握度**：🟡（已成功啟動並操作，但檔案不是自己寫的）
- **核心概念**：用一個 YAML 描述多個容器怎麼一起跑
- **關鍵設定**：鎖版本（不用 latest）、volume（保資料）、healthcheck（等就緒）

### F4. GitHub Actions
- **掌握度**：⬜

### F5. 結構化 Logging
- **掌握度**：⬜

### F6. Prometheus + Grafana
- **掌握度**：⬜
- **註**：停損順序第 1 位，可能不做

---

## 延伸學習方向（專案結束後）

- 訊息佇列（Kafka / RabbitMQ）取代資料庫輪詢
- Outbox Pattern 與最終一致性
- Circuit Breaker（Resilience4j）
- 分散式追蹤（OpenTelemetry）
- DDD 戰術模式（Aggregate、Value Object）
- Kubernetes 部署
