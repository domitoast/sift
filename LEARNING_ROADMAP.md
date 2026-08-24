# LEARNING_ROADMAP.md — 學習地圖

> 每學會一項就更新一次。掌握度定義：
> - ⬜ 沒概念
> - 🟡 聽過 / 看得懂別人寫的
> - 🟢 能自己寫出來
> - ⭐ 能講清楚為什麼，並說出替代方案與取捨

**最後更新**：Day 6

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
| Spring Boot 核心 | 10 | 8 | 1 |
| 資料層 | 7 | 3 | 0 |
| Reliability engineering（深度戰場） | 8 | 2 | 0 |
| 安全 | 5 | **5** | 2 |
| 測試 | 5 | 3 | 0 |
| 基礎設施 | 6 | 2 | 1 |

> **Day 6 現況**：安全類（D）今天一次補齊到 🟡 以上，是目前進展最快的一區。
> **深度戰場（C 類）仍幾乎沒動**——那是 Day 11–16 的主場。
> **Day 7 空手日的驗證重點**：A3（分層架構）、A4（DTO）、B1（JPA）、B7（Flyway）。
>
> ⚠️ **Day 6 的節奏警訊**：一天內改動十個檔案且中途沒有可運作的成果，
> 導致理解斷裂。往後每個工作段落都應該以「有東西能跑」作為結束條件。

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
- **掌握度**：⬜
- **本專案用途**：文件版本衝突處理
- **⚠️ 深度戰場**

### B5. pessimistic lock
- **掌握度**：⬜

### B6. 索引設計
- **掌握度**：🟡
- **核心概念**：每個索引都必須說得出「它服務哪一個查詢」；說不出來的索引是純成本
- **本專案用途**：排程撈取待抓來源、DLQ 查詢、文件列表
- **常見錯誤**：亂加索引拖慢寫入；忘記 soft delete 後 `deleted_at` 也要進索引
- **面試考點**：你為什麼加這個索引？加了索引為什麼寫入會變慢？

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

### C3. Distributed lock
- **掌握度**：⬜
- **本專案用途**：多實例部署時，排程任務不能重複執行

### C4. Task state machine
- **掌握度**：⬜
- **本專案用途**：PENDING → RUNNING → SUCCESS / FAILED

### C5. Timeout（逾時）
- **掌握度**：⬜
- **常見錯誤**：不設逾時，導致執行緒被外部服務拖死

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
