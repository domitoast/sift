# Sift

個人知識庫。可以自己寫筆記，也預計讓系統定時去抓訂閱的來源、去掉重複的文章、產生摘要，我看過之後再決定要不要留下來。

目前完成的是知識庫本身與認證，自動抓取的部分還在做。

---

## 現在能做什麼

| 功能 | 說明 |
|---|---|
| 註冊 / 登入 | 密碼用 BCrypt，登入拿到 access token 與 refresh token |
| Token 換發 | access token 15 分鐘過期，用 refresh token 換新的 |
| 登出 | 讓 refresh token 立即失效 |
| 建立 / 讀取 / 編輯 / 刪除筆記 | 刪除是 soft delete，資料保留 |
| 筆記列表 | 分頁，預設每頁 20 筆 |

## 還沒做

- 自動抓取來源（RSS）與 LLM 摘要
- 全文搜尋
- 部署與 CI
- 前端

---

## 跑起來

需要 Java 21、Docker、Maven。

```bash
git clone https://github.com/domitoast/sift.git
cd sift

# 1. 準備環境變數
cp .env.example .env
```

`.env` 裡的 `SIFT_JWT_SECRET` 要自己產生一組（至少 512 bits）：

```bash
openssl rand -base64 64
```

把結果填進 `.env`，然後：

```bash
# 2. 起資料庫
docker compose up -d

# 3. 跑起來（Flyway 會自動建表）
mvn spring-boot:run
```

確認活著：

```bash
curl -i http://localhost:8080/actuator/health
```

跑測試（需要資料庫在跑）：

```bash
mvn test
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
| GET | `/me` | 自己的帳號資料 |
| POST | `/documents` | 建立筆記 |
| GET | `/documents` | 列表（分頁） |
| GET | `/documents/{id}` | 讀單篇 |
| PUT | `/documents/{id}` | 編輯，需帶 `version` |
| DELETE | `/documents/{id}` | 刪除 |

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

---

## 專案結構

```
src/main/java/dev/sift/
├── auth/          認證：JWT 簽發與驗證、refresh token
├── user/          使用者：註冊、自己的資料
├── document/      筆記：CRUD
├── common/        跨領域：全域例外處理、分頁回應格式
└── config/        Spring 設定

src/main/resources/db/migration/    Flyway migration
docs/                               設計文件
docs/adr/                           設計決策紀錄（14 份）
```

---

## 測試

37 個測試，其中 33 個是整合測試（真的起 Spring 與資料庫）。

比較值得看的幾個：

- 別人的合法帳號讀不到你的筆記，而且回 404 看不出它存在
- 編輯衝突時，先寫入者的內容必須完好無損（光是回 409 不夠）
- soft delete 之後查不到，但資料庫那一列還在
- 用過的 refresh token 再用一次會觸發連坐作廢

---

## 已知問題

- 測試與開發共用同一個資料庫，還沒改成 Testcontainers
- 登入沒有防暴力破解
- 沒有限制 HTTP request body 的大小
- 過期的 refresh token 沒有清除排程
