import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'
import { formatFecha, formatSoles } from '@/shared/format'
import type { Presupuesto } from './api-presupuestos'

const esc = (t: string | number | null | undefined) =>
  String(t ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const GRAVEDAD: Record<string, string> = { LEVE: 'Leve', MODERADO: 'Moderado', SEVERO: 'Severo' }

interface Datos {
  presupuesto: Presupuesto
  pacienteNombre: string
  pacienteDni?: string
  sedeNombre: string
  negocio?: 'VILLADENT' | 'VILLAMAX'
}

/** Presupuesto imprimible o para guardar en PDF: nombre completo, cada tratamiento con sus piezas y el total. */
export function documentoPresupuesto({ presupuesto: p, pacienteNombre, pacienteDni, sedeNombre, negocio = 'VILLADENT' }: Datos): string {
  const filas = p.items
    .map(
      (i) => `<tr><td>${esc(i.nombre)}${i.gravedad ? ` <span class="g">(${GRAVEDAD[i.gravedad]})</span>` : ''}</td>
<td>${i.piezaFdi ?? '—'}</td><td class="n">${i.cantidad}</td><td class="n">${formatSoles(i.precioUnitario)}</td><td class="n">${formatSoles(i.subtotal)}</td></tr>`,
    )
    .join('')
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Presupuesto</title>
<style>@page{size:A4;margin:14mm}body{font-family:Arial,sans-serif;font-size:12px;color:#1b1916;line-height:1.45}
.datos{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px;margin:0 0 14px}.datos b{color:#524d46}
table{width:100%;border-collapse:collapse}th,td{padding:6px;text-align:left;border-bottom:1px solid #e8e5dd}th{background:#f4f2ed;font-size:11px}.n{text-align:right}
.g{color:#7a746b;font-size:10px}.total{margin-top:12px;text-align:right;font-size:15px;font-weight:700}
.nota{margin-top:14px;font-size:11px;color:#524d46}.pie{margin-top:26px;border-top:1px solid #d6d2c7;padding-top:8px;font-size:10px;color:#7a746b}
${ESTILO_CABECERA}</style></head><body>
${cabeceraImpresion({ negocio, titulo: 'Presupuesto de tratamiento', subtitulo: `${sedeNombre} · ${formatFecha(p.creadoEn)}` })}
<div class="datos"><div><b>Paciente:</b> ${esc(pacienteNombre)}</div><div><b>DNI:</b> ${esc(pacienteDni ?? '—')}</div>
<div><b>Doctor:</b> ${esc(p.especialistaNombre ?? '—')}</div><div><b>Motivo:</b> ${esc(p.motivo ?? '—')}</div></div>
<table><thead><tr><th>Tratamiento</th><th>Pieza</th><th class="n">Piezas</th><th class="n">Costo por pieza</th><th class="n">Subtotal</th></tr></thead><tbody>${filas}</tbody></table>
<p class="total">Total del presupuesto: ${formatSoles(p.total)}</p>
${p.notas ? `<p class="nota"><b>Notas:</b> ${esc(p.notas)}</p>` : ''}
<p class="pie">Presupuesto referencial emitido el ${formatFecha(p.creadoEn)}. Los precios pueden variar si cambia el diagnóstico durante el tratamiento. Información confidencial del paciente.</p>
</body></html>`
}
