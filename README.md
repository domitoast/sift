# Sift

[![CI](https://github.com/domitoast/sift/actions/workflows/ci.yml/badge.svg)](https://github.com/domitoast/sift/actions/workflows/ci.yml)

個人知識庫。可以自己寫筆記，也讓系統定時去抓訂閱的來源、去掉重複的文章、產生摘要，我看過之後再決定要不要留下來。

---

## 現在能做什麼

**知識庫**

| 功能 | 說明 |
|---|---|
| 註冊 / 登入 | 密碼用 BCrypt，登入拿到 access token 與 refresh token |
| Token 換發 | access token 15 分鐘過期，用 refresh token 換新的 |
| 登出 | 讓 refresh token 立即失效 |
| 建立 / 讀取 / 編輯 / 刪除筆記 | 刪除是 soft delete，資料保留 |
| 筆記列表與搜尋 | 分頁，預設每頁 20 筆 |

**自動抓取管線**

| 功能 | 說明 |
|---|---|
| 訂閱來源管理 | 新增當下就試抓一次，網址不能用就進不了資料庫 |
| RSS autodiscovery | 填網站首頁也可以，會自己找出真正的 feed 網址 |
| 定時抓取 | 每個來源一張工作卡，狀態與失敗原因都留著 |
| 去重 | 依內容雜湊判斷，網址帶不同追蹤參數不會被當成新文章 |
| LLM 摘要 | 使用者自帶 API key（BYOK），加密後存放 |
| 失敗處理 | 暫時性失敗會 exponential backoff 重試，最多 3 次 |
| 每日配額 | 防止排程設錯而狂打 API |

## 還沒做

- **把摘要好的文章收進知識庫**（`fetched_item` → `document` 那一步）
- 真正的 LLM 供應商串接（目前是一個假的實作，方便測試）
- 部署
- 前端

---

## 跑起來

需要 Java 21 和 Docker。**不需要另外裝 Maven**——專案內含 Maven Wrapper。

```bash
git clone https://github.com/domitoast/sift.git
cd sift

# 1. 準備環境變數
cp .env.example .env
```

`.env` 裡有兩把金鑰要自己產生：

```bash
# 簽 JWT 用（至少 512 bits）
openssl rand -base64 64

# 加密使用者的 LLM API key 用（必須正好 256 bits）
openssl rand -base64 32
```

分別填進 `SIFT_JWT_SECRET` 與 `SIFT_ENCRYPTION_SECRET`。

> 兩把刻意分開，不共用。前者外洩代表 token 可以被偽造，
> 後者外洩代表所有使用者的 API key 變明文——影響範圍不同，
> 而且輪替其中一把時不該連帶弄壞另一把。

然後：

```bash
# 2. 起資料庫
docker compose up -d

# 3. 跑起來（Flyway 會自動建表）
./mvnw spring-boot:run
```

確認活著：

```bash
curl -i http://localhost:8080/actuator/health
```

跑測試（需要資料庫在跑）：

```bash
./mvnw test
```

---

## API

所有路徑都在 `/api/v1` 之下。`/auth/**` 與 `/actuator/health` 不需要登入，其餘都要。

| Method | 路徑 | 說明 |
|---|---|---|
| POST | `/auth/register` | 註冊 |
| POST | `/auth/login` | 登入，回傳兩張 token |
| POST | `/auth/refresh` | 換發 access token |
| POST | `/auth/logout` | 作廢 refresh token |
| GET | `/me` | 自己的帳號資料（API key 只回遮罩形式） |
| PUT | `/me/llm-key` | 設定自己的 LLM API key |
| DELETE | `/me/llm-key` | 移除 API key |
| POST | `/documents` | 建立筆記 |
| GET | `/documents` | 列表（分頁、可搜尋） |
| GET | `/documents/{id}` | 讀單篇 |
| PUT | `/documents/{id}` | 編輯，需帶 `version` |
| DELETE | `/documents/{id}` | 刪除 |
| GET | `/documents/{id}/versions` | 版本歷史 |
| POST | `/sources` | 新增訂閱來源（會當場試抓驗證） |
| GET | `/sources` | 列出自己的來源 |
| PATCH | `/sources/{id}` | 改名稱或啟用狀態 |
| DELETE | `/sources/{id}` | 刪除來源（已抓的文章保留） |
| GET | `/sources/{id}/fetch-jobs` | 該來源最近的抓取結果與失敗原因 |

錯誤回應用 RFC 7807 Problem Details：

```json
{
  "type": "https://sift.dev/errors/document-conflict",
  "title": "編輯衝突",
  "status": 409,
  "detail": "這篇文件已被修改，請重新載入後再編輯",
  "instance": "/api/v1/documents/63",
  "currentVersion": 1
}
```

---

## 技術選型

| | 用什麼 | 為什麼 |
|---|---|---|
| 語言 | Java 21 | |
| 框架 | Spring Boot 3.5 | |
| 資料庫 | PostgreSQL 17 | 需要 partial index 來配合 soft delete，MySQL 沒有（[ADR-007](docs/adr/ADR-007-choose-postgresql.md)） |
| 資料庫版本管理 | Flyway | 結構變更要能進 Git、能重現 |
| 認證 | JWT + 資料庫存的 refresh token | 純 JWT 無法登出（[ADR-010](docs/adr/ADR-010-refresh-token-persistence.md)） |

---

## 幾個做過取捨的地方

每個設計決定都寫在 [`docs/adr/`](docs/adr/)，包含當時考慮過但否決的選項。挑幾個比較有意思的：

**權限檢查寫在查詢條件裡，不是查完再比對**
（[ADR-013](docs/adr/ADR-013-ownership-check-in-query.md)）

Repository 只提供 `findByIdAndUserIdAndDeletedAtIsNull`，沒有「只用 id 查」的方法。這樣不可能忘記加權限檢查——忘了的話程式碼根本編譯不過。

查不到別人的資料時回 404 而不是 403，因為 403 等於告訴對方「這個 id 是存在的」。

**編輯衝突用 optimistic lock，而且做了兩層**
（[ADR-014](docs/adr/ADR-014-optimistic-lock-for-edit-conflict.md)）

實測過：兩個人先後儲存同一份筆記，後存的會把先存的蓋掉，而且伺服器回 200，沒有人會發現。

解法是讀取時回傳 `version`，編輯時帶回來比對。但只靠 JPA 的 `@Version` 不夠——HTTP 每個請求都重新載入，載到的一定是最新版本，過期的版本號在呼叫端手上。所以 Service 層另外做一次明確比對。

不用 pessimistic lock 是因為那個鎖要跨越「使用者思考的時間」，而伺服器不知道他什麼時候會送出，甚至不知道他是不是關掉分頁走了。

**refresh token 每次換發都輪替，用來偵測盜用**
（[ADR-011](docs/adr/ADR-011-refresh-token-rotation-in-place.md)）

一張 refresh token 只能用一次。如果同一張被用了兩次，代表有兩個人持有過它，其中一個是攻擊者——這時把該使用者所有憑證全部作廢，兩邊都重新登入。

原本的設計是每次換發新增一列紀錄，算過之後發現一萬個使用者一年會產生 2 GB。改成原地更新同一列、多存一個「上一代的雜湊值」，偵測能力不變，空間差 100 倍。

**跨聚合只存 id，不建立 JPA 關聯**
（[ADR-012](docs/adr/ADR-012-no-jpa-associations-across-aggregates.md)）

`Document` 只存 `userId`，沒有 `@ManyToOne User`。因為沒有任何一支 API 需要從筆記取得作者資料，建了關聯只是多一個 N+1 的來源。

這個決定後來有付出代價：摘要排程需要「撈出擁有者已設定 API key 的文章」，那要跨三張表，而沒有關聯就沒辦法用 JPQL join。最後用了一句原生 SQL，並在那個方法上寫清楚理由——ADR-012 管的是 Java 物件之間不要互相持有，不是資料庫不准 join。

**排程沒有加 distributed lock**
（[ADR-018](docs/adr/ADR-018-no-distributed-lock.md)）

多實例部署時排程會重複執行，標準解法是 ShedLock。但先去看了自己的 schema，發現 `fetch_job` 上的 partial unique index 已經擋住了真正會造成傷害的那一層——兩個實例同時建立任務，第二個會撞唯一約束後跳過。

所以沒裝，並寫下重新評估的條件：部署超過一個實例，或**新增任何一個「重複執行會造成傷害」的排程**（寄信、扣款）。第二條是最容易被忽略的——現在安全是因為現有的排程剛好都有保護，下一個可能沒有。

**摘要失敗用 exponential backoff，而且加了 jitter**
（[ADR-008](docs/adr/ADR-008-day5-schema-simplification.md)）

暫時性失敗（429、timeout）等 60 秒 → 120 秒 → 240 秒後重試，最多 3 次。永久性失敗（API key 無效）不重試。

只有 backoff 還不夠：一批文章若在同一秒失敗，它們的下次重試也會在同一秒——尖峰只是往後移了一分鐘。所以等待時間加了 ±20% 的隨機。

抓取失敗則完全不重試，等隔天的排程即可。兩者的失敗特性不同，重試策略沒有理由一樣。

---

## 專案結構

```
src/main/java/dev/sift/
├── auth/          認證：JWT 簽發與驗證、refresh token
├── user/          使用者：註冊、自己的資料、API key 加解密
├── document/      筆記：CRUD 與版本歷史
├── source/        訂閱來源
├── fetch/         抓取管線：工作卡、HTTP client、feed 解析、去重
├── summarize/     摘要：LLM 介面、重試策略、配額
├── common/        跨領域：全域例外處理、分頁回應格式
└── config/        Spring 設定

.github/workflows/ci.yml            每次 push 自動跑測試
src/main/resources/db/migration/    Flyway migration（7 份）
docs/                               設計文件
docs/adr/                           設計決策紀錄（18 份）
docs/ANNOTATIONS.md                 專案用到的每一個註解
docs/TESTING.md                     怎麼讀這個專案的測試
```

---

## 測試

162 個測試，82 個 unit、80 個 integration（真的起 Spring 與資料庫）。
每次 push 由 GitHub Actions 自動執行。

整合測試比教科書建議的比例高很多，那是刻意的——
回頭看實際踩到的 bug（例外處理器吞掉 404、測試設定檔遮蔽主設定、
Hibernate 的 flush 時機），沒有一個是 unit test 抓得到的。

比較值得看的幾個：

- 別人的合法帳號讀不到你的筆記，而且回 404 看不出它存在
- 編輯衝突時，先寫入者的內容必須完好無損（光是回 409 不夠）
- 同一篇文章換一組追蹤參數再抓一次，不會產生第二筆
- 使用者的 API key 在資料庫裡查不到明文，回應裡也只有遮罩
- 訂閱網址指向內部位址（`169.254.169.254`）時，連線在發出前就被拒絕

---

## 已知問題

- **`fetched_item` 到 `document` 的最後一步還沒做**——摘要好了但收不進知識庫
- LLM 是假的實作，還沒接真的供應商
- 測試與開發共用同一個資料庫，還沒改成 Testcontainers
- 登入沒有防暴力破解
- 沒有限制 HTTP request body 的大小
- 有些來源的 `<description>` 本來就不是內文（例如 Hacker News 只給一個留言連結），
  摘要的品質會受限。真要全文得去抓原始網頁，那是另一個工程
