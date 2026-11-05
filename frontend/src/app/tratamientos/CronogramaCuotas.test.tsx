import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CronogramaCuotas } from './CronogramaCuotas'

describe('Cronograma de cuotas', () => {
  it('no dibuja nada si el plan no es en cuotas', () => {
    const { container } = render(<CronogramaCuotas cuotas={[]} vencidas={0} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('lista cada cuota con su estado y avisa de las vencidas', () => {
    render(
      <CronogramaCuotas
        vencidas={1}
        cuotas={[
          { numero: 0, concepto: 'Cuota inicial', venceEn: '2026-10-01', monto: 600, pagado: 600, saldo: 0, estado: 'PAGADA' },
          { numero: 1, concepto: 'Cuota 1', venceEn: '2026-11-01', monto: 120, pagado: 0, saldo: 120, estado: 'VENCIDA' },
          { numero: 2, concepto: 'Cuota 2', venceEn: '2099-12-01', monto: 120, pagado: 0, saldo: 120, estado: 'PENDIENTE' },
        ]}
      />,
    )

    expect(screen.getByText('1 cuota vencida')).toBeInTheDocument()
    expect(screen.getByText('Pagada')).toBeInTheDocument()
    expect(screen.getByText('Vencida')).toBeInTheDocument()
    expect(screen.getByText('Pendiente')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(4)
  })
})
