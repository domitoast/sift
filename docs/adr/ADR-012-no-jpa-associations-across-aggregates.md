# ADR-012: 跨聚合邊界只存 id，不建立 JPA 關聯

## 狀態

已接受（Day 9）

## 背景

`refresh_token.user_id` 與 `document.user_id` 都是指向 `app_user` 的外鍵。
在 JPA 中，Java 這一側有兩種對應方式，**產生的資料庫結構完全相同**：

```java
// 選項 A
@Column(name = "user_id")
private Long userId;

// 選項 B
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

必須在寫第一個有外鍵的 entity 時做出決定，因為之後會被複製到所有 entity。

## 考慮過的選項

### 選項 A：只存 id（採用）

- 優點：**所有查詢都是明寫的**，不會有「讀一個屬性卻送出一次 SQL」的情況
- 優點：不可能發生 N+1 或 `LazyInitializationException`
- 優點：跨得過未來的服務邊界（id 是普世的，物件參考不是）
- 缺點：需要作者資料時得自己查一次
- 缺點：**用不到 JPA 的核心功能，面試被問到時只能講理論**

### 選項 B：使用 `@ManyToOne`

- 優點：`document.getUser().getEmail()` 一行取得關聯資料
- 優點：JPA 的主要賣點，是常見的面試題
- 缺點：**查詢變成隱形的**。程式碼看不出哪一行會打資料庫
- 缺點：N+1 風險。20 篇文件 + 逐一取作者 = 21 次查詢
- 缺點：`LazyInitializationException`——離開交易範圍才存取關聯就爆炸

## 決定

採用選項 A：**跨聚合（aggregate）邊界一律只存 id。**

聚合內部（例如未來的 `document` 與 `document_version`）不受此限，
屆時另行評估。

## 理由

**決定性的問題是：「本專案有任何一個地方，需要從 Document 或 RefreshToken
取得 User 的資料嗎？」答案是沒有。**

- 驗證 refresh token 只需要知道「這是誰的」——一個 id 就夠
- 文件的四支 API 全都不回傳作者資訊（回傳的一定是自己的文件）

**為了一個用不到的能力，換來 N+1 與 lazy loading 的風險，不划算。**

微服務興起之後，「跨聚合只存 id」在業界的接受度明顯提高，
因為物件參考跨不過服務邊界，而 id 可以。

## 後果

### 好處

- 每一次資料庫查詢都寫在明處
- 不會有 N+1 或 `LazyInitializationException`

### 代價

- **學習面的損失**：JPA 關聯是面試常考題，本專案不會實際用到，
  只能靠理論回答。Day 10 的 `document_version`（聚合內部）
  是唯一可能實際練習的機會
- 若日後需要在列表顯示作者名稱，得自己 join 或分兩次查

### 重新評估的時機

- 出現「一次查詢需要同時取得兩個 entity 的資料」且效能成為問題時
- 加入聚合內部的關聯時（`document` ↔ `document_version`）
