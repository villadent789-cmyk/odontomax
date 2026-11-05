# OdontoMax frontend (Villadent · Villamax)

React + TypeScript + Vite, Tailwind CSS v4, componentes estilo shadcn/ui (Radix), TanStack Router y Query,
React Hook Form + Zod, Motion, Recharts, Vitest + Testing Library + MSW y Playwright.

Dos aplicaciones con un solo design system:

- Web pública: `/`, `/servicios`, `/villamax`, `/reservar` (asistente A4), `/reserva/:codigo` (ticket A5 tras
  reservar; si no hay ticket en la pestaña, consulta A6) y `/reserva?codigo=...` (consulta A6).
- Sistema interno: `/app/...` (en desarrollo también `/app/_design`, catálogo de componentes).
- Acceso (B1): `/app/login` (Login), `/app/login/2fa`, `/app/login/ajustes-2fa`, `/app/login/recuperar` y
  `/restablecer-password?token=...` (la ruta del enlace que envía el backend por correo).

## Comandos

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo en http://localhost:5173 (`/api` se reenvía a http://localhost:8080) |
| `npm run build` | Chequeo de tipos y build de producción en `dist/` |
| `npm run lint` | ESLint (incluye reglas de accesibilidad jsx-a11y) |
| `npm test` | Vitest: unitarios y de componentes, con la red simulada por MSW |
| `npm run test:e2e` | Playwright (= `npx playwright test`); la primera vez: `npx playwright install chromium` |
| `npm run generate:api` | Genera `src/shared/api/schema.d.ts` desde `../docs/api/openapi.json` |

## Cliente de API

- `src/shared/api/schema.d.ts`: tipos generados del contrato (no se edita a mano; se regenera con
  `npm run generate:api` cada vez que cambie `docs/api/openapi.json`).
- `src/shared/api/cliente-api.ts`: `apiTipada.get/post/put/patch/delete('/ruta/{id}', { path, query, cuerpo })`.
  Ruta, parámetros, cuerpo y respuesta salen del contrato; por debajo usa el cliente http (`src/shared/http`), que
  añade el JWT y traduce 401/403/429.
- `src/shared/api/tipos-manuales.ts`: tipos de los endpoints que el contrato no tipa (cada uno cita su fila de
  `docs/frontend/brechas-api.md`, Parte A). El cliente los usa en lugar del `object` genérico.

## Tests

**Unitarios y de componentes (Vitest + Testing Library):** `npm test` (o `npm run test:watch`).
La red se simula con MSW (`src/test/msw.ts`): los manejadores y datos de prueba se tipan con los tipos generados,
así que una respuesta con forma distinta a la del contrato no compila. Cualquier petición sin manejador hace fallar
el test. En los tests el cliente apunta a `http://localhost:3000/api/v1` (`test.env` en `vite.config.ts`).

**E2E (Playwright):** `npx playwright test` (o `npm run test:e2e`). Levanta `npm run dev` (o reutiliza el que ya
esté corriendo en el puerto 5173) y simula la API en el navegador con `page.route`, con respuestas con la forma
exacta del contrato: no necesita backend. Flujos actuales:

- `e2e/login-2fa.spec.ts`: login con 2FA hasta `/app`, y código erróneo / token temporal agotado que vuelve al login.
- `e2e/reserva-cita.spec.ts`: flujo crítico del paciente **"reservar cita y pagar adelanto"**. Cubre la reserva de
  punta a punta (asistente de 4 pasos → `POST /reservas-publicas` → ticket). El **pago de adelanto no existe en la
  API** (brecha G-1 de `docs/frontend/brechas-api.md`): el test comprueba que el ticket avisa de que el pago se hace en
  la clínica y que no se ofrece ningún pago en línea. Cuando exista el endpoint, el flujo se amplía.
- `e2e/crear-reprogramar-cita.spec.ts`: flujo crítico de recepción **"crear y reprogramar una cita"** (agenda B3 →
  nueva cita B5 → detalle B4 → reprogramar).
- `e2e/bandeja-estado.spec.ts`: recepción cambia el estado de una cita desde la bandeja del día (B2).

Para no chocar con un servidor que ya use el puerto 5173, el puerto de los E2E se cambia con `E2E_PORT`
(p. ej. `E2E_PORT=5180 npx playwright test`): Playwright levanta `vite --port 5180 --strictPort` y lo apaga al acabar.

**E2E contra el backend real (opción futura, no implementada):** quitar los `page.route`, arrancar el backend en
`http://localhost:8080` (el proxy de Vite reenvía `/api`) con usuarios de prueba sembrados (uno con 2FA y su
secreto TOTP para generar códigos en el test) y separarlo en otro proyecto de Playwright para que no corra por
defecto.

## Sesión y datos de ejemplo en desarrollo

La sesión es siempre la real: tras el login, `GET /auth/me` (nombre, rol, sede) y `GET /sedes` (público) para el
selector de sede. El JWT se guarda en `sessionStorage`; el token temporal del 2FA solo vive en memoria.

En desarrollo, la pantalla de acceso muestra una franja coral con **«Entrar con sesión de ejemplo»** (usuario y
sedes de `src/dev-fixtures/`, eligiendo el rol). Es una opción explícita: nunca se entra automáticamente. Ese
módulo solo se carga con `import.meta.env.DEV` y no entra en el build de producción.
