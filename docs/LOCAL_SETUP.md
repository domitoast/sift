# 本機環境設定

新加入的人（包含三個月後的你自己）照這份文件走，應該五分鐘內能把專案跑起來。

---

## 前置需求

| 項目 | 版本 | 確認指令 |
|---|---|---|
| JDK | 21 | `java -version` |
| Docker Desktop | 任意近期版本 | `docker --version` |
| IntelliJ IDEA | 任意版本 | — |

---

## 步驟 1：建立 `.env`

專案根目錄有一份 `.env.example`，複製並改名：

```powershell
Copy-Item .env.example .env
```

然後編輯 `.env`，把 `SIFT_JWT_SECRET` 換成自己產生的隨機值。

**PowerShell 產生方式**：

```powershell
$bytes = New-Object byte[] 64
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

> ⚠️ `.env` 已列入 `.gitignore`，不會被提交。**永遠不要移除那條忽略規則。**

---

## 步驟 2：啟動資料庫

```powershell
docker compose up -d
docker compose ps        # STATUS 要顯示 (healthy)
```

`docker compose` 會自動讀取同目錄的 `.env`，不需要額外設定。

若 `.env` 缺少任何必要變數，啟動會直接失敗並指出缺哪一個——**這是刻意的**，
避免用預設弱密碼默默跑起來。

---

## 步驟 3：設定 IntelliJ 的環境變數

Spring Boot 應用程式讀不到 `.env`（那是 Docker 的機制，不是 Java 的），
因此需要在 IntelliJ 的執行設定中告訴它。

**操作路徑**：

1. 右上角執行設定下拉選單 → `Edit Configurations...`
2. 左側選 `SiftApplication`
3. 找到 `Environment variables` 欄位（若沒看到，點 `Modify options` → 勾選 `Environment variables`）
4. 貼上以下內容（**用分號分隔，不要換行**）：

```
SIFT_DB_PASSWORD=local_dev_only_change_me;SIFT_JWT_SECRET=你的金鑰
```

5. `Apply` → `OK`

**驗證方式**：啟動應用程式。若環境變數沒設好，
啟動會失敗並顯示 `Could not resolve placeholder 'SIFT_DB_PASSWORD'`。

> **為什麼不用 `.env` 自動載入？**
> 那需要額外的 IntelliJ 外掛（EnvFile）或第三方函式庫。
> 手動設定一次即可，而且**「知道怎麼設執行環境變數」是實務上一定會用到的技能**——
> 部署到任何平台時，設定方式都是同一個概念。

---

## 步驟 4：啟動與驗證

1. 執行 `SiftApplication`
2. 瀏覽器開啟 http://localhost:8080/actuator/health
3. 應顯示 `{"status":"UP", ..., "db":{"status":"UP"}}`

---

## 執行測試

測試**不需要**設定環境變數——`src/test/resources/application.yml`
提供了測試專用的假值。

但測試目前仍需要 Docker 的 PostgreSQL 在執行中。

```powershell
docker compose up -d
```

然後在 IntelliJ 右鍵 `src/test/java` → `Run 'All Tests'`。

> **已知技術債**：整合測試依賴外部的 Docker 環境。
> Day 18 會改用 Testcontainers，由測試自行啟動與銷毀資料庫容器。

---

## 常見問題

**啟動時出現 `Could not resolve placeholder 'SIFT_JWT_SECRET'`**
→ IntelliJ 的 Environment variables 沒設，回到步驟 3。

**`docker compose up` 說 `SIFT_DB_PASSWORD 未設定`**
→ `.env` 不存在或缺少該變數，回到步驟 1。

**啟動時出現 `Connection refused`**
→ 資料庫沒在跑，執行 `docker compose up -d`。

**port 8080 已被占用**
→ 在 `application.yml` 最上方加入：
```yaml
server:
  port: 8081
```

---

## 完整重置（資料全清）

```powershell
docker compose down -v      # -v 連同資料一起刪除
docker compose up -d
```

重新啟動應用程式後，Flyway 會重新建立所有資料表。
