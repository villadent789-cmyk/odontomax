import { formatFecha, formatSoles } from '@/shared/format'
import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'

interface FilaCuenta {
  fecha?: string
  pacienteNombre?: string
  estudio?: string
  precioCobrado?: number
  montoComision?: number
  estado?: string
  fechaLiquidacion?: string
}

export interface EstadoCuenta {
  doctorNombre?: string
  clinicaOrigen?: string
  desde?: string
  hasta?: string
  filas?: FilaCuenta[]
  totalGenerado?: number
  totalPagado?: number
  totalPendiente?: number
}

const escapar = (texto: string | undefined) =>
  (texto ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** Documento imprimible del estado de cuenta; todo lo que viene del servidor se escapa. */
export function documentoEstadoCuenta(c: EstadoCuenta): string {
  const filas = (c.filas ?? [])
    .map(
      (f) => `<tr><td>${f.fecha ? formatFecha(f.fecha) : ''}</td><td>${escapar(f.pacienteNombre)}</td><td>${escapar(f.estudio)}</td>
<td class="n">${formatSoles(f.precioCobrado ?? 0)}</td><td class="n">${formatSoles(f.montoComision ?? 0)}</td>
<td>${f.estado === 'LIQUIDADO' ? `Pagada${f.fechaLiquidacion ? ' ' + formatFecha(f.fechaLiquidacion) : ''}` : 'Pendiente'}</td></tr>`,
    )
    .join('')
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Estado de cuenta</title>
<style>@page{size:A4;margin:14mm}body{font-family:Arial,sans-serif;font-size:12px;color:#111}h1{font-size:18px;margin:0 0 4px}
table{width:100%;border-collapse:collapse;margin-top:12px}th,td{border-bottom:1px solid #ccc;padding:5px 6px;text-align:left}
.n{text-align:right}.tot{margin-top:14px;display:flex;gap:24px;justify-content:flex-end;font-size:13px}${ESTILO_CABECERA}</style></head><body>
${cabeceraImpresion({ negocio: 'VILLAMAX', titulo: 'Estado de cuenta', subtitulo: 'Comisiones por derivación' })}
<div>${escapar(c.doctorNombre)}${c.clinicaOrigen ? ' · ' + escapar(c.clinicaOrigen) : ''}</div>
<div>Periodo: ${c.desde ? formatFecha(c.desde) : ''} al ${c.hasta ? formatFecha(c.hasta) : ''}</div>
<table><thead><tr><th>Fecha</th><th>Paciente</th><th>Estudio</th><th class="n">Cobrado</th><th class="n">Comisión</th><th>Estado</th></tr></thead><tbody>${filas || '<tr><td colspan="6">Sin derivaciones en el periodo.</td></tr>'}</tbody></table>
<div class="tot"><span>Generado: <b>${formatSoles(c.totalGenerado ?? 0)}</b></span><span>Pagado: <b>${formatSoles(c.totalPagado ?? 0)}</b></span><span>Pendiente: <b>${formatSoles(c.totalPendiente ?? 0)}</b></span></div>
</body></html>`
}
