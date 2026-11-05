import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

const ID = 'p0000000-0000-4000-8000-000000000009'

const PACIENTE = {
  id: ID,
  dni: '40111222',
  nombres: 'Rosa',
  apellidos: 'Quispe Vera',
  nombreCompleto: 'Rosa Quispe Vera',
  edad: 41,
  telefono: '964000111',
  sedeId: 'sede-chilca',
  saldoPendiente: 0,
  tieneDeuda: false,
}

/** Textos que antes se mostraban a TODOS los pacientes aunque no existieran. */
const TEXTOS_DE_EJEMPLO = [
  /Brackets/,
  /VLL-8902/,
  /Check-in Recepción/,
  /Limpieza Profunda/,
  /Sillón Clínico 1/,
  /Paciente acude para control de higiene intermedio/,
  /CBCT FOV 8x8/,
  /Sincronizado en R2/,
  /Atención reciente/,
]

interface Datos {
  paciente?: typeof PACIENTE
  citas?: unknown[]
  tratamientos?: unknown[] | 'error'
  notas?: unknown[]
  radiografias?: unknown[] | 'error'
}

function api({ paciente = PACIENTE, citas = [], tratamientos = [], notas = [], radiografias = [] }: Datos = {}) {
  servidor.use(
    http.get(`${API}/pacientes/:id`, () => HttpResponse.json(paciente)),
    http.get(`${API}/citas`, () => HttpResponse.json(citas)),
    http.get(`${API}/tratamientos/paciente/:id`, () =>
      tratamientos === 'error' ? errorBackend(500, 'Fallo temporal') : HttpResponse.json(tratamientos),
    ),
    http.get(`${API}/evoluciones/pacientes/:id`, () => HttpResponse.json(notas)),
    http.get(`${API}/radiografias/paciente/:id`, () =>
      radiografias === 'error' ? errorBackend(500, 'Fallo temporal') : HttpResponse.json(radiografias),
    ),
    http.get(`${API}/odontograma/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/plantillas`, () => HttpResponse.json([])),
    http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([])),
    http.post(`${API}/historia-clinica/pacientes/:id/apertura`, () => HttpResponse.json({})),
  )
}

const plan = (extra: Record<string, unknown> = {}) => ({
  id: 't-1',
  pacienteId: ID,
  nombre: 'Plan base',
  estado: 'EN_PROCESO',
  aceptadoPorPaciente: true,
  modalidadPago: 'CUOTAS',
  totalSesiones: 4,
  sesionesRealizadas: 1,
  costoTotal: 800,
  montoPagado: 200,
  saldoPendiente: 600,
  items: [],
  ...extra,
})

