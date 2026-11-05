import { definicionEstado, type DefinicionEstado } from '../estados'
import { Badge, type BadgeProps } from './Badge'

interface EstadoBadgeProps extends Omit<BadgeProps, 'tono' | 'children'> {
  /** Valor del estado tal como lo devuelve la API. */
  estado: string
  /** Solo para pruebas y para el catálogo /app/_design. Por defecto usa el mapa único ESTADOS. */
  mapa?: Record<string, DefinicionEstado>
}

export function EstadoBadge({ estado, mapa, ...props }: EstadoBadgeProps) {
  const { texto, tono } = definicionEstado(estado, mapa)
  return (
    <Badge tono={tono} punto data-estado={estado} {...props}>
      {texto}
    </Badge>
  )
}
