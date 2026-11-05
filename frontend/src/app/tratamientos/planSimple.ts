import type { TratamientoCreateRequestDTO } from './api-tratamientos'

export interface DatosPlanSimple {
  pacienteId: string
  sedeId: string
  especialistaId: string
  nombre: string
  costoTotal: number
  totalSesiones: number
  descuentoTotal?: number
  observaciones?: string
  /** Plan en cuotas (ortodoncia): el costo del plan es la suma del cronograma. */
  cuotas?: { montoInicial: number; montoMensual: number; cantidadMensuales: number; primerVencimiento: string }
  /** Precio del catálogo y motivo si el costo acordado es distinto. */
  precioCatalogo?: number
  motivoAjustePrecio?: string
  /** Del catálogo: se guarda en el ítem del plan. */
  procedimientoId?: string
  /** Piezas o unidades y costo por pieza: el total del plan es piezas × costo por pieza. */
  piezas?: number
  costoPorPieza?: number
  /** Gravedad del caso, si el costo de la pieza depende de ella. */
  gravedad?: 'LEVE' | 'MODERADO' | 'SEVERO'
  /** Otros casos del mismo tratamiento (p. ej. una pieza leve y otra severa): cada uno con su gravedad, piezas y costo. */
  otrosCasos?: { gravedad?: 'LEVE' | 'MODERADO' | 'SEVERO'; piezas: number; costoPorPieza: number }[]
}

export function crearSolicitudPlanSimple(datos: DatosPlanSimple): TratamientoCreateRequestDTO {
  const nombre = datos.nombre.trim()
  const descuento = Number(datos.descuentoTotal ?? 0)
  return {
    pacienteId: datos.pacienteId,
    sedeId: datos.sedeId,
    especialistaId: datos.especialistaId,
    nombre,
    modalidadPago: 'CUOTAS',
    costoTotal: datos.costoTotal,
    descuentoTotal: descuento,
    totalSesiones: datos.totalSesiones,
    pagadoAntesSistema: 0,
    observaciones: datos.observaciones?.trim() || undefined,
    cuotas: datos.cuotas,
    precioCatalogo: datos.precioCatalogo,
    motivoAjustePrecio: datos.motivoAjustePrecio?.trim() || undefined,
    items: itemsDelPlan(datos, nombre, descuento),
  }
}

/**
 * Un ítem por caso. Si el total es exactamente la suma de piezas × costo por pieza de los casos, cada ítem guarda sus
 * piezas, su costo unitario y su gravedad; si el doctor acordó otro total, un solo ítem lleva el total acordado.
 */
function itemsDelPlan(datos: DatosPlanSimple, nombre: string, descuento: number) {
  const extras = datos.otrosCasos ?? []
  const unitario = datos.costoPorPieza
  const suma = (unitario ?? 0) * (datos.piezas ?? 1) + extras.reduce((s, c) => s + c.piezas * c.costoPorPieza, 0)
  if (extras.length === 0 || datos.cuotas || unitario == null || Math.abs(suma - datos.costoTotal) >= 0.005) return [itemDelPlan(datos, nombre, descuento)]
  const principal = itemDelPlan({ ...datos, costoTotal: unitario * (datos.piezas ?? 1) }, nombre, descuento)
  return [
    principal,
    ...extras.map((c) => ({ procedimientoId: datos.procedimientoId, nombreProcedimiento: nombre, precioBase: c.costoPorPieza, cantidad: c.piezas, descuento: 0, gravedad: c.gravedad })),
  ]
}

/**
 * El ítem del caso principal. Si el total es exactamente piezas × costo por pieza, el ítem guarda las piezas, el costo
 * unitario y la gravedad; si el doctor acordó otro total, el ítem lleva el total acordado como una sola unidad.
 */
function itemDelPlan(datos: DatosPlanSimple, nombre: string, descuento: number) {
  const piezas = datos.piezas ?? 1
  const unitario = datos.costoPorPieza
  const cuadra = unitario != null && piezas >= 1 && Math.abs(unitario * piezas - datos.costoTotal) < 0.005
  if (cuadra && !datos.cuotas) {
    return {
      procedimientoId: datos.procedimientoId,
      nombreProcedimiento: nombre,
      precioBase: unitario,
      cantidad: piezas,
      descuento,
      gravedad: datos.gravedad,
    }
  }
  return { procedimientoId: datos.procedimientoId, nombreProcedimiento: nombre, precioBase: datos.costoTotal, cantidad: 1, descuento }
}
