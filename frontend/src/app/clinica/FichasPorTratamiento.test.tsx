import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { FichasPorTratamiento } from './FichasPorTratamiento'
import type { PlantillaTratamiento, RegistroPlantilla } from './api-plantillas'

const RESTAURACION: PlantillaTratamiento = {
  codigo: 'RESTAURACION',
  nombre: 'Restauración',
  negocio: 'VILLADENT',
  descripcion: 'Resina o ionómero en una pieza.',
  version: 1,
  esquema: {
    secciones: [
      {
        titulo: 'Pieza',
        campos: [
          { id: 'pieza', etiqueta: 'Pieza dental (FDI)', tipo: 'pieza', obligatorio: true },
          { id: 'caras', etiqueta: 'Caras', tipo: 'multiple', opciones: ['Oclusal', 'Mesial'] },
        ],
      },
      { titulo: 'Procedimiento', campos: [{ id: 'material', etiqueta: 'Material y marca', tipo: 'texto' }] },
    ],
  },
}

const CERRADA: RegistroPlantilla = {
  id: 'r-1',
  pacienteId: 'p-1',
  plantillaCodigo: 'RESTAURACION',
  plantillaNombre: 'Restauración',
  plantillaVersion: 1,
  datos: { pieza: 24, material: 'Resina A2' },
  estado: 'CERRADA' as never,
  creadoEn: '2026-10-20T15:00:00Z',
  creadoPorNombre: 'Doctor B',
}

let registros: RegistroPlantilla[] = []
let cuerpos: Record<string, unknown>[] = []

function api(opciones: { falloAlCerrar?: boolean } = {}) {
  cuerpos = []
  servidor.use(
    http.get(`${API}/plantillas`, () => HttpResponse.json([RESTAURACION])),
    http.get(`${API}/plantillas/registros/paciente/p-1`, () => HttpResponse.json(registros)),
    http.post(`${API}/plantillas/registros`, async ({ request }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      cuerpos.push(cuerpo)
      const nuevo: RegistroPlantilla = { ...CERRADA, id: 'r-nuevo', estado: 'BORRADOR', datos: cuerpo.datos as never }
      registros = [nuevo, ...registros]
      return HttpResponse.json(nuevo, { status: 201 })
    }),
    http.post(`${API}/plantillas/registros/:id/cerrar`, () =>
      opciones.falloAlCerrar ? errorBackend(400, 'Falta completar: Pieza dental (FDI)') : HttpResponse.json({ ...CERRADA, id: 'r-nuevo', estado: 'CERRADO' }),
    ),
  )
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <FichasPorTratamiento pacienteId="p-1" negocio="VILLADENT" planesEnCurso={[{ id: 't-1', nombre: 'Restauración LISS simple' }]} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  registros = []
})

describe('Fichas por tratamiento', { timeout: 15_000 }, () => {
  it('sin fichas lo dice, y una nueva ficha se arma con el formulario de su plantilla y se guarda como borrador', async () => {
    api()
    const user = userEvent.setup({ delay: null })
    montar()
    expect(await screen.findByText('Aún no hay fichas de tratamiento para este paciente.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '+ Nueva ficha' }))
    const elegir = within(await screen.findByRole('dialog', { name: 'Nueva ficha de tratamiento' }))
    // La plantilla se sugiere porque coincide con un plan en curso del paciente.
    expect(elegir.getByText(/Coincide con el plan en curso: Restauración LISS simple/)).toBeInTheDocument()
    await user.click(elegir.getByRole('button', { name: /Restauración/ }))

    const editor = within(await screen.findByRole('dialog', { name: 'Restauración' }))
    await user.type(editor.getByLabelText('Pieza dental (FDI)'), '24')
    await user.click(editor.getByRole('button', { name: 'Mesial' }))
    await user.type(editor.getByLabelText('Material y marca'), 'Resina A2')
    await user.click(editor.getByRole('button', { name: 'Guardar borrador' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0]).toMatchObject({
      pacienteId: 'p-1',
      plantillaCodigo: 'RESTAURACION',
      tratamientoId: 't-1',
      datos: { pieza: 24, caras: ['Mesial'], material: 'Resina A2' },
    })
  })

  it('si el servidor no deja cerrar la ficha, muestra el motivo y no la cierra', async () => {
    api({ falloAlCerrar: true })
    const user = userEvent.setup({ delay: null })
    montar()
    const nueva = await screen.findByRole('button', { name: '+ Nueva ficha' })
    await waitFor(() => expect(nueva).toBeEnabled())
    await user.click(nueva)
    await user.click(await screen.findByRole('button', { name: /Restauración/ }))
    const editor = within(await screen.findByRole('dialog', { name: 'Restauración' }))
    await user.click(editor.getByRole('button', { name: 'Cerrar ficha' }))

    expect(await editor.findByRole('alert')).toHaveTextContent('Falta completar: Pieza dental (FDI)')
  })

  it('una ficha cerrada se ve en solo lectura y ofrece corregir', async () => {
    registros = [{ ...CERRADA, estado: 'CERRADO' }]
    api()
    const user = userEvent.setup({ delay: null })
    montar()
    expect(await screen.findByText('Cerrada')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ver' }))
    const editor = within(await screen.findByRole('dialog', { name: 'Restauración' }))
    expect(editor.getByLabelText('Pieza dental (FDI)')).toBeDisabled()
    expect(editor.getByLabelText('Pieza dental (FDI)')).toHaveValue(24)
    expect(editor.queryByRole('button', { name: 'Cerrar ficha' })).not.toBeInTheDocument()
  })
})
