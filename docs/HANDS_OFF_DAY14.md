# 空手日 #2 — 防止重複訂閱同一個來源

**日期**：Day 14
**預估**：90 分鐘
**規則**：**不使用 AI。** 不問 Claude、不用 Copilot、不用 ChatGPT。

---

## 可以做的事

- ✅ **打開你自己專案裡任何既有的程式碼來看、來模仿**
- ✅ 查 Spring / JPA 官方文件、Google、Stack Overflow
- ✅ 用 `curl` 或 `Invoke-RestMethod` 試

## 不可以做的事

- ❌ 問 AI「這段怎麼寫」
- ❌ 讓 AI 產生任何一行程式碼

> **卡住是預期中的，不是失敗。**
> 卡住的時候不要求救——**記在本檔最下方的「卡住紀錄」。**
> 明天 code review 時，那份紀錄比完成度更有價值。

---

## 現況：這是一個真的 bug

```
POST /api/v1/sources  {"url":"https://news.ycombinator.com/rss", ...}   → 201
POST /api/v1/sources  {"url":"https://news.ycombinator.com/rss", ...}   → 201  ← 又建了一筆
```

同一個使用者可以把同一個網址加無數次。等 Day 15 排程開始抓取，
**同一篇文章就會被抓 N 次、摘要 N 次（花 N 倍的錢）。**

---

## 必做（這是及格線）

1. 同一個使用者不能重複新增同一個 `url`
2. 撞到重複時回 **409 Conflict**，不是 500，也不是靜默成功
3. **寫一個測試證明它**

---

## 你可以照抄的範本

**「註冊時 email 重複」走的是一模一樣的路。** 打開這四個地方看一遍：

| 檔案 | 看什麼 |
|---|---|
| `user/UserRepository.java` | `existsByEmailAndDeletedAtIsNull` 這個方法怎麼宣告的 |
| `user/UserService.java` 的 `register()` | 檢查寫在哪一行、丟什麼 |
| `user/EmailAlreadyUsedException.java` | 一個自訂 exception 長什麼樣 |
| `common/GlobalExceptionHandler.java` | 那個 exception 怎麼變成 409 |

**你要寫的是同一組東西，只是把 `user` / `email` 換成 `source` / `url`。**

測試的部分看 `source/SourceFlowIntegrationTest.java`，
第 1 題（新增來源 → 201）就是你要改寫的骨架。

---

## 你必須自己決定的三件事

**這三題沒有標準答案在程式碼裡，你要自己想，並且把理由寫在下面。**

### 決定 1：「重複」的範圍

全域唯一（整個系統一個 url 只能存在一次），還是每個使用者各自唯一？

> 提示：先去看 `docs/adr/ADR-017-per-user-sources.md`。

**我的決定與理由**：

```
（寫在這裡）
```

### 決定 2：已經被 soft delete 的來源

使用者刪掉了 `https://a.com/rss`，然後又想加回來。應該成功還是回 409？

**決定：應該成功。**

**理由（kevin 於 code review 時口述，答對且是事前想過的）**：

> 刪掉的來源要能加回來。所以方法名字結尾要帶 `AndDeletedAtIsNull`——
> 少了它，使用者刪掉一個來源之後就永遠加不回來，會一直收到 409。

**這是今天最有價值的一項**：三個決定裡最容易漏掉的，而且是在寫程式當下
自己推導出來的，不是照抄。由測試 11 保護。

### 決定 3：狀態碼

409 Conflict 還是 400 Bad Request？為什麼？

**決定：409 Conflict。**

**理由**：

> 400 的語意是「你的請求本身有問題」——格式錯、缺欄位、型別不對。
> 但這個請求格式完全正確，`name`、`url`、`type` 都合法，
> 它只是**跟伺服器目前的資料狀態衝突**。409 Conflict 正是這個語意。

> ⚠️ **誠實記錄**：這一題是照 `EmailAlreadyUsedException` 的 handler 抄的，
> 抄的時候沒有先讀那段註解。理由是 code review 時才補上的。

---

## 驗收標準

- [ ] 同一個 url 連送兩次，第二次回 **409**
- [ ] 回應是 Problem Details 格式（跟 email 重複時長得一樣）
- [ ] **不同使用者**各自訂閱同一個 url → 兩次都 201
- [ ] `.\mvnw.cmd test` 全綠，**57 個測試一個都不能紅**
- [ ] 三個決定都寫下理由

---

## 加分題

`UserService.register()` 裡有**兩道防線**擋 email 重複。

**第二道防線是**：資料庫的 partial unique index。

```sql
CREATE UNIQUE INDEX uq_app_user_email
    ON app_user (email)
    WHERE deleted_at IS NULL;
```

**來源需不需要？** 需要，而且**它早就存在了**：

```sql
-- V1__init.sql
CREATE UNIQUE INDEX uq_source_user_url
    ON source (user_id, url)
    WHERE deleted_at IS NULL;
```

> ⚠️ **題目卡出錯了，是 Claude 的責任。**
>
> 原本寫「同一個網址可以加無數次 → 201」。**那是錯的**，寫題目時沒有回去查
> `V1__init.sql`。真實情況是第二次 POST 會回 **500**——
> 資料庫擋下來丟出 `DataIntegrityViolationException`，
> 但 `GlobalExceptionHandler` 沒有對應的 handler，落到 catch-all。
>
> **所以今天做的事，實際上是「把一個 500 變成正確的 409」**，
> 而不是「從無到有加上唯一性」。不需要 V5 migration。

---

## Code Review 結果（Day 14）

### 自己寫對的

| 項目 | 說明 |
|---|---|
| `existsByUrlAndUserIdAndDeletedAtIsNull` | 三段條件全對，兩個設計決定編碼在方法名字裡 |
| Service 的檢查位置 | 放在 `new Source(...)` 之前，還沒建物件就擋掉 |
| Handler 的 409 + `setType` + `setTitle` | 完整照範本，一項沒漏 |
| **決定 2** | 事前想過，不是抄的 |

### Claude 補完的

| # | 問題 | 處理 |
|---|---|---|
| 1 | 沒有測試（必做第 3 項） | 補上測試 9、10、11 |
| 2 | `import dev.sift.source.*;` 多餘 | 刪除（同 package 不需 import） |
| 3 | `SourceAlreadySubscribeException` 文法 | 改為 `Subscribed`（比照 `EmailAlreadyUsedException`） |
| 4 | handler 放在檔案最尾、`FieldError` record 之後 | 移到 `handleSourceNotFound` 旁邊 |
| 5 | 沒有第二道防線的 catch | 加上 `catch (DataIntegrityViolationException)`，避免並發時變 500 |
| 6 | `save()` 改為 `saveAndFlush()` | 見下方說明 |

### 為什麼 `save()` 要改成 `saveAndFlush()`

`save()` 之後，Hibernate **不保證**當下就把 INSERT 送到資料庫——
它可能等到 transaction commit 才送。而 commit 發生在 `create()` **回傳之後**，
那時候已經離開 try 區塊，**那個 catch 永遠不會被觸發**。

`saveAndFlush()` 強迫立刻送出，唯一約束的衝突才會在 try 裡面被接住。

> 這就是 Day 10 那個 `flush()` 的同一件事：
> **「Hibernate 把工作攢著，不是你叫它做它就馬上做。」**

### 沒做的部分

- 卡住紀錄空白（沒有記錄過程）
- 決定 1、3 的理由是 code review 時才補的，不是寫程式當下想的
