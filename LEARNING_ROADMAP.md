# LEARNING_ROADMAP.md — 學習地圖

> 每學會一項就更新一次。掌握度定義：
> - ⬜ 沒概念
> - 🟡 聽過 / 看得懂別人寫的
> - 🟢 能自己寫出來
> - ⭐ 能講清楚為什麼，並說出替代方案與取捨

**最後更新**：Day 3

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

---

## 進度總覽

| 分類 | 項目數 | 已達 🟢 以上 |
|---|---|---|
| Spring Boot 核心 | 10 | 0 |
| 資料層 | 7 | 0 |
| Reliability engineering（深度戰場） | 8 | 0 |
| 安全 | 5 | 0 |
| 測試 | 5 | 0 |
| 基礎設施 | 6 | 0 |

---

## A. Spring Boot 核心

### A1. Dependency Injection / IoC 容器
- **掌握度**：⬜
- **核心概念**：物件不自己 new 依賴，由容器注入
- **本專案用途**：所有 Service、Repository 的組裝
- **常見錯誤**：用欄位注入而非建構子注入；循環依賴
- **面試考點**：為什麼要 DI？建構子注入 vs 欄位注入的差別？

### A2. Bean 與生命週期
- **掌握度**：⬜
- **核心概念**：由 Spring 容器管理的物件
- **常見錯誤**：以為每次拿到的都是新物件（預設是 singleton）
- **面試考點**：Bean scope 有哪些？singleton 有什麼陷阱？

### A3. 分層架構（Controller / Service / Repository）
- **掌握度**：⬜
- **常見錯誤**：業務邏輯寫在 Controller；Service 直接回傳 Entity

### A4. DTO vs Entity
- **掌握度**：⬜
- **面試考點**：為什麼不直接回傳 Entity？

### A5. Validation（Bean Validation）
- **掌握度**：⬜

### A6. 全域例外處理（@ControllerAdvice）
- **掌握度**：⬜

### A7. Configuration & Profiles
- **掌握度**：⬜

### A8. @Transactional 與 transaction 邊界
- **掌握度**：⬜
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
- **掌握度**：⬜

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
- **掌握度**：⬜

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
- **掌握度**：⬜

### D5. 常見漏洞防護（SQL Injection / XSS / CSRF）
- **掌握度**：⬜

---

## E. 測試

### E1. 單元測試（JUnit 5）
- **掌握度**：⬜

### E2. Mockito
- **掌握度**：⬜
- **常見錯誤**：mock 太多，測到的是 mock 不是邏輯

### E3. Spring Boot Test 分層
- **掌握度**：⬜

### E4. Testcontainers
- **掌握度**：⬜
- **核心概念**：測試時用真的資料庫（跑在 Docker 裡）

### E5. 測試覆蓋率與其陷阱
- **掌握度**：⬜
- **面試考點**：覆蓋率 100% 代表沒 bug 嗎？

---

## F. 基礎設施

### F1. Docker 基礎
- **掌握度**：⬜

### F2. Dockerfile 最佳化（多階段建置）
- **掌握度**：⬜

### F3. Docker Compose
- **掌握度**：⬜

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
