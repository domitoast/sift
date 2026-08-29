# ADR-018: 排程暫不使用 distributed lock

## 狀態

已接受（Day 15）

## 背景

Day 15 加入 `@Scheduled` 定時抓取。

多實例部署時，每個實例都會各自執行自己的排程，彼此不知道對方存在：

```
機器 A  @Scheduled 每小時抓一次
機器 B  @Scheduled 每小時抓一次
        ↓
     每小時抓兩次
```

十個實例就抓十次。Day 18 加上 LLM 摘要之後，重複抓取直接等於重複付費——
同一份內容付十倍的錢。

這是排程系統的經典問題，標準解法是 distributed lock。

## 考慮過的選項

### 選項 A：導入 ShedLock

在共用資料庫建一張 `shedlock` 表，執行前先搶鎖：

```java
@Scheduled(fixedDelayString = "...")
@SchedulerLock(name = "fetchAll", lockAtMostFor = "10m")
public void fetchAll() { ... }
```

- 優點：整輪排程只有一個實例會執行，語意清楚
- 優點：學習價值高，是業界具名工具
- 缺點：多一個依賴、一張表、一份 migration，約 40 分鐘
- 缺點：`lockAtMostFor` 是新的可調參數——設太短會兩台同時跑，
  設太長則實例當機後要等很久才能恢復

### 選項 B：不導入，依賴既有的 unique index（採用）

V5 已經有：

```sql
CREATE UNIQUE INDEX uq_fetch_job_active
    ON fetch_job (source_id)
    WHERE status IN ('PENDING', 'RUNNING');
```

兩個實例同時跑時的實際行為：

```
機器 A：INSERT fetch_job (source_id=1, RUNNING)  → 成功
機器 B：INSERT fetch_job (source_id=1, RUNNING)  → 撞到唯一約束
        → FetchJobService 的 catch 接住
        → log「這個來源已有進行中的任務」→ 跳過
```

- 優點：**不會重複抓取，也不會重複付費**——想防的事已經被防住了
- 優點：零新增依賴
- 缺點：每輪每個來源都會多一次白費的 INSERT 嘗試
- 缺點：log 會出現 warn 噪音

## 決定

採用選項 B。

## 理由

### 一、想防的事已經被防住了

ShedLock 防的是「整輪重複執行」，`uq_fetch_job_active` 防的是「同一個來源
重複建立任務」。**後者才是真正會造成傷害的那一層。**

前者被繞過的代價只是幾次白費的資料庫往返，不是重複抓取。

### 二、本專案不會有第二個實例

雲端部署已於 Day 7.5 依停損順序砍除。這個專案在可預見的範圍內
只會跑一個實例，因此選項 A 解決的是一個不存在的問題。

### 三、另一個排程本身就是 idempotent

Day 15 同時加入的 refresh token 清除排程是
`DELETE FROM refresh_token WHERE expires_at < now()`。
兩個實例同時執行的結果與一個實例相同——刪掉已經被刪掉的列沒有任何影響。

## 後果

### 好處

- 不增加依賴、資料表與可調參數
- 保護機制集中在資料庫約束，與 `uq_app_user_email`、`uq_source_user_url`
  是同一個模式，全專案一致

### 代價

- **多實例時每輪會有 N 次白費的 INSERT 嘗試**（N = 來源數）。
  資料量小時可忽略，來源數上千時就是每輪上千次無效寫入
- 那些失敗會產生 warn 等級的 log，看起來像有問題，實際上是正常運作
- 放棄了一次實作 distributed lock 的經驗

### 重新評估的時機

**任一條成立就要重新考慮選項 A：**

1. **部署超過一個實例**
2. **新增任何一個「重複執行會造成傷害」的排程**——
   例如寄送通知信、扣款、呼叫收費的外部 API。
   這類工作沒有 unique index 可以依靠，`DELETE` 那種 idempotent 的性質也不成立
3. 來源數超過 1000，白費的 INSERT 開始有量級上的意義

> 第 2 點是最容易被忽略的：**現在安全，是因為現有的兩個排程剛好都有保護。
> 下一個排程可能沒有。加新排程時要回來讀這一段。**
