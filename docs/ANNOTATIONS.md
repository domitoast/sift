# ANNOTATIONS.md — 本專案用到的所有註解

> Day 15 整理。**只列這個專案真的用到的**，不是 Spring 的完整清單。
>
> 每一項的格式：**一句話說明** → 必要時給一個小例子 → 不用會怎樣。

---

## 0. 先搞懂註解是什麼

**註解本身什麼事都不做。** 它只是「貼在程式碼上的標籤」。

真正做事的是別人——Spring 在啟動時掃描這些標籤，看到什麼就做什麼。

```java
@Service                       // ← 這行本身不會建立任何東西
public class UserService { }   //   是 Spring 啟動時掃到它，才去 new 一個
```

**所以少貼一個標籤，通常不會編譯失敗，而是「那件事靜靜地沒有發生」。**
這是註解最容易害人的地方。

---

## 1. 讓 Spring 建立並保管這個物件（bean 註冊）

| 註解 | 用途 |
|---|---|
| `@Component` | 最通用的一個：「請 Spring 建立這個類別的實例」 |
| `@Service` | 功能上**完全等同** `@Component`，差別只在語意——讀的人一看就知道這是業務邏輯層 |
| `@RestController` | `@Controller` + `@ResponseBody`。回傳值會被自動轉成 JSON，不是找 HTML 樣板 |
| `@Configuration` | 這個類別裡有 `@Bean` 方法，或負責設定 |
| `@RestControllerAdvice` | 跨所有 Controller 的共同處理。本專案用在 `GlobalExceptionHandler` |
| `@Bean` | 標在**方法**上：這個方法的回傳值交給 Spring 保管 |

### `@Bean` 什麼時候用

當那個類別**不是你寫的**、你沒辦法在它上面貼 `@Component` 時。

```java
// PasswordConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();   // BCryptPasswordEncoder 是 Spring 的類別
}                                          // 我們改不了它，只能自己 new 出來交給 Spring
```

### 本專案的 bean 清單

| 類型 | 數量 | 有哪些 |
|---|---|---|
| `@Service` | 7 | `UserService` `AuthService` `JwtService` `DocumentService` `SourceService` `FetchService` `FetchJobService` |
| `@Component` | 5 | `JwtAuthenticationFilter` `FeedParser` `FetchClient` `FetchScheduler` `RefreshTokenCleanupScheduler` |
| `@RestController` | 4 | `UserController` `AuthController` `DocumentController` `SourceController` |
| `@Configuration` | 4 | `SecurityConfig` `PasswordConfig` `SchedulingConfig` `JwtProperties` |
| `@Bean` 方法 | 2 | `PasswordEncoder` `SecurityFilterChain` |
| `JpaRepository` 介面 | 6 | 六個 Repository，**Spring 自動產生實作** |

---

## 2. Web / REST

| 註解 | 用途 |
|---|---|
| `@RequestMapping("/api/v1/sources")` | 標在類別上：這個 Controller 底下所有路徑的共同前綴 |
| `@GetMapping` `@PostMapping` `@PutMapping` `@PatchMapping` `@DeleteMapping` | 對應五個 HTTP 方法 |
| `@RequestBody` | 把請求的 JSON 轉成 Java 物件 |
| `@PathVariable` | 從路徑裡取值：`/sources/{id}` 的那個 `id` |
| `@RequestParam` | 從查詢字串取值：`?q=關鍵字` 的那個 `q` |
| `@AuthenticationPrincipal` | 取出「目前登入的是誰」 |
| `@PageableDefault` | 分頁參數的預設值 |
| `@ExceptionHandler` | 這個方法負責處理某一種 exception |

### `@PathVariable` vs `@RequestParam`

```java
GET /api/v1/documents/42?q=會議

@PathVariable Long id      // 42   ← 在路徑「裡面」
@RequestParam String q     // 會議  ← 在問號「後面」
```

**判斷法**：這個值是不是「指定哪一個資源」？是 → `@PathVariable`。
只是篩選或選項 → `@RequestParam`。

### `@AuthenticationPrincipal`

```java
@GetMapping("/me")
public UserResponse me(@AuthenticationPrincipal Long userId) { ... }
```

