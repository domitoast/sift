# PROJECT_STATE.md — 專案現況快照

**快照日期**：Day 8（2026-08-22）

> 這是一份「現在長什麼樣」的地圖，不是設計文件。
> 每個空手日與每週結束時更新一次。

---

## 1. 一句話現況

**使用者可以註冊、可以登入拿到 token，而且 token 現在真的能保護 API。除此之外什麼都還沒有。**

---

## 2. 對外的 API（只有兩支）

| Method | 路徑 | 需要登入？ | 做什麼 | 成功回應 |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ | 註冊 | 201 + `UserResponse` |
| POST | `/api/v1/auth/login` | ❌ | 登入 | 200 + access + refresh |
| POST | `/api/v1/auth/refresh` | ❌ | 換發（含 rotation 與盜用偵測） | 200 + 新的一對 |
| POST | `/api/v1/auth/logout` | ❌ | 作廢該張 refresh token | **204** |
| GET | `/api/v1/me` | ✅ | 當前登入者資料 | 200 + `UserResponse` |
| GET | `/actuator/health` | ❌ | 健康檢查 | 200 |

**其他所有路徑一律 401。**

`/auth/**` 全部是 `permitAll`——包含 refresh 與 logout。
這是必要的：呼叫 refresh 的前提就是 access token 已經過期，
若要求先通過認證才能換發，會形成「要有 token 才能換 token」的死鎖。
安全性由 refresh token 本身保證，不由 filter chain 保證。

`docs/API_DESIGN.md` 裡設計了 27 支 endpoint——**其中 25 支還不存在**。
設計文件寫的是「將來要有什麼」，這份寫的是「現在有什麼」。兩者不要混淆。

---

## 3. 程式碼（18 個 main + 2 個 test）

```
dev.sift
├── SiftApplication.java            啟動點
│
├── user/                           使用者領域
│   ├── User.java                   Entity ←→ app_user 資料表
│   ├── UserRepository.java         資料存取
│   ├── UserService.java            註冊邏輯（BCrypt、重複檢查）
│   ├── AuthController.java         ★ register 與 login 兩支 API 的入口
│   ├── EmailAlreadyUsedException.java
│   └── dto/
│       ├── RegisterRequest.java    進來的（含 email 正規化）
│       └── UserResponse.java       出去的（不含密碼雜湊）
│
├── auth/                           認證領域
│   ├── JwtService.java             簽發 / 驗證 token、產生 refresh token 值
│   ├── AuthService.java            登入邏輯（比對密碼、發 token）
│   ├── JwtAuthenticationFilter.java ★ Day 7 新增：驗票，把身分放進 SecurityContext
│   ├── InvalidCredentialsException.java
│   └── dto/
│       ├── LoginRequest.java
│       └── TokenResponse.java      目前只有 accessToken，沒有 refreshToken
│
├── config/
│   ├── SecurityConfig.java         ★ Day 7 改寫：哪些路徑要登入
│   ├── JwtProperties.java          讀 application.yml 的 sift.jwt.*
│   └── PasswordConfig.java         提供 BCryptPasswordEncoder
│
└── common/
    └── GlobalExceptionHandler.java 所有錯誤回應的唯一出口
```

### AuthController 放在 `user/` 是不對的

它同時處理 register（user 領域）與 login（auth 領域），
按分層應該拆成 `UserController` 與 `AuthController`，或整個移到 `auth/`。

**已知的技術債，Day 8 處理。**

---

## 4. 錯誤處理——全部集中在 GlobalExceptionHandler

**這是唯一產生錯誤回應的地方。** 目前有 6 種：

| 例外 | 狀態碼 | 何時發生 | 加入於 |
|---|---|---|---|
| `EmailAlreadyUsedException` | 409 | 註冊時 email 重複 | Day 5 |
| `InvalidCredentialsException` | 401 | 帳號或密碼錯誤 | Day 5 |
| `MethodArgumentNotValidException` | 400 | `@Valid` 檢查不過 | Day 5 |
| `NoResourceFoundException` | 404 | 路徑不存在 | Day 7 |
| `HttpRequestMethodNotSupportedException` | 405 | 用錯 HTTP method | Day 7 |
| `HttpMessageNotReadableException` | 400 | JSON 格式壞掉 | Day 7 |
| `Exception`（catch-all） | 500 | 其他所有情況 | Day 5 |

回應格式一律是 **RFC 7807 Problem Details**：

```json
{
  "type": "https://sift.dev/errors/xxx",
  "title": "人看的標題",
  "status": 404,
  "detail": "具體說明",
  "instance": "/api/v1/documents"
}
```

**注意**：security filter chain 擋下的 401 **不經過這裡**，
它由 `HttpStatusEntryPoint` 直接回傳，body 是空的。
**兩種 401 的長相不一樣**——這是判斷「請求有沒有進到 MVC」的線索。

---

## 5. 資料庫（8 張表，只有 2 張有在用）

