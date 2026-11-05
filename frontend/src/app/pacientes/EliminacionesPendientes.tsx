import { useState } from 'react'
import { Alert, Badge, Button, toast } from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { useEliminacionesPendientes, useResolverEliminacion, type SolicitudEliminacion } from './api-eliminaciones'

/** Bandeja del dueño: las eliminaciones de radiografías y pacientes que esperan su autorización. */
export function EliminacionesPendientes() {
  const pendientes = useEliminacionesPendientes(true)
  const resolver = useResolverEliminacion()
  const [error, setError] = useState<string | null>(null)

  if (!pendientes.data || pendientes.data.length === 0) return null

  async function decidir(s: SolicitudEliminacion, aprobar: boolean) {
    setError(null)
    try {
      await resolver.mutateAsync({ id: s.id!, aprobar })
      toast(aprobar ? 'Eliminación aprobada y realizada' : 'Eliminación rechazada')
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  return (
    <section aria-label="Eliminaciones pendientes" className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card">
      <div className="flex items-center gap-2">
        <h2 className="font-display text-lg font-bold text-on-surface">Eliminaciones por autorizar</h2>
        <Badge tono="accion">{pendientes.data.length}</Badge>
      </div>
      {error && (
        <Alert tono="error" titulo="No se pudo resolver">
          {error}
        </Alert>
      )}
      <ul className="mt-3 flex flex-col gap-3">
        {pendientes.data.map((s) => (
          <li key={s.id} className="rounded-panel border border-outline-variant/40 p-3 text-sm">
            <p className="font-semibold text-on-surface">{s.descripcion}</p>
            <p className="mt-1 text-on-surface-variant">
              Motivo: {s.motivo} · Pide: {s.solicitadoPorNombre}
            </p>
            <p className="mt-1 text-xs text-on-surface-variant">
              Al aprobar se elimina de forma permanente, incluido el archivo guardado.
            </p>
            <div className="mt-2 flex gap-2">
              <Button type="button" onClick={() => void decidir(s, true)} disabled={resolver.isPending}>
                Aprobar y eliminar
              </Button>
              <Button type="button" variant="secundario" onClick={() => void decidir(s, false)} disabled={resolver.isPending}>
                Rechazar
              </Button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
