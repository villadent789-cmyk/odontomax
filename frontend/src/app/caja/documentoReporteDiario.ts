import { formatFecha, formatFechaHora, formatSoles } from '@/shared/format'
import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'

interface PorMetodo {
  efectivo?: number
  yape?: number
  plin?: number
  tarjetaDebito?: number
  tarjetaCredito?: number
  total?: number
}

interface Arqueo {
  esperado?: number
  contado?: number
  diferencia?: number
}

export interface ReporteDiario {
  fecha?: string
  sedeNombre?: string
  ingresos?: PorMetodo
  egresos?: PorMetodo
  balance?: number
  egresosPorCategoria?: { categoria?: string; monto?: number }[]
  cierres?: { cerradaEn?: string; efectivo?: Arqueo; yape?: Arqueo; plin?: Arqueo; tarjeta?: Arqueo; observaciones?: string }[]
}

const escapar = (texto: string | undefined) =>
  (texto ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function diferencia(valor: number | undefined): string {
  const v = valor ?? 0
  if (v === 0) return 'Cuadra'
  return `${v > 0 ? 'Sobra' : 'Falta'} ${formatSoles(Math.abs(v))}`
}

/** Documento imprimible del reporte diario de caja; todo lo que viene del servidor se escapa. */
export function documentoReporteDiario(r: ReporteDiario): string {
  const fila = (nombre: string, ingreso?: number, egreso?: number) =>
    `<tr><td>${nombre}</td><td class="n">${formatSoles(ingreso ?? 0)}</td><td class="n">${formatSoles(egreso ?? 0)}</td></tr>`
  const categorias = (r.egresosPorCategoria ?? [])
    .map((c) => `<tr><td>${escapar(c.categoria)}</td><td class="n">${formatSoles(c.monto ?? 0)}</td></tr>`)
    .join('')
  const cierres = (r.cierres ?? [])
    .map(
      (c) => `<tr><td>${c.cerradaEn ? formatFechaHora(c.cerradaEn) : ''}</td>
<td>${diferencia(c.efectivo?.diferencia)}</td><td>${diferencia(c.yape?.diferencia)}</td><td>${diferencia(c.plin?.diferencia)}</td><td>${diferencia(c.tarjeta?.diferencia)}</td>
<td>${escapar(c.observaciones)}</td></tr>`,
    )
    .join('')
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Reporte diario de caja</title>
<style>@page{size:A4;margin:14mm}body{font-family:Arial,sans-serif;font-size:12px;color:#111}h1{font-size:18px;margin:0 0 4px}h2{font-size:14px;margin:18px 0 4px}
table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #ccc;padding:5px 6px;text-align:left}.n{text-align:right}${ESTILO_CABECERA}</style></head><body>
${cabeceraImpresion({ negocio: r.sedeNombre?.toLowerCase().includes('villamax') ? 'VILLAMAX' : 'VILLADENT', titulo: 'Reporte diario de caja', subtitulo: `${r.sedeNombre ?? ''}${r.fecha ? ' · ' + formatFecha(r.fecha) : ''}` })}
<h2>Por medio de pago</h2>
<table><thead><tr><th>Medio</th><th class="n">Cobrado</th><th class="n">Egresos</th></tr></thead><tbody>
${fila('Efectivo', r.ingresos?.efectivo, r.egresos?.efectivo)}${fila('Yape', r.ingresos?.yape, r.egresos?.yape)}${fila('Plin', r.ingresos?.plin, r.egresos?.plin)}
${fila('Tarjeta de débito', r.ingresos?.tarjetaDebito, 0)}${fila('Tarjeta de crédito', r.ingresos?.tarjetaCredito, r.egresos?.tarjetaDebito)}
${fila('Total', r.ingresos?.total, r.egresos?.total)}</tbody></table>
<p><strong>Balance del día: ${formatSoles(r.balance ?? 0)}</strong></p>
<h2>Egresos por categoría</h2>
<table><tbody>${categorias || '<tr><td>Sin egresos</td><td></td></tr>'}</tbody></table>
<h2>Cierres de caja</h2>
<table><thead><tr><th>Hora</th><th>Efectivo</th><th>Yape</th><th>Plin</th><th>Tarjetas</th><th>Observaciones</th></tr></thead>
<tbody>${cierres || '<tr><td colspan="6">Sin cierres en el día</td></tr>'}</tbody></table>
</body></html>`
}