那個 `userId` 是 `JwtAuthenticationFilter` 驗證 token 之後放進
`SecurityContextHolder` 的。**能拿到值就代表已經驗證過了**——
不需要在 Controller 裡再檢查一次。

### `@ExceptionHandler` 的挑選規則

**不是看檔案裡的順序，是看繼承距離——最接近的那個贏。**

```java
@ExceptionHandler(Exception.class)              // 萬用的
@ExceptionHandler(SourceNotFoundException.class) // 精確的 ← 這個會被選中
```

> ⚠️ Day 7 的 bug 就出在這裡：只有萬用的那個，
> 結果 Spring 內建的 `NoResourceFoundException`（404）被吞成了 500。

---

## 3. 資料庫 / JPA

| 註解 | 用途 |
|---|---|
| `@Entity` | 這個類別對應資料庫的一張表 |
| `@Table(name = "fetch_job")` | 指定表名。不寫的話 Hibernate 依類別名稱猜 |
| `@Id` | 主鍵 |
| `@GeneratedValue(strategy = IDENTITY)` | id 由資料庫產生（對應 `BIGSERIAL`） |
| `@Column(name = "user_id", nullable = false)` | 對應哪個欄位、有什麼限制 |
| `@Enumerated(EnumType.STRING)` | enum 存成字串 |
| `@Version` | optimistic lock 用的版本號 |
| `@Generated` | 這個值由**資料庫**產生，Java 這邊只讀 |
| `@Query` | 自己寫查詢（JPQL） |
| `@Modifying` | 這個 `@Query` 是寫入不是查詢 |
| `@Param` | 把方法參數綁到查詢裡的 `:name` |

### `@Enumerated` 一定要寫 `STRING`

```java
@Enumerated(EnumType.STRING)      // 存 "RUNNING"
private FetchStatus status;

// 不寫的話預設是 ORDINAL，存的是「第幾個」：0, 1, 2, 3
```

**為什麼 `ORDINAL` 很危險**：哪天有人在 enum 中間插一個新值，
所有既有資料的意義就全部平移了。**資料庫裡的 `1` 昨天是 RUNNING，今天變成 SUCCESS。**

### `@Generated` 解決什麼

```java
@Column(name = "updated_at", insertable = false, updatable = false)
@Generated(event = {EventType.INSERT, EventType.UPDATE})
private Instant updatedAt;
```

`updated_at` 是資料庫的 trigger 在維護的。
這組註解的意思是「**Java 不要寫這個欄位，但寫入後要把資料庫算出來的值讀回來**」。

### `@Modifying` 少了會怎樣

```java
@Modifying                                    // ← 少了這行
@Query("DELETE FROM RefreshToken rt WHERE ...")
int deleteExpired(...);
```

Spring 會把 `DELETE` 當成查詢執行，**啟動或執行時直接丟例外**。
好消息是它會爆，不是靜靜跑錯。

---

## 4. 交易

| 註解 | 用途 |
|---|---|
| `@Transactional` | 這個方法裡的資料庫操作是一個交易：正常結束就提交，丟例外就回滾 |
| `@Transactional(readOnly = true)` | 唯讀。Hibernate 可以跳過 dirty checking，省記憶體與 CPU |
| `@Transactional(noRollbackFor = X.class)` | 丟出 X 時**不要**回滾 |

### 三個一定要知道的規則

**① 只有 unchecked exception 會觸發回滾。**

```java
throw new RuntimeException();   // ✅ 回滾
throw new IOException();        // ❌ 不回滾（checked exception）
```

**② self-invocation 會讓它完全失效。**

```java
public void a() {
    this.b();       // ← 同一個類別內部呼叫，proxy 被跳過
}                   //   @Transactional 不生效，而且沒有任何錯誤訊息

@Transactional
public void b() { ... }
```

**解法**：把 `b()` 搬到另一個 bean。
本專案的 `FetchService` / `FetchJobService` 就是為了這個而拆開的。

**③ 交易裡不要做網路 I/O。**

一個 10 秒的 HTTP 請求會佔住一條資料庫連線 10 秒。
連線池只有 10 條，佔滿之後**所有使用者的 API 都會卡住**。

---

## 5. 驗證

