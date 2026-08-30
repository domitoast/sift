# TESTING.md — 怎麼讀懂這個專案的測試

> Day 16 整理。起因：「我對測試部分的程式碼理解力很差，可以說是看不太懂。」
>
> **所有例子都來自這個專案現有的測試，沒有新編的。**
>
> 註解本身的說明在 [`ANNOTATIONS.md`](ANNOTATIONS.md) 第 10 節，這份講的是「怎麼讀」。

---

## 1. 一題測試就是一個方法

最短的一題（`FetchJobTest`）：

```java
@Test
void newJob_shouldBePending() {
    FetchJob job = new FetchJob(1L);
    assertThat(job.getStatus()).isEqualTo(FetchStatus.PENDING);
}
```

它只做兩件事：`new` 一個東西，檢查它的狀態對不對。**沒有魔法。**

`@Test` 的意思是「跑 `mvnw test` 的時候請執行這個方法」。

---

## 2. 每一題都是三段

```java
@Test
@DisplayName("PENDING → RUNNING，同時記下 startedAt")
void start_fromPending_shouldRunAndRecordTime() {

    FetchJob job = new FetchJob(SOURCE_ID);          // ① 準備

    job.start();                                      // ② 執行

    assertThat(job.getStatus())                       // ③ 檢查
            .isEqualTo(FetchStatus.RUNNING);
    assertThat(job.getStartedAt()).isNotNull();
}
```

**準備 → 執行 → 檢查。** 不管幾行，所有測試都是這個形狀。

看不懂一題測試的時候，第一件事就是把它切成這三段。
切不出來通常代表那題測試寫得不好。

> 業界叫它 **Arrange–Act–Assert**，簡稱 AAA。

---

## 3. `assertThat` 的詞彙表

`assertThat` 就是「我主張某件事應該成立」。不成立就讓測試變紅。

```java
assertThat(實際的東西).應該怎樣(期望的值);
```

| 寫法 | 意思 |
|---|---|
| `.isEqualTo(X)` | 應該等於 X |
| `.isNull()` / `.isNotNull()` | 應該是 / 不是 null |
| `.isTrue()` / `.isFalse()` | 應該是 true / false |
| `.isZero()` | 應該是 0 |
| `.hasSize(3)` | 清單裡應該有 3 個 |
| `.isEmpty()` / `.isPresent()` | `Optional` 應該是空的 / 有值 |
| `.contains("週會")` | 裡面應該包含這段 |
| `.startsWith("https://")` | 應該以這個開頭 |

### 測「應該要失敗」用另一個

```java
assertThatThrownBy(job::succeed)
        .isInstanceOf(IllegalFetchJobTransitionException.class)
        .hasMessageContaining("PENDING");
```

> 「執行 `job.succeed()` 應該要丟出這種例外，而且訊息裡要有 `PENDING`。」
>
> **它沒丟例外的話，測試才會紅。**

### 為什麼是 `job::succeed` 不是 `job.succeed()`

```java
assertThatThrownBy(job.succeed());     // ❌ 現在就執行了，例外當場爆掉
assertThatThrownBy(job::succeed);      // ✅ 傳進去的是「一個還沒執行的動作」
```

`job::succeed` 是 `() -> job.succeed()` 的簡寫，叫 **method reference**。

**關鍵是執行的時機**：你要把「動作本身」交出去，讓 `assertThatThrownBy`
自己去執行並攔截例外。傳「執行完的結果」進去就來不及了。

同樣的寫法在專案裡還有 `.map(SourceResponse::from)`。

---

## 4. 整合測試那一長串怎麼讀

```java
mockMvc.perform(post("/api/v1/sources")                    // ② 執行
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Hacker News","url":"https://...","type":"RSS"}
                        """))
        .andExpect(status().isCreated())                    // ③ 檢查
        .andExpect(jsonPath("$.enabled").value(true));
```

翻成中文：

> 「假裝有人送了一個 `POST /api/v1/sources`，帶著這個 token 和這段 JSON。
> 我期望回應是 201，而且回應 JSON 裡的 `enabled` 是 `true`。」

