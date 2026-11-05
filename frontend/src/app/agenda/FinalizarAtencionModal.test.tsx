import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { cita } from '@/test/msw-agenda'
import { FinalizarAtencionModal } from './FinalizarAtencionModal'

const AVISO = 'Aviso: La caja de la sede no está abierta. Se abrirá automáticamente con saldo inicial S/ 0.00 al registrar el cobro.'
const CITA = cita({ estado: 'EN_SALA', sedeId: 'sede-chilca' })

type Caja = 'cerrada' | 'abierta' | 'error'

let llamadas: string[] = []

function api(caja: Caja, opciones: { pagoFalla?: boolean } = {}) {
  llamadas = []
  servidor.use(
    http.get(`${API}/caja/activa`, ({ request }) => {
      llamadas.push(`caja:${new URL(request.url).searchParams.get('sedeId')}`)
      if (caja === 'error') return errorBackend(500, 'Fallo temporal del servidor')
      return caja === 'abierta'
        ? HttpResponse.json({ id: 'c-1', sedeId: 'sede-chilca', estado: 'ABIERTA' })
        : new HttpResponse(null, { status: 204 })
    }),
    http.post(`${API}/pagos`, () => {
      llamadas.push('pago')
      return opciones.pagoFalla ? errorBackend(400, 'No hay una caja abierta en la sede') : HttpResponse.json({ id: 'pg-1' })
    }),
    http.post(`${API}/evoluciones/pacientes/:id`, () => {
      llamadas.push('nota')
      return HttpResponse.json({ id: 'n-1' })
    }),
    http.patch(`${API}/citas/:id/estado`, () => {
      llamadas.push('estado')
      return HttpResponse.json({ ...CITA, estado: 'ATENDIDA' })
    }),
  )
}

function montar(abierto = true) {
  const alCerrar = vi.fn()
  const onActualizada = vi.fn()
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <FinalizarAtencionModal cita={CITA} abierto={abierto} alCerrar={alCerrar} onActualizada={onActualizada} />
    </QueryClientProvider>,
  )
  return { alCerrar, onActualizada }
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Dra. Elena Paucar', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})

