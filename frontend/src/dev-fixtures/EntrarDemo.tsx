import { useNavigate, useRouterState } from '@tanstack/react-router'
import { FlaskConical } from 'lucide-react'
import { useState } from 'react'
import { NOMBRE_ROL, ROLES, guardarSesion, type Rol } from '@/shared/auth'
import { destinoSeguro } from '@/app/auth/sesion-real'
import { sesionDemo } from './index'

/**
 * Franja coral SOLO de desarrollo en la pantalla de acceso: entrar con la sesión de ejemplo es una opción
 * explícita (nunca automática). Con backend, el flujo normal es el login real.
 */
export default function EntrarDemo() {
  const navigate = useNavigate()
  const redirect = useRouterState({
    select: (s) => {
      const r: unknown = (s.location.search as Record<string, unknown>).redirect
      return typeof r === 'string' ? r : undefined
    },
  })
  const [rol, setRol] = useState<Rol>('RECEPCION')

  const entrar = () => {
    guardarSesion(sesionDemo(rol))
    void navigate({ href: destinoSeguro(redirect) })
  }

  return (
    <div
      role="note"
      aria-label="Aviso de desarrollo"
      className="flex flex-wrap items-center gap-x-4 gap-y-2 bg-secondary-fixed px-4 py-2 text-sm text-on-secondary-fixed"
    >
      <span className="flex items-center gap-2 font-bold">
        <FlaskConical aria-hidden className="size-4" />
        Desarrollo: puedes entrar sin backend con datos de ejemplo (src/dev-fixtures).
      </span>
      <label className="flex items-center gap-2">
        Rol de prueba:
        <select
          value={rol}
          onChange={(e) => setRol(e.target.value as Rol)}
          className="rounded-md bg-surface-container-lowest px-2 py-1 text-on-surface focus-visible:outline-2 focus-visible:outline-focus"
        >
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {NOMBRE_ROL[r]} ({r})
            </option>
          ))}
        </select>
      </label>
      <button
        type="button"
        onClick={entrar}
        className="rounded-pill bg-on-secondary-fixed px-3 py-1 font-bold text-secondary-fixed focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        Entrar con sesión de ejemplo
      </button>
    </div>
  )
}