| 表 | 狀態 | 說明 |
|---|---|---|
| `app_user` | ✅ **使用中** | 註冊會寫入 |
| `flyway_schema_history` | ✅ **使用中** | Flyway 自己管的 |
| `refresh_token` | ✅ **使用中** | 登入寫入、換發原地更新、登出與盜用偵測填 `revoked_at` |
| `source` | ⬜ 空的 | Day 11 |
| `fetch_job` | ⬜ 空的 | Day 11 |
| `fetched_item` | ⬜ 空的 | Day 11 |
| `document` | ⬜ 空的 | Day 8 |
| `document_version` | ⬜ 空的 | Day 9 |

Migration 檔：

| 檔案 | 內容 |
|---|---|
| `V1__init.sql` | 6 張業務表 |
| `V2__add_refresh_token.sql` | `refresh_token` 表（型別誤用 `CHAR`） |
| `V3__add_refresh_token_rotation.sql` | 加 `previous_token_hash`（同樣誤用 `CHAR`） |
| `V4__use_varchar_for_token_hash.sql` | 改為 `VARCHAR(64)`，修正 V2 / V3 |

> V2、V3 的錯誤刻意留在歷史裡。已執行的 migration 不能修改，
> 只能往後新增一份——V4 的註解說明了它為何存在。

---

## 6. 一個請求走過的完整路線

以 `POST /api/v1/auth/register` 為例：

```
HTTP 請求
   ↓
[Security Filter Chain]
   ├─ JwtAuthenticationFilter   讀 Authorization 標頭（沒有 → 什麼都不做）
   └─ AuthorizationFilter       比對規則 → /auth/** 是 permitAll → 放行
   ↓
DispatcherServlet               找路由
   ↓
AuthController.register()       ★ 進入我們的程式碼
   ↓  @Valid 觸發驗證，失敗 → MethodArgumentNotValidException → 400
   ↓
UserService.register()          @Transactional 開始
   ├─ 查 email 有沒有重複
   ├─ BCrypt 雜湊密碼
   └─ userRepository.save()
   ↓                            @Transactional 結束（commit）
UserResponse.from(user)         轉成對外的 DTO
   ↓
201 Created + Location 標頭
```

**任何一步丟出例外 → GlobalExceptionHandler → Problem Details。**

---

## 7. 測試（9 個，全部來自 Day 5）

**共 20 個，全綠。**

| 檔案 | 數量 | 類型 |
|---|---|---|
| `UserServiceTest` | 4 | 單元測試（Mockito，不碰資料庫） |
| `AuthControllerIntegrationTest` | 5 | 整合測試 |
| `AuthFlowIntegrationTest` | **11** | 整合測試（Day 8 新增） |

`AuthFlowIntegrationTest` 涵蓋：登入發兩張票、資料庫存雜湊不存原文、
`/me` 的三種情境、rotation 原地更新、**盜用偵測與連坐作廢**、
登出失效、登出 idempotent。

### 已知的測試盲點

- **`noRollbackFor` 驗證不到**：測試本身跑在「結束必定回滾」的交易裡，
  拿掉那個設定測試很可能仍是綠的。要真正驗證需改用真實 HTTP 呼叫並手動清資料。
- **測試與開發共用同一個資料庫**：因此所有斷言都必須限定在
  「本次測試建立的使用者」範圍內，不能假設表是空的。Testcontainers 可解。

> **「測試綠燈」不等於「行為正確」。要知道自己的測試涵蓋不到哪裡。**

---

## 8. 已知技術債

| # | 項目 | 預定處理 |
|---|---|---|
| ~~1~~ | ~~登入與 JWT filter 沒有任何測試~~ | ✅ Day 8 完成 |
| ~~2~~ | ~~`refresh_token` 表建好但沒程式碼用~~ | ✅ Day 8 完成 |
| 3 | `AuthController` 放在 `user/` 套件（現在還多管了 refresh / logout） | Day 9 |
| 4 | `UserService.register()` 直接吃 API 層的 DTO | Day 9 |
| 5 | 整合測試需要 Docker 在跑 → Testcontainers | Day 18 |
| 11 | `noRollbackFor` 的行為無法被現有測試驗證 | Day 18 |
| 12 | Spring Security 產生的 in-memory 使用者造成啟動 WARN | Day 18 |
| 13 | refresh token 重複使用缺少「數秒寬限期」，前端逾時重試會被誤判為盜用 | 待評估 |
| 6 | 沒有 Maven Wrapper（CI 需要） | Day 19 |
| 7 | retry 參數（1s / 2× / 60s / 3 次）是猜的 | Day 13 |
| 8 | `llm_api_key_encrypted` 的加密演算法未定 | Day 16 |
| 9 | **`POST /auth/login` 無任何暴力破解防護**（原路線圖遺漏，Day 7 由使用者提出） | 待排 |
| 10 | `SIFT_JWT_REFRESH_TTL_DAYS=7` 是抄業界慣例，未為本專案計算過 | 待重新評估 |

---

## 9. 怎麼把系統跑起來

```powershell
# 1. 資料庫
cd C:\Users\kevin\桌面\Side\sift
docker compose up -d

# 2. 應用程式：在 IntelliJ 執行 SiftApplication
#    （需要 .env 的環境變數，見 docs/LOCAL_SETUP.md）

# 3. 確認活著
curl.exe -i http://localhost:8080/actuator/health
```
