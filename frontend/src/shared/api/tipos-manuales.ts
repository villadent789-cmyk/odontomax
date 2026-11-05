/**
 * Tipos escritos a mano para los endpoints que el contrato OpenAPI no tipa.
 *
 * Única fuente de estos tipos: cada entrada cita la fila de docs/frontend/brechas-api.md (Parte A) de la que sale.
 * El cliente tipado (cliente-api.ts) usa estas definiciones EN LUGAR del `object`/`Map` genérico del contrato.
 * Cuando el Backend v2 tipe un endpoint en el contrato, se borra su entrada de aquí (y la fila de brechas-api.md).
 */
import type { components } from './schema'

type Esquemas = components['schemas']

/** { mensaje } — respuesta mínima de varios endpoints de identidad y pacientes. */
export interface RespuestaMensaje {
  mensaje: string
}

/** brechas-api.md A.1, filas `POST /auth/2fa/verificar-activar` y `POST /auth/2fa/desactivar`. */
export interface RespuestaEstado2fa {
  mensaje: string
  activo: boolean
}

/** brechas-api.md A.1, filas `POST /usuarios/{id}/reset-password` y `POST /usuarios/{id}/2fa/restablecer`. */
export interface RespuestaStatusMensaje {
  status: number
  mensaje: string
}

/** brechas-api.md A.1, fila `DELETE /usuarios/{id}`. */
export interface RespuestaEliminarUsuario {
  status: number
  tipo: 'LOGICO' | 'FISICO'
  mensaje: string
}

/** Página de Spring Data (brechas-api.md A.1, fila `GET /pacientes` con `page`). */
export interface PaginaSpring<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  numberOfElements: number
  first: boolean
  last: boolean
  empty: boolean
  sort?: unknown
  pageable?: unknown
}

/** brechas-api.md A.1, fila `GET /radiografias/{id}/url-firmada`. */
export interface UrlFirmadaRadiografia {
  id: string
  urlFirmada: string
  expiraEnMinutos: number
}

/** brechas-api.md A.1, fila `POST /reservas-publicas` (201). */
export interface TicketReserva {
  codigoTicket: string
  sedeId: string
  sedeNombre: string
  /** Fecha ISO (yyyy-MM-dd). */
  fecha: string
  turno: string
  estado: string
  especialidad: string
  mensaje: string
  // V2: Adelanto y pagos
  adelantoRequerido?: boolean
  adelantoMonto?: number
  adelantoInstrucciones?: string
  adelantoTelefonoYape?: string
  adelantoQrUrl?: string
  adelantoMinutosExpiracion?: number
  /** WhatsApp de la sede donde se atenderá (si el servidor lo envía). */
  telefonoWhatsappSede?: string
}

/** brechas-api.md A.1, fila `POST /reservas-publicas/consulta` (200). */
export interface ConsultaReserva {
  ticket: string
  estado: string
  /** Fecha ISO (yyyy-MM-dd). */
  fecha: string
  turno: string
  sedeId: string
  sedeNombre: string
}

/** Estados de cita válidos (brechas-api.md A.2, fila `PATCH /citas/{id}/estado`). */
export const ESTADOS_CITA = [
  'PENDIENTE_WEB',
  'PENDIENTE_DE_PAGO',
  'CONFIRMADA_CON_ADELANTO',
  'EXPIRADA',
  'PROGRAMADA',
  'CONFIRMADA',
  'EN_SALA',
  'EN_ATENCION',
  'ATENDIDA',
  'CANCELADA',
  'NO_ASISTIO',
] as const
export type EstadoCita = (typeof ESTADOS_CITA)[number]

/**
 * Respuestas 2xx reales por operación ("MÉTODO /ruta" tal como figura en el contrato).
 * brechas-api.md A.1 «Respuestas sin esquema tipado».
 */
export interface RespuestasManuales {
  'POST /auth/cambiar-password': RespuestaMensaje
  'POST /auth/2fa/verificar-activar': RespuestaEstado2fa & { activo: true }
  'POST /auth/2fa/desactivar': RespuestaEstado2fa & { activo: false }
  'POST /usuarios/{id}/reset-password': RespuestaStatusMensaje
  'POST /usuarios/{id}/2fa/restablecer': RespuestaStatusMensaje
  'DELETE /usuarios/{id}': RespuestaEliminarUsuario
  /** Sin `page` devuelve un arreglo; con `page`, una página de Spring Data. */
  'GET /pacientes': Esquemas['PacienteResponseDTO'][] | PaginaSpring<Esquemas['PacienteResponseDTO']>
  'POST /pacientes/{id}/archivar': RespuestaMensaje
  'DELETE /pacientes/{id}': RespuestaStatusMensaje
  /** El contrato la tipa como `string`; es el PDF binario (se pide con `binario: true`). Ver brechas-api.md A.1. */
  'POST /recetas/pdf': Blob
  'GET /radiografias/{id}/url-firmada': UrlFirmadaRadiografia
  'POST /reservas-publicas': TicketReserva
  'POST /reservas-publicas/consulta': ConsultaReserva
  /** brechas-api.md A.3: 204 sin cuerpo cuando no hay caja abierta. */
  'GET /caja/activa': Esquemas['CajaSesionResponseDTO'] | undefined
}

/** Cuerpos de petición reales (brechas-api.md A.2 «Cuerpos de petición sin esquema tipado»). */
export interface CuerposManuales {
  'PATCH /citas/{id}/estado': { estado: EstadoCita; motivo?: string }
  'POST /pacientes/{id}/archivar': { motivo: string }
}
