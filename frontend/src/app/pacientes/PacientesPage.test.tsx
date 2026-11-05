import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as descargas from '@/shared/descargas'
import * as imprimir from '@/shared/imprimir'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { PacienteDTO } from './api-pacientes'

vi.mock('@/shared/imprimir', async (original) => ({
  ...(await original<typeof import('@/shared/imprimir')>()),
  imprimirDocumento: vi.fn(async () => {}),
}))

vi.mock('@/shared/descargas', async (original) => ({
  ...(await original<typeof import('@/shared/descargas')>()),
  descargarArchivo: vi.fn(),
}))

const API = 'http://localhost:3000/api/v1'

const PACIENTE_1: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000001',
  dni: '45908123',
  nombres: 'Luciana',
  apellidos: 'Meza Cárdenas',
  nombreCompleto: 'Luciana Meza Cárdenas',
  fechaNacimiento: '1995-05-14',
  edad: 29,
  telefono: '964128340',
  email: 'luciana@gmail.com',
  sedeId: 'sede-chilca',
  genero: 'FEMENINO',
  fuenteCaptacion: 'TIKTOK',
  numeroHistoriaClinica: 'HC-2024-089',
  saldoPendiente: 140.0,
  tieneDeuda: true,
  totalInasistencias: 0,
  avisoInasistencias: false,
  esCumpleaneroMes: false,
  alergias: [],
}

const PACIENTE_2: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000002',
  dni: '40918742',
  nombres: 'Gonzalo',
  apellidos: 'Astuvilca Ramos',
  nombreCompleto: 'Gonzalo Astuvilca Ramos',
  fechaNacimiento: '1987-10-20',
  edad: 37,
  telefono: '964902441',
  sedeId: 'sede-chilca',
  genero: 'MASCULINO',
  fuenteCaptacion: 'LOCAL',
  numeroHistoriaClinica: 'HC-2023-198',
  saldoPendiente: 220.0,
  tieneDeuda: true,
  totalInasistencias: 2,
  avisoInasistencias: true,
  esCumpleaneroMes: false,
  alergias: ['Penicilina'],
}

const PACIENTE_3: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000003',
  dni: '71299401',
  nombres: 'Pamela',
  apellidos: 'Flores Paucar',
  nombreCompleto: 'Pamela Flores Paucar',
  fechaNacimiento: '2002-10-08',
  edad: 22,
  telefono: '987321004',
  sedeId: 'sede-villamax-chilca',
  genero: 'FEMENINO',
  fuenteCaptacion: 'REFERIDO_DOCTOR',
  doctorReferidor: 'Dr. Roberto Alanya',
  numeroHistoriaClinica: 'HC-2025-014',
  saldoPendiente: 0,
  tieneDeuda: false,
  totalInasistencias: 0,
  avisoInasistencias: false,
  esCumpleaneroMes: true,
  alergias: [],
}

