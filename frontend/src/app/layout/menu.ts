import {
  BarChart3,
  Boxes,
  CalendarClock,
  ClipboardList,
  ClipboardPlus,
  FlaskConical,
  LayoutDashboard,
  ListChecks,
  ScanLine,
  Settings2,
  ShieldCheck,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { puede, type Negocio, type Recurso, type Rol } from '@/shared/auth'

export type RutaMenu =
  | '/app'
  | '/app/bandeja'
  | '/app/agenda'
  | '/app/pacientes'
  | '/app/nuevo-tratamiento'
  | '/app/caja'
  | '/app/inventario'
  | '/app/laboratorio'
  | '/app/radiologia'
  | '/app/estadisticas'
  | '/app/configuracion'
  | '/app/auditoria'
  | '/app/villamax'
  | '/app/villamax/estudios'

/** Secciones del menú lateral: lo de todos los días arriba, la gestión en medio y la configuración al final. */
export type GrupoMenu = 'diario' | 'gestion' | 'sistema'

export const TITULO_GRUPO: Record<GrupoMenu, string> = {
  diario: 'Día a día',
  gestion: 'Gestión',
  sistema: 'Sistema',
}

export interface ItemMenu {
  etiqueta: string
  icono: LucideIcon
  recurso: Recurso
  grupo: GrupoMenu
  /** Ruta; los módulos sin API todavía no tienen ruta y se muestran como "Próximamente". */
  ruta: RutaMenu | null
  proximamente?: boolean
}

/** Menú lateral para Villadent (odontología). */
export const MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', icono: LayoutDashboard, recurso: 'inicio', grupo: 'diario', ruta: '/app' },
  { etiqueta: 'Bandeja del día', icono: ClipboardList, recurso: 'bandeja', grupo: 'diario', ruta: '/app/bandeja' },
  { etiqueta: 'Agenda', icono: CalendarClock, recurso: 'agenda', grupo: 'diario', ruta: '/app/agenda' },
  { etiqueta: 'Pacientes', icono: ListChecks, recurso: 'pacientes', grupo: 'diario', ruta: '/app/pacientes' },
  { etiqueta: 'Nuevo tratamiento', icono: ClipboardPlus, recurso: 'nuevoTratamiento', grupo: 'diario', ruta: '/app/nuevo-tratamiento' },
  { etiqueta: 'Caja', icono: Wallet, recurso: 'caja', grupo: 'diario', ruta: '/app/caja' },
  { etiqueta: 'Almacén', icono: Boxes, recurso: 'inventario', grupo: 'gestion', ruta: '/app/inventario' },
  { etiqueta: 'Laboratorio Dental', icono: FlaskConical, recurso: 'laboratorio', grupo: 'gestion', ruta: '/app/laboratorio' },
  { etiqueta: 'Villamax Radiología', icono: ScanLine, recurso: 'villamax', grupo: 'gestion', ruta: '/app/radiologia' },
  { etiqueta: 'Reportes', icono: BarChart3, recurso: 'estadisticas', grupo: 'gestion', ruta: '/app/estadisticas' },
  { etiqueta: 'Auditoría', icono: ShieldCheck, recurso: 'auditoria', grupo: 'sistema', ruta: '/app/auditoria' },
  { etiqueta: 'Administración', icono: Settings2, recurso: 'configuracion', grupo: 'sistema', ruta: '/app/configuracion' },
]

/**
 * Menú propio del módulo Villamax (Radiología 3D / Paso 6):
 * - Resumen: métricas operativas y balance del centro radiológico.
 * - Estudios: catálogo con precios oficiales y comisión al doctor derivador.
 * - Derivaciones y comisiones: gestión de convenios y órdenes externas.
 * - Caja: caja de Villamax Chilca.
 * - Agenda: un doctor radiólogo, un sillón 3D.
 * Oculta lo exclusivamente odontológico (odontograma, planes de ortodoncia, almacén dental, laboratorio).
 */
export const MENU_VILLAMAX: ItemMenu[] = [
  { etiqueta: 'Resumen', icono: LayoutDashboard, recurso: 'inicio', grupo: 'diario', ruta: '/app' },
  { etiqueta: 'Agenda', icono: CalendarClock, recurso: 'agenda', grupo: 'diario', ruta: '/app/agenda' },
  { etiqueta: 'Pacientes', icono: ListChecks, recurso: 'pacientes', grupo: 'diario', ruta: '/app/pacientes' },
  { etiqueta: 'Nuevo tratamiento', icono: ClipboardPlus, recurso: 'nuevoTratamiento', grupo: 'diario', ruta: '/app/nuevo-tratamiento' },
  { etiqueta: 'Estudios', icono: ScanLine, recurso: 'inicio', grupo: 'diario', ruta: '/app/villamax/estudios' },
  { etiqueta: 'Derivaciones y comisiones', icono: ScanLine, recurso: 'villamax', grupo: 'diario', ruta: '/app/radiologia' },
  { etiqueta: 'Caja', icono: Wallet, recurso: 'caja', grupo: 'diario', ruta: '/app/caja' },
  { etiqueta: 'Reportes', icono: BarChart3, recurso: 'estadisticas', grupo: 'gestion', ruta: '/app/estadisticas' },
  { etiqueta: 'Auditoría', icono: ShieldCheck, recurso: 'auditoria', grupo: 'sistema', ruta: '/app/auditoria' },
  { etiqueta: 'Administración', icono: Settings2, recurso: 'configuracion', grupo: 'sistema', ruta: '/app/configuracion' },
]

/** Módulos que no se muestran en el menú odontológico de Villadent. */
export const MODULOS_OCULTOS: readonly Recurso[] = ['villamax', 'bandeja']

export function menuParaRol(rol: Rol, negocio?: Negocio): ItemMenu[] {
  if (negocio === 'VILLAMAX') {
    return MENU_VILLAMAX.filter((item) => {
      if ((item.grupo === 'gestion' || item.grupo === 'sistema') && rol !== 'ADMIN_GENERAL') {
        return false
      }
      return puede(rol, item.recurso)
    })
  }
  const visible = (item: ItemMenu) => !MODULOS_OCULTOS.includes(item.recurso)
  return MENU.filter((item) => visible(item) && puede(rol, item.recurso))
}

export interface SeccionMenu {
  grupo: GrupoMenu
  titulo: string
  items: ItemMenu[]
}

/** El menú del rol agrupado por secciones; las secciones sin ítems para ese rol no aparecen. */
export function menuAgrupadoParaRol(rol: Rol, negocio?: Negocio): SeccionMenu[] {
  const items = menuParaRol(rol, negocio)
  return (Object.keys(TITULO_GRUPO) as GrupoMenu[])
    .map((grupo) => ({ grupo, titulo: TITULO_GRUPO[grupo], items: items.filter((i) => i.grupo === grupo) }))
    .filter((seccion) => seccion.items.length > 0)
}
