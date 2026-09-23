# ADR-019: refresh token 改放 HttpOnly cookie，access token 只留記憶體

## 狀態

已接受（取代原本「兩張 token 都放 sessionStorage」的做法）

## 背景

後端從一開始就把 token 拆成兩張：

| | 壽命 | 出現頻率 |
|---|---|---|
| access token | 15 分鐘 | 每一個 API 請求都帶 |
| refresh token | 7 天 | 只在換票時出現 |

拆開的目的是**讓兩張票的暴露程度不同**：常常出門的那張短命，就算被偷也很快失效；
長命的那張很少出門，而且後端可以作廢它（ADR-010）、偵測盜用（ADR-011）。

前端後來做的時候，兩張都存進了 `sessionStorage`。這造成兩個問題：

1. **XSS 一次拿走兩張。** 頁面上的任何 JavaScript 都讀得到 `sessionStorage`，
   惡意程式碼也一樣。前端最主要的威脅正是 XSS，所以在這個威脅之下，
   兩張票的暴露程度完全相同——後端拆兩張票的主要意義消失了。
2. **關分頁就登出。** `sessionStorage` 隨分頁消失，7 天壽命的 refresh token
   實際上只活到使用者關分頁為止。

問題不在任何一行程式碼寫錯，而在**後端的安全設計有一個隱含前提——「兩張票放在不同的地方」——
前端實作時沒有承接這個前提**。

## 考慮過的選項

### 選項 A：改成 localStorage

- 優點：前端改幾行，關分頁不再登出
- 缺點：XSS 問題完全沒變，只是換了一個一樣危險的地方

### 選項 B：refresh token 放 HttpOnly cookie，access token 只留記憶體（採用）

- refresh token 由後端用 `Set-Cookie` 發出，頁面上的 JavaScript **讀不到**
- 換票時瀏覽器自動附上 cookie，前端程式碼不經手
- access token 只存在一個模組變數裡，不寫進任何 storage
- 打開頁面時先靜默呼叫一次 `/auth/refresh`，換得到就直接進主畫面

## 決策

採用選項 B。cookie 屬性：

```
Set-Cookie: sift_refresh=...; Path=/api/v1/auth; Max-Age=604800;
            Secure; HttpOnly; SameSite=Strict
```

| 屬性 | 擋的是什麼 |
|---|---|
| `HttpOnly` | XSS 讀取 |
| `Secure` | 明文 HTTP 傳輸時被竊聽 |
| `SameSite=Strict` | 別的網站讓瀏覽器自動附上 cookie（CSRF） |
| `Path=/api/v1/auth` | 一般 API 請求不帶它，出門次數降到最低 |

`/auth/login`、`/auth/refresh` 的 body 不再包含 refresh token；
`/auth/refresh`、`/auth/logout` 改從 cookie 讀取，不再收 JSON body。

## 後果

**變好的：**

- XSS 最多拿到 access token，15 分鐘後失效；refresh token 拿不到
- 關分頁、重開瀏覽器，7 天內都還是登入狀態
- 登出時後端同時作廢資料庫紀錄並清掉 cookie

**新增的注意事項：**

- **並發換票必須合併成一次。** 頁面載入時可能有好幾個請求同時拿到 401，
  React 開發模式也會把 effect 跑兩次。如果各自去換票，同一張 refresh token
  會被送出兩次，後端的盜用偵測會判定為竊取、作廢所有憑證，使用者直接被登出。
  前端 `auth.js` 用一個共用的 in-flight promise 處理這件事。
- `Secure` cookie 在 `http://localhost` 可以用（瀏覽器把 localhost 視為安全來源），
  但從別台機器用明文 HTTP 連進來時不會被存下。這種情況需設
  `SIFT_JWT_REFRESH_COOKIE_SECURE=false`。
- 仍然擋不住的：XSS 在頁面開著的期間，可以直接用 access token 打 API。
  HttpOnly 保護的是「長期憑證被帶走」，不是「頁面被控制」。

## 教訓

安全設計常常建立在「某樣東西放在某個地方」的前提上，而這個前提寫在設計者腦中，
不寫在程式碼裡。後端的測試全部通過，前端的功能也全部正常——
**兩邊各自都是對的，組合起來才是錯的。** 這種問題只有回頭問「當初為什麼要這樣設計」才看得到。
