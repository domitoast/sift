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

**我的決定與理由**：

```
（寫在這裡）
```

### 決定 3：狀態碼

409 Conflict 還是 400 Bad Request？為什麼？

> 提示：想想「這個請求本身有問題」和「這個請求跟現在的資料狀態衝突」的差別。

**我的決定與理由**：

```
（寫在這裡）
```

---

## 驗收標準

- [ ] 同一個 url 連送兩次，第二次回 **409**
- [ ] 回應是 Problem Details 格式（跟 email 重複時長得一樣）
- [ ] **不同使用者**各自訂閱同一個 url → 兩次都 201
- [ ] `.\mvnw.cmd test` 全綠，**57 個測試一個都不能紅**
- [ ] 三個決定都寫下理由

---

## 加分題（做不完完全不扣分）

`UserService.register()` 裡有**兩道防線**擋 email 重複。

1. 找出第二道是什麼，寫在下面
2. 想一想：來源這裡需不需要同樣的第二道？
3. 如果需要 → 那要多寫一份 `V5` migration

**第二道防線是**：

```
（寫在這裡）
```

**來源需不需要？理由**：

```
（寫在這裡）
```

---

## 卡住紀錄

**每次卡超過 10 分鐘就記一筆。格式：**

```
[時間] 卡在哪 → 我試了什麼 → 有沒有解決
```

例如：

```
[20 分鐘] 不知道 repository 方法名要怎麼取，Spring 才認得
       → 去看 UserRepository 照著改
       → 解決了，但不確定名字對不對
```

紀錄：

```
（寫在這裡）
```

---

## 完成後

**不要先問我對不對。** 先自己回答一次：

- 我能不能逐行解釋我寫的每一行？
- 那個測試如果我把 service 的檢查註解掉，它會不會紅？（**去實際試一次**）

然後才叫我來 review。
