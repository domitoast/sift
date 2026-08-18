# ER Diagram

**繪製工具**：Mermaid（可直接在 GitHub、VS Code 中渲染）
**最後更新**：Day 3

---

## 完整關聯圖

```mermaid
erDiagram
    APP_USER ||--o{ SOURCE : "訂閱"
    APP_USER ||--o{ DOCUMENT : "擁有"
    APP_USER ||--o{ TAG : "建立"

    SOURCE ||--o{ FETCH_JOB : "被排程抓取"
    SOURCE ||--o{ FETCHED_ITEM : "產出"

    FETCH_JOB ||--o{ FETCHED_ITEM : "本次抓到"

    FETCHED_ITEM |o--o| DOCUMENT : "promote 為"

    DOCUMENT ||--o{ DOCUMENT_VERSION : "歷史版本"
    DOCUMENT ||--o{ DOCUMENT_TAG : ""
    TAG ||--o{ DOCUMENT_TAG : ""

    APP_USER {
        bigserial id PK
        varchar email UK "唯一"
        varchar password_hash "BCrypt，60 字元"
        text llm_api_key_encrypted "加密，可為空"
        int daily_llm_call_count
        date daily_llm_count_date
        timestamptz deleted_at "soft delete"
        timestamptz created_at
        timestamptz updated_at
    }

    SOURCE {
        bigserial id PK
        bigint user_id FK
        varchar name
        varchar url "與 user_id 組成唯一（未刪除者）"
        varchar type "RSS / ATOM"
        boolean enabled
        int fetch_interval_minutes
        timestamptz last_success_at
        int consecutive_failure_count
        timestamptz deleted_at "soft delete"
        timestamptz created_at
        timestamptz updated_at
    }

    FETCH_JOB {
        bigserial id PK
        bigint source_id FK
        varchar status "PENDING/RUNNING/SUCCESS/FAILED"
        timestamptz started_at
        timestamptz finished_at
        int retry_count
        timestamptz next_retry_at
        varchar failure_type "TRANSIENT / PERMANENT"
        text failure_reason
        int item_count
        timestamptz created_at
        timestamptz updated_at
    }

    FETCHED_ITEM {
        bigserial id PK
        bigint source_id FK
        bigint fetch_job_id FK
        varchar external_url
        char content_hash "SHA-256，與 source_id 組成唯一"
        varchar title
        text raw_content "30 天後清空"
        timestamptz published_at
        varchar status "NEW/SUMMARIZING/READY/PROMOTED/FAILED/DISCARDED"
        int retry_count
        timestamptz next_retry_at
        varchar failure_type
        text failure_reason
        text summary
        timestamptz summarized_at
        timestamptz promoted_at
        timestamptz created_at
        timestamptz updated_at
    }

    DOCUMENT {
        bigserial id PK
        bigint user_id FK
        varchar origin "MANUAL / FETCHED"
        bigint fetched_item_id FK "唯一，可為空"
        varchar title
        text content "Markdown"
        text note "使用者對抓取內容的註解"
        bigint version "optimistic lock 用"
        timestamptz deleted_at "soft delete"
        timestamptz created_at
        timestamptz updated_at
    }

    DOCUMENT_VERSION {
        bigserial id PK
        bigint document_id FK
        int version_number "與 document_id 組成唯一"
        varchar title
        text content
        timestamptz created_at
        timestamptz updated_at
    }

    TAG {
        bigserial id PK
        bigint user_id FK
        varchar name "與 user_id 組成唯一"
        timestamptz created_at
        timestamptz updated_at
    }

    DOCUMENT_TAG {
        bigint document_id PK_FK
        bigint tag_id PK_FK
    }
```

---

## 關聯基數說明

Mermaid ER 圖的符號讀法：

| 符號 | 意義 |
|---|---|
| `\|\|` | 恰好一個（one and only one） |
| `o{` | 零或多個（zero or more） |
| `\|o` | 零或一個（zero or one） |

逐條說明：

| 關聯 | 基數 | 白話 |
|---|---|---|
| `APP_USER → SOURCE` | 1 對多 | 一個使用者可訂閱多個來源；每個來源只屬於一個使用者 |
| `SOURCE → FETCH_JOB` | 1 對多 | 一個來源會被排程抓取很多次，每次一筆紀錄 |
| `SOURCE → FETCHED_ITEM` | 1 對多 | 一個來源會產出很多篇文章 |
| `FETCH_JOB → FETCHED_ITEM` | 1 對多 | 一次抓取可能抓到 0 到 N 篇 |
| `FETCHED_ITEM → DOCUMENT` | **0或1 對 0或1** | 一筆 staging 項目最多 promote 成一份文件；<br>手寫文件則沒有對應的 staging 項目 |
| `DOCUMENT → DOCUMENT_VERSION` | 1 對多 | 一份文件有多個歷史版本 |
| `DOCUMENT ↔ TAG` | 多對多 | 透過 `DOCUMENT_TAG` 中介表 |

---

## 兩層架構在圖上的位置

```
┌──────────────── Pipeline（staging：可能失敗、可能重複）────────────────┐
│                                                                        │
│   SOURCE  ──→  FETCH_JOB  ──→  FETCHED_ITEM                            │
│                                     │                                  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │ promote（僅 READY 狀態可晉升）
                                      ▼
┌──────────────── Knowledge Base（curated：一律是成品）─────────────────┐
│                                                                        │
│   DOCUMENT  ──→  DOCUMENT_VERSION                                      │
│      │                                                                 │
│      └──→  DOCUMENT_TAG  ←──  TAG                                      │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

`FETCHED_ITEM → DOCUMENT` 這條線是整張圖唯一的跨層連結，
也是 ADR-002 決策的具體體現。

---

## 為什麼 FETCHED_ITEM 同時連到 SOURCE 和 FETCH_JOB

乍看之下 `source_id` 是多餘的——透過 `fetch_job_id` 就能查到 source。

保留它的理由是**查詢效率**。

「列出某個來源的所有文章」是最常見的查詢。
若只有 `fetch_job_id`，每次都必須 join `fetch_job` 才能過濾來源。
直接保留 `source_id` 可省下這個 join。

這是**刻意的反正規化（denormalization）**——
用一點冗餘換取查詢速度，是經過權衡後的選擇，而非疏漏。

代價是：若某筆 `fetched_item` 的 `source_id` 與其
`fetch_job.source_id` 不一致，資料就矛盾了。
因此寫入時必須由 Service 保證兩者一致。
