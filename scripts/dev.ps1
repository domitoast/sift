# =============================================================================
# 一個指令把整個開發環境叫起來：資料庫 + 後端 + 前端。
#
# 用法（在專案根目錄）：
#   .\scripts\dev.ps1
#
# 停止：回到這個視窗按 Enter。
#
# -----------------------------------------------------------------------------
# 【為什麼需要這個檔案】
#
# 在這之前，每次開工要做四件事：
#
#   1. docker compose up -d
#   2. 等資料庫真的起來（不等的話 Flyway 會連線失敗）
#   3. 開一個視窗跑 .\mvnw.cmd spring-boot:run
#   4. 再開一個視窗 cd web 然後 npm run dev
#
# 而且第 3 步在 PowerShell 裡直接跑會失敗——因為 .env 裡的變數
# 只有 docker compose 會自動讀，Spring Boot 不會。
# 所以你一直是靠 IntelliJ 的執行設定在跑後端。
#
# 那代表一件事：<b>這個專案的「怎麼跑起來」只存在你的 IDE 設定裡，
# 沒有進版控。</b>換一台電腦、或是面試官 clone 下來，都跑不起來。
#
# 這個腳本把那份知識寫成檔案。
# =============================================================================

$ErrorActionPreference = 'Stop'

# $PSScriptRoot 是「這個腳本所在的資料夾」，不是「你執行時所在的資料夾」。
# 用它來定位專案根目錄，腳本就可以從任何地方呼叫。
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root '.env'

# -----------------------------------------------------------------------------
# 1. 把 .env 讀進這個 process 的環境變數
# -----------------------------------------------------------------------------
#
# 【為什麼要自己讀】
#
# docker compose 會自動讀同目錄的 .env，那是 compose 自己的功能。
# 但 mvnw / java / node 完全不知道 .env 這種東西存在——
# 它們只看「作業系統交給我的環境變數」。
#
# 【為什麼設在這裡就夠了】
#
# Windows 的子行程會繼承父行程的環境變數。
# 這個腳本設好之後，下面 Start-Process 開出去的後端視窗就自動有了，
# 不需要在每個視窗各設一次。
#
# ⚠️ 這些變數只活在這次執行裡，關掉視窗就消失。
#    這是好事——不會污染你整台電腦的環境。

if (-not (Test-Path $envFile)) {
    Write-Host "找不到 .env" -ForegroundColor Red
    Write-Host "先跑：Copy-Item .env.example .env，然後填好金鑰" -ForegroundColor Yellow
    exit 1
}

Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()

    # 跳過空行與註解
    if ($line -eq '' -or $line.StartsWith('#')) { return }

    # 只切第一個 = ——值裡面本來就可能有 =（Base64 的結尾就常常是）
    $i = $line.IndexOf('=')
    if ($i -lt 1) { return }

    $key = $line.Substring(0, $i).Trim()
    $value = $line.Substring($i + 1).Trim().Trim('"').Trim("'")

    # Set-Item env:\NAME 是 PowerShell 設環境變數的寫法。
    # 不能寫 $env:$key = ... ——那是字串插值，不是動態變數名。
    Set-Item -Path "env:\$key" -Value $value
}

Write-Host "已載入 .env" -ForegroundColor DarkGray

# -----------------------------------------------------------------------------
# 2. 起資料庫，並且「等到它真的可以連線」
# -----------------------------------------------------------------------------
#
# 【為什麼不能 up -d 之後直接跑後端】
#
# docker compose up -d 回傳的意思是「容器啟動了」，
# 不是「PostgreSQL 準備好接受連線了」。
# 第一次啟動時 Postgres 要初始化資料目錄，大約要幾秒。
#
# 這中間如果 Spring Boot 就去連，Flyway 會連線失敗然後整個應用程式起不來。
# 你會看到一長串 stack trace，然後以為是程式壞了——實際上只是早了三秒。
#
# 【怎麼知道「真的好了」】
#
# docker-compose.yml 裡已經有 healthcheck（跑 pg_isready）。
# 這裡只要去問 Docker「那個檢查現在的結果是什麼」就好——
# 不用自己再實作一次判斷邏輯。
#
# <b>已經有人在檢查的事，去讀它的結論，不要自己再檢查一次。</b>

Write-Host "`n[1/3] 資料庫…" -ForegroundColor Cyan

Push-Location $root
try {
    # ⚠️ 只起 postgres，不要起整個 compose。
    #
    # docker-compose.yml 現在有兩個 service：postgres 和 app。
    # 不指定 service 的話會把容器化的 app 也叫起來，它佔住 8080，
    # 然後下面的 mvnw spring-boot:run 就會 port already in use。
    #
    # 開發模式要的是「資料庫在容器裡，程式在你機器上」。
    docker compose up -d postgres | Out-Null
} finally {
    Pop-Location
}

