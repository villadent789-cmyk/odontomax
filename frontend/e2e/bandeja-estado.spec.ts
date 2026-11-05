/**
 * B2 · Recepción cambia el estado de una cita desde la bandeja del día (PATCH /citas/{id}/estado).
 * API simulada con page.route (forma exacta del contrato); no necesita backend.
 */
import { expect, test } from '@playwright/test'
import { HOY, MANANA, cita, iniciarRecepcion, json, type Cita } from './agenda-comun'

test('recepción marca la llegada de un paciente y pasa a la sala de espera', async ({ page }) => {
  await iniciarRecepcion(page)
  let citaHoy: Cita = cita({ pacienteNombre: 'David Cerrón', hora: '09:30:00' })
  let cuerpoEstado: unknown

  await page.route(/\/api\/v1\/citas(\?.*)?$/, (route) => {
    const fecha = new URL(route.request().url()).searchParams.get('fecha')
    return json(route, fecha === HOY ? [citaHoy] : fecha === MANANA ? [cita({ id: 'c0000000-0000-4000-8000-0000000000b1', fecha: MANANA, estado: 'CONFIRMADA' })] : [])
  })
  await page.route('**/api/v1/citas/reservas-web-pendientes**', (route) => json(route, []))
  await page.route('**/api/v1/citas/lista-espera**', (route) =>
    json(route, citaHoy.estado === 'EN_SALA' ? [citaHoy] : []),
  )
  await page.route(`**/api/v1/citas/${citaHoy.id}/estado`, async (route) => {
    cuerpoEstado = route.request().postDataJSON()
    citaHoy = { ...citaHoy, estado: 'EN_SALA' }
    await json(route, citaHoy)
  })

  await page.goto('/app/bandeja')
  await expect(page.getByRole('heading', { level: 2, name: 'Operación del día' })).toBeVisible()
  const fila = page.getByRole('region', { name: 'Citas de hoy' }).getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'David Cerrón' }) })
  await expect(fila.getByText('Programada')).toBeVisible()
  await expect(page.getByText('Sala vacía')).toBeVisible()

  await fila.getByRole('button', { name: 'Marcar llegada' }).click()

  await expect(fila.getByText('En sala de espera')).toBeVisible()
  expect(cuerpoEstado).toEqual({ estado: 'EN_SALA' })
  const sala = page.getByRole('complementary', { name: 'Sala de espera' })
  await expect(sala.getByText('David Cerrón')).toBeVisible()
  // En sala se ofrece pasar al sillón (y cancelar), no volver a confirmar.
  await expect(fila.getByRole('button', { name: 'Pasar al sillón' })).toBeVisible()
  await expect(fila.getByRole('button', { name: 'Confirmar' })).toHaveCount(0)
})
