# =============================================================================
# 清空內容資料（保留帳號與訂閱來源）。
#
# 用法（在專案根目錄）：
#   .\scripts\reset-data.ps1
#
# 它做的事只有一件：把 reset-data.sql 餵給容器裡的 psql。
# 真正的邏輯全部在那個 .sql 檔案裡——
#
# 【為什麼不把 SQL 直接寫在這個 .ps1 裡】
#
# 那樣會有兩份：一份給 Windows 用，將來還要一份給 Mac/Linux 的 .sh。
# 兩份一定會走樣。
#
# SQL 只有一份，包裝可以有很多層。
# =============================================================================

$ErrorActionPreference = 'Stop'

$container = 'sift-postgres'   # 與 docker-compose.yml 的 container_name 一致
$sqlFile = Join-Path $PSScriptRoot 'reset-data.sql'

# 先確認容器在跑。不確認的話 docker exec 的錯誤訊息很難懂
$running = docker ps --filter "name=$container" --format "{{.Names}}"
if (-not $running) {
    Write-Host "找不到執行中的容器 '$container'" -ForegroundColor Red
    Write-Host "先跑：docker compose up -d" -ForegroundColor Yellow
    exit 1
}

Write-Host "清空內容資料（保留帳號與訂閱來源）…" -ForegroundColor Cyan

Get-Content $sqlFile -Raw -Encoding UTF8 |
        docker exec -i $container psql -U sift -d sift

Write-Host "`n完成。重新整理前端即可。" -ForegroundColor Green
