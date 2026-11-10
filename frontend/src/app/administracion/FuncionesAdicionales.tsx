import { NOMBRE_ROL } from '@/shared/auth'

/** Funciones que se pueden sumar al cargo principal. */
export type FuncionAdicional = 'ODONTOLOGO' | 'RECEPCION'
const OPCIONES: readonly FuncionAdicional[] = ['ODONTOLOGO', 'RECEPCION']

interface Props {
  /** Cargo principal elegido: su función no se ofrece como adicional. */
  rolPrincipal: string
  valor: readonly FuncionAdicional[]
  onChange: (v: FuncionAdicional[]) => void
}

/**
 * «Funciones adicionales»: una misma persona puede ser, por ejemplo, odontólogo y recepcionista con una sola cuenta. Sus
 * permisos se suman: no cambia de perfil, tiene los de ambas funciones a la vez.
 */
export function FuncionesAdicionales({ rolPrincipal, valor, onChange }: Props) {
  if (rolPrincipal === 'ADMIN_GENERAL') return null
  const ofrecidas = OPCIONES.filter((o) => o !== rolPrincipal)
  return (
    <fieldset className="flex flex-col gap-2 rounded-panel bg-surface-container-low p-3">
      <legend className="px-1 text-sm font-semibold text-on-surface">Funciones adicionales (opcional)</legend>
      <p className="text-xs text-on-surface-variant">
        Si la misma persona cumple dos funciones, márcala aquí: usa una sola cuenta y tiene los permisos de ambas a la vez.
      </p>
      {ofrecidas.map((o) => (
        <label key={o} className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={valor.includes(o)}
            onChange={(e) => onChange(e.target.checked ? [...valor, o] : valor.filter((x) => x !== o))}
          />
          También cumple la función de {NOMBRE_ROL[o]}
        </label>
      ))}
    </fieldset>
  )
}
