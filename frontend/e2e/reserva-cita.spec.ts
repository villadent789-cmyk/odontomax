/**
 * Flujo crítico de la web pública: "reservar cita y pagar adelanto".
 *
 * Se cubre la RESERVA de punta a punta (asistente A4 → ticket A5). El PAGO DE ADELANTO no existe en la API
 * (docs/frontend/brechas-api.md, G-1 y A5-1): el test comprueba que el ticket avisa de que el pago se hace en la
 * clínica y que no se ofrece ningún pago en línea. Cuando exista el endpoint de adelanto, este flujo se amplía.
 *
 * API simulada con page.route y respuestas con la forma exacta del contrato (tipos de docs/api/openapi.json y
 * tipos-manuales.ts). No necesita backend.
 */
import { expect, test, type Page, type Route } from '@playwright/test'
import type { components } from '../src/shared/api/schema'
import type { TicketReserva } from '../src/shared/api/tipos-manuales'

type Esquemas = components['schemas']

const SEDES: Esquemas['SedeResponseDTO'][] = [
  {
    id: 'sede-chilca',
    nombre: 'Sede Chilca',
    activa: true,
    sillones: 4,
    direccion: 'Av. 9 de Diciembre 482',
    horarioTexto: 'Lun - Sáb: 08:30 a 20:00',
  },
  { id: 'sede-centro', nombre: 'Sede Huancayo Centro', activa: true, sillones: 3, direccion: 'Calle Real 520, 2do piso' },
]

const SERVICIOS: Esquemas['ServicioResponseDTO'][] = [
  {
    id: 'srv-evaluacion',
    nombre: 'Evaluación & Diagnóstico',
    descripcion: 'Incluye odontograma digital, cámara intraoral e informe médico inicial.',
    especialidad: 'Odontología general',
    precio: 30,
    duracionMin: 30,
    orden: 1,
    activo: true,
  },
  {
    id: 'srv-limpieza',
    nombre: 'Limpieza & Profilaxis profunda',
    descripcion: 'Ultrasonido, destartraje y pulido con flúor',
    especialidad: 'Odontología general',
    precio: 70,
    duracionMin: 45,
    orden: 2,
    activo: true,
  },
]

const ESPECIALISTAS: Esquemas['EspecialistaResponseDTO'][] = [
  {
    id: '3f0c7a52-1d2e-4b8a-9c3d-5e6f7a8b9c0d',
    nombre: 'Dra. Elena Paucar',
    especialidad: 'Odontología general',
    sedes: ['sede-chilca'],
    activo: true,
  },
]

function json(route: Route, cuerpo: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(cuerpo) })
}

async function simularCatalogo(page: Page) {
  await page.route(/\/api\/v1\/sedes(\?.*)?$/, (route) => json(route, SEDES))
  await page.route(/\/api\/v1\/reservas-publicas\/catalogo\/servicios(\?.*)?$/, (route) => json(route, SERVICIOS))
  await page.route(/\/api\/v1\/reservas-publicas\/catalogo\/especialistas(\?.*)?$/, (route) => json(route, ESPECIALISTAS))
}

/** Hoy en Lima (aaaa-mm-dd), igual que el asistente. */
function hoyEnLima() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date())
}

test('reservar cita: asistente de 4 pasos → POST /reservas-publicas → ticket (pago de adelanto: brecha G-1)', async ({
  page,
}) => {
  await simularCatalogo(page)
  let cuerpo: unknown
  await page.route('**/api/v1/reservas-publicas', async (route) => {
    cuerpo = route.request().postDataJSON()
    const ticket: TicketReserva = {
      codigoTicket: 'OM-7K9M2P4X',
      sedeId: 'sede-chilca',
      sedeNombre: 'sede-chilca',
      fecha: hoyEnLima(),
      turno: 'TARDE',
      estado: 'PENDIENTE_WEB',
      especialidad: 'Evaluación & Diagnóstico',
      mensaje: '¡Reserva registrada con éxito! La clínica se comunicará contigo vía WhatsApp para confirmar tu turno exacto.',
    }
    await json(route, ticket, 201)
  })

  await page.goto('/')
  await page.getByRole('link', { name: 'Reserva tu cita', exact: true }).click()
  await expect(page).toHaveURL(/\/reservar$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Reserva tu cita' })).toBeVisible()

  // Paso 1: sede y servicio (con teclado: el radio se marca con espacio).
  await expect(page.getByText('Paso 1 de 4', { exact: true })).toBeVisible()
  await page.getByRole('radio', { name: /Sede Chilca/ }).check()
  await page.getByRole('radio', { name: /Evaluación & Diagnóstico/ }).focus()
  await page.keyboard.press('Space')
  await expect(page.getByRole('radio', { name: /Evaluación & Diagnóstico/ })).toBeChecked()
  await page.getByRole('button', { name: /Continuar a selección de fecha/ }).click()

  // Paso 2: fecha (hoy) y turno.
  await expect(page.getByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })).toBeFocused()
  await page.getByRole('radio', { name: /\(hoy\)/ }).check()
  await page.getByRole('radio', { name: 'Tarde' }).check()
  await page.getByRole('button', { name: /Continuar a tus datos/ }).click()

  // Paso 3: datos del paciente.
  await page.getByLabel('DNI o documento').fill('45908123')
  await page.getByLabel('Nombres').fill('Carlos')
  await page.getByLabel('Apellidos').fill('Huamán Rojas')
  await page.getByLabel('Celular o WhatsApp').fill('987654321')
  await page.getByRole('button', { name: /Revisar mi reserva/ }).click()

  // Paso 4: revisión y confirmación.
  await expect(page.getByRole('heading', { name: 'Revisa tu reserva' })).toBeVisible()
  await page.getByRole('button', { name: 'Confirmar reserva' }).click()

  // Ticket (A5, parte existente).
  await expect(page).toHaveURL(/\/reserva\/OM-7K9M2P4X$/)
  await expect(page.getByRole('heading', { level: 1, name: '¡Tu reserva está registrada!' })).toBeFocused()
  await expect(page.getByText('Código: OM-7K9M2P4X')).toBeVisible()
  await expect(page.getByText(/La clínica se comunicará contigo vía WhatsApp/)).toBeVisible()
  // El nombre de la sede sale de GET /sedes (el backend devuelve el id como sedeNombre).
  await expect(page.getByText('Sede Chilca')).toBeVisible()
  await expect(page.getByText('Tarde', { exact: true })).toBeVisible()

  // Pago de adelanto: brecha G-1. Se informa el pago en clínica y no hay ningún pago en línea.
  await expect(page.getByRole('heading', { name: 'Pago en la clínica' })).toBeVisible()
  await expect(page.getByText(/No necesitas pagar nada ahora en línea/)).toBeVisible()
  await expect(page.getByRole('button', { name: /pagar|yape/i })).toHaveCount(0)

  expect(cuerpo).toEqual({
    dni: '45908123',
    nombres: 'Carlos',
    apellidos: 'Huamán Rojas',
    telefono: '987654321',
    motivoConsulta: 'Evaluación & Diagnóstico',
    sedeId: 'sede-chilca',
    fechaPreferencia: hoyEnLima(),
    turno: 'TARDE',
  })
})
