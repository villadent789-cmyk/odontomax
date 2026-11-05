/**
 * Flujo clínico del doctor: "Nuevo tratamiento" → buscar al paciente → plan con varios casos de gravedad.
 * API simulada con page.route (sin backend). Comprueba lo que la pantalla le manda al servidor.
 */
import { expect, test } from '@playwright/test'
import { ID_PACIENTE, ID_PAUCAR, SEDES, json, responderVacioLoNoSimulado, type Esquemas } from './agenda-comun'

const PACIENTE: Esquemas['PacienteResponseDTO'] = {
  id: ID_PACIENTE,
  dni: '42891023',
  nombres: 'María',
  apellidos: 'Tovar Lazo',
  nombreCompleto: 'María Tovar Lazo',
  telefono: '964123456',
  sedeId: 'sede-chilca',
}

const CURACION = { id: 'srv-curacion', nombre: 'Curación', especialidad: 'Odontología general', precio: 80, precioLeve: 50, precioModerado: 80, precioSevero: 120, activo: true, orden: 1 }

async function iniciarDoctor(page: import('@playwright/test').Page) {
  await responderVacioLoNoSimulado(page)
  await page.clock.setFixedTime(new Date('2026-10-08T15:00:00Z'))
  await page.addInitScript(() => {
    window.sessionStorage.setItem(
      'odontomax.sesion',
      JSON.stringify({
        token: 'jwt-final',
        usuario: {
          id: '0b6f3c1e-2a51-4f0e-9d7a-1c2b3d4e5f61',
          nombre: 'Dra. Elena Paucar',
          rol: 'ODONTOLOGO',
          sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }],
        },
        sedeActivaId: 'sede-chilca',
      }),
    )
  })
  await page.route('**/api/v1/sedes', (route) => json(route, SEDES))
  await page.route(/\/api\/v1\/especialistas(\?.*)?$/, (route) =>
    json(route, [{ id: ID_PAUCAR, nombre: 'Dra. Elena Paucar', especialidad: 'Rehabilitación oral', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 2 }, usuarioId: '0b6f3c1e-2a51-4f0e-9d7a-1c2b3d4e5f61', activo: true }]),
  )
}

