import { defineConfig, devices } from '@playwright/test'

/**
 * Puerto del servidor de desarrollo para los E2E. Por defecto el de `npm run dev` (5173); se puede cambiar con
 * E2E_PORT (p. ej. `E2E_PORT=5180 npx playwright test`) para no chocar con un servidor que ya esté en uso.
 */
const PUERTO = process.env.E2E_PORT ?? '5173'
const URL_BASE = `http://localhost:${PUERTO}`

/**
 * E2E de los flujos críticos con la API simulada en el navegador (page.route): no necesitan backend.
 * Antes del primer uso: `npx playwright install chromium`. Ver README (opción futura: contra el backend real).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL: URL_BASE,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: PUERTO === '5173' ? 'npm run dev' : `npm run dev -- --port ${PUERTO} --strictPort`,
    url: URL_BASE,
    reuseExistingServer: !process.env.CI,
  },
})
