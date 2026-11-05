import type { TonoBadge } from '@/design-system'
import type { AuditoriaDTO } from './api-auditoria'

/** Eventos por página en la tabla (la paginación es en el cliente: el backend no pagina). */
export const POR_PAGINA = 25

/** Valor del selector de sede para los eventos sin sede (el backend los muestra como "General"). */
export const SIN_SEDE = '__sin_sede__'

/**
 * Módulos que el backend registra hoy en auditoria_log (el filtro del servidor compara el nombre exacto).
 * El texto es el que ve el usuario; el valor es el que se envía en ?modulo=.
 */
export const MODULOS: readonly { valor: string; etiqueta: string }[] = [
  { valor: 'USUARIOS', etiqueta: 'Usuarios' },
  { valor: 'SEGURIDAD', etiqueta: 'Seguridad' },
  { valor: 'PACIENTES', etiqueta: 'Pacientes' },
  { valor: 'CITAS', etiqueta: 'Citas (agenda)' },
  { valor: 'CONFIGURACION', etiqueta: 'Configuración' },
  { valor: 'HISTORIA_CLINICA', etiqueta: 'Historia clínica' },
  { valor: 'ODONTOGRAMA', etiqueta: 'Odontograma' },
  { valor: 'IMAGENES_CLINICAS', etiqueta: 'Imágenes clínicas' },
  { valor: 'TRATAMIENTOS', etiqueta: 'Tratamientos' },
  { valor: 'CAJA', etiqueta: 'Caja' },
  { valor: 'PAGOS', etiqueta: 'Pagos' },
  { valor: 'GASTOS', etiqueta: 'Gastos' },
  { valor: 'INVENTARIO', etiqueta: 'Inventario' },
  { valor: 'LABORATORIO', etiqueta: 'Laboratorio' },
  { valor: 'SISTEMA', etiqueta: 'Sistema' },
]

export function etiquetaModulo(modulo: string | undefined): string {
  return MODULOS.find((m) => m.valor === modulo)?.etiqueta ?? modulo ?? '—'
}

const TONO_MODULO: Record<string, TonoBadge> = {
  USUARIOS: 'peligro',
  SEGURIDAD: 'peligro',
  CAJA: 'accion',
  PAGOS: 'accion',
  GASTOS: 'accion',
  HISTORIA_CLINICA: 'primario',
  ODONTOGRAMA: 'primario',
  IMAGENES_CLINICAS: 'primario',
  TRATAMIENTOS: 'primario',
}

export const tonoModulo = (modulo: string | undefined): TonoBadge => TONO_MODULO[modulo ?? ''] ?? 'neutro'

const TONO_ROL: Record<string, TonoBadge> = {
  ADMIN_GENERAL: 'primario',
  ADMIN_SEDE: 'primario',
  ODONTOLOGO: 'villamax',
  RECEPCION: 'accion',
}

export const tonoRol = (rol: string | undefined): TonoBadge => TONO_ROL[rol ?? ''] ?? 'neutro'

/** Minúsculas y sin tildes, para que "curacion" encuentre "Curación". */
export function normalizar(texto: string | undefined | null): string {
  return (texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

export interface FiltrosCliente {
  texto: string
  /** '' = todas; SIN_SEDE = eventos sin sede; si no, el id de la sede. */
  sede: string
  /** '' = todas; si no, yyyy-MM-dd (hora de Lima, tal como la devuelve el backend). */
  fecha: string
}

/** Filtros que el backend no ofrece: texto libre (usuario, detalle, acción, IP), sede y fecha. */
export function filtrarEventos(eventos: readonly AuditoriaDTO[], { texto, sede, fecha }: FiltrosCliente): AuditoriaDTO[] {
  const buscado = normalizar(texto.trim())
  return eventos.filter((e) => {
    if (sede === SIN_SEDE ? !!e.sedeId : sede !== '' && e.sedeId !== sede) return false
    if (fecha && e.fecha !== fecha) return false
    if (!buscado) return true
    return [e.usuarioNombre, e.detalle, e.accion, e.ip].some((campo) => normalizar(campo).includes(buscado))
  })
}

export function paginar<T>(items: readonly T[], pagina: number, porPagina = POR_PAGINA) {
  const totalPaginas = Math.max(1, Math.ceil(items.length / porPagina))
  const actual = Math.min(Math.max(1, pagina), totalPaginas)
  return { items: items.slice((actual - 1) * porPagina, actual * porPagina), pagina: actual, totalPaginas }
}

/** El detalle es texto libre; si resulta ser JSON se muestra identado. */
export function detalleLegible(detalle: string | undefined): { json: boolean; texto: string } {
  const crudo = detalle ?? ''
  const t = crudo.trim()
  if (t.startsWith('{') || t.startsWith('[')) {
    try {
      return { json: true, texto: JSON.stringify(JSON.parse(t), null, 2) }
    } catch {
      /* no era JSON válido: se muestra tal cual */
    }
  }
  return { json: false, texto: crudo }
}
