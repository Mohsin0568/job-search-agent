import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Forward /api calls to the Spring Boot backend during development,
    // so the browser sees a single origin and no CORS setup is needed locally.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