| 註解 | 檢查什麼 |
|---|---|
| `@Valid` | 標在 Controller 參數上：**請對這個物件執行驗證**。少了它，下面全部不會生效 |
| `@NotBlank` | 不是 null、不是空字串、不是只有空白 |
| `@NotNull` | 不是 null（空字串可以） |
| `@Size(max = 200)` | 長度上限 |
| `@Email` | 是不是 email 格式 |
| `@Pattern(regexp = "...")` | 符合正規表示式 |
| `@PositiveOrZero` | ≥ 0 |

### `@Valid` 是開關

```java
public ResponseEntity<?> create(@Valid @RequestBody CreateSourceRequest request)
//                              ↑ 少了這個，DTO 上所有的 @NotBlank @Size 全部不執行
```

**而且不會有錯誤訊息。** 你以為有驗證，其實沒有。

### 本專案的一個實例

```java
@Pattern(regexp = "^https?://.+", message = "網址必須以 http:// 或 https:// 開頭")
String url
```

擋掉 `file:///etc/passwd`（SSRF）。
⚠️ 但它**只檢查協定，不檢查目的地**——`http://169.254.169.254/` 一樣會通過。
真正的目的地檢查在 `FetchClient.assertNotInternalAddress()`。

---

## 6. 設定

| 註解 | 用途 |
|---|---|
| `@ConfigurationProperties(prefix = "sift.jwt")` | 把 yml 裡那一整段對應成一個 Java 物件 |
| `@ConfigurationPropertiesScan` | 去找有 `@ConfigurationProperties` 的類別 |
| `@Value("${sift.xxx}")` | 讀單一個設定值 |

### `@ConfigurationProperties` vs `@Value`

```java
// @Value：一個一個讀，字串打錯要執行到那行才知道
@Value("${sift.jwt.secret}") private String secret;

// @ConfigurationProperties：整段對應成物件，型別安全
@ConfigurationProperties(prefix = "sift.jwt")
public class JwtProperties {
    private String secret;
    private int accessTokenTtlMinutes;
}
```

**超過兩三個值就該用 `@ConfigurationProperties`。**

---

## 7. 排程

| 註解 | 用途 |
|---|---|
| `@EnableScheduling` | **總開關。少了它 `@Scheduled` 完全不生效，而且沒有警告** |
| `@Scheduled(fixedDelay = N)` | 上次**結束**後隔 N 毫秒再跑 |
| `@Scheduled(fixedRate = N)` | 每 N 毫秒**開始**一次（不管上次跑完沒） |
| `@Scheduled(cron = "0 0 3 * * *")` | 在特定時刻執行 |

### `fixedDelay` vs `fixedRate`

假設間隔 5 秒，但那件事要做 8 秒：

```
fixedRate = 5s
  0s  開始 ━━━━━━━━━━━> 8s
  5s      開始 ━━━━━━━━━━━>      ← 前一個還沒完就又開一個
  → 愈積愈多，執行緒被吃光

fixedDelay = 5s
  0s  開始 ━━━━━━━━━━━> 8s 結束
                        等 5 秒 → 13s 才開始下一次
  → 永遠不重疊
```

**耗時不可控的工作（例如連外網）一律用 `fixedDelay`。**

### cron 是六個欄位

```
秒 分 時 日 月 星期
0  0  3  *  *  *     → 每天 03:00:00
```

⚠️ Linux 的 cron 是五個欄位。照抄會在啟動時失敗。

---

## 8. 條件式建立

| 註解 | 用途 |
|---|---|
| `@ConditionalOnProperty` | 設定檔的某個值符合，才建立這個 bean |
| `@ConditionalOnMissingBean` | **只有在你沒自己提供時**才建立 |

### 這就是 Spring Boot 的核心機制

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sift.scheduling.enabled",
                       havingValue = "true", matchIfMissing = true)