test('el doctor busca a la paciente sin tilde y crea un plan con casos de distinta gravedad', async ({ page }) => {
  await iniciarDoctor(page)
  let busqueda: string | null = null
  let cuerpoPlan: Record<string, unknown> | undefined

  await page.route(/\/api\/v1\/pacientes(\?.*)?$/, (route) => {
    busqueda = new URL(route.request().url()).searchParams.get('busqueda')
    return json(route, [PACIENTE])
  })
  await page.route('**/api/v1/servicios**', (route) => json(route, [CURACION]))
  await page.route('**/api/v1/pacientes/*/ficha-ingreso', (route) => json(route, { actualizadoEn: '2026-10-01T10:00:00Z' }))
  await page.route('**/api/v1/odontograma/pacientes/*', (route) =>
    json(route, [{ id: 'r1', pacienteId: ID_PACIENTE, piezaFdi: 16, estado: 'caries', caras: ['O'], activo: true, registradoEn: '2026-10-01T10:00:00Z' }]),
  )
  await page.route('**/api/v1/tratamientos/paciente/*', (route) => json(route, []))
  await page.route(/\/api\/v1\/tratamientos$/, async (route) => {
    if (route.request().method() !== 'POST') return json(route, [])
    cuerpoPlan = route.request().postDataJSON() as Record<string, unknown>
    return json(route, { id: 't-nuevo', pacienteId: ID_PACIENTE, nombre: 'Curación', costoTotal: 370, saldoPendiente: 370, estado: 'EN_PROCESO' }, 201)
  })

  await page.goto('/app/nuevo-tratamiento')
  await expect(page.getByRole('heading', { level: 1, name: 'Nuevo tratamiento' })).toBeVisible()

  // 1. Buscar sin tilde ni mayúscula y elegir a la paciente.
  await page.getByRole('searchbox').fill('maria')
  await page.getByText('María Tovar Lazo').first().click()
  expect(busqueda).toBe('maria')
  await expect(page.getByRole('heading', { name: 'María Tovar Lazo' })).toBeVisible()
  await expect(page.getByText(/La ficha de ingreso ya se llenó/)).toBeVisible()
  await expect(page.getByText('Lo que hay en el odontograma')).toBeVisible()

  // 2. Plan con tres casos: leve, moderado y 2 severos.
  await page.getByRole('button', { name: 'Crear plan de tratamiento' }).click()
  const plan = page.getByRole('dialog', { name: /Nuevo Plan de Tratamiento/ })
  await plan.getByLabel('Tratamiento del catálogo').selectOption('srv-curacion')
  await plan.getByLabel('Gravedad del caso', { exact: true }).selectOption('LEVE')
  await plan.getByRole('button', { name: /Agregar otro caso/ }).click()
  await plan.getByLabel('Gravedad del caso 2').selectOption('MODERADO')
  await plan.getByRole('button', { name: /Agregar otro caso/ }).click()
  await plan.getByLabel('Gravedad del caso 3').selectOption('SEVERO')
  await plan.getByLabel('Piezas del caso 3').fill('2')
  await expect(plan.getByLabel('Presupuesto total (S/) *')).toHaveValue('370.00')

  await plan.getByRole('button', { name: 'Crear plan' }).click()

  await expect.poll(() => cuerpoPlan).toBeDefined()
  expect(cuerpoPlan).toMatchObject({ costoTotal: 370, pacienteId: ID_PACIENTE })
  expect(cuerpoPlan?.items).toEqual([
    expect.objectContaining({ cantidad: 1, precioBase: 50, gravedad: 'LEVE' }),
    expect.objectContaining({ cantidad: 1, precioBase: 80, gravedad: 'MODERADO' }),
    expect.objectContaining({ cantidad: 2, precioBase: 120, gravedad: 'SEVERO' }),
  ])
})

test('con «Todas» las sedes el administrador general ve los atajos de todas las sedes', async ({ page }) => {
  await responderVacioLoNoSimulado(page)
  await page.addInitScript(() => {
    window.sessionStorage.setItem(
      'odontomax.sesion',
      JSON.stringify({
        token: 'jwt-final',
        usuario: {
          id: '0b6f3c1e-2a51-4f0e-9d7a-1c2b3d4e5f62',
          nombre: 'Dueño',
          rol: 'ADMIN_GENERAL',
          sedes: [
            { id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' },
            { id: 'sede-huancayo', nombre: 'Sede Huancayo', marca: 'VILLADENT' },
          ],
        },
        negocioActivo: 'VILLADENT',
        sedeActivaId: null,
      }),
    )
  })
  await page.route('**/api/v1/sedes', (route) => json(route, SEDES))
  await page.route('**/api/v1/presupuestos/pendientes**', (route) => {
    const sede = new URL(route.request().url()).searchParams.get('sedeId')
    return json(route, sede === 'sede-huancayo' ? [{ id: 'pr1', pacienteId: 'p-3', pacienteNombre: 'Luis de Huancayo', total: 100, lineas: 1, creadoEn: '2026-10-07T10:00:00Z' }] : [])
  })
  await page.route('**/api/v1/tratamientos/sede/*', (route) =>
    json(route, route.request().url().endsWith('sede-chilca') ? [{ id: 't1', pacienteId: 'p-4', pacienteNombre: 'Rosa de Chilca', nombre: 'Endodoncia', estado: 'EN_PROCESO', sesionesRealizadas: 1, totalSesiones: 3 }] : []),
  )

  await page.goto('/app/nuevo-tratamiento')
  await expect(page.getByText('Luis de Huancayo')).toBeVisible()
  await expect(page.getByText('Rosa de Chilca')).toBeVisible()
  await expect(page.getByText('Sede Huancayo').first()).toBeVisible()
  await expect(page.getByText(/Elige una sede arriba/)).toHaveCount(0)
})
