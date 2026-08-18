# DEVELOPMENT_WORKFLOW.md — 開發流程

---

## 1. 專案階段流程（Phase Flow）

任何一步沒完成，不進入下一步。

| # | 階段 | 產出 | 狀態 |
|---|---|---|---|
| 1 | 產品定位 | Positioning Statement | ✅ 完成 |
| 2 | Domain 分析 | 名詞表 / Ubiquitous Language | ⬜ |
| 3 | Requirement Gathering | 需求清單 | ⬜ |
| 4 | User Story | User Story 列表 | ⬜ |
| 5 | Use Case | 主要 Use Case | ⬜ |
| 6 | Functional Requirements | FR 清單 | ⬜ |
| 7 | Non-functional Requirements | NFR 清單 | ⬜ |
| 8 | SRS | SRS 文件 | ⬜ |
| 9 | Architecture Design | 架構圖 + ADR | ⬜ |
| 10 | Database Design | 資料表設計 | ⬜ |
| 11 | ER Diagram | ER 圖 | ⬜ |
| 12 | API Design | OpenAPI 草案 | ⬜ |
| 13 | Security Design | 認證授權設計 | ⬜ |
| 14 | Testing Strategy | 測試策略文件 | ⬜ |
| 15 | Logging Strategy | 日誌規範 | ⬜ |
| 16 | Monitoring Strategy | 監控指標定義 | ⬜ |
| 17 | Docker Architecture | Compose 設計 | ⬜ |
| 18 | CI/CD Pipeline Design | Pipeline 設計 | ⬜ |
| 19 | Git Flow | 分支策略 | ⬜ |
| 20 | Sprint Planning | 21 天路線圖 | ⬜ |
| 21 | 實作 | 程式碼 | ⬜ |

> 21 天的現實：階段 2–20 不會各花一整天。多數會在前 3 天內以「夠用就好」的深度完成，
> 但**每一步都必須有書面產出**，不能跳過。

---

## 2. Feature 開發流程

每一個 Feature 都照這個順序走，不得一次完成：

1. Requirement — 這個功能要解決什麼問題
2. 業務流程討論 — 正常流程與例外流程
3. Database Design — 需要哪些欄位、索引、約束
4. API Design — endpoint、請求 / 回應格式、狀態碼
5. Entity — 資料模型
6. DTO — 對外的資料傳輸物件
7. Validation — 輸入驗證規則
8. Repository — 資料存取層
9. Service — 業務邏輯與 transaction 邊界
10. Controller — HTTP 入口
11. Exception Handling — 錯誤處理與回應格式
12. Unit Test
13. Integration Test
14. Security Test
15. API Documentation
16. Code Review
17. Refactoring
18. Merge

---

## 3. 每日節奏

### 開工前（Claude 必須提供）

- 今天要完成什麼
- 為什麼要做
- 業界重要性
- 會學到什麼
- Definition of Done
- 預估時間

### 收工後（Claude 必須整理）

- 今天學到了什麼
- 用了哪些設計
- 哪些業界觀念
- 哪些地方可以改善
- 哪些值得寫成 ADR
- 下一步建議

並更新 `LEARNING_ROADMAP.md`。

### 每週一次：空手日

- 完全不使用 AI
- 獨力實作一個小功能
- 卡住是預期中的
- 當天結束後才做 Code Review

---

## 4. Git Flow

### 分支策略（簡化版 Git Flow）

```
main        ← 永遠可部署，只接受來自 develop 的合併
  └ develop ← 整合分支
      └ feature/xxx  ← 功能開發
      └ fix/xxx      ← 錯誤修正
      └ chore/xxx    ← 雜項（設定、文件）
```

### Commit Message 規範（Conventional Commits）

```
<type>(<scope>): <subject>

type: feat | fix | docs | style | refactor | test | chore | perf
```

範例：

```
feat(document): add optimistic locking to document update
fix(pipeline): prevent duplicate fetch when scheduler runs on two instances
test(pipeline): add retry behaviour integration test
docs(adr): record decision to use ShedLock over Quartz clustering
```

### PR 規則

- 一個 Feature 一個 PR
- PR 描述必須包含：做了什麼、為什麼這樣做、怎麼測試
- 自己 review 一次再交給 Claude review
- CI 綠燈才能合併

---

## 5. Code Review 檢查表

每個 Feature 完成後逐項檢查：

### 設計層面

- [ ] 職責是否單一？Service 有沒有做 Controller 的事？
- [ ] transaction 邊界劃在正確的地方嗎？
- [ ] 有沒有把業務邏輯洩漏到 Controller 或 Repository？
- [ ] 這個設計三個月後的我看得懂嗎？

### 正確性

- [ ] 例外情況處理了嗎？（null、空集合、外部服務掛掉）
- [ ] 併發情況下會出事嗎？
- [ ] 重複執行會不會造成重複資料？（idempotency）

### 效能

- [ ] 有 N+1 查詢嗎？
- [ ] 該加索引的欄位加了嗎？
- [ ] 有沒有不必要的全表掃描？

### 安全

- [ ] 輸入驗證做了嗎？
- [ ] 敏感資訊有沒有進到日誌？
- [ ] 權限檢查在正確的層級嗎？

### 測試

- [ ] 正常路徑有測試嗎？
- [ ] 例外路徑有測試嗎？
- [ ] 測試名稱說得出它在測什麼嗎？

### 可觀測性

- [ ] 出事的時候，靠日誌查得出原因嗎？
- [ ] 關鍵操作有記錄嗎？

---

## 6. ADR（Architecture Decision Record）

任何有「其他選項」的決策都要記錄。存放於 `docs/adr/`。

格式：

```markdown
# ADR-001: 標題

## 狀態
提議中 / 已接受 / 已棄用 / 被 ADR-XXX 取代

## 背景
我們面臨什麼問題？

## 考慮過的選項
- 選項 A：優點 / 缺點
- 選項 B：優點 / 缺點

## 決定
我們選擇了 X。

## 理由
為什麼？

## 後果
這個決定帶來什麼好處與代價？未來什麼情況下需要重新評估？
```
