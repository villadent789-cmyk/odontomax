import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { RegistrarMovimientoModal } from './RegistrarMovimientoModal'
import type { ProductoInventarioDTO } from './api-inventario'

const PRODUCTO_MOCK: ProductoInventarioDTO = {
  id: 'a0000000-0000-4000-8000-000000000001',
  codigo: 'MAT-001',
  nombre: 'Anestesia Lidocaína 2%',
  stock_minimo: 10,
  activo: true,
  stockPorSede: { 'sede-chilca-1': 50 },
  stockTotal: 50,
  alertaStockBajo: false,
}

const SEDES_MOCK = [{ id: 'sede-chilca-1', nombre: 'Sede Chilca' }]

describe('RegistrarMovimientoModal', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })
  })

  it('limpia la fecha de entrega al cerrar y reabrir el modal', async () => {
    const user = userEvent.setup()

    function Wrapper({ abierto }: { abierto: boolean }) {
      return (
        <QueryClientProvider client={queryClient}>
          <RegistrarMovimientoModal
            abierto={abierto}
            onCerrar={() => {}}
            productoInicial={PRODUCTO_MOCK}
            productos={[PRODUCTO_MOCK]}
            sedeDefecto="sede-chilca-1"
            sedesDisponibles={SEDES_MOCK}
          />
        </QueryClientProvider>
      )
    }

    const { rerender } = render(<Wrapper abierto={true} />)

    const inputEntrega = screen.getByLabelText(/fecha de entrega/i)
    expect(inputEntrega).toHaveValue('')

    await user.type(inputEntrega, '2026-11-15')
    expect(inputEntrega).toHaveValue('2026-11-15')

    // Cerrar el modal
    rerender(<Wrapper abierto={false} />)
    expect(screen.queryByLabelText(/fecha de entrega/i)).not.toBeInTheDocument()

    // Reabrir el modal
    rerender(<Wrapper abierto={true} />)
    const inputEntregaReabierto = screen.getByLabelText(/fecha de entrega/i)
    expect(inputEntregaReabierto).toHaveValue('')
  })
})
