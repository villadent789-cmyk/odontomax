import { useRouter } from '@tanstack/react-router'
import { FlaskConical } from 'lucide-react'
import { NOMBRE_ROL, ROLES, guardarSesion, useSesion, type Rol } from '@/shared/auth'
import { sesionDemo } from './index'

/** Franja visible solo en desarrollo: avisa que los datos son de ejemplo y permite probar el menú por rol. */
export default function DevBanner() {
  const sesion = useSesion()
  const router = useRouter()
  if (!sesion?.demo) return null

  const cambiarRol = (rol: Rol) => {
    guardarSesion(sesionDemo(rol))
    void router.invalidate()
  }

  return (
    <div
      role="note"
      aria-label="Aviso de desarrollo"
      className="flex flex-wrap items-center gap-x-4 gap-y-2 bg-secondary-fixed px-4 py-2 text-sm text-on-secondary-fixed"
    >
      <span className="flex items-center gap-2 font-bold">
        <FlaskConical aria-hidden className="size-4" />
        Desarrollo: usuario y sedes de ejemplo (src/dev-fixtures), no son datos reales.
      </span>
      <label className="flex items-center gap-2">
        Probar rol:
        <select
          value={sesion.usuario.rol}
          onChange={(e) => cambiarRol(e.target.value as Rol)}
          className="rounded-md bg-surface-container-lowest px-2 py-1 text-on-surface focus-visible:outline-2 focus-visible:outline-focus"
        >
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {NOMBRE_ROL[r]} ({r})
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
