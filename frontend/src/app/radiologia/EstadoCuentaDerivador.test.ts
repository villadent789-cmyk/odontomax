import { describe, expect, it } from 'vitest'
import { documentoEstadoCuenta } from './documentoEstadoCuenta'

describe('Estado de cuenta del doctor que deriva', () => {
  const cuenta = {
    doctorNombre: 'Dr. <Palomino> & Cía',
    desde: '2026-10-01',
    hasta: '2026-10-31',
    filas: [
      { fecha: '2026-10-10', pacienteNombre: 'Luciana "Meza"', estudio: 'Panorámica', precioCobrado: 50, montoComision: 5, estado: 'LIQUIDADO', fechaLiquidacion: '2026-10-20' },
      { fecha: '2026-10-11', pacienteNombre: 'Renzo', estudio: 'Tomografía', precioCobrado: 190, montoComision: 19, estado: 'PENDIENTE' },
    ],
    totalGenerado: 24,
    totalPagado: 5,
    totalPendiente: 19,
  }

  it('lista cada derivación con su estado y los tres totales', () => {
    const html = documentoEstadoCuenta(cuenta)
    expect(html).toContain('Pagada')
    expect(html).toContain('Pendiente')
    expect(html).toContain('Generado: <b>S/ 24.00</b>')
    expect(html).toContain('Pagado: <b>S/ 5.00</b>')
    expect(html).toContain('Pendiente: <b>S/ 19.00</b>')
  })

  it('escapa lo que viene del servidor para que no inyecte HTML en el documento', () => {
    const html = documentoEstadoCuenta(cuenta)
    expect(html).not.toContain('<Palomino>')
    expect(html).toContain('&lt;Palomino&gt; &amp; Cía')
    expect(html).toContain('Luciana &quot;Meza&quot;')
  })

  it('sin derivaciones lo dice', () => {
    expect(documentoEstadoCuenta({ ...cuenta, filas: [] })).toContain('Sin derivaciones en el periodo.')
  })
})
