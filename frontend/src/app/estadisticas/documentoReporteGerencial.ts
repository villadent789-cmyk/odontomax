import { formatFecha, formatSoles } from '@/shared/format'
import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'
import type {
  ReporteCitasDTO,
  ReporteDeudoresDTO,
  ReporteDoctoresDTO,
  ReporteFinancieroDTO,
  ReporteInventarioDTO,
  ReportePacientesDTO,
} from './types'

const esc = (t: string | number | null | undefined) =>
  String(t ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const pct = (n: number | undefined) => `${(n ?? 0).toFixed(1).replace(/\.0$/, '')} %`

const NOMBRE_ESTADO: Record<string, string> = {
  ATENDIDA: 'Atendidas',
  CONFIRMADA: 'Confirmadas',
  PROGRAMADA: 'Programadas',
  EN_SALA: 'En sala de espera',
  PENDIENTE_WEB: 'Reservas web por confirmar',
  CANCELADA: 'Canceladas',
  NO_ASISTIO: 'No asistieron',
  FALTO: 'No asistieron',
  EXPIRADA: 'Expiradas',
}

const NOMBRE_METODO: Record<string, string> = { EFECTIVO: 'Efectivo', YAPE: 'Yape', PLIN: 'Plin', TARJETA: 'Tarjeta', TARJETA_DEBITO: 'Tarjeta de débito', TARJETA_CREDITO: 'Tarjeta de crédito', TRANSFERENCIA: 'Transferencia' }

const NOMBRE_FUENTE: Record<string, string> = {
  FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram', TIKTOK: 'TikTok', GOOGLE: 'Google', REFERIDO_PACIENTE: 'Recomendado por un paciente',
  REFERIDO_DOCTOR: 'Derivado por un doctor', LOCAL: 'Pasó por el local', OTRO: 'Otro',
}

/** Barras horizontales simples (se imprimen bien, sin gráficos): etiqueta, barra proporcional y valor. */
function barras(filas: { etiqueta: string; valor: number; texto: string }[]): string {
  const max = Math.max(1, ...filas.map((f) => f.valor))
  return `<div class="barras">${filas
    .map(
      (f) => `<div class="b-fila"><span class="b-et">${esc(f.etiqueta)}</span><span class="b-pista"><span class="b-barra" style="width:${Math.max(2, Math.round((f.valor / max) * 100))}%"></span></span><span class="b-val">${esc(f.texto)}</span></div>`,
    )
    .join('')}</div>`
}

function tabla(cabeceras: string[], filas: (string | number)[][], derecha: number[] = []): string {
  const th = cabeceras.map((c, i) => `<th${derecha.includes(i) ? ' class="n"' : ''}>${esc(c)}</th>`).join('')
  const tr = filas
    .map((f) => `<tr>${f.map((c, i) => `<td${derecha.includes(i) ? ' class="n"' : ''}>${esc(c)}</td>`).join('')}</tr>`)
    .join('')
  return `<table><thead><tr>${th}</tr></thead><tbody>${tr || `<tr><td colspan="${cabeceras.length}" class="vacio">Sin datos en este periodo.</td></tr>`}</tbody></table>`
}

function seccion(titulo: string, explicacion: string, contenido: string): string {
  return `<section class="sec"><h2>${esc(titulo)}</h2><p class="expl">${esc(explicacion)}</p>${contenido}</section>`
}

function cifra(titulo: string, valor: string, significado: string): string {
  return `<div class="cifra"><div class="c-t">${esc(titulo)}</div><div class="c-v">${esc(valor)}</div><div class="c-s">${esc(significado)}</div></div>`
}

export interface DatosReporte {
  negocio: 'VILLADENT' | 'VILLAMAX'
  sedeNombre: string
  /** Título del documento; por defecto «Reporte de gestión». */
  titulo?: string
  desde?: string
  hasta?: string
  financiero?: ReporteFinancieroDTO
  citas?: ReporteCitasDTO
  pacientes?: ReportePacientesDTO
  doctores?: ReporteDoctoresDTO
  inventario?: ReporteInventarioDTO
  deudores?: ReporteDeudoresDTO
}

/** Resumen en lenguaje sencillo: lo primero que lee quien recibe el reporte. */
function resumenEnPalabras(d: DatosReporte): string[] {
  const f = d.financiero
  const c = d.citas
  const p = d.pacientes
  const frases: string[] = []
  if (f) {
    frases.push(
      `Se cobraron ${formatSoles(f.totalIngresos)} y se gastaron ${formatSoles(f.totalGastos)}, por lo que quedó una ganancia de ${formatSoles(f.balanceNeto)}.`,
    )
    if (f.cuentasPorCobrar > 0) {
      frases.push(`Todavía hay ${formatSoles(f.cuentasPorCobrar)} por cobrar en ${f.tratamientosConSaldoPendiente} tratamiento(s) con saldo pendiente.`)
    }
  }
  if (c) {
    frases.push(
      `Se atendieron ${c.atendidas} de ${c.totalCitas} citas agendadas; ${c.canceladas} se cancelaron y ${c.noAsistio} pacientes no asistieron. La asistencia fue de ${pct(c.tasaAsistencia)}.`,
    )
  }
  if (p) {
    frases.push(`Llegaron ${p.pacientesNuevos} pacientes nuevos; en total hay ${p.totalPacientes} pacientes registrados.`)
  }
  const i = d.inventario
  if (i && (i.productosStockBajo > 0 || i.productosPorVencer > 0 || i.productosVencidos > 0)) {
    frases.push(
      `En el almacén hay ${i.productosStockBajo} producto(s) con poco stock, ${i.productosPorVencer} por vencer y ${i.productosVencidos} vencido(s): conviene reponer o revisar.`,
    )
  }
  return frases
}

export function documentoReporteGerencial(d: DatosReporte): string {
  const periodo = `${d.desde ? formatFecha(d.desde) : ''} al ${d.hasta ? formatFecha(d.hasta) : ''}`
  const f = d.financiero
  const c = d.citas
  const p = d.pacientes
  const i = d.inventario
  const partes: string[] = []

  const frases = resumenEnPalabras(d)
  if (frases.length) {
    partes.push(`<section class="resumen"><h2>Resumen del periodo</h2><ul>${frases.map((t) => `<li>${esc(t)}</li>`).join('')}</ul></section>`)
  }

  if (f) {
    partes.push(`<div class="cifras">${[
      cifra('Ingresos', formatSoles(f.totalIngresos), 'Todo el dinero cobrado a pacientes en el periodo.'),
      cifra('Gastos', formatSoles(f.totalGastos), 'Compras y pagos registrados en caja.'),
      cifra('Ganancia', formatSoles(f.balanceNeto), 'Ingresos menos gastos.'),
      cifra('Por cobrar', formatSoles(f.cuentasPorCobrar), 'Lo que los pacientes aún deben de sus tratamientos.'),
    ].join('')}</div>`)

    const metodos = (f.ingresosPorMetodo ?? []).filter((m) => m.total > 0)
    partes.push(
      seccion(
        'Cómo pagaron los pacientes',
        'Dinero cobrado según el medio de pago. Sirve para saber cuánto debe haber en efectivo y cuánto en Yape, Plin o tarjetas.',
        metodos.length
          ? barras(metodos.map((m) => ({ etiqueta: NOMBRE_METODO[m.metodo] ?? m.metodo, valor: m.total, texto: `${formatSoles(m.total)} · ${m.cantidad} cobro(s)` })))
          : '<p class="vacio">Sin cobros en este periodo.</p>',
      ),
    )

    if ((f.ingresosPorTratamiento ?? []).length) {
      partes.push(
        seccion(
          'Tratamientos que más ingresos dejaron',
          'Los servicios que más dinero generaron y cuántas veces se realizaron.',
          tabla(
            ['Tratamiento', 'Veces realizado', 'Ingresos'],
            [...f.ingresosPorTratamiento].sort((a, b) => b.totalIngresos - a.totalIngresos).slice(0, 10).map((t) => [t.procedimientoNombre, t.cantidadRealizados, formatSoles(t.totalIngresos)]),
            [1, 2],
          ),
        ),
      )
    }
    if ((f.gastosPorCategoria ?? []).length) {
      partes.push(
        seccion(
          'En qué se gastó',
          'Gastos del periodo agrupados por categoría.',
          barras([...(f.gastosPorCategoria ?? [])].sort((a, b) => b.total - a.total).map((g) => ({ etiqueta: g.categoria.replace(/_/g, ' ').toLowerCase(), valor: g.total, texto: formatSoles(g.total) }))),
        ),
      )
    }
  }

  if (c) {
    const estados = (c.citasPorEstado ?? []).filter((e) => e.cantidad > 0)
    partes.push(
      seccion(
        'Citas del periodo',
        `La asistencia mide cuántas de las citas agendadas terminaron atendidas (${pct(c.tasaAsistencia)} en este periodo). Una asistencia baja indica pacientes que reservan y no vienen.`,
        estados.length ? barras(estados.map((e) => ({ etiqueta: NOMBRE_ESTADO[e.estado] ?? e.estado, valor: e.cantidad, texto: `${e.cantidad} · ${pct(e.porcentaje)}` }))) : '<p class="vacio">Sin citas en este periodo.</p>',
      ),
    )
  }

  if (p) {
    const fuentes = (p.fuentesCaptacion ?? []).filter((x) => x.cantidad > 0)
    partes.push(
      seccion(
        'Pacientes',
        `Nuevos son los que se registraron en el periodo (${p.pacientesNuevos}); recurrentes, los que volvieron (${p.pacientesRecurrentes}); inactivos, los que llevan mucho tiempo sin venir (${p.pacientesInactivos}).`,
        fuentes.length
          ? `<p class="sub">¿Cómo llegaron los pacientes?</p>${barras(fuentes.map((x) => ({ etiqueta: NOMBRE_FUENTE[x.fuente] ?? x.fuente, valor: x.cantidad, texto: `${x.cantidad} · ${pct(x.porcentaje)}` })))}`
          : '',
      ),
    )
  }

  if (d.doctores && d.doctores.doctores.length) {
    partes.push(
      seccion(
        'Rendimiento por doctor',
        'Cuántas citas atendió cada doctor, cuánto dinero generó y su ticket promedio (lo que deja en promedio cada tratamiento).',
        tabla(
          ['Doctor', 'Citas atendidas', 'Canceladas', 'No asistieron', 'Ingresos', 'Ticket promedio'],
          d.doctores.doctores.map((x) => [x.nombre, x.citasAtendidas, x.citasCanceladas, x.citasNoAsistio, formatSoles(x.ingresosGenerados), formatSoles(x.ticketPromedio)]),
          [1, 2, 3, 4, 5],
        ),
      ),
    )
  }

  if (d.deudores && d.deudores.pacientes.length) {
    partes.push(
      seccion(
        'Pacientes con saldo pendiente',
        `Deuda total acumulada: ${formatSoles(d.deudores.deudaTotalAcumulada)} en ${d.deudores.totalPacientesDeudores} paciente(s). Se muestran los de mayor deuda.`,
        tabla(
          ['Paciente', 'DNI', 'Teléfono', 'Debe'],
          [...d.deudores.pacientes].sort((a, b) => b.saldoPendiente - a.saldoPendiente).slice(0, 10).map((x) => [x.pacienteNombre, x.dni, x.telefono ?? '—', formatSoles(x.saldoPendiente)]),
          [3],
        ),
      ),
    )
  }

  if (i) {
    const alertas = i.alertasStock ?? []
    const lotes = i.lotesVencimiento ?? []
    partes.push(
      seccion(
        'Almacén de insumos',
        `Hay ${i.totalProductos} productos; el stock valorizado es ${formatSoles(i.valorizacionTotal)}. Aquí se listan los que requieren atención.`,
        `${alertas.length ? `<p class="sub">Poco stock</p>${tabla(['Producto', 'Stock actual', 'Mínimo'], alertas.slice(0, 12).map((a) => [a.nombre, a.stockActual, a.stockMinimo]), [1, 2])}` : '<p class="vacio">Ningún producto con poco stock.</p>'}${lotes.length ? `<p class="sub">Por vencer o vencidos</p>${tabla(['Producto', 'Lote', 'Vence', 'Estado'], lotes.slice(0, 12).map((l) => [l.productoNombre, l.lote, formatFecha(l.fechaVencimiento), l.estado === 'VENCIDO' ? 'Vencido' : `En ${l.diasParaVencer} día(s)`]))}` : ''}`,
      ),
    )
  }

  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${esc(d.titulo ?? 'Reporte de gestión')}</title>
<style>
@page{size:A4;margin:14mm}
body{font-family:Arial,sans-serif;font-size:12px;line-height:1.45;color:#1b1916}
h2{font-size:14px;margin:0 0 4px;color:#0f172a}
.resumen{background:#faf7ef;border:1px solid #ebd6ab;border-radius:8px;padding:12px 16px;margin-bottom:14px}
.resumen ul{margin:6px 0 0;padding-left:18px}.resumen li{margin:3px 0}
.cifras{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:14px}
.cifra{border:1px solid #d6d2c7;border-radius:8px;padding:8px 10px}
.c-t{font-size:10px;text-transform:uppercase;letter-spacing:.08em;color:#68625a}.c-v{font-size:17px;font-weight:700;margin:2px 0}.c-s{font-size:10px;color:#68625a}
.sec{margin-top:16px;break-inside:avoid}
.expl{margin:0 0 8px;color:#524d46}
.sub{font-weight:700;margin:10px 0 4px}
table{width:100%;border-collapse:collapse}th,td{padding:5px 6px;text-align:left;border-bottom:1px solid #e8e5dd}
th{background:#f4f2ed;font-size:11px;color:#403c36}.n{text-align:right}
.vacio{color:#7a746b;font-style:italic}
.barras{display:flex;flex-direction:column;gap:5px}
.b-fila{display:grid;grid-template-columns:34% 1fr 24%;align-items:center;gap:8px}
.b-et{text-transform:capitalize}.b-pista{height:9px;background:#ece9e1;border-radius:5px;overflow:hidden}.b-barra{display:block;height:100%;background:#ac8449;border-radius:5px}
.b-val{text-align:right;font-weight:600}
.pie{margin-top:22px;border-top:1px solid #d6d2c7;padding-top:8px;font-size:10px;color:#7a746b}
${ESTILO_CABECERA}
</style></head><body>
${cabeceraImpresion({ negocio: d.negocio, titulo: d.titulo ?? 'Reporte de gestión', subtitulo: `${d.sedeNombre} · ${periodo}` })}
${partes.join('\n')}
<p class="pie">Reporte generado desde el sistema el ${formatFecha(new Date().toISOString())}. Las cifras corresponden al periodo indicado y a la sede seleccionada. Información confidencial de uso interno.</p>
</body></html>`
}