| 語法 | 意思 |
|---|---|
| `mockMvc.perform(...)` | 假裝發一個 HTTP 請求（不開真的 port，不用瀏覽器） |
| `.header(...)` | 加請求標頭 |
| `.content("...")` | 請求的 body |
| `.andExpect(...)` | 一項檢查，可以串很多個 |

### 狀態碼是用名字不是數字

| 方法 | 碼 | 方法 | 碼 |
|---|---|---|---|
| `isOk()` | 200 | `isUnauthorized()` | 401 |
| `isCreated()` | 201 | `isForbidden()` | 403 |
| `isNoContent()` | 204 | `isNotFound()` | 404 |
| `isBadRequest()` | 400 | `isConflict()` | 409 |

也可以寫 `status().is(201)`，但可讀性差——`isConflict()` 一看就知道在測什麼。

### `jsonPath` 的 `$`

`$` 代表「JSON 的最外層」。

```
回應是 {"id":1,"name":"Hacker News","enabled":true}
   $.name            → "Hacker News"
   $.enabled         → true

回應是 [{"name":"A"},{"name":"B"}]
   $[0].name         → "A"
   $.length()        → 2
```

---

## 5. unit test 還是 integration test

**看有沒有 `@SpringBootTest`。**

| | unit test | integration test |
|---|---|---|
| 啟動 Spring | ❌ | ✅ |
| 連資料庫 | ❌ | ✅ |
| 一題多久 | 幾十毫秒 | 幾百毫秒～幾秒 |
| 測什麼 | **「這段邏輯對不對」** | **「這些東西接起來對不對」** |

### 本專案現況（Day 16）

| 檔案 | 題數 | 類型 |
|---|---|---|
| `FetchJobTest` | 9 | unit |
| `FeedParserTest` | 8 | unit |
| `FeedDiscovererTest` | 6 | unit |
| `InternalAddressCheckerTest` | 7 | unit |
| `FetchClientTest` | 9 | unit（但會發真的 HTTP 到自架的假伺服器） |
| `UserServiceTest` | 4 | unit（Mockito） |
| `UserRegistrationIntegrationTest` | 5 | integration |
| `AuthFlowIntegrationTest` | 11 | integration |
| `DocumentFlowIntegrationTest` | 29 | integration |
| `SourceFlowIntegrationTest` | 15 | integration |

**43 unit / 60 integration。**

### 為什麼整合測試這麼多

回頭看這個專案真正踩到的 bug：

| Bug | unit test 抓得到嗎 |
|---|---|
| 404 被 catch-all 吞成 500 | ❌ 那是 `@ExceptionHandler` 的行為 |
| `application.yml` 被測試那份遮蔽 | ❌ 那是設定載入 |
| PUT 回應帶著舊的 version | ❌ 那是 Hibernate 的 flush 時機 |
| 改別人的文件回 404 | ❌ 那是 Security + 查詢條件 |

**四個全部抓不到。** 它們都不是「某段邏輯錯了」，
而是「東西接起來之後行為不對」。

教科書說 unit test 應該比 integration test 多。
**這個專案反過來，而且是刻意的。**

---

## 6. mock

### 先看問題

`SourceService.create()` 會呼叫 `feedResolver.resolve(url)`，而那個東西**會連網路**。

所以整合測試一跑，就會真的去連 `https://mine.example.com/rss`：

- 那個網址不存在 → 測試一定失敗
- 就算換成真的網址 → 每題都要等網路
- 別人的網站掛掉 → 你的測試紅，但你的程式沒問題

### 所以做一個假的

```java
@MockitoBean
private FeedResolver feedResolver;
```

**「把 Spring 容器裡那個真的 `FeedResolver` 換成假的。」**

然後告訴它該怎麼回答：

```java
when(feedResolver.resolve("https://blog.example.com"))
        .thenReturn("https://blog.example.com/feed.xml");
```

> 「當有人拿這個網址來呼叫 `resolve`，就回傳這個。」

### 三種「就…」

