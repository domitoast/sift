# 空手日 #1 — 題目卡（Day 7）

**規則**：不使用 AI。**可以讀自己專案的程式碼、可以查官方文件。**

---

## 1. 題目

實作 `GET /api/v1/me`——回傳**當前登入者**的資料。

| 情境 | 預期回應 |
|---|---|
| 帶有效 token | 200 + `{"id":1,"email":"...","createdAt":"..."}` |
| 不帶 token | 401（**這個你不用寫，已經有了**） |
| token 過期或亂改 | 401（**同上**） |

**注意第 2、3 列**——那兩件事 `SecurityConfig` 已經處理完了。
**你只需要處理「已經確定登入了，接下來要做什麼」。**

---

## 2. 驗收標準

- [ ] 帶有效 token → 200，回傳的 email 正確
- [ ] 回應**不包含** `passwordHash`
- [ ] 不帶 token → 401
- [ ] 沒有在 Controller 裡寫業務邏輯（查資料庫的動作不該出現在 Controller）
- [ ] 沒有直接回傳 `User` entity

---

## 3. 你要模仿的路徑

`POST /api/v1/auth/register` 走過的每一步，`GET /me` 幾乎都要走一次。
**打開這四個檔案對照著看：**

| 順序 | 檔案 | 這一層負責什麼 | 你要注意的 |
|---|---|---|---|
| 1 | `user/AuthController.java` | **接 HTTP**：路徑、method、狀態碼 | `@RestController`、`@RequestMapping` 怎麼組出完整路徑 |
| 2 | `user/UserService.java` | **業務邏輯**：查詢、判斷、丟例外 | `@Transactional` 加在哪、為什麼 |
| 3 | `user/UserRepository.java` | **存取資料庫** | 方法名稱的**命名規則**——Spring 靠名字自動產生查詢 |
| 4 | `user/dto/UserResponse.java` | **對外的資料形狀** | `from(User)` 這個靜態方法在做什麼 |

### 一支 API 的通用骨架

```
Controller  收請求 → 呼叫 Service → 把結果包成回應
    ↓
Service     查資料 → 判斷 → 轉成 DTO
    ↓
Repository  真的去資料庫撈
```

**register 是這個形狀，`GET /me` 也是。** 差別只在細節。

---

## 4. 唯一沒有前例可循的部分

**「當前登入者是誰？」** ——register 不需要知道這件事，所以你在既有程式碼裡找不到範例。

### 先理解發生了什麼

今天早上我寫的 `JwtAuthenticationFilter` 做了這件事：

```
驗證 token 成功 → 把「使用者 id」放進一個叫 SecurityContext 的地方
```

**SecurityContext 是什麼**：Spring Security 保管「這個請求是誰」的地方。
它綁在**當前這個請求**上，不同請求之間互不干擾。

**所以 Controller 不需要自己解析 token。** 身分已經放在那裡了，直接拿就好。

### 怎麼拿

Spring 提供一個標註叫 **`@AuthenticationPrincipal`**，寫在 Controller 方法的參數上，
Spring 會自動把 SecurityContext 裡的身分塞進來。

```java
public XXX me(@AuthenticationPrincipal 某個型別 某個名字) { ... }
```

### 你要自己找出來的：那個型別是什麼

**去讀 `auth/JwtAuthenticationFilter.java` 第 121 行。**

那一行是 `new UsernamePasswordAuthenticationToken(第一個參數, null, List.of())`。

**第一個參數放進去的是什麼型別，`@AuthenticationPrincipal` 拿出來的就是什麼型別。**

---

## 5. 三個你會遇到的設計決定

**這些沒有標準答案，我要看你怎麼想。做完之後告訴我你的理由。**

### 決定 1：這支 API 該放在哪個 Controller？

放進現有的 `AuthController`？還是開一個新的？

**提示**：`AuthController` 上面寫著 `@RequestMapping("/api/v1/auth")`。
你的路徑是 `/api/v1/me`，不是 `/api/v1/auth/me`。這件事會逼你做決定。

### 決定 2：Repository 現有的方法夠用嗎？

`UserRepository` 現在有：

```
findByEmailAndDeletedAtIsNull(String email)
existsByEmailAndDeletedAtIsNull(String email)
```

你手上有的是 **id**，不是 email。

`JpaRepository` 內建了一個 `findById(Long)`——**但它會不會把已刪除的使用者也撈出來？**

想清楚再決定要不要加新方法。要加的話，**照現有方法的命名規則命名。**

### 決定 3：查不到使用者的時候怎麼辦？

理論上不會發生（token 有效代表這個人存在），但如果真的查不到：

- 回 500？
- 回 404？
- 回 401？

**選一個，並說得出理由。** 現有的 `GlobalExceptionHandler` 已經有七種處理，
你可能需要新增第八種，也可能沿用現成的。

---

## 6. 卡住的時候

**卡住是預期中的，不是失敗。** 但有順序：

1. **先看錯誤訊息**——完整讀完，不要只看第一行
2. **回去對照 register 那條路**——「同樣的位置，register 是怎麼寫的？」
3. **加 `System.out.println` 或中斷點**——確認執行到哪裡、變數是什麼
4. **查 Spring 官方文件**
5. **卡超過 20 分鐘就停下來，把「卡在哪、試過什麼」寫下來**

**第 5 點的紀錄比程式碼本身更有價值。** 我 review 的時候會先看它。

---

## 7. 怎麼測

```powershell
cd C:\Users\kevin\桌面\Side\sift

# 拿 token
$token = (curl.exe -s -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" -d "@login.json" | ConvertFrom-Json).accessToken

# 帶 token（預期 200 + 你的資料）
curl.exe -i http://localhost:8080/api/v1/me -H "Authorization: Bearer $token"

# 不帶 token（預期 401）
curl.exe -i http://localhost:8080/api/v1/me
```

---

## 8. 完成後回報

告訴我：

1. **能跑嗎**（貼上兩個 curl 的結果）
2. **三個設計決定你各選了什麼、為什麼**
3. **哪裡卡住了、卡多久、怎麼解決的**

第 3 點請誠實寫，**包括最後放棄沒解決的部分**。

---

> **不要在完成前問 AI。** 卡住、放棄、只寫出一半——都是有效的結果。
> 空手日要量的是「你現在實際能做到什麼」，不是「你能交出什麼」。
