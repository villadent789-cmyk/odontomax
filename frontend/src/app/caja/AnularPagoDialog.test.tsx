import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { API, HttpResponse, servidor } from '@/test/msw'
import { anularPago } from './api-caja'
import { AnularPagoDialog } from './AnularPagoDialog'

const PAGO = { comprobante: 'R001-000007', monto: 80, metodoPago: 'EFECTIVO' }

function montar(extra: { error?: string | null; enviando?: boolean } = {}) {
  const onConfirmar = vi.fn()
  const alCerrar = vi.fn()
  render(<AnularPagoDialog pago={PAGO} enviando={extra.enviando ?? false} error={extra.error ?? null} alCerrar={alCerrar} onConfirmar={onConfirmar} />)
  return { onConfirmar, alCerrar }
}

describe('Anular un comprobante', () => {
  it('con un motivo rápido ya se puede confirmar, y el detalle es opcional', async () => {
    const { onConfirmar } = montar()
    await userEvent.selectOptions(screen.getByLabelText('Motivo'), 'Método de pago equivocado (Yape, efectivo, tarjeta)')
    await userEvent.type(screen.getByLabelText(/Detalle/), 'Era Yape')
    await userEvent.click(screen.getByRole('button', { name: 'Anular comprobante' }))

    expect(onConfirmar).toHaveBeenCalledWith('Método de pago equivocado (Yape, efectivo, tarjeta): Era Yape')
  })

  it('el motivo rápido solo también vale', async () => {
    const { onConfirmar } = montar()
    await userEvent.click(screen.getByRole('button', { name: 'Anular comprobante' }))

    expect(onConfirmar).toHaveBeenCalledWith('Error en el monto')
  })

  it('"Otro motivo" exige escribir el detalle', async () => {
    const { onConfirmar } = montar()
    await userEvent.selectOptions(screen.getByLabelText('Motivo'), 'Otro motivo')
    await userEvent.click(screen.getByRole('button', { name: 'Anular comprobante' }))

    expect(await screen.findByText(/Escribe el motivo/)).toBeInTheDocument()
    expect(onConfirmar).not.toHaveBeenCalled()
  })

  it('muestra el error del servidor dentro del diálogo', () => {
    montar({ error: 'No se puede anular un pago en EFECTIVO porque no existe ninguna caja abierta' })

    expect(screen.getByText(/no existe ninguna caja abierta/)).toBeInTheDocument()
  })
})

describe('anularPago (API)', () => {
  it('envía el motivo con el nombre que espera el servidor: motivoAnulacion', async () => {
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.post(`${API}/pagos/:id/anular`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'p-1', anulado: true })
      }),
    )

    await anularPago('p-1', 'Cobro duplicado')

    expect(cuerpo).toEqual({ motivoAnulacion: 'Cobro duplicado' })
  })
})
