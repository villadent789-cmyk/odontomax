import { diaSemanaIso, nombreDia, sumarDias } from './fechas'
import { hoyEnLima } from '@/shared/format'

/** Lo mínimo que se necesita de un turno del especialista (HorarioEspecialistaResponseDTO). */
export interface TurnoDoctor {
  sedeId?: string
  diaSemana?: number
  horaInicio?: string
  horaFin?: string
}

const NOMBRES = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const nombreIso = (dia: number) => NOMBRES[dia - 1]
const hhmm = (hora: string | undefined) => (hora ?? '').slice(0, 5)

/** "Lunes", "Lunes y Miércoles", "Lunes a Sábado", "Lunes, Martes y Jueves a Sábado": 3 o más días seguidos se resumen con "a". */
export function textoDias(dias: readonly number[]): string {
  const ordenados = [...new Set(dias)].filter((d) => d >= 1 && d <= 7).sort((a, b) => a - b)
  const tramos: number[][] = []
  for (const d of ordenados) {
    const ultimo = tramos[tramos.length - 1]
    if (ultimo && d === ultimo[ultimo.length - 1] + 1) ultimo.push(d)
    else tramos.push([d])
  }
  const partes = tramos.flatMap((t) =>
    t.length >= 3 ? [`${nombreIso(t[0])} a ${nombreIso(t[t.length - 1])}`] : t.map(nombreIso),
  )
  if (partes.length <= 1) return partes[0] ?? ''
  return `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`
}

const deLaSede = (turnos: readonly TurnoDoctor[] | undefined, sedeId: string | null) =>
  (turnos ?? []).filter((t) => t.diaSemana && (!sedeId || t.sedeId === sedeId))

/** Días (1..7) en que atiende en esa sede, de lunes a domingo. */
export function diasDeAtencion(turnos: readonly TurnoDoctor[] | undefined, sedeId: string | null): number[] {
  return [...new Set(deLaSede(turnos, sedeId).map((t) => t.diaSemana!))].sort((a, b) => a - b)
}

/**
 * "Lunes y Miércoles de 09:00 a 13:00" (si los turnos difieren: "Lunes de 09:00 a 13:00; Martes de 15:00 a 19:00").
 * `null` si no tiene turnos en esa sede.
 */
export function resumenAtencion(turnos: readonly TurnoDoctor[] | undefined, sedeId: string | null): string | null {
  const propios = deLaSede(turnos, sedeId)
  if (propios.length === 0) return null
  const grupos = new Map<string, number[]>()
  for (const t of propios) {
    const rango = `de ${hhmm(t.horaInicio)} a ${hhmm(t.horaFin)}`
    grupos.set(rango, [...(grupos.get(rango) ?? []), t.diaSemana!])
  }
  return [...grupos.entries()]
    .sort((a, b) => Math.min(...a[1]) - Math.min(...b[1]))
    .map(([rango, dias]) => `${textoDias(dias)} ${rango}`)
    .join('; ')
}

/** "los lunes", "los sábados": solo se pluraliza el nombre que no termina en "s". */
function losDia(fecha: string): string {
  const nombre = nombreDia(fecha)
  return `los ${nombre.endsWith('s') ? nombre : `${nombre}s`}`
}

/**
 * Aviso cuando la fecha elegida cae en un día sin turnos del especialista en esa sede. `null` si ese día sí atiende
 * (o si no hay horarios cargados todavía, para no afirmar algo sin datos).
 */
export function avisoDiaSinTurnos(
  turnos: readonly TurnoDoctor[] | undefined,
  sedeId: string | null,
  fecha: string,
): string | null {
  if (!turnos) return null
  const dias = diasDeAtencion(turnos, sedeId)
  if (dias.length === 0) return 'El especialista no tiene horario de atención en esta sede.'
  if (dias.includes(diaSemanaIso(fecha))) return null
  return `El especialista no atiende ${losDia(fecha)} en esta sede. Días de atención: ${textoDias(dias)}.`
}

const MESES_CORTOS = ['', 'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Set', 'Oct', 'Nov', 'Dic']

/**
 * Próximas fechas a partir de hoy (en hora de Lima) en las que el doctor tiene turnos en esta sede.
 * Útil para ofrecer atajos de 1-clic a la recepcionista.
 */
export function proximasFechasAtencion(
  turnos: readonly TurnoDoctor[] | undefined,
  sedeId: string | null,
  limite = 4,
  desde = hoyEnLima()
): Array<{ fecha: string; label: string }> {
  const dias = diasDeAtencion(turnos, sedeId)
  if (dias.length === 0) return []
  const resultado: Array<{ fecha: string; label: string }> = []
  for (let i = 0; i <= 30 && resultado.length < limite; i++) {
    const f = sumarDias(desde, i)
    const diaIso = diaSemanaIso(f)
    if (dias.includes(diaIso)) {
      const nombre = nombreDia(f)
      const partes = f.split('-')
      const diaNum = partes[2]
      const mesNum = Number(partes[1])
      const mesNom = MESES_CORTOS[mesNum] ?? ''
      const esHoy = i === 0
      const label = esHoy ? `Hoy (${diaNum} ${mesNom})` : `${nombre.slice(0, 3)} ${diaNum} ${mesNom}`
      resultado.push({ fecha: f, label })
    }
  }
  return resultado
}
