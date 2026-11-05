/**
 * Utilidades de los E2E de agenda (B2-B5): sesión de recepción ya iniciada (sessionStorage, como tras el login B1),
 * reloj fijo en Lima y la API simulada con page.route con la forma exacta del contrato.
 */
import type { Page, Route } from '@playwright/test'
import type { components } from '../src/shared/api/schema'

export type Esquemas = components['schemas']
export type Cita = Esquemas['CitaResponseDTO']

export const HOY = '2026-10-08'
export const MANANA = '2026-10-09'
export const ID_PAUCAR = '3f0c7a52-1d2e-4b8a-9c3d-5e6f7a8b9c0d'
export const ID_PACIENTE = 'a1b2c3d4-0000-4000-8000-000000000001'

export const SEDES: Esquemas['SedeResponseDTO'][] = [
  { id: 'sede-chilca', nombre: 'Sede Chilca', activa: true, sillones: 3, direccion: 'Av. 9 de Diciembre 482' },
]

export const ESPECIALISTAS: Esquemas['EspecialistaResponseDTO'][] = [
  { id: ID_PAUCAR, nombre: 'Dra. Elena Paucar', especialidad: 'Rehabilitación oral', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 2 }, activo: true },
]

export function json(route: Route, cuerpo: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(cuerpo) })
}

export function cita(extra: Partial<Cita> = {}): Cita {
  return {
    id: 'c0000000-0000-4000-8000-000000000001',
    pacienteId: ID_PACIENTE,
    pacienteNombre: 'Beatriz Tovar Lazo',
    pacienteTelefono: '964123456',
    doctorId: ID_PAUCAR,
    doctorNombre: 'Dra. Elena Paucar',
    sedeId: 'sede-chilca',
    sillonNumero: 1,
    motivoConsulta: 'Carillas de resina',
    fecha: HOY,
    hora: '09:00:00',
    duracionMinutos: 30,
    estado: 'PROGRAMADA',
    ...extra,
  }
}

/**
 * Todo lo que el test no simula responde vacío en vez de llegar al backend real (que contestaría 401 al token falso y
 * cerraría la sesión). Se registra primero: las rutas específicas del test, que se añaden después, tienen prioridad.
 */
export async function responderVacioLoNoSimulado(page: Page) {
  await page.route('**/api/v1/**', (route) => json(route, []))
}

/** Sesión de recepción de Sede Chilca y reloj fijo: jueves 08/10/2026 10:00 en Lima. */
export async function iniciarRecepcion(page: Page) {
  await responderVacioLoNoSimulado(page)
  await page.clock.setFixedTime(new Date('2026-10-08T15:00:00Z'))
  await page.addInitScript(() => {
    window.sessionStorage.setItem(
      'odontomax.sesion',
      JSON.stringify({
        token: 'jwt-final',
        usuario: {
          id: '0b6f3c1e-2a51-4f0e-9d7a-1c2b3d4e5f60',
          nombre: 'Katherin Mendoza',
          rol: 'RECEPCION',
          sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }],
        },
        sedeActivaId: 'sede-chilca',
      }),
    )
  })
  await page.route('**/api/v1/sedes', (route) => json(route, SEDES))
  await page.route(/\/api\/v1\/especialistas(\?.*)?$/,(route) => json(route, ESPECIALISTAS))
  await page.route('**/api/v1/especialistas/*/horarios', (route) =>
    json(
      route,
      [1, 2, 3, 4, 5, 6].map((dia) => ({
        id: `h0000000-0000-4000-8000-00000000000${dia}`,
        diaSemana: dia,
        horaInicio: '08:00:00',
        horaFin: '13:00:00',
        sedeId: 'sede-chilca',
      })),
    ),
  )
}
