import { describe, expect, it } from 'vitest'
import { documentoReporteGerencial } from './documentoReporteGerencial'
import type { ReporteCitasDTO, ReporteFinancieroDTO } from './types'

const FINANCIERO = {
  sedeNombre: 'Villadent Chilca',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-29',
  totalIngresos: 2360,
  totalGastos: 45,
  balanceNeto: 2315,
  totalEfectivo: 1400,
  totalYape: 960,
  totalTarjeta: 0,
  totalTransferencia: 0,
  cuentasPorCobrar: 300,
  tratamientosConSaldoPendiente: 1,
  ingresosPorDia: [],
  ingresosPorMetodo: [
    { metodo: 'EFECTIVO', total: 1400, cantidad: 3 },
    { metodo: 'YAPE', total: 960, cantidad: 2 },
  ],
  ingresosPorDoctor: [],
  ingresosPorTratamiento: [{ procedimientoNombre: 'Ortodoncia <b>autoligados</b>', totalIngresos: 1200, cantidadRealizados: 1 }],
  ingresosPorSede: [],
  gastosPorCategoria: [{ categoria: 'INSUMOS_MATERIALES', total: 45 }],
} as unknown as ReporteFinancieroDTO

const CITAS = {
  sedeNombre: 'Villadent Chilca',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-29',
  totalCitas: 7,
  atendidas: 2,
  canceladas: 1,
  noAsistio: 0,
  tasaAsistencia: 28.57,
  citasPorEstado: [{ estado: 'ATENDIDA', cantidad: 2, porcentaje: 28.57 }],
} as unknown as ReporteCitasDTO

describe('documentoReporteGerencial', () => {
  it('abre con un resumen en palabras y explica cada sección', () => {
    const html = documentoReporteGerencial({ negocio: 'VILLADENT', sedeNombre: 'Villadent Chilca', desde: '2026-10-01', hasta: '2026-10-29', financiero: FINANCIERO, citas: CITAS })
    expect(html).toContain('Reporte de gestión')
    expect(html).toContain('Resumen del periodo')
    expect(html).toContain('Se cobraron S/ 2,360.00 y se gastaron S/ 45.00')
    expect(html).toContain('ganancia de S/ 2,315.00')
    expect(html).toContain('Todavía hay S/ 300.00 por cobrar')
    expect(html).toContain('Se atendieron 2 de 7 citas agendadas')
    // Cada cifra y cada sección traen su explicación.
    expect(html).toContain('Todo el dinero cobrado a pacientes en el periodo.')
    expect(html).toContain('Cómo pagaron los pacientes')
    expect(html).toContain('Efectivo')
    expect(html).toContain('insumos materiales')
  })

  it('usa el título del cierre de mes cuando se indica', () => {
    const html = documentoReporteGerencial({ negocio: 'VILLADENT', sedeNombre: 'Villadent Chilca', titulo: 'Cierre de mes · septiembre de 2026', financiero: FINANCIERO })
    expect(html).toContain('<title>Cierre de mes · septiembre de 2026</title>')
    expect(html).toContain('Cierre de mes · septiembre de 2026')
  })

  it('escapa el texto que viene del sistema y no rompe sin datos', () => {
    const html = documentoReporteGerencial({ negocio: 'VILLAMAX', sedeNombre: 'Villamax Chilca', financiero: FINANCIERO })
    expect(html).not.toContain('<b>autoligados</b>')
    expect(html).toContain('&lt;b&gt;autoligados&lt;/b&gt;')
    expect(html).toContain('VILLAMAX 3D')
    expect(() => documentoReporteGerencial({ negocio: 'VILLADENT', sedeNombre: 'X' })).not.toThrow()
  })
})
