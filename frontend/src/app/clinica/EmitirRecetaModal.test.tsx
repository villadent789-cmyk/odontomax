import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Toaster } from '@/design-system'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { EmitirRecetaModal, type PacienteReceta } from './EmitirRecetaModal'

const PACIENTE: PacienteReceta = { id: 'p-1', nombreCompleto: 'Luciana Meza Cárdenas', dni: '45908123', edad: 29 }

function sesion() {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-doc', nombre: 'Dra. Elena Paucar', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

let recetas: Record<string, unknown>[] = []
let descargas: { nombre: string; tamano: number }[] = []

function api(opciones: { cop?: string; respuesta?: () => Response } = {}) {
  recetas = []
  servidor.use(
    http.get(`${API}/especialistas`, () =>
      HttpResponse.json([
        { id: 'e-1', usuarioId: 'u-doc', nombre: 'Dra. Elena Paucar', especialidad: 'Rehabilitación oral', cop: opciones.cop, sedes: ['sede-chilca'], activo: true },
      ]),
    ),
    http.post(`${API}/recetas/pdf`, async ({ request }) => {
      recetas.push((await request.json()) as Record<string, unknown>)
      return opciones.respuesta?.() ?? new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } })
    }),
  )
}

function montar() {
  const alCerrar = vi.fn()
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <EmitirRecetaModal abierto alCerrar={alCerrar} paciente={PACIENTE} />
      <Toaster />
    </QueryClientProvider>,
  )
  return { alCerrar }
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  sesion()
  descargas = []
  URL.createObjectURL = vi.fn((b: Blob) => {
    descargas.push({ nombre: '', tamano: b.size })
    return 'blob:receta'
  })
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    descargas[descargas.length - 1].nombre = this.download
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  limpiarSesion()
})

async function llenarMedicamento(n = 1, nombre = 'Amoxicilina 500mg') {
  await userEvent.type(screen.getByLabelText(`Fármaco / Presentación ${n}`), nombre)
}

// Escribe ~100 caracteres con userEvent: bajo la carga de la suite completa 15 s se quedaba corto.
describe('Emitir receta médica', { timeout: 30_000 }, () => {
  it('precarga paciente, sede, profesional de la sesión y COP del perfil de especialista', async () => {
    api({ cop: '12345' })
    montar()
    const dialogo = await screen.findByRole('dialog', { name: 'Emitir receta médica' })
    const d = within(dialogo)
    expect(d.getByText('Luciana Meza Cárdenas')).toBeInTheDocument()
    expect(d.getByText('DNI 45908123')).toBeInTheDocument()
    expect(d.getByText('29 años')).toBeInTheDocument()
    expect(d.getByText('Villadent Chilca')).toBeInTheDocument()
    expect(d.getByLabelText('Profesional tratante')).toHaveValue('Dra. Elena Paucar')
    await waitFor(() => expect(d.getByLabelText(/COP/)).toHaveValue('12345'))
  })

  it('sin medicamentos no llama al backend y avisa', async () => {
    api()
    montar()
    await userEvent.click(await screen.findByRole('button', { name: 'Descargar Receta PDF' }))
    expect(await screen.findByText('Agrega al menos un medicamento.')).toBeInTheDocument()
    expect(recetas).toEqual([])
  })

  it('el fármaco es obligatorio: una fila con dosis pero sin fármaco no se envía', async () => {
    api()
    montar()
    await userEvent.type(await screen.findByLabelText('Dosis 1'), '1 cápsula')
    await userEvent.click(screen.getByRole('button', { name: 'Descargar Receta PDF' }))
    expect(await screen.findByText('Indica el fármaco o presentación.')).toBeInTheDocument()
    expect(recetas).toEqual([])
  })

  it('exige el nombre del profesional tratante', async () => {
    api()
    montar()
    await llenarMedicamento()
    await userEvent.clear(await screen.findByLabelText('Profesional tratante'))
    await userEvent.click(screen.getByRole('button', { name: 'Descargar Receta PDF' }))
    expect(await screen.findByText('Escribe el nombre del profesional tratante.')).toBeInTheDocument()
    expect(recetas).toEqual([])
  })

  it('envía POST /recetas/pdf con los datos, descarga receta-{paciente}.pdf, avisa y cierra', async () => {
    api({ cop: '12345' })
    const { alCerrar } = montar()
    await waitFor(() => expect(screen.getByLabelText(/COP/)).toHaveValue('12345'))

    await userEvent.type(screen.getByLabelText('Diagnóstico (opcional)'), 'Pulpitis irreversible pieza 16')
    await llenarMedicamento()
    await userEvent.type(screen.getByLabelText('Dosis 1'), '1 cápsula')
    await userEvent.type(screen.getByLabelText('Frecuencia 1'), 'Cada 8 horas')
    await userEvent.type(screen.getByLabelText('Duración 1'), 'Por 7 días')
    await userEvent.type(screen.getByLabelText('Indicaciones 1'), 'Después de las comidas')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar medicamento' }))
    await llenarMedicamento(2, 'Ibuprofeno 400mg')
    await userEvent.type(screen.getByLabelText('Indicaciones generales (opcional)'), 'Dieta blanda por 24 horas')
    await userEvent.click(screen.getByRole('button', { name: 'Descargar Receta PDF' }))

    await waitFor(() => expect(alCerrar).toHaveBeenCalled())
    expect(recetas).toHaveLength(1)
    expect(recetas[0]).toMatchObject({
      pacienteId: 'p-1',
      pacienteNombre: 'Luciana Meza Cárdenas',
      pacienteDni: '45908123',
      pacienteEdad: 29,
      doctorNombre: 'Dra. Elena Paucar',
      doctorCop: '12345',
      sedeNombre: 'Villadent Chilca',
      diagnostico: 'Pulpitis irreversible pieza 16',
      indicacionesGenerales: 'Dieta blanda por 24 horas',
      medicamentos: [
        { medicamento: 'Amoxicilina 500mg', dosis: '1 cápsula', frecuencia: 'Cada 8 horas', duracion: 'Por 7 días', indicaciones: 'Después de las comidas' },
        { medicamento: 'Ibuprofeno 400mg' },
      ],
    })
    expect(recetas[0].fecha).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(descargas).toEqual([{ nombre: 'receta-luciana-meza-cardenas.pdf', tamano: 4 }])
    expect(await screen.findByText('Receta descargada con éxito')).toBeInTheDocument()
  })

  it('solo se puede quitar un medicamento cuando hay más de uno', async () => {
    api()
    montar()
    await screen.findByLabelText('Fármaco / Presentación 1')
    expect(screen.queryByRole('button', { name: /Quitar medicamento/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Agregar medicamento' }))
    expect(screen.getAllByRole('button', { name: /Quitar medicamento/ })).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: 'Quitar medicamento 2' }))
    expect(screen.queryByLabelText('Fármaco / Presentación 2')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Quitar medicamento/ })).not.toBeInTheDocument()
  })

  it('si el backend rechaza la receta muestra su mensaje, no descarga y deja el modal abierto', async () => {
    api({ respuesta: () => errorBackend(400, 'El nombre del profesional tratante es obligatorio') })
    const { alCerrar } = montar()
    await llenarMedicamento()
    await userEvent.click(await screen.findByRole('button', { name: 'Descargar Receta PDF' }))
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('El nombre del profesional tratante es obligatorio')
    expect(descargas).toEqual([])
    expect(alCerrar).not.toHaveBeenCalled()
  })
})
