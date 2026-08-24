# REVIEW_DAY9.md — 專案結構與程式碼審視

**日期**：Day 9（2026-08-23）
**範圍**：全專案的檔案位置、命名、重複、文件一致性
**下次審視**：Day 16

> 這是新增的例行檢查：每 7 天一次，不看功能，只看「東西有沒有放對地方、
> 有沒有越來越難維護」。功能正確性由測試負責。

---

## 嚴重度分級

| 等級 | 意思 |
|---|---|
| 🔴 | 現在就該修，會持續造成傷害 |
| 🟡 | 該修，但可以排進既有的時程 |
| 🟢 | 知道就好，目前無害 |

---

## 一、檔案位置

### 🔴 1. `user/AuthController.java` 管了不屬於它的東西

```
user/AuthController.java
├── POST /auth/register   ← user 領域
├── POST /auth/login      ← auth 領域
├── POST /auth/refresh    ← auth 領域
└── POST /auth/logout     ← auth 領域
```

**三個問題疊在一起：**

1. 名字叫 `AuthController`，卻放在 `user/` 套件
2. 它同時依賴 `UserService` 與 `AuthService`，橫跨兩個領域
3. Day 8 又往裡面加了兩支 auth 的 API，**問題正在變大而不是變小**

**建議**：拆成兩個。

```
user/UserController.java     ← GET /me、POST /register
auth/AuthController.java     ← login / refresh / logout
```

**Day 10 處理。** 這是純搬移，測試會保護你。

### 🟡 2. `config/JwtProperties.java` 的位置可辯論

它只被 `auth` 套件裡的兩個類別使用，按「領域內聚」應該放在 `auth/`。
放在 `config/` 的理由是「它是 `@ConfigurationProperties`」——**但那是它的機制，不是它的職責。**

對照：`PasswordConfig` 被 `user` 與 `auth` 共用，放 `config/` 是對的。

**建議**：移到 `auth/JwtProperties.java`。低優先，Day 20 整理時順手。

### 🟢 3. `common/PageResponse.java` 位置正確

它與任何領域無關，`common/` 是對的。

---

## 二、命名

### 🟡 4. 測試類別命名不一致，而且有一個名不副實

| 檔案 | 實際測什麼 | 問題 |
|---|---|---|
| `user/AuthControllerIntegrationTest` | **註冊**流程 | **名字說 Auth，測的是註冊** |
| `auth/AuthFlowIntegrationTest` | 登入 / 換發 / 登出 | ✅ |
| `document/DocumentFlowIntegrationTest` | 文件 CRUD | ✅ |
| `user/UserServiceTest` | 註冊的單元測試 | ✅ |

**建議**：`AuthControllerIntegrationTest` → `UserRegistrationIntegrationTest`。

**為什麼要在意**：測試紅燈時，你看到的第一個資訊是類別名稱。
名字錯了會把你導向錯誤的檔案。

---

## 三、重複

### 🟢 5. 兩個整合測試都有自己的「註冊 + 登入」輔助方法

`AuthFlowIntegrationTest` 與 `DocumentFlowIntegrationTest` 各有一份類似的登入流程。

**現在不要抽出來。** 兩份重複還在可容忍範圍，而過早抽出共用基底類別
會讓測試之間產生耦合——改一個測試的需求，可能弄壞另一個。

**觀察指標：出現第三份時再抽。**

---

## 四、根目錄與版控

### 🔴 6. 五個含機密的暫存檔還躺在專案根目錄

```
doc.json  login.json  logout.json  mary.json  refresh1.json
```

- `login.json`、`mary.json` 含**真實密碼**
- `logout.json`、`refresh1.json` 含**真實的 refresh token**

**已經加進 `.gitignore`（`/*.json`），所以不會進版控。**
但實體檔案還在硬碟上。

**建議**：測完就刪。

### 🟡 7. `.github/modernize/` 是 IDE 產生的垃圾