| 寫法 | 意思 |
|---|---|
| `.thenReturn(X)` | 就回傳 X |
| `.thenThrow(new FeedNotFoundException(url))` | **就丟出這個例外**（用來測失敗情境） |
| `.thenAnswer(call -> call.getArgument(0))` | 就把傳進去的第一個參數原封不動回傳 |

`anyString()` 是「任何字串都算」。所以：

```java
when(feedResolver.resolve(anyString())).thenAnswer(call -> call.getArgument(0));
```

＝「不管誰拿什麼字串來問，都原封不動還回去」
＝「假裝使用者填的本來就是 feed 網址」。

### `@Mock` vs `@MockitoBean`

| | `@Mock` | `@MockitoBean` |
|---|---|---|
| 有沒有 Spring | 沒有 | 有 |
| 誰拿到假的 | 只有你手動塞的那個 | **整個容器裡所有需要它的地方** |
| 本專案 | `UserServiceTest` | `SourceFlowIntegrationTest` |

---

## 7. 什麼時候**不**該 mock

### 錯誤示範

```java
@MockitoBean SourceRepository sourceRepository;        // ❌

when(sourceRepository.existsByUrlAndUserIdAndDeletedAtIsNull(...))
        .thenReturn(false);
```

這樣測出來的是：

> **「我叫 mock 回 false，然後它回了 false。」**

什麼都沒測到。真正會出錯的地方——**方法名字對不對、SQL 產得對不對、
`AndDeletedAtIsNull` 有沒有生效**——一個都沒碰到。

### 原則

> **只 mock 你控制不了的東西。**

| 該 mock | 不該 mock |
|---|---|
| 外部網路（`FeedResolver`、`FetchClient`） | 你自己的 Repository |
| 寄信、付款、簡訊 | 你自己的 Service |
| 隨機數、現在時間（有時候） | 資料庫 |

---

## 8. 怎麼判斷一個測試有沒有價值

> **把它對應的那段程式碼註解掉，它會不會變紅？**
>
> **不會紅的測試是假的。**

### 一個好例子

`existsByUrlAndUserIdAndDeletedAtIsNull` 這個方法名字有三段條件，
所以寫了三題，一段一題：

| 測試 | 拿掉哪一段會紅 |
|---|---|
| 9. 同一人重複 → 409 | `Url` |
| 10. 不同人同 url → 都成功 | `AndUserId` |
| 11. 刪掉後可重新訂閱 | `AndDeletedAtIsNull` |

只寫第 9 題的話，`AndUserId` 和 `AndDeletedAtIsNull`
被誰刪掉都不會有人發現。

### 另一個原則

**「操作被拒絕」和「資料真的沒被改」是兩件事。**

```java
mockMvc.perform(patch("/api/v1/sources/" + id) ...)
        .andExpect(status().isNotFound());        // 回 404 了

// 但這樣還不夠——要確認資料真的沒被改
mockMvc.perform(get("/api/v1/sources") ...)
        .andExpect(jsonPath("$[0].name").value("原始名稱"));
```

這個模式在專案裡出現了四次。**只檢查狀態碼，等於相信「回 404 就代表沒改到」。**

---

## 9. 常見疑問

**Q：為什麼有些方法名這麼長？**

`create_withDuplicateUrl_shouldReturnConflict` 的結構是
`做什麼_在什麼情況下_應該怎樣`。測試失敗時光看名字就知道壞了什麼，
不用進去讀程式碼。

**Q：`@DisplayName` 和方法名重複，為什麼兩個都要？**

方法名是給程式看的（英文、無空格），`@DisplayName` 是給人看的
（可以用中文、可以加 ★ 標記重點）。IDE 和報告顯示的是後者。

**Q：`@BeforeEach` 裡的東西，每一題都會重跑嗎？**

會。**每一題都是全新的開始**，前一題留下的狀態不會影響下一題。

**Q：整合測試寫進資料庫的資料會留下來嗎？**

不會。整合測試都有 `@Transactional`，每題結束自動回滾。

> ⚠️ 但這也代表**測試不能假設資料庫是空的**——
> 開發時手動塞的資料還在。Day 8 就因為這個踩過一次，
> 所以每個斷言都要限定在測試自己建立的資料上。
