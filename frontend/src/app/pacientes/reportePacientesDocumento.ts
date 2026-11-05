import type { FilaReporte, PacientesPorEstado, ResumenReporte } from './reportePacientes'

export const MEMBRETE = 'Centro Odontológico Villadent & Centro Tomográfico Villamax 3D'

interface Datos {
  /** Sede activa del usuario. */
  sede: string
  /** Fecha y hora de emisión ya formateadas en hora de Lima. */
  emitidoEn: string
  /** AAAA-MM-DD, para el título del documento (nombre sugerido al guardar el PDF). */
  fecha: string
  pacientes: PacientesPorEstado
  resumen: ResumenReporte
  filas: FilaReporte[]
}

const POSICIONES_RANKING = 10

/** Escapa texto para HTML: los nombres y tratamientos vienen de la base de datos. */
export function escaparHtml(texto: string | number | undefined | null): string {
  return String(texto ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
}

const ESTILOS = `
  @page { size: A4; margin: 14mm 12mm; }
  * { box-sizing: border-box; }
  body { font-family: 'Segoe UI', Arial, sans-serif; color: #1c1b1f; font-size: 10.5pt; margin: 0; }
  header { border-bottom: 2px solid #0f172a; padding-bottom: 8px; margin-bottom: 14px; }
  header h1 { font-size: 14pt; margin: 0 0 2px; color: #0f172a; }
  header p { margin: 0; font-size: 9.5pt; color: #444; }
  h2 { font-size: 11.5pt; margin: 16px 0 6px; color: #0f172a; }
  .kpis { display: flex; gap: 8px; }
  .kpi { flex: 1; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 10px; }
  .kpi strong { display: block; font-size: 16pt; color: #0f172a; }
  .kpi span { font-size: 8.5pt; color: #555; }
  table { width: 100%; border-collapse: collapse; }
  th, td { border: 1px solid #cbd5e1; padding: 4px 6px; text-align: left; vertical-align: top; font-size: 9pt; }
  th { background: #f1f5f9; }
  thead { display: table-header-group; }
  tr { page-break-inside: avoid; }
  td.num { text-align: right; }
  .vacio { color: #666; font-style: italic; }
  footer { margin-top: 14px; padding-top: 6px; border-top: 1px solid #cbd5e1; font-size: 8pt; color: #666; }
`

/** Documento completo del reporte, listo para imprimir o guardar como PDF. Todo el texto variable va escapado. */
export function construirDocumentoReporte(d: Datos): string {
  const ranking = d.resumen.ranking.slice(0, POSICIONES_RANKING)
  const filaRanking = (r: (typeof ranking)[number]) =>
    `<tr><td class="num">${r.posicion}°</td><td>${escaparHtml(r.tratamiento)}</td><td class="num">${r.cantidad}</td><td class="num">${r.concluidos}</td><td class="num">${r.enProceso}</td></tr>`
  const filaPaciente = (f: FilaReporte) =>
    `<tr><td>${escaparHtml(f.dni)}</td><td>${escaparHtml(f.nombre)}</td><td>${escaparHtml(f.telefono || '—')}</td><td>${escaparHtml(f.sede)}</td><td>${escaparHtml(f.tratamiento)}</td><td>${escaparHtml(f.estado || '—')}</td></tr>`

  return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>reporte-pacientes-${escaparHtml(d.fecha)}</title>
<style>${ESTILOS}</style>
</head>
<body>
<header>
  <h1>${escaparHtml(MEMBRETE)}</h1>
  <p><strong>Reporte de pacientes y tratamientos</strong></p>
  <p>Sede activa: ${escaparHtml(d.sede)} &nbsp;·&nbsp; Fecha de emisión: ${escaparHtml(d.emitidoEn)} (hora de Lima)</p>
</header>

<h2>Resumen clínico</h2>
<div class="kpis">
  <div class="kpi"><strong>${d.pacientes.total}</strong><span>Pacientes registrados en este reporte</span></div>
  <div class="kpi"><strong>${d.pacientes.enTratamiento}</strong><span>En tratamiento activo</span></div>
  <div class="kpi"><strong>${d.pacientes.conTratamientoConcluido}</strong><span>Con tratamiento concluido</span></div>
  <div class="kpi"><strong>${d.pacientes.sinTratamiento}</strong><span>Sin tratamientos</span></div>
</div>

<h2>Tratamientos más realizados en la clínica</h2>
<p style="margin:0 0 6px;font-size:9pt;color:#555">
  Total de tratamientos: <strong>${d.resumen.totalTratamientos}</strong>
  (${d.resumen.concluidos} concluidos · ${d.resumen.enProceso} en proceso), de las sedes incluidas.
</p>
${
  ranking.length === 0
    ? '<p class="vacio">Todavía no hay tratamientos en proceso ni concluidos.</p>'
    : `<table>
  <thead><tr><th style="width:9%">Puesto</th><th>Tratamiento</th><th style="width:12%">Cantidad</th><th style="width:12%">Concluidos</th><th style="width:12%">En proceso</th></tr></thead>
  <tbody>${ranking.map(filaRanking).join('')}</tbody>
</table>`
}

<h2>Pacientes</h2>
${
  d.filas.length === 0
    ? '<p class="vacio">No hay pacientes con los filtros aplicados.</p>'
    : `<table>
  <thead><tr><th style="width:10%">DNI</th><th>Nombres y apellidos</th><th style="width:13%">Celular / WhatsApp</th><th style="width:15%">Sede</th><th>Tratamiento</th><th style="width:11%">Estado</th></tr></thead>
  <tbody>${d.filas.map(filaPaciente).join('')}</tbody>
</table>`
}

<footer>Documento confidencial de uso interno: contiene datos personales protegidos por la Ley N.° 29733. ${escaparHtml(MEMBRETE)}.</footer>
</body>
</html>`
}
