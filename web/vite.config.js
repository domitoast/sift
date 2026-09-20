import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  /*
   * 開發時的代理設定。
   *
   * 【要解決的問題】
   *
   * 開發時有兩個伺服器同時在跑：
   *   localhost:5173  Vite（提供頁面，改檔案馬上更新）
   *   localhost:8080  Spring Boot（提供 API）
   *
   * 瀏覽器有一條規則叫 same-origin policy：
   * 從 5173 載入的頁面，預設不能去讀 8080 的東西——
   * 就算兩個都在你自己的電腦上也一樣，因為 port 不同就算不同來源。
   *
   * 【兩種解法】
   *
   * 一、在 Spring Boot 開 CORS，明確允許 5173 來存取。
   *     缺點是正式環境用不到那個設定，卻得一直留著它。
   *
   * 二、用 proxy（採用）。
   *     瀏覽器以為自己只在跟 5173 說話，Vite 在背後把 /api 的請求
   *     轉給 8080。對瀏覽器來說從頭到尾只有一個來源，沒有跨來源問題。
   *
   * 【正式環境不需要這個】
   *
   * build 之後的檔案由 Spring Boot 自己提供，頁面和 API 都在 8080，
   * 本來就是同一個來源。這段設定只在 npm run dev 時生效。
   */
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
