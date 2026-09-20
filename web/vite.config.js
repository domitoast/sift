import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],

  /*
   * Development only.
   *
   * In dev there are two servers: Vite on 5173 serving the page, Spring Boot
   * on 8080 serving the API. Different ports mean different origins, so the
   * browser would block the calls.
   *
   * Proxying rather than enabling CORS on the server: CORS would be config
   * that exists purely for development and has to be carried in production
   * forever. Here the browser only ever talks to 5173.
   *
   * None of this applies to a built app: the static files are served by
   * Spring Boot, so page and API share one origin already.
   */
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