describe('Módulo 6 · Cobro en sillón con la caja cerrada', { timeout: 15_000 }, () => {
  it('con la caja cerrada y un monto escrito avisa que primero debe abrirse', async () => {
    api('cerrada')
    montar()
    await waitFor(() => expect(llamadas).toContain('caja:sede-chilca'))
    // Sin monto todavía: no hay nada que advertir.
    expect(screen.queryByText(AVISO)).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Monto (S/)'), '80')
    expect(await screen.findByText(AVISO)).toBeInTheDocument()

    // Si borra el monto (atender sin cobrar) el aviso desaparece.
    await userEvent.clear(screen.getByLabelText('Monto (S/)'))
    expect(screen.queryByText(AVISO)).not.toBeInTheDocument()
  })

  it('con la caja abierta no muestra el aviso', async () => {
    api('abierta')
    montar()
    await waitFor(() => expect(llamadas).toContain('caja:sede-chilca'))
    await userEvent.type(screen.getByLabelText('Monto (S/)'), '80')
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByText(AVISO)).not.toBeInTheDocument()
  })

  it('si la consulta de caja falla no inventa un aviso', async () => {
    api('error')
    montar()
    await waitFor(() => expect(llamadas).toContain('caja:sede-chilca'))
    await userEvent.type(screen.getByLabelText('Monto (S/)'), '80')
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByText(AVISO)).not.toBeInTheDocument()
  })

  it('con el modal cerrado no consulta la caja', async () => {
    api('cerrada')
    montar(false)
    await new Promise((r) => setTimeout(r, 50))
    expect(llamadas).toEqual([])
  })

  it('si el cobro falla por caja cerrada no guarda la nota ni marca la cita atendida', async () => {
    api('cerrada', { pagoFalla: true })
    const { alCerrar, onActualizada } = montar()
    await userEvent.type(screen.getByLabelText('Procedimiento realizado / Plan (P)'), 'Curación pieza 16')
    await userEvent.type(screen.getByLabelText('Monto (S/)'), '80')
    await userEvent.click(screen.getByRole('button', { name: 'Finalizar Atención' }))

    expect(await screen.findByText('No hay una caja abierta en la sede')).toBeInTheDocument()
    expect(llamadas.filter((l) => !l.startsWith('caja'))).toEqual(['pago'])
    expect(alCerrar).not.toHaveBeenCalled()
    expect(onActualizada).not.toHaveBeenCalled()
  })

  it('con la caja abierta cobra primero, luego guarda la nota y marca la cita atendida', async () => {
    api('abierta')
    const { alCerrar, onActualizada } = montar()
    await userEvent.type(screen.getByLabelText('Procedimiento realizado / Plan (P)'), 'Curación pieza 16')
    await userEvent.type(screen.getByLabelText('Monto (S/)'), '80')
    await userEvent.click(screen.getByRole('button', { name: 'Finalizar Atención' }))

    await waitFor(() => expect(alCerrar).toHaveBeenCalled())
    expect(llamadas.filter((l) => !l.startsWith('caja'))).toEqual(['pago', 'nota', 'estado'])
    expect(onActualizada).toHaveBeenCalledWith(expect.objectContaining({ estado: 'ATENDIDA' }))
  })

  it('con Villamax activo omite campos de nota clinica, precarga monto del estudio y permite cobrar con Efectivo, Yape y Plin', async () => {
    guardarSesion({
      token: 't',
      usuario: { id: 'u-admin', nombre: 'Admin General', rol: 'ADMIN_GENERAL', sedes: [{ id: 'sede-villamax-chilca', nombre: 'Sede Villamax Chilca', marca: 'VILLAMAX' }] },
      sedeActivaId: 'sede-villamax-chilca',
      negocioActivo: 'VILLAMAX',
    })
    api('abierta')
    servidor.use(
      http.get(`${API}/tratamientos/:id`, () =>
        HttpResponse.json({ id: 't-1', nombre: 'Radiografía Panorámica', saldoPendiente: 60 }),
      ),
    )
    const citaVillamax = cita({
      id: 'cita-vmx-1',
      estado: 'EN_ATENCION',
      sedeId: 'sede-villamax-chilca',
      motivoConsulta: 'Radiografía Panorámica',
      tratamientoId: 't-1',
    })
    const alCerrar = vi.fn()
    const onActualizada = vi.fn()
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={cliente}>
        <FinalizarAtencionModal cita={citaVillamax} abierto={true} alCerrar={alCerrar} onActualizada={onActualizada} />
      </QueryClientProvider>,
    )

    // No debe mostrar campos de nota clínica
    expect(screen.queryByLabelText(/Motivo \/ Queja del paciente/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Procedimiento realizado \/ Plan/i)).not.toBeInTheDocument()

    // Precarga el monto con el saldo pendiente (60.00)
    await waitFor(() => {
      expect(screen.getByLabelText('Monto (S/)')).toHaveValue(60)
    })

    // Opciones de método de pago: EFECTIVO, YAPE, PLIN
    const selectorMetodo = screen.getByLabelText('Método de pago')
    expect(within(selectorMetodo).getByRole('option', { name: 'Efectivo' })).toBeInTheDocument()
    expect(within(selectorMetodo).getByRole('option', { name: 'Yape' })).toBeInTheDocument()
    expect(within(selectorMetodo).getByRole('option', { name: 'Plin' })).toBeInTheDocument()

    // Cambiar a PLIN y finalizar
    await userEvent.selectOptions(selectorMetodo, 'PLIN')
    await userEvent.click(screen.getByRole('button', { name: 'Finalizar Atención' }))

    await waitFor(() => expect(alCerrar).toHaveBeenCalled())
    // No debe llamar a nota SOAP
    expect(llamadas.filter((l) => !l.startsWith('caja'))).toEqual(['pago', 'estado'])
    expect(onActualizada).toHaveBeenCalledWith(expect.objectContaining({ estado: 'ATENDIDA' }))
  })
})