```
.github/modernize/java-upgrade/hooks/scripts/recordToolUse.ps1
.github/modernize/java-upgrade/hooks/scripts/recordToolUse.sh
```

不是你建的，也與專案無關。**而且 `.github/` 之後要放 CI 設定，
這堆東西會讓那個資料夾看起來很混亂。**

**建議**：刪除。

---

## 五、文件漂移

### 🔴 8. `docs/API_DESIGN.md` 已經與實作不符

Day 3 寫的設計文件，至今未更新。已知的落差：

- 沒有 `PageResponse` 的格式定義（分頁是 Day 9 才加的）
- refresh / logout 的回應細節與實作不同
- 未記錄「rotation」與「盜用偵測」的行為
- 27 支設計中的 endpoint，目前只實作 8 支，文件沒有標示狀態

### 🟡 9. `docs/DATABASE_DESIGN.md` 缺少 V3 / V4 的變更

`refresh_token.previous_token_hash` 沒有出現在文件裡。

**這是 Day 5 出現過的同一個問題**——當時是 `app_user.deleted_at` 漏記。

> **文件漂移不是一次性的錯誤，是持續發生的現象。**
> 兩個業界解法：一是紀律（改 schema 時同步改文件），
> 二是自動化（從 migration 或 entity 產生文件）。
> 本專案先用紀律，Day 20 評估要不要自動化。

### 🟢 10. `docs/HANDS_OFF_DAY7.md` 是一次性文件

空手日的題目卡，任務已結束。留著沒有壞處，
但 Day 20 整理文件時應該移到 `docs/archive/` 或直接刪除。

---

## 六、程式碼

### 🟡 11. `UserService.register()` 直接接收 API 層的 DTO

```java
public UserResponse register(RegisterRequest request)
```

Service 因此依賴了 `user.dto` 套件。若日後要從別的入口建立使用者
（例如管理後台、批次匯入），呼叫端得先組一個「HTTP 請求物件」，
語意上很怪。

**對照**：`DocumentService.create(Long userId, CreateDocumentRequest request)`
有同樣的問題，但至少 userId 是分開傳的。

**這是已知技術債，不急。** 真正該處理的時機是「出現第二個入口」。

### 🟢 12. Javadoc 密度遠高於真實專案

已記錄在 `PROJECT_RULES.md` 2.1。**Day 20 統一刪減。**

現階段刻意保留——它們是教材。

---

## 七、本次審視發現的最重要問題

### 🔴 測試設定檔遮蔽了主設定檔（已於 Day 9 修正）

`src/test/resources/application.yml` 與 `src/main/resources/application.yml`
同名，classpath 上只會載入前者。**主設定檔在測試時從未被載入。**

因此測試環境的 `max-page-size`、`open-in-view`、`ddl-auto` 全部是
Spring 的預設值，而非我們設定的值。**這個狀態從 Day 5 持續到 Day 9。**

**發現方式**：Day 9 寫了一個斷言 `size` 上限為 100 的測試，實際量到 2000。

**修正**：改名為 `application-test.yml` + `@ActiveProfiles("test")`，
讓兩份設定變成「疊加」而非「取代」。

**教訓**：

> **設定檔如果跑在跟正式環境不同的值上，測試測到的就不是要上線的東西。**
> **而且這種錯誤不會有任何警告。**

---

## 行動清單

| 優先 | 項目 | 何時 |
|---|---|---|
| 🔴 | 刪除根目錄的五個 json 暫存檔 | **現在** |
| 🔴 | 拆分 `AuthController` | Day 10 |
| 🔴 | 更新 `API_DESIGN.md` | Day 10 |
| 🟡 | 刪除 `.github/modernize/` | Day 10 |
| 🟡 | 更新 `DATABASE_DESIGN.md` | Day 10 |
| 🟡 | 重新命名 `AuthControllerIntegrationTest` | Day 10 |
| 🟡 | `JwtProperties` 移到 `auth/` | Day 20 |
| 🟢 | 刪減 Javadoc、歸檔一次性文件 | Day 20 |