/** Abre la ficha y espera a que cargue el paciente: desde ahí las tarjetas del Resumen ya existen (cargando, con datos o con error). */
async function abrir() {
  await renderRuta(`/app/pacientes/${ID}`)
  await screen.findByRole('heading', { name: 'Rosa Quispe Vera' })
}
const tarjeta = (titulo: string | RegExp) => within(screen.getByRole('region', { name: titulo }))

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Ficha · Resumen con datos reales', { timeout: 20_000 }, () => {
  it('un paciente sin datos muestra vacíos honestos y NINGÚN texto de ejemplo', async () => {
    api()
    await abrir()

    expect(await tarjeta('Próxima atención programada').findByText('Sin citas programadas en los próximos 30 días.')).toBeInTheDocument()
    expect(await tarjeta('Tratamiento activo').findByText('Este paciente no tiene un tratamiento activo.')).toBeInTheDocument()
    expect(await tarjeta('Última nota de evolución odontológica').findByText('Aún no hay notas de evolución registradas para este paciente.')).toBeInTheDocument()
    expect(await tarjeta('Radiografías y estudios').findByText('Aún no hay radiografías, tomografías ni fotografías de este paciente.')).toBeInTheDocument()

    for (const texto of TEXTOS_DE_EJEMPLO) expect(screen.queryByText(texto)).not.toBeInTheDocument()
    // Recepción puede agendar y todos pueden crear el plan desde aquí.
    expect(tarjeta('Próxima atención programada').getByRole('link', { name: 'Agendar cita' })).toHaveAttribute('href', '/app/agenda')
    expect(tarjeta('Tratamiento activo').getByRole('button', { name: 'Crear plan de tratamiento' })).toBeInTheDocument()
  })

  it('presenta saldo negativo como crédito a favor y no como deuda', async () => {
    api({ paciente: { ...PACIENTE, saldoPendiente: -1500, tieneDeuda: true } })
    await abrir()

    expect(screen.getAllByText('Saldo a favor S/ 1,500.00').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('Saldo a favor', { exact: true })).toBeInTheDocument()
    expect(screen.queryByText('Cuotas por cobrar')).not.toBeInTheDocument()
  })

  it('"Crear plan de tratamiento" abre el formulario del plan para este paciente', async () => {
    api()
    await abrir()
    await userEvent.click(await tarjeta('Tratamiento activo').findByRole('button', { name: 'Crear plan de tratamiento' }))
    expect(await screen.findByRole('dialog', { name: 'Nuevo Plan de Tratamiento' })).toBeInTheDocument()
  })

  it('con notas, la última se muestra completa y "+ Nueva nota" desaparece del vacío', async () => {
    api({
      notas: [
        {
          id: 'n1', pacienteId: ID, autorNombre: 'Dra. Elena Paucar', autorRol: 'ODONTOLOGO', tipoNota: 'SOAP', firmado: true,
          creadoEn: '2026-10-10T15:00:00Z', subjetivo: 'Dolor al masticar', plan: 'Endodoncia pieza 36',
        },
      ],
    })
    await abrir()
    const nota = tarjeta('Última nota de evolución odontológica')
    expect(await nota.findByText('Dra. Elena Paucar')).toBeInTheDocument()
    expect(nota.getByText('Dolor al masticar')).toBeInTheDocument()
    expect(nota.getByText('Endodoncia pieza 36')).toBeInTheDocument()
    expect(nota.queryByText(/Aún no hay notas/)).not.toBeInTheDocument()
  })

  it('el tratamiento activo es el EN_PROCESO (no el completado ni el cancelado) con avance, procedimientos y saldos reales', async () => {
    api({
      tratamientos: [
        plan({ id: 't-c', nombre: 'Plan completado', estado: 'COMPLETADO' }),
        plan({ id: 't-p', nombre: 'Plan en espera', estado: 'PLANIFICADO', aceptadoPorPaciente: false }),
        plan({
          id: 't-e', nombre: 'Rehabilitación oral', estado: 'EN_PROCESO', totalSesiones: 5, sesionesRealizadas: 2,
          costoTotal: 1500, montoPagado: 600, pagadoAntesSistema: 300, saldoPendiente: 600, especialistaNombre: 'Dr. Mario Rivas',
          items: [
            { id: 'i1', nombreProcedimiento: 'Corona porcelana', piezaFdi: 36, estado: 'PENDIENTE' },
            { id: 'i2', nombreProcedimiento: 'Profilaxis', estado: 'REALIZADO' },
          ],
        }),
        plan({ id: 't-x', nombre: 'Plan cancelado', estado: 'CANCELADO' }),
      ],
    })
    await abrir()
    const t = tarjeta('Tratamiento activo: Rehabilitación oral')
    expect(await t.findByText('Presupuesto aceptado')).toBeInTheDocument()
    expect(t.getByText(/Dr\. Mario Rivas/)).toBeInTheDocument()
    expect(t.getByText(/Sesiones:/).closest('span')).toHaveTextContent('Sesiones: 2 de 5')
    expect(t.getByText('40%')).toBeInTheDocument()
    expect(t.getByText('Corona porcelana · pieza 36')).toBeInTheDocument()
    expect(t.getByText('Pendiente')).toBeInTheDocument()
    expect(t.getByText('Realizado')).toBeInTheDocument()
    // Pagado = lo cobrado en caja (600) + lo cancelado antes del sistema (300).
    expect(t.getByText('Costo total').nextElementSibling).toHaveTextContent('S/ 1,500.00')
    expect(t.getByText('Pagado').nextElementSibling).toHaveTextContent('S/ 900.00')
    expect(t.getByText('Saldo').nextElementSibling).toHaveTextContent('S/ 600.00')
    expect(t.getByText('y 1 plan(es) activo(s) más')).toBeInTheDocument()
    expect(screen.queryByText('Plan completado')).not.toBeInTheDocument()
    expect(screen.queryByText('Plan cancelado')).not.toBeInTheDocument()
  })

  it('un plan sin aceptar se indica como pendiente de aceptación', async () => {
    api({ tratamientos: [plan({ nombre: 'Plan nuevo', estado: 'PLANIFICADO', aceptadoPorPaciente: false })] })
    await abrir()
    expect(await tarjeta('Tratamiento activo: Plan nuevo').findByText('Presupuesto pendiente de aceptación')).toBeInTheDocument()
  })

  it('"Ver planes de tratamiento" abre la pestaña de tratamientos', async () => {
    api({ tratamientos: [plan({ nombre: 'Plan visible' })] })
    await abrir()
    await userEvent.click(await tarjeta('Tratamiento activo: Plan visible').findByRole('button', { name: 'Ver planes de tratamiento' }))
    expect(await screen.findByRole('heading', { name: /Planes de Tratamiento & Presupuestos/i })).toBeInTheDocument()
  })

  it('las radiografías son las reales, de la más reciente a la más antigua, y la galería se abre desde la tarjeta', async () => {
    api({
      radiografias: [
        { id: 'r1', pacienteId: ID, tipoEstudio: 'PANORAMICA', etapa: 'INICIAL', archivoUrl: 'x', nombreArchivo: 'a.jpg', tamanoBytes: 1, fechaToma: '2026-01-05T10:00:00Z', diagnosticoRadiografico: 'Sin hallazgos' },
        { id: 'r2', pacienteId: ID, tipoEstudio: 'PERIAPICAL', piezaFdi: 16, etapa: 'CONTROL', archivoUrl: 'y', nombreArchivo: 'b.jpg', tamanoBytes: 1, fechaToma: '2026-09-20T10:00:00Z', diagnosticoRadiografico: 'Lesión periapical incipiente' },
      ],
    })
    await abrir()
    const e = tarjeta('Radiografías y estudios')
    const items = await e.findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Radiografía Periapical')
    expect(items[0]).toHaveTextContent('pieza 16')
    expect(items[0]).toHaveTextContent('Lesión periapical incipiente')
    expect(items[1]).toHaveTextContent('Panorámica Digital HD')
    await userEvent.click(e.getByRole('button', { name: 'Ver galería completa' }))
    expect(await screen.findByText(/Estudios Radiológicos & Imagenología Maxilofacial 3D/i)).toBeInTheDocument()
  })

  it('si falla una consulta lo dice: nunca lo disfraza de "no hay datos"', async () => {
    api({ tratamientos: 'error', radiografias: 'error' })
    await abrir()
    expect(await tarjeta('Radiografías y estudios').findByRole('alert')).toHaveTextContent('No se pudo cargar esta información')
    expect(await tarjeta('Tratamiento activo').findByRole('alert')).toHaveTextContent('No se pudo cargar esta información')
    expect(screen.queryByText('Este paciente no tiene un tratamiento activo.')).not.toBeInTheDocument()
    expect(screen.queryByText(/Aún no hay radiografías/)).not.toBeInTheDocument()
    // Las demás tarjetas siguen respondiendo.
    expect(await tarjeta('Próxima atención programada').findByText('Sin citas programadas en los próximos 30 días.')).toBeInTheDocument()
  })

  it('la próxima cita es la más cercana entre varias y avisa cuántas más hay', async () => {
    const base = { pacienteId: ID, doctorNombre: 'Dr. Mario Rivas', sedeId: 'sede-chilca', sillonNumero: 2, estado: 'PROGRAMADA' }
    api({
      citas: [
        { ...base, id: 'c3', fecha: '2026-11-02', hora: '16:00:00', motivoConsulta: 'Control tardío' },
        { ...base, id: 'c1', fecha: '2026-10-25', hora: '08:30:00', motivoConsulta: 'Limpieza dental' },
        { ...base, id: 'c2', fecha: '2026-10-25', hora: '11:00:00', motivoConsulta: 'Control mismo día' },
        { ...base, id: 'c0', fecha: '2026-10-22', hora: '09:00:00', estado: 'ATENDIDA', motivoConsulta: 'Ya atendida' },
      ],
    })
    await abrir()
    const c = tarjeta('Próxima atención programada')
    expect(await c.findByText('Limpieza dental')).toBeInTheDocument()
    expect(c.getByText(/25\/10\/2026 · 08:30/)).toBeInTheDocument()
    expect(c.getByText(/Sillón 2/)).toBeInTheDocument()
    expect(c.getByText('y 2 cita(s) más')).toBeInTheDocument()
    expect(c.queryByText('Ya atendida')).not.toBeInTheDocument()
  })
})
