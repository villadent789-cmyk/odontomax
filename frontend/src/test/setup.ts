import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { servidor } from './msw'

// jsdom no implementa scrollTo (lo usa la restauración de scroll del router).
if (typeof window !== 'undefined') window.scrollTo = (() => {}) as typeof window.scrollTo

// Red simulada (MSW): cualquier petición sin manejador hace fallar el test.
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  servidor.resetHandlers()
})
afterAll(() => servidor.close())
