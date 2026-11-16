import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion, type SedeSesion, type Sesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, http, servidor } from '@/test/msw'
import { RegistrarPacienteModal } from './RegistrarPacienteModal'

const SEDES_PRUEBA: SedeSesion[] = [
  { id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' },
  { id: 'sede-centro', nombre: 'Sede Huancayo Centro', marca: 'VILLADENT' },
  { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
]

function sesion(negocio: 'VILLADENT' | 'VILLAMAX'): Sesion {
  return {
    token: 'jwt-prueba',
    usuario: {
      nombre: 'Admin General',
      rol: 'ADMIN_GENERAL',
      sedes: SEDES_PRUEBA,
    },
    sedeActivaId: negocio === 'VILLAMAX' ? 'sede-villamax-chilca' : 'sede-chilca',
    negocioActivo: negocio,
  }
}

function renderizar(negocio: 'VILLADENT' | 'VILLAMAX', alGuardar = vi.fn()) {
  guardarSesion(sesion(negocio))
  servidor.use(
    http.get(`${API}/sedes`, () => {
      return HttpResponse.json([
        { id: 'sede-chilca', nombre: 'Sede Chilca', unidadDeNegocio: 'VILLADENT', activa: true },
        { id: 'sede-centro', nombre: 'Sede Huancayo Centro', unidadDeNegocio: 'VILLADENT', activa: true },
        { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca (Radiología 3D)', unidadDeNegocio: 'VILLAMAX', activa: true },
      ])
    }),
  )
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    ...render(
      <QueryClientProvider client={qc}>
        <RegistrarPacienteModal abierto={true} alCerrar={vi.fn()} alGuardar={alGuardar} />
      </QueryClientProvider>,
    ),
    qc,
  }
}

describe('RegistrarNuevoPacienteModal según negocio activo', () => {
  beforeEach(() => {
    sessionStorage.clear()
    _reiniciarCacheSesion()
  })
  afterEach(() => {
    limpiarSesion()
  })

  it('en Villadent muestra subtítulo odontológico, sedes de Villadent sin sufijos y campos clínicos', async () => {
    renderizar('VILLADENT')

    // Subtítulo
    expect(
      screen.getByText(/Apertura de historia clínica digital para Villadent/i),
    ).toBeInTheDocument()

    // Selector de sede con solo sedes de Villadent, sin sufijo (VILLADENT)
    const selectSede = (await screen.findByLabelText(/Sede de Registro \*/i)) as HTMLSelectElement
    expect(selectSede.tagName).toBe('SELECT')
    await waitFor(() => {
      const opciones = Array.from(selectSede.options).map((o) => o.textContent?.trim())
      expect(opciones).toEqual(['Sede Chilca', 'Sede Huancayo Centro'])
    })

    const opciones = Array.from(selectSede.options).map((o) => o.textContent?.trim())
    for (const op of opciones) {
      expect(op).not.toMatch(/\(VILLADENT\)|\(VILLAMAX\)/)
    }

    // Campos odontológicos visibles
    expect(screen.getByLabelText(/Alergias conocidas/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Antecedentes médicos relevantes/i)).toBeInTheDocument()
    expect(screen.getByText(/¿Es paciente gestante\?/i)).toBeInTheDocument()
  })

  it('en Villamax muestra subtítulo radiológico, sede fija sin selector y oculta campos odontológicos', async () => {
    renderizar('VILLAMAX')

    // Subtítulo
    expect(
      screen.getByText(/Apertura de historia clínica digital para Villamax/i),
    ).toBeInTheDocument()

    // Sede fija sin selector
    expect(screen.queryByRole('combobox', { name: /Sede de Registro/i })).not.toBeInTheDocument()
    const inputFijo = (await screen.findByLabelText(/Sede de Registro \*/i)) as HTMLInputElement
    expect(inputFijo.tagName).toBe('INPUT')
    expect(inputFijo).toHaveAttribute('readonly')
    await waitFor(() => {
      expect(inputFijo.value).toBe('Villamax Chilca (Radiología 3D)')
    })

    // Campos odontológicos NO se muestran
    expect(screen.queryByLabelText(/Alergias conocidas/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Antecedentes médicos relevantes/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/¿Es paciente gestante\?/i)).not.toBeInTheDocument()
  })

  it('la fecha de nacimiento se escribe con el teclado (no es un calendario) y se envía en formato ISO', async () => {
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.post(`${API}/pacientes`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'p-nuevo', ...cuerpo }, { status: 201 })
      }),
    )
    const alGuardar = vi.fn()
    renderizar('VILLADENT', alGuardar)

    const fecha = screen.getByLabelText(/Fecha de Nacimiento/i)
    expect(fecha).toHaveAttribute('type', 'text')
    await userEvent.type(screen.getByLabelText(/DNI \(8 dígitos\)/i), '71213266')
    await userEvent.type(screen.getByLabelText(/Nombres/i), 'Gustavo')
    await userEvent.type(screen.getByLabelText(/Apellidos/i), 'Machuca Peñaloza')
    await userEvent.type(fecha, '15031975')
    expect(fecha).toHaveValue('15/03/1975')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar Paciente' }))

    await waitFor(() => expect(alGuardar).toHaveBeenCalled())
    expect(cuerpo).toMatchObject({ dni: '71213266', fechaNacimiento: '1975-03-15' })
  })

  it('con una fecha de nacimiento que no existe no registra al paciente y lo avisa', async () => {
    let llamadas = 0
    servidor.use(http.post(`${API}/pacientes`, () => { llamadas += 1; return HttpResponse.json({}, { status: 201 }) }))
    renderizar('VILLADENT')

    await userEvent.type(screen.getByLabelText(/DNI \(8 dígitos\)/i), '71213266')
    await userEvent.type(screen.getByLabelText(/Nombres/i), 'Gustavo')
    await userEvent.type(screen.getByLabelText(/Apellidos/i), 'Machuca')
    await userEvent.type(screen.getByLabelText(/Fecha de Nacimiento/i), '31021980')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar Paciente' }))

    expect((await screen.findAllByText(/no existe/i)).length).toBeGreaterThan(0)
    expect(llamadas).toBe(0)
  })
})