$deadline = (Get-Date).AddSeconds(60)
while ($true) {
    # --format 讓 docker 只吐出我們要的那個欄位，不用自己剖析一大包 JSON
    #
    # ⚠️ 容器還沒建好時 docker inspect 會以非 0 結束。
    #    PowerShell 7.4 之後，$ErrorActionPreference='Stop' 會把那個當成錯誤丟出來，
    #    所以這一句要單獨放寬——「查不到」在這裡是預期中的過渡狀態，不是失敗。
    $health = $null
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        $health = docker inspect --format '{{.State.Health.Status}}' sift-postgres 2>$null
    } finally {
        $ErrorActionPreference = 'Stop'
    }

    if ($health -eq 'healthy') { break }

    if ((Get-Date) -gt $deadline) {
        Write-Host "資料庫 60 秒還沒就緒（目前狀態：$health）" -ForegroundColor Red
        Write-Host "看日誌：docker compose logs postgres" -ForegroundColor Yellow
        exit 1
    }

    Start-Sleep -Milliseconds 500
}

Write-Host "      healthy" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 3. 前端的相依套件
# -----------------------------------------------------------------------------
#
# node_modules 不進版控（太大、而且跟平台有關），
# 所以 clone 下來的第一次一定要 npm install。
# 這裡自動判斷，省掉「為什麼 vite 找不到」這種第一次一定會踩的坑。

$web = Join-Path $root 'web'

if (-not (Test-Path (Join-Path $web 'node_modules'))) {
    Write-Host "`n[2/3] 第一次執行，安裝前端套件（會花一兩分鐘）…" -ForegroundColor Cyan
    Push-Location $web
    try {
        npm install
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n[2/3] 前端套件已安裝" -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# 4. 後端與前端，各開一個視窗
# -----------------------------------------------------------------------------
#
# 【為什麼是分開的視窗，不是全部塞在這一個】
#
# 兩個程序的輸出混在一起會很難讀：Spring 的日誌一次好幾行，
# Vite 的熱更新訊息一秒好幾則，交錯之後兩邊都看不懂。
#
# 分開之後，你要看誰的錯誤就看哪個視窗——這是最常做的事。
#
# 【-NoExit 是刻意的】
#
# 沒有它的話，程序一崩潰視窗就跟著關掉，你連錯誤訊息都來不及看。

Write-Host "`n[3/3] 啟動後端與前端…" -ForegroundColor Cyan

$backend = Start-Process powershell -PassThru -WorkingDirectory $root -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle='Sift 後端 :8080'; .\mvnw.cmd spring-boot:run"
)

$frontend = Start-Process powershell -PassThru -WorkingDirectory $web -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle='Sift 前端 :5173'; npm run dev"
)

# -----------------------------------------------------------------------------
# 5. 等後端真的起來再告訴你可以用了
# -----------------------------------------------------------------------------
#
# 同樣的道理：「行程啟動了」不等於「Spring Boot 準備好了」。
# 冷啟動加上 Flyway migration，大約 15～30 秒。
#
# 這裡打的是 /actuator/health——那支端點存在的意義就是回答這個問題。

Write-Host "      等後端就緒（約 20 秒）…" -ForegroundColor DarkGray

$deadline = (Get-Date).AddSeconds(120)
$ready = $false

while ((Get-Date) -lt $deadline) {
    try {
        # -UseBasicParsing 在舊版 PowerShell 上避免它去叫 IE 引擎剖析 HTML
        $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' `
                               -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        # 還沒起來就是連線被拒，這是預期中的，不是錯誤——所以吞掉繼續等
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
if ($ready) {
    Write-Host "  前端  http://localhost:5173" -ForegroundColor Green
    Write-Host "  後端  http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "  後端 120 秒內沒有回應，去「Sift 後端」視窗看錯誤訊息" -ForegroundColor Red
}

Write-Host "`n  資料庫留在背景（docker compose down 才會停）" -ForegroundColor DarkGray
Write-Host ""
Read-Host "按 Enter 停止前端與後端"

# -----------------------------------------------------------------------------
# 6. 收拾
# -----------------------------------------------------------------------------
#
# 【為什麼不是 Stop-Process】
#
# 我們啟動的是 powershell.exe，真正在跑的是它底下的 java.exe 和 node.exe。
# 只殺 powershell 的話，那兩個會變成孤兒繼續佔著 8080 和 5173，
# 下次啟動就會看到「port already in use」。
#
# taskkill /T 是「連同整棵子行程樹一起殺」，/F 是強制。
#
# <b>殺行程要殺整棵樹。只殺看得見的那個，留下來的才是麻煩。</b>

$ErrorActionPreference = 'SilentlyContinue'   # 視窗可能已被手動關掉，殺不到是正常的

foreach ($p in @($backend, $frontend)) {
    if ($p -and -not $p.HasExited) {
        taskkill /PID $p.Id /T /F 2>&1 | Out-Null
    }
}

Write-Host "已停止。資料庫還在跑——要一起停：docker compose down" -ForegroundColor Green
Write-Host "（若之前用 docker compose 跑過整套，app 容器可能還佔著 8080：docker compose stop app）" -ForegroundColor DarkGray
