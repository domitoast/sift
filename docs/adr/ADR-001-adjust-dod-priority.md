# ADR-001: 調整 Definition of Done 的優先層級

## 狀態

已接受（Day 1）

## 背景

`PROJECT_RULES.md` 原本的 DoD 分層如下：

- **Should（目標）**：雲端部署 + HTTPS、Prometheus + Grafana
- **Could（加分）**：LLM 每日摘要頁、部署自動化

開發者提出調整意向：希望把 LLM 摘要與自動化升為 Should，把雲端部署與監控降為 Could。

理由是這兩項更貼近專案的核心定位——內容管線才是深度戰場，
而監控與雲端部署屬於周邊的維運面，學習密度較低。

## 問題

直接對調會產生 **dependency inversion**：

「部署自動化（push 即部署）」被列為 Should，
但其前置條件「雲端部署（有伺服器可部署）」被列為 Could。

沒有部署目標，就不可能有自動部署。這個排序在動手時必定卡住。

## 考慮過的選項

### 選項 A：直接對調，不處理相依性

- 優點：最快，完全照開發者意向
- 缺點：排出一個做不到的順序，第 19 天才會發現

### 選項 B：維持原狀，拒絕調整

- 優點：無相依性問題
- 缺點：違背專案定位，把學習密度低的項目排在高的前面

### 選項 C：對調，但把 CD 拆成兩段（採用）

將 Continuous Delivery 拆解為：

1. **build 段**：push → CI 測試 → build Docker image → 推上 container registry
   （不需要伺服器）
2. **deploy 段**：registry → 伺服器 → health check
   （需要伺服器）

build 段列入 Should，deploy 段列入 Could。

- 優點：符合開發者意向，同時消除相依性問題；build 段本身已是完整的 CD 技能展示
- 缺點：無公開網址，面試無法直接給連結

## 決定

採用選項 C。

**Should**：LLM 每日摘要頁、CD build 段（image 自動建置並推上 registry）
**Could**：雲端部署 + HTTPS、Prometheus + Grafana、完整自動部署

## 後果

### 好處

- 資源集中在內容管線與 LLM 整合，與專案定位一致
- CD 能力仍然被展示，且不需要雲端花費
- 停損時砍掉的都是 Could，不影響 Should

### 代價

- 面試時無法提供公開網址
- 緩解方式：本機 `docker compose up` 現場 demo，或事先錄製 demo 影片

### 重新評估的時機

若在 Day 18 前進度超前，或取得免費雲端資源（Oracle Cloud Free Tier），
可將 deploy 段重新提升為 Should。
