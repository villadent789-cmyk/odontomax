import { Link } from '@tanstack/react-router'
import { ChevronsLeft, ChevronsRight } from 'lucide-react'
import { Badge, cn } from '@/design-system'
import type { Negocio, Rol } from '@/shared/auth'
import { Marca } from './Marca'
import { menuAgrupadoParaRol, type ItemMenu } from './menu'

interface SidebarProps {
  rol: Rol
  negocio?: Negocio
  contraido: boolean
  onAlternar: () => void
  /** Clases extra para la barra (p. ej. ocultarla en celular). */
  className?: string
  /** Se llama al elegir una opción (en celular cierra el menú). */
  onNavegar?: () => void
}

const claseItem =
  'group relative flex min-h-10 items-center gap-3 rounded-control px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus'

export function Sidebar({ rol, negocio, contraido, onAlternar, className, onNavegar }: SidebarProps) {
  const secciones = menuAgrupadoParaRol(rol, negocio)

  function elemento(item: ItemMenu) {
    const Icono = item.icono
    const contenido = (
      <>
        <Icono aria-hidden className="size-[1.15rem] shrink-0" />
        <span className={cn('flex-1 truncate', contraido && 'sr-only')}>{item.etiqueta}</span>
      </>
    )
    if (!item.ruta || item.proximamente) {
      return (
        <li key={item.etiqueta}>
          <span
            aria-disabled="true"
            title={contraido ? `${item.etiqueta} (próximamente)` : undefined}
            className={cn(claseItem, 'cursor-not-allowed font-normal text-on-surface-variant', contraido && 'justify-center px-0')}
          >
            <Icono aria-hidden className="size-[1.15rem] shrink-0" />
            <span className={cn('flex min-w-0 flex-1 flex-col items-start gap-1', contraido && 'sr-only')}>
              <span className="truncate">{item.etiqueta}</span>
              <Badge mayusculas className="px-2 py-0.5 text-xs">
                Próximamente
              </Badge>
            </span>
          </span>
        </li>
      )
    }
    return (
      <li key={item.etiqueta}>
        <Link
          to={item.ruta}
          onClick={onNavegar}
          activeOptions={{ exact: item.ruta === '/app' }}
          title={contraido ? item.etiqueta : undefined}
          className={cn(
            claseItem,
            'text-on-surface-variant hover:bg-surface-container hover:text-primary',
            'aria-[current=page]:bg-acento/15 aria-[current=page]:font-semibold aria-[current=page]:text-primary',
            'aria-[current=page]:before:absolute aria-[current=page]:before:inset-y-2 aria-[current=page]:before:-left-3.5 aria-[current=page]:before:w-[3px] aria-[current=page]:before:rounded-r-full aria-[current=page]:before:bg-acento',
            contraido && 'justify-center px-0',
          )}
        >
          {contenido}
        </Link>
      </li>
    )
  }

  return (
    <aside
      className={cn(
        'sticky top-0 flex h-dvh shrink-0 flex-col gap-6 overflow-y-auto overflow-x-hidden border-r border-outline-variant/70 bg-surface-container-lowest px-3.5 py-5 transition-[width] duration-200 print:hidden no-print',
        contraido ? 'w-[4.75rem]' : 'w-64',
        className,
      )}
    >
      <div className={cn('flex items-center gap-2', contraido ? 'flex-col' : 'justify-between px-1')}>
        <Marca compacta={contraido} negocio={negocio} />
        <button
          type="button"
          onClick={onAlternar}
          aria-expanded={!contraido}
          aria-controls="menu-principal"
          aria-label={contraido ? 'Expandir menú lateral' : 'Contraer menú lateral'}
          className="inline-flex size-9 items-center justify-center rounded-xl text-on-surface-variant hover:bg-surface-container-high hover:text-primary focus-visible:outline-2 focus-visible:outline-focus [&_svg]:size-4.5"
        >
          {contraido ? <ChevronsRight aria-hidden /> : <ChevronsLeft aria-hidden />}
        </button>
      </div>

      <nav id="menu-principal" aria-label="Menú principal" className="flex flex-col gap-6">
        {secciones.map((seccion, i) => (
          <div key={seccion.grupo} role="group" aria-label={seccion.titulo} className="flex flex-col gap-1">
            {contraido ? (
              i > 0 && <hr aria-hidden className="mx-2 border-outline-variant/40" />
            ) : (
              <p aria-hidden className="px-3 pb-1 text-xs font-semibold tracking-etiqueta text-on-surface-variant/70 uppercase">
                {seccion.titulo}
              </p>
            )}
            <ul className="flex flex-col gap-0.5">{seccion.items.map(elemento)}</ul>
          </div>
        ))}
      </nav>
    </aside>
  )
}
