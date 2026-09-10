# 空手日 #3 — 讓使用者看得到抓下來的文章

**日期**：Day 21
**預估**：100 分鐘
**規則**：**不使用 AI。** 不問 Claude、不用 Copilot、不用 ChatGPT。

---

## 可以做的事

- ✅ **打開你自己專案裡任何既有的程式碼來看、來模仿**
- ✅ 查 Spring / JPA 官方文件、Google
- ✅ 用 `Invoke-RestMethod` 試

## 不可以做的事

- ❌ 問 AI「這段怎麼寫」
- ❌ 讓 AI 產生任何一行程式碼

> **卡住是預期中的，不是失敗。**
> **記在本檔最下方的「卡住紀錄」。** 那份紀錄比完成度更有價值。

---

## 現況：一個很明顯的洞

你的系統現在會抓文章、去重、產生摘要，全部存進 `fetched_item`。

**但沒有任何一支 API 讀得到它們。**

```
使用者：我訂閱了 Hacker News，然後呢？
系統  ：（資料庫裡有 30 篇文章和摘要，但你看不到）
```

`GET /sources/{id}/fetch-jobs` 只回傳「抓取成功了幾次」，
回不了「抓到了什麼」。

---

## 必做（這是及格線）

**做出 `GET /api/v1/sources/{id}/items`**，列出某個來源抓到的文章。

1. 只能看自己的來源。**看別人的 → 404**
2. 新的文章排前面
3. 有數量上限
4. **寫測試**

---

## 你可以照抄的範本

**`GET /sources/{id}/fetch-jobs` 跟你要做的幾乎一模一樣。**
它前天才寫好，打開這四個地方看：

| 檔案 | 看什麼 |
|---|---|
| `source/SourceController.java` 的 `fetchJobs()` | 路徑怎麼寫、`limit` 怎麼夾範圍 |
| `source/SourceService.java` 的 `findFetchJobs()` | **權限為什麼要分兩步查** |
| `fetch/FetchJobRepository.java` 的 `findBySourceIdOrderByCreatedAtDesc` | 方法名字怎麼取、`Limit` 怎麼用 |
| `fetch/dto/FetchJobResponse.java` | DTO 長什麼樣、`from()` 怎麼寫 |

**你要寫的是同一組東西，只是把「抓取紀錄」換成「文章」。**

---

## 你必須自己決定的三件事

**寫下理由，不只是寫下答案。**

### 決定 1：`raw_content` 要不要回傳？

`fetched_item` 有一個 `raw_content` 欄位，存的是文章原始內文，
可能好幾 KB。

列表要不要把它一起回傳？

> 提示：回去看 `document/DocumentSummary.java`，還有 Day 9 為什麼會生出那個東西。

**我的決定與理由**：

```
（寫在這裡）
```

### 決定 2：`status` 是 `FAILED` 的文章，要不要出現在列表裡？

有些文章摘要失敗了（API key 無效、內容為空）。
它們該不該被使用者看到？

> 提示：想想使用者看到「一篇沒有摘要的文章」時會怎麼想。
> 再想想他看不到它時會怎麼想。

**我的決定與理由**：

```
（寫在這裡）
```

### 決定 3：路徑為什麼是 `/sources/{id}/items` 而不是 `/items?sourceId=x`？

> 提示：`SourceController.fetchJobs()` 的 Javadoc 裡寫了同一個問題的答案。

**我的決定與理由**：

```
（寫在這裡）
```

---

## 測試怎麼寫（這次給你骨架）

**Day 14 你的程式碼寫對了，但沒寫測試。這次先看骨架再動手。**

在 `SourceFlowIntegrationTest` 裡加，**至少三題**：

```java
@Test
@DisplayName("16. 列出某個來源的文章")
void items_shouldReturnArticles() throws Exception {
    // 準備：建一個來源
    // 執行：GET /api/v1/sources/{id}/items
    // 檢查：狀態 200
}

@Test
@DisplayName("17. ★ 看別人的來源的文章 → 404")
void items_otherUsersSource_shouldReturnNotFound() throws Exception {
    // 準備：用 ownerToken 建一個來源
    // 執行：用 otherToken 去讀它
    // 檢查：404
}

@Test
@DisplayName("18. 剛建立的來源還沒抓過 → 空陣列，不是 404")
void items_newSource_shouldReturnEmptyList() throws Exception {
    // 「來源存在但沒有文章」和「來源不存在」是兩件事
}
```

**第 17 題可以直接照抄第 14 題（`fetchJobs_otherUsersSource_shouldReturnNotFound`），
把路徑換掉就好。**

---

## 驗收標準

- [ ] `GET /api/v1/sources/{id}/items` 回 200 與一個陣列
- [ ] 用別人的 token 讀 → **404**
- [ ] 沒有文章時回空陣列（`[]`），**不是 404**
- [ ] 有數量上限，`?limit=999999` 不會回傳全部
- [ ] `.\mvnw.cmd test` 全綠，**162 題一題都不能紅**
- [ ] **至少三題新測試**
- [ ] 三個決定都寫下理由

---

## 加分題（做不完不扣分）

回傳的欄位裡要不要有 `failureReason`？

如果一篇文章摘要失敗了，使用者看到失敗原因有沒有幫助？
還是那是內部資訊，只會讓他困惑？

**我的決定與理由**：

```
（寫在這裡）
```

---

## 卡住紀錄

**每次卡超過 10 分鐘就記一筆。**

```
[時間] 卡在哪 → 我試了什麼 → 有沒有解決
```

**Day 14 這一欄是空的。這次不要。**
**卡住的內容決定我明天要重講哪個部分。**

紀錄：

```
（寫在這裡）
```

---

## 完成後

**不要先問我對不對。** 先自己回答：

- 我能不能逐行解釋我寫的每一行？
- **把 Service 裡的權限檢查那一行註解掉，第 17 題會不會紅？**（去實際試一次）

然後才叫我來 review。
