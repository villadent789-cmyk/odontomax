/**
 * Reserva pública (A4-A6). Todos los endpoints son públicos (SecurityConfig: `/reservas-publicas/**` y `GET /sedes`),
 * así que se llaman con `sinRedireccion`: un 401/403 inesperado no debe llevar al login del personal.
 *
 * La reserva pública NO trabaja por hueco exacto: el cuerpo es sede + fecha de preferencia + turno (MANANA/TARDE) y
 * la clínica confirma la hora por WhatsApp. `/especialistas/{id}/disponibilidad` exige JWT y no se usa aquí
 * (brechas-api.md, A4-1).
 */
import { useQuery } from '@tanstack/react-query'
import { apiTipada, type Cuerpo, type Esquema } from '@/shared/api'
import { api, BloqueoError, HttpError } from '@/shared/http'

export type SedePublica = Esquema<'SedeResponseDTO'>
export type ServicioPublico = Esquema<'ServicioResponseDTO'>
export type EspecialistaPublico = Esquema<'EspecialistaResponseDTO'>
export type CuerpoReserva = Cuerpo<'post', '/reservas-publicas'>
export type CuerpoConsulta = Cuerpo<'post', '/reservas-publicas/consulta'>

const sinRedireccion = true

/** Servicios de una misma especialidad: la sección con su título y los tratamientos debajo. */
export interface GrupoServicios {
  especialidad: string
  servicios: ServicioPublico[]
}

/** Agrupa por especialidad respetando el orden en que aparece cada una (el del catálogo). */
export function agruparPorEspecialidad(servicios: ServicioPublico[]): GrupoServicios[] {
  const grupos = new Map<string, ServicioPublico[]>()
  for (const s of servicios) {
    const clave = s.especialidad?.trim() || 'Otros servicios'
    grupos.set(clave, [...(grupos.get(clave) ?? []), s])
  }
  return [...grupos].map(([especialidad, lista]) => ({ especialidad, servicios: lista }))
}

/** «Desde S/ 60.00»: los precios del tarifario son mínimos; el precio final se define en la evaluación. */
export function textoPrecioDesde(precio: number | null | undefined): string | null {
  return precio != null && precio > 0 ? `Desde S/ ${Number(precio).toFixed(2)}` : null
}

export const clavesReserva = {
  sedes: ['publico', 'sedes'] as const,
  servicios: ['publico', 'catalogo', 'servicios'] as const,
  especialistas: ['publico', 'catalogo', 'especialistas'] as const,
}

export function listarSedes(negocio?: string) {
  const url = negocio ? `/sedes?negocio=${encodeURIComponent(negocio)}` : '/sedes'
  return api.get<SedePublica[]>(url, { sinRedireccion })
}

/** Servicios activos ordenados por `orden` (y por nombre si no lo tienen). */
export async function listarServicios(negocio?: string): Promise<ServicioPublico[]> {
  const url = negocio
    ? `/reservas-publicas/catalogo/servicios?negocio=${encodeURIComponent(negocio)}`
    : '/reservas-publicas/catalogo/servicios'
  const servicios = await api.get<ServicioPublico[]>(url, { sinRedireccion })
  return servicios
    .filter((s) => s.activo !== false && s.id && s.nombre)
    .sort((a, b) => (a.orden ?? 9999) - (b.orden ?? 9999) || (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'))
}

export async function listarEspecialistas(sedeId?: string): Promise<EspecialistaPublico[]> {
  const url = sedeId
    ? `/reservas-publicas/catalogo/especialistas?sedeId=${encodeURIComponent(sedeId)}`
    : '/reservas-publicas/catalogo/especialistas'
  const especialistas = await api.get<EspecialistaPublico[]>(url, { sinRedireccion })
  return especialistas.filter((e) => e.activo !== false && e.nombre)
}

export function registrarReserva(cuerpo: CuerpoReserva) {
  return apiTipada.post('/reservas-publicas', { cuerpo, sinRedireccion })
}

export function consultarReserva(cuerpo: CuerpoConsulta) {
  return apiTipada.post('/reservas-publicas/consulta', { cuerpo, sinRedireccion })
}

/** Especialistas que atienden en la sede y cuya especialidad coincide con la del servicio (sin distinguir mayúsculas). */
export function especialistasPara(
  especialistas: EspecialistaPublico[],
  sedeId: string | undefined,
  especialidad: string | undefined,
): EspecialistaPublico[] {
  if (!sedeId) return []
  const buscada = especialidad?.trim().toLocaleLowerCase('es-PE')
  return especialistas.filter(
    (e) =>
      (e.sedes ?? []).includes(sedeId) &&
      (!buscada || (e.especialidad ?? '').trim().toLocaleLowerCase('es-PE') === buscada),
  )
}

export type ErrorReserva =
  | { tipo: 'bloqueo'; mensaje: string }
  | { tipo: 'rechazo'; mensaje: string }
  | { tipo: 'red'; mensaje: string }

/** Traduce el error de una petición pública: 429 (límite), 4xx con el mensaje del backend, o fallo de red/servidor. */
export function interpretarErrorReserva(error: unknown, generico: string): ErrorReserva {
  if (error instanceof BloqueoError) return { tipo: 'bloqueo', mensaje: error.message }
  if (error instanceof HttpError && error.status >= 400 && error.status < 500) {
    return { tipo: 'rechazo', mensaje: error.message }
  }
  return { tipo: 'red', mensaje: generico }
}

/* ───────── Consultas (TanStack Query) ───────── */

export function useSedes(negocio?: string) {
  return useQuery({
    queryKey: [...clavesReserva.sedes, negocio ?? 'todos'],
    queryFn: () => listarSedes(negocio),
    select: (s) => s.filter((x) => x.activa !== false && x.id !== 'sede-chilca-2'),
  })
}
export function useServicios(negocio?: string) {
  return useQuery({
    queryKey: [...clavesReserva.servicios, negocio ?? 'VILLADENT'],
    queryFn: () => listarServicios(negocio),
  })
}
export function useEspecialistas(sedeId?: string) {
  return useQuery({
    queryKey: [...clavesReserva.especialistas, sedeId ?? 'todas'],
    queryFn: () => listarEspecialistas(sedeId),
  })
}

/** Doctores activos asignados a la sede (misma fuente que la agenda). */
export function doctoresParaSede(
  especialistas: EspecialistaPublico[] | undefined,
  sedeId: string | undefined,
): EspecialistaPublico[] {
  if (!especialistas || !sedeId) return []
  return especialistas.filter(
    (e) => e.activo !== false && (e.sedes == null || e.sedes.length === 0 || e.sedes.includes(sedeId)),
  )
}
