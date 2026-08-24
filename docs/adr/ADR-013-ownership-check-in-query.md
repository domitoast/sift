# ADR-013: 權限檢查寫進查詢條件，而非查詢之後比對

## 狀態

已接受（Day 9）。**本文件記錄了一次意見分歧與其取捨。**

## 背景

多數 API 需要確保「使用者只能存取自己的資料」。實作方式有兩種：

```java
// 寫法一：查出來，再比對
Document doc = repository.findById(id).orElseThrow();
if (!doc.getUserId().equals(currentUserId)) {
    throw new DocumentNotFoundException();
}

// 寫法二：查詢條件即含擁有者
Document doc = repository
        .findByIdAndUserIdAndDeletedAtIsNull(id, currentUserId)
        .orElseThrow(DocumentNotFoundException::new);
```

此決定必須在寫第一支需要權限的 API 時定案，因為後續會被複製到每一支。

## 考慮過的選項

### 寫法一：查詢後比對

- 優點：**權限檢查是「看得見」的**。逐行讀程式碼就知道這裡有做檢查
- 優點：方法名稱短，語意直觀
- 缺點：**忘記寫那個 if，就是一個漏洞，而且完全沒有錯誤徵兆**
- 缺點：每新增一支 API 都要記得複製那段判斷
- 缺點：需要另外決定回 403 還是 404

### 寫法二：權限寫進查詢條件（採用）

- 優點：**Repository 不提供「只用 id 查」的方法，因此不可能忘記帶擁有者**
- 優點：查不到 = 不存在 = 不是你的 = 已刪除，四種情況自然收斂成 404，
  不需要額外判斷，也不洩漏資源是否存在（ADR-006）
- 優點：`soft delete` 的過濾在同一個條件裡順便完成
- 缺點：**方法名稱很長**（`findByIdAndUserIdAndDeletedAtIsNull`），
  要逐字讀完才知道它做了權限檢查——**意圖是「藏起來」的**
- 缺點：對不熟悉 Spring Data 命名規則的人，可讀性較差

## 決定

採用寫法二，**並在 Service 層加一行註解把意圖拉回檯面**：

```java
// 查詢條件已含 userId：查不到 = 不存在，或不是這個人的。兩者不區分。
Document document = documentRepository
        .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
        .orElseThrow(DocumentNotFoundException::new);
```

## 理由

**使用者曾對此提出異議，認為寫法一可讀性較高。這個論點成立**——
寫法二確實把權限檢查藏進了一個長方法名裡。

最終仍選擇寫法二，理由是：

**一、失敗模式的嚴重度不對等。**
寫法一忘記檢查 → IDOR 漏洞，OWASP 排名第一，且完全無徵兆。
寫法二的失敗模式只是「方法名很長」。

**二、Repository 不提供不安全的方法，就沒有人能寫出不安全的呼叫。**
這與 `UserResponse` 只列出允許外洩的欄位是同一個原則：
**預設安全，而非記得要安全。**

**三、對「讀別人程式碼」的訓練價值更高。**
使用者的目標包含 agent 協作與程式碼審查。
習慣「權限應該在查詢條件裡」之後，看到
`repository.findById(id)` 這種寫法會立刻警覺——
而那正是 AI 生成的程式碼最常出現的漏洞形態。

## 後果

### 好處

- 新增 API 時無法「忘記」加權限檢查
- 404/403 的選擇不必每次重新判斷
- soft delete 一併處理

### 代價

- **可讀性確實下降**，以一行註解補償
- Repository 的方法名稱會隨條件增加而變長。
  超過三個條件時應改用 `@Query` 並自行命名

### 重新評估的時機

- 查詢條件超過三個，方法名稱失控時
- 出現「同一份資料有多種存取權限」的需求
  （例如共享文件、唯讀協作者）——屆時單一的 `userId` 條件不再足夠，
  需要獨立的授權層
