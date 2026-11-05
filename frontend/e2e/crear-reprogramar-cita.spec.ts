/**
 * Flujo crítico de recepción (B3-B5): "crear y reprogramar una cita".
 * Agenda → Nueva cita (paciente existente, especialista, hueco libre de la disponibilidad real) → POST /citas →
 * detalle de la cita (B4) → Reprogramar → PATCH /citas/{id}/reprogramar. API simulada con page.route y respuestas con
 * la forma exacta del contrato; no necesita backend.
 */
import { expect, test } from '@playwright/test'
import { ID_PACIENTE, ID_PAUCAR, MANANA, cita, iniciarRecepcion, json, type Cita, type Esquemas } from './agenda-comun'

const PACIENTE: Esquemas['PacienteResponseDTO'] = {
  id: ID_PACIENTE,
  dni: '42891023',
  nombres: 'Beatriz',
  apellidos: 'Tovar Lazo',
  nombreCompleto: 'Beatriz Tovar Lazo',
  telefono: '964123456',
  sedeId: 'sede-chilca',
}

test('recepción crea una cita en un hueco libre y luego la reprograma', async ({ page }) => {
  await iniciarRecepcion(page)
  const citas: Cita[] = []
  let cuerpoCrear: unknown
  let cuerpoReprogramar: unknown

  await page.route(/\/api\/v1\/citas(\?.*)?$/, async (route) => {
    if (route.request().method() === 'POST') {
      cuerpoCrear = route.request().postDataJSON()
      const c = cuerpoCrear as Esquemas['CitaRequestDTO']
      const nueva = cita({
        id: 'c0000000-0000-4000-8000-0000000000f1',
        fecha: c.fecha,
        hora: `${c.hora}:00`,
        duracionMinutos: c.duracionMinutos,
        sillonNumero: c.sillonNumero,
        motivoConsulta: c.motivoConsulta,
      })
      citas.push(nueva)
      return json(route, nueva, 201)
    }
    const fecha = new URL(route.request().url()).searchParams.get('fecha')
    return json(route, citas.filter((c) => c.fecha === fecha))
  })
  await page.route('**/api/v1/citas/reservas-web-pendientes**', (route) => json(route, []))
  await page.route('**/api/v1/citas/lista-espera**', (route) => json(route, []))
  await page.route('**/api/v1/pacientes?**', (route) => json(route, [PACIENTE]))
  await page.route(`**/api/v1/pacientes/${ID_PACIENTE}`, (route) => json(route, PACIENTE))
  await page.route('**/api/v1/especialistas/*/disponibilidad**', (route) => {
    const fecha = new URL(route.request().url()).searchParams.get('fecha')!
    const ocupadas = new Set(citas.filter((c) => c.fecha === fecha).map((c) => c.hora!.slice(0, 5)))
    const slots = ['08:00', '08:30', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30'].map((h) => ({
      hora: `${h}:00`,
      disponible: !ocupadas.has(h),
      motivoNoDisponible: ocupadas.has(h) ? 'OCUPADO' : undefined,
    }))
    return json(route, { especialistaId: ID_PAUCAR, fecha, sedeId: 'sede-chilca', atiendeEseDia: true, slots })
  })
  await page.route('**/api/v1/citas/*/reprogramar', async (route) => {
    cuerpoReprogramar = route.request().postDataJSON()
    const r = cuerpoReprogramar as Esquemas['CitaReprogramarRequestDTO']
    citas[0] = { ...citas[0], fecha: r.nuevaFecha, hora: `${r.nuevoHorario}:00`, motivoReprogramacion: r.motivo }
    return json(route, citas[0])
  })

  await page.goto('/app/agenda')
  await expect(page.getByRole('heading', { level: 1, name: /Jueves, 8 de octubre de 2026/ })).toBeVisible()

  // 1. Crear
  await page.getByRole('button', { name: 'Nueva cita', exact: true }).click()
  const nueva = page.getByRole('dialog', { name: 'Nueva cita' })
  await nueva.getByLabel('Paciente', { exact: true }).fill('4289')
  await nueva.getByRole('button', { name: /Beatriz Tovar Lazo/ }).click()
  await nueva.getByLabel('Especialista').selectOption(ID_PAUCAR)
  await nueva.getByLabel('Fecha', { exact: true }).fill(MANANA)
  await nueva.getByText('09:00', { exact: true }).click()
  await nueva.getByLabel('Motivo o procedimiento').fill('Evaluación y diagnóstico')
  await nueva.getByRole('button', { name: 'Agendar cita' }).click()
  await expect(nueva).toBeHidden()
  expect(cuerpoCrear).toEqual({
    pacienteId: ID_PACIENTE,
    pacienteNombre: 'Beatriz Tovar Lazo',
    pacienteTelefono: '964123456',
    doctorId: ID_PAUCAR,
    doctorNombre: 'Dra. Elena Paucar',
    sedeId: 'sede-chilca',
    fecha: MANANA,
    hora: '09:00',
    duracionMinutos: 30,
    sillonNumero: 2,
    motivoConsulta: 'Evaluación y diagnóstico',
  })

  // 2. La cita aparece en la agenda de mañana
  await page.getByRole('button', { name: 'Día siguiente' }).click()
  const tarjeta = page.getByRole('button', { name: /Ver cita: 09:00 a 09:30, Beatriz Tovar Lazo, Programada/ })
  await expect(tarjeta).toBeVisible()

  // 3. Reprogramar desde el detalle
  await tarjeta.click()
  const panel = page.getByRole('dialog', { name: 'Beatriz Tovar Lazo' })
  await expect(panel.getByText('Sede Chilca · Sillón 2')).toBeVisible()
  await panel.getByRole('button', { name: 'Reprogramar' }).click()
  const reprogramar = page.getByRole('dialog', { name: 'Reprogramar cita' })
  await expect(reprogramar.getByLabel('Nueva fecha')).toHaveValue(MANANA)
  await reprogramar.getByText('11:00', { exact: true }).click()
  await reprogramar.getByLabel('Motivo de la reprogramación').fill('La paciente pidió más tarde')
  await reprogramar.getByRole('button', { name: 'Reprogramar' }).click()
  await expect(reprogramar).toBeHidden()
  expect(cuerpoReprogramar).toEqual({ nuevaFecha: MANANA, nuevoHorario: '11:00', motivo: 'La paciente pidió más tarde' })
  await expect(panel.getByText(/Viernes 09\/10\/2026 · 11:00 a 11:30/)).toBeVisible()

  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: /Ver cita: 11:00 a 11:30, Beatriz Tovar Lazo/ })).toBeVisible()
})