public class SchedulingConfig { }
```

| 設定值 | 結果 |
|---|---|
| `true` | 建立 |
| `false` | **當作這個類別不存在** |
| 沒寫 | 建立（`matchIfMissing = true`） |

**Spring Boot 的 auto-configuration 全部是用同一套機制寫的。**
`SecurityConfig` 能覆蓋掉 Spring 的預設安全設定，就是因為它們的預設值上面掛著
`@ConditionalOnMissingBean`。

---

## 9. 應用程式進入點

```java
@SpringBootApplication   // = 底下這三個的組合
```

| 拆開來是 | 做什麼 |
|---|---|
| `@SpringBootConfiguration` | 這是一個設定類別 |
| `@EnableAutoConfiguration` | **依 classpath 上有什麼，自動組裝** |
| `@ComponentScan` | 掃描這個 package 底下所有的 `@Component` / `@Service` / … |

**中間那個就是 Spring Boot 之所以是 Spring Boot 的原因。**

啟動時加 `--debug` 會印出完整報告：哪些 auto-configuration 被啟用、哪些沒有、為什麼。

---

## 10. 測試

| 註解 | 用途 |
|---|---|
| `@Test` | 這是一個測試 |
| `@DisplayName("...")` | 測試報告上顯示的名稱，可以用中文 |
| `@BeforeEach` | 每一題執行**前**都先跑一次（通常用來準備共同的東西） |
| `@AfterEach` | 每一題執行**後**都跑一次（通常用來收拾，例如關掉測試用的伺服器） |
| `@SpringBootTest` | **啟動整個 Spring**（有這個就是 integration test） |
| `@AutoConfigureMockMvc` | 提供 `MockMvc`：模擬 HTTP 請求但不開真的 port |
| `@ActiveProfiles("test")` | 啟用 test profile，載入 `application-test.yml` |
| `@ExtendWith(MockitoExtension.class)` | 啟用 Mockito（不啟動 Spring 時用） |
| `@Mock` | 產生一個假的物件 |
| `@InjectMocks` | 把上面那些假物件塞進要測的類別 |
| `@MockitoBean` | **在 Spring 的容器裡，把某個真的 bean 換成假的**（Day 16 新增） |

### `@Mock` 和 `@MockitoBean` 的差別

兩個都是「做一個假的」，差在**誰會拿到那個假的**。

| | `@Mock` | `@MockitoBean` |
|---|---|---|
| 有沒有 Spring | 沒有 | **有** |
| 誰拿到假的 | 只有你手動塞進去的那個物件 | **整個 Spring 容器裡，所有需要它的地方** |
| 搭配 | `@ExtendWith(MockitoExtension.class)` | `@SpringBootTest` |
| 本專案 | `UserServiceTest` | `SourceFlowIntegrationTest` |

`SourceFlowIntegrationTest` 用 `@MockitoBean` 的理由：
`SourceService` 是 Spring 建立並注入 `FeedResolver` 的，
我們沒有機會手動塞東西進去。`@MockitoBean` 是去**換掉容器裡那一個**，
所以 `SourceService` 拿到的自然就是假的。

> ⚠️ `@MockitoBean` 是 Spring Boot 3.4 起的寫法，
> 取代舊的 `@MockBean`（已 deprecated）。網路上的舊教學大多還是 `@MockBean`。

### 怎麼一眼分辨 unit / integration test

**看有沒有 `@SpringBootTest`。**

| | unit test | integration test |
|---|---|---|
| 啟動 Spring | ❌ | ✅ |
| 連資料庫 | ❌ | ✅ |
| 速度 | 幾十毫秒 | 幾百毫秒～幾秒 |
| 本專案 | `FetchJobTest` `FeedParserTest` `UserServiceTest` | 其餘四個 |

---

## 11. 純 Java，不是 Spring 的

| 註解 | 用途 |
|---|---|
| `@Override` | 這個方法覆寫父類別的。**寫錯方法名時編譯就會失敗**，是免費的保險 |

---

## 附錄：最容易踩的五個「少了就靜靜失效」

| 少了什麼 | 後果 | 會不會有錯誤訊息 |
|---|---|---|
| `@EnableScheduling` | 排程永遠不執行 | ❌ 完全沒有 |
| `@Valid` | 所有欄位驗證都不執行 | ❌ 完全沒有 |
| `@Transactional`（因 self-invocation） | 失敗不回滾 | ❌ 完全沒有 |
| `@Enumerated(STRING)` | 存成數字，日後改 enum 順序資料全錯 | ❌ 當下沒有 |
| `@Modifying` | `UPDATE`/`DELETE` 無法執行 | ✅ **會爆**（唯一會爆的） |

> **前四個的共同點：程式跑得動、測試可能還會過，只有在特定情況下才會現形。**