function mockApi(pacientes: PacienteDTO[] = [PACIENTE_1, PACIENTE_2, PACIENTE_3]) {
  servidor.use(
    http.get(`${API}/sedes`, () => {
      return HttpResponse.json([
        { id: 'sede-chilca', nombre: 'Villadent Chilca', activa: true, unidadDeNegocio: 'VILLADENT' },
        { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', activa: true, unidadDeNegocio: 'VILLAMAX' },
      ])
    }),
    http.get(`${API}/pacientes`, () => {
      return HttpResponse.json(pacientes)
    }),
    http.post(`${API}/pacientes`, async ({ request }) => {
      const cuerpo = (await request.json()) as { nombres: string; apellidos: string }
      return HttpResponse.json({
        id: 'p0000000-0000-4000-8000-000000000099',
        ...cuerpo,
        nombreCompleto: `${cuerpo.nombres} ${cuerpo.apellidos}`,
        edad: 30,
        saldoPendiente: 0,
        tieneDeuda: false,
        totalInasistencias: 0,
        avisoInasistencias: false,
        esCumpleaneroMes: false,
      })
    })
  )
}

describe('B6 · Directorio y Lista de Pacientes (Stitch)', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-recepcion',
      usuario: {
        id: 'u-1',
        nombre: 'Katherin Mendoza',
        rol: 'RECEPCION',
        sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca',
    })
    mockApi()
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('renderiza el encabezado oficial, badge Junín y los 4 KPI cards con datos reales', async () => {
    await renderRuta('/app/pacientes')

    expect(await screen.findByRole('heading', { name: /Directorio de Pacientes/i })).toBeInTheDocument()
    expect(screen.getByText('Junín Sede-Network')).toBeInTheDocument()

    // 4 StatCards
    expect(screen.getByText(/POBLACIÓN TOTAL/i)).toBeInTheDocument()
    expect(screen.getByText(/NUEVOS ESTE MES/i)).toBeInTheDocument()
    expect(screen.getAllByText(/CUENTAS POR COBRAR/i).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/CUMPLEAÑOS DE/i)).toBeInTheDocument()
  })

  it('lista a los pacientes con sus identificadores, badges de inasistencia y origen', async () => {
    await renderRuta('/app/pacientes')

    // Pacientes en la tabla
    expect(await screen.findByText('Luciana Meza Cárdenas')).toBeInTheDocument()
    expect(screen.getByText('Gonzalo Astuvilca Ramos')).toBeInTheDocument()
    expect(screen.getByText('Pamela Flores Paucar')).toBeInTheDocument()

    // Badges de alerta médica y trazabilidad
    expect(screen.getByText(/2 inasistencias/i)).toBeInTheDocument()
    expect(screen.getByText('Villamax 3D')).toBeInTheDocument()
    expect(screen.getByText('Cumpleañero')).toBeInTheDocument()
  })

  it('presenta un saldo negativo como crédito a favor, no como deuda', async () => {
    mockApi([{ ...PACIENTE_3, saldoPendiente: -1500, tieneDeuda: true }])
    await renderRuta('/app/pacientes')

    const fila = (await screen.findByText('Pamela Flores Paucar')).closest('tr')
    if (!fila) throw new Error('No se encontró la fila de la paciente')
    expect(within(fila).getByText('Saldo a favor S/ 1,500.00')).toHaveClass('text-emerald-700')
    expect(within(fila).queryByText(/Deuda:/)).not.toBeInTheDocument()
  })

  it('el modal de registro rápido pide solo lo de recepción: sin correo ni texto legal de la Ley 29733', async () => {
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/pacientes')

    await user.click(await screen.findByRole('button', { name: /\+ Registrar nuevo paciente/i }))

    expect(await screen.findByRole('heading', { name: /Registrar Nuevo Paciente/i })).toBeInTheDocument()
    for (const campo of [/DNI \(8 dígitos\)/, /Nombres/, /Apellidos/, /Fecha de Nacimiento/, /Género/, /Sede de Registro/, /Teléfono WhatsApp/, /¿Cómo nos conoció\?/, /Alergias conocidas/, /Antecedentes médicos/]) {
      expect(screen.getByLabelText(campo)).toBeInTheDocument()
    }
    expect(screen.queryByLabelText(/Correo Electrónico/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Ley 29733 de Protección de Datos Personales/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /Ley 29733|consentimiento/i })).not.toBeInTheDocument()
  })

  it('un paciente sin atenciones es "Nuevo" y solo se ofrece "Reactivar" a quien concluyó un tratamiento hace más de 6 meses', { timeout: 20_000 }, async () => {
    const hace = (dias: number) => new Date(Date.now() - dias * 86_400_000).toISOString().slice(0, 10)
    mockApi([
      { ...PACIENTE_1, ultimaCitaAtendida: undefined },
      { ...PACIENTE_2, ultimaCitaAtendida: hace(250) },
      { ...PACIENTE_3, ultimaCitaAtendida: hace(300) },
    ])
    servidor.use(
      http.get(`${API}/tratamientos/sede/sede-chilca`, () =>
        HttpResponse.json([{ id: 't-fin', pacienteId: PACIENTE_2.id, nombre: 'Limpieza', estado: 'COMPLETADO' }]),
      ),
    )
    await renderRuta('/app/pacientes')
    const fila = async (nombre: string) => (await screen.findByRole('link', { name: nombre })).closest('tr') as HTMLElement

    const nueva = await fila('Luciana Meza Cárdenas')
    expect(within(nueva).getByText('● Nuevo')).toBeInTheDocument()
    expect(within(nueva).queryByText('● Inactivo')).not.toBeInTheDocument()
    expect(within(nueva).queryByRole('button', { name: 'Reactivar' })).not.toBeInTheDocument()

    // Concluyó un tratamiento y no viene hace 250 días: sí.
    await waitFor(async () => expect(within(await fila('Gonzalo Astuvilca Ramos')).getByRole('button', { name: 'Reactivar' })).toBeInTheDocument(), { timeout: 8000 })
    // 300 días sin venir pero sin tratamiento concluido: no.
    expect(within(await fila('Pamela Flores Paucar')).queryByRole('button', { name: 'Reactivar' })).not.toBeInTheDocument()
  })

  it('el administrador general con dos negocios solo consulta los tratamientos de las sedes del negocio activo', async () => {
    const consultadas: string[] = []
    servidor.use(
      http.get(`${API}/tratamientos/sede/:sedeId`, ({ params }) => {
        consultadas.push(String(params.sedeId))
        return HttpResponse.json([])
      }),
    )
    guardarSesion({
      token: 'token-admin',
      usuario: {
        id: 'u-admin',
        nombre: 'Administrador General',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
          { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
        ],
      },
      sedeActivaId: 'sede-chilca',
      negocioActivo: 'VILLADENT',
    })
    await renderRuta('/app/pacientes')
    await waitFor(() => expect(consultadas).toContain('sede-chilca'))

    expect(consultadas).not.toContain('sede-villamax-chilca')
  })

  it('"Exportar reporte" imprime un documento formal (PDF) con membrete, resumen, ranking y la tabla de pacientes', async () => {
    const user = userEvent.setup({ delay: null })
    vi.mocked(imprimir.imprimirDocumento).mockClear()
    servidor.use(
      http.get(`${API}/tratamientos/sede/sede-chilca`, () =>
        HttpResponse.json([
          { id: 't1', pacienteId: PACIENTE_1.id, nombre: 'Ortodoncia', estado: 'EN_PROCESO' },
          { id: 't2', pacienteId: PACIENTE_1.id, nombre: 'Limpieza', estado: 'COMPLETADO' },
          { id: 't3', pacienteId: PACIENTE_2.id, nombre: 'Limpieza', estado: 'COMPLETADO' },
          { id: 't4', pacienteId: PACIENTE_2.id, nombre: 'Implante', estado: 'PLANIFICADO' },
        ]),
      ),
    )
    await renderRuta('/app/pacientes')
    await user.click(await screen.findByRole('button', { name: /Exportar reporte/ }))

    await waitFor(() => expect(imprimir.imprimirDocumento).toHaveBeenCalledTimes(1))
    const html = vi.mocked(imprimir.imprimirDocumento).mock.calls[0][0]
    // Encabezado formal.
    expect(html).toContain('Centro Odontológico Villadent &amp; Centro Tomográfico Villamax 3D')
    expect(html).toContain('Sede activa: Villadent Chilca')
    expect(html).toMatch(/Fecha de emisión: \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}.*\(hora de Lima\)/)
    // Resumen clínico: 3 pacientes listados; Luciana en tratamiento, Gonzalo con tratamiento concluido, Pamela sin ninguno.
    expect(html).toMatch(/<strong>3<\/strong><span>Pacientes registrados/)
    expect(html).toMatch(/<strong>1<\/strong><span>En tratamiento activo/)
    expect(html).toMatch(/<strong>1<\/strong><span>Con tratamiento concluido/)
    expect(html).toMatch(/<strong>1<\/strong><span>Sin tratamientos/)
    // Ranking y tabla.
    expect(html).toContain('<td class="num">1°</td><td>Limpieza</td><td class="num">2</td>')
    expect(html).toContain('<td class="num">2°</td><td>Ortodoncia</td><td class="num">1</td>')
    expect(html).toContain('<td>45908123</td><td>Luciana Meza Cárdenas</td><td>964128340</td><td>Villadent Chilca</td><td>Ortodoncia</td><td>En proceso</td>')
    expect(html).toContain('<td>40918742</td><td>Gonzalo Astuvilca Ramos</td>')
    // El plan solo planificado no cuenta como tratamiento realizado.
    expect(html).not.toContain('Implante')
    // Ya no se descarga un archivo.
    expect(descargas.descargarArchivo).not.toHaveBeenCalled()
    expect(await screen.findByText(/Reporte listo para guardar como PDF: 3 pacientes/)).toBeInTheDocument()
  })

  it('si no se pueden leer los tratamientos, avisa y no imprime un reporte incompleto', async () => {
    const user = userEvent.setup({ delay: null })
    vi.mocked(imprimir.imprimirDocumento).mockClear()
    servidor.use(http.get(`${API}/tratamientos/sede/sede-chilca`, () => new HttpResponse(null, { status: 500 })))
    await renderRuta('/app/pacientes')
    await user.click(await screen.findByRole('button', { name: /Exportar reporte/ }))
    expect(await screen.findByText('No se pudo exportar el reporte')).toBeInTheDocument()
    expect(imprimir.imprimirDocumento).not.toHaveBeenCalled()
  })

  it('si el navegador no puede abrir la impresión lo dice', async () => {
    const user = userEvent.setup({ delay: null })
    vi.mocked(imprimir.imprimirDocumento).mockRejectedValueOnce(new imprimir.ErrorImpresion('El navegador no pudo abrir la impresión.'))
    await renderRuta('/app/pacientes')
    await user.click(await screen.findByRole('button', { name: /Exportar reporte/ }))
    expect(await screen.findByText('El navegador no pudo abrir la impresión.')).toBeInTheDocument()
  })

  it('registrar un paciente no envía correo ni consentimiento de promociones', async () => {
    const user = userEvent.setup({ delay: null })
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.post(`${API}/pacientes`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...PACIENTE_1, id: 'nuevo', dni: '12345678' })
      }),
    )
    await renderRuta('/app/pacientes')
    await user.click(await screen.findByRole('button', { name: /\+ Registrar nuevo paciente/i }))
    await user.type(await screen.findByLabelText(/DNI \(8 dígitos\)/), '12345678')
    await user.type(screen.getByLabelText(/Nombres/), 'Carlos')
    await user.type(screen.getByLabelText(/Apellidos/), 'Huamán Baldeón')
    await user.type(screen.getByLabelText(/Teléfono WhatsApp/), '964123456')
    await user.click(screen.getByRole('button', { name: 'Registrar Paciente' }))

    await waitFor(() => expect(cuerpo).not.toBeNull())
    expect(cuerpo).toMatchObject({ dni: '12345678', nombres: 'Carlos', apellidos: 'Huamán Baldeón', telefono: '964123456' })
    expect(cuerpo).not.toHaveProperty('email')
    expect(cuerpo).not.toHaveProperty('consentimientoPromociones')
  })
})
