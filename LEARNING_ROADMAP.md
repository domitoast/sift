# LEARNING_ROADMAP.md — 學習地圖

> 每學會一項就更新一次。掌握度定義：
> - ⬜ 沒概念
> - 🟡 聽過 / 看得懂別人寫的
> - 🟢 能自己寫出來
> - ⭐ 能講清楚為什麼，並說出替代方案與取捨

**最後更新**：Day 5

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
| 測試「沒做某件事」 | 🟡 | `verify(repo, never()).save(any())`——<br>少了它，「先存再丟例外」的錯誤寫法也會通過測試<br>⚠️ Day 5 抽問答錯，降級。Day 7 空手日重測 |
| 誰守得住哪一側？ | 🟡 | Java↔資料庫：`ddl-auto: validate` 與 Spring Data 在**啟動時**就爆<br>Java↔JSON：**只有整合測試**。框架完全不把關<br>⚠️ Day 5 抽問答錯（選了「編譯不過」），需重測 |

---

## 進度總覽

| 分類 | 項目數 | 🟡 以上 | 🟢 以上 |
|---|---|---|---|
| Spring Boot 核心 | 10 | 7 | 1 |
| 資料層 | 7 | 3 | 0 |
| Reliability engineering（深度戰場） | 8 | 2 | 0 |
| 安全 | 5 | 1 | 1 |
| 測試 | 5 | 3 | 0 |
| 基礎設施 | 6 | 2 | 1 |

> **Day 5 現況**：Spring Boot 核心已大量接觸但多停在 🟡（看得懂，沒自己寫）。
> **深度戰場（C 類）幾乎沒動**——那是 Day 11–16 的主場。
> Day 7 空手日的驗證重點：A3（分層架構）、A4（DTO）、B1（JPA）。

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
- **掌握度**：⬜

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
- **掌握度**：⬜

### B3. N+1 查詢問題
- **掌握度**：⬜
- **面試考點**：怎麼發現？怎麼解？

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
- **掌握度**：⬜

### D2. JWT
- **掌握度**：⬜
- **面試考點**：JWT 的缺點是什麼？怎麼登出？

### D3. Refresh Token
- **掌握度**：⬜
- **面試考點**：為什麼需要兩個 token？refresh token 被偷怎麼辦？

### D4. 密碼雜湊（BCrypt）
- **掌握度**：🟢
- **核心**：雜湊不可逆；BCrypt 刻意很慢（cost factor 10 ≈ 100ms）；自動加 salt
- **輸出格式**：`$2a$10$<22字元salt><31字元hash>`，共 60 字元
- **面試考點**：為什麼不用 MD5/SHA-256？（太快，方便暴力破解）

### D5. 常見漏洞防護（SQL Injection / XSS / CSRF）
- **掌握度**：⬜

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
