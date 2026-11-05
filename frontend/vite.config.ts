/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    // En desarrollo, /api/v1/... se reenvía al backend Spring Boot local.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    pool: 'threads',
    testTimeout: 15_000,
    // URL absoluta para que fetch funcione en Node/jsdom; MSW intercepta este origen (src/test/msw.ts).
    env: { VITE_API_URL: 'http://localhost:3000/api/v1' },
    passWithNoTests: true,
  },
})
