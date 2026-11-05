import { apiTipada, type Esquema } from '@/shared/api'
import type { PacienteDTO } from './api-pacientes'

type Tratamiento = Esquema<'TratamientoResponseDTO'>

/** Solo cuentan los tratamientos en curso o ya realizados: un plan propuesto o cancelado no es un tratamiento hecho. */
const ESTADO_REPORTE: Record<string, string> = {
  EN_PROCESO: 'En proceso',
  COMPLETADO: 'Concluido',
}

export interface FilaReporte {
  dni: string
  nombre: string
  telefono: string
  sede: string
  tratamiento: string
  estado: string
}

export interface PosicionRanking {
  posicion: number
  tratamiento: string
  cantidad: number
  concluidos: number
  enProceso: number
}

export interface ResumenReporte {
  totalTratamientos: number
  concluidos: number
  enProceso: number
  ranking: PosicionRanking[]
}

/** GET /tratamientos/sede/{id} de cada sede (en paralelo). Si una falla, falla todo: un reporte a medias engañaría. */
export async function listarTratamientosDeSedes(sedeIds: string[]): Promise<Tratamiento[]> {
  const unicas = [...new Set(sedeIds.filter(Boolean))]
  const listas = await Promise.all(unicas.map((sedeId) => apiTipada.get('/tratamientos/sede/{sedeId}', { path: { sedeId } })))
  return listas.flat()
}

const esDelReporte = (t: Tratamiento) => !!t.estado && t.estado in ESTADO_REPORTE

/** Una fila por paciente y tratamiento; el paciente sin tratamientos en curso o realizados aparece una vez, "Sin tratamientos". */
export function construirFilas(
  pacientes: PacienteDTO[],
  tratamientos: Tratamiento[],
  nombreSede: (sedeId: string | undefined) => string,
): FilaReporte[] {
  const porPaciente = new Map<string, Tratamiento[]>()
  for (const t of tratamientos.filter(esDelReporte)) {
    if (!t.pacienteId) continue
    porPaciente.set(t.pacienteId, [...(porPaciente.get(t.pacienteId) ?? []), t])
  }
  return pacientes.flatMap((p) => {
    const base = {
      dni: p.dni ?? '',
      nombre: p.nombreCompleto ?? `${p.nombres ?? ''} ${p.apellidos ?? ''}`.trim(),
      telefono: p.telefono ?? '',
      sede: nombreSede(p.sedeId),
    }
    const propios = (p.id && porPaciente.get(p.id)) || []
    if (propios.length === 0) return [{ ...base, tratamiento: 'Sin tratamientos', estado: '' }]
    return propios.map((t) => ({ ...base, tratamiento: t.nombre?.trim() || 'Sin nombre', estado: ESTADO_REPORTE[t.estado!] }))
  })
}

/** Total de tratamientos (en proceso + concluidos) y ranking de los que más se hicieron, de más a menos. */
export function construirResumen(tratamientos: Tratamiento[]): ResumenReporte {
  const grupos = new Map<string, { nombre: string; concluidos: number; enProceso: number }>()
  for (const t of tratamientos.filter(esDelReporte)) {
    const nombre = t.nombre?.trim() || 'Sin nombre'
    const clave = nombre.toLocaleLowerCase('es')
    const g = grupos.get(clave) ?? { nombre, concluidos: 0, enProceso: 0 }
    if (t.estado === 'COMPLETADO') g.concluidos += 1
    else g.enProceso += 1
    grupos.set(clave, g)
  }
  const ranking = [...grupos.values()]
    .map((g) => ({ tratamiento: g.nombre, cantidad: g.concluidos + g.enProceso, concluidos: g.concluidos, enProceso: g.enProceso }))
    .sort((a, b) => b.cantidad - a.cantidad || a.tratamiento.localeCompare(b.tratamiento, 'es'))
    .map((g, i) => ({ posicion: i + 1, ...g }))
  const concluidos = ranking.reduce((s, r) => s + r.concluidos, 0)
  const enProceso = ranking.reduce((s, r) => s + r.enProceso, 0)
  return { totalTratamientos: concluidos + enProceso, concluidos, enProceso, ranking }
}

/** Fecha de hoy en Lima, AAAA-MM-DD (para el nombre del archivo). */
export function fechaHoyLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(ahora)
}

export interface PacientesPorEstado {
  total: number
  /** Con al menos un tratamiento en proceso. */
  enTratamiento: number
  /** Con tratamientos concluidos y ninguno en proceso. */
  conTratamientoConcluido: number
  /** Sin ningún tratamiento en curso ni concluido. */
  sinTratamiento: number
}

/** Cuántos de los pacientes del reporte están en tratamiento, ya concluyeron o no tienen ninguno. */
export function contarPacientesPorEstado(pacientes: PacienteDTO[], tratamientos: Tratamiento[]): PacientesPorEstado {
  const enProceso = new Set<string>()
  const concluidos = new Set<string>()
  for (const t of tratamientos) {
    if (!t.pacienteId) continue
    if (t.estado === 'EN_PROCESO') enProceso.add(t.pacienteId)
    else if (t.estado === 'COMPLETADO') concluidos.add(t.pacienteId)
  }
  let enTratamiento = 0
  let conTratamientoConcluido = 0
  for (const p of pacientes) {
    if (p.id && enProceso.has(p.id)) enTratamiento += 1
    else if (p.id && concluidos.has(p.id)) conTratamientoConcluido += 1
  }
  return { total: pacientes.length, enTratamiento, conTratamientoConcluido, sinTratamiento: pacientes.length - enTratamiento - conTratamientoConcluido }
}
