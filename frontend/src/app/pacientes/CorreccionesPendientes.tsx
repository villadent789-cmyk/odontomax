import { useState } from 'react'
import { Alert, Badge, Button, toast } from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { cambiosDe, useResolverSolicitud, useSolicitudesPendientes, type SolicitudCorreccion } from './api-correcciones'

/** Bandeja del dueño: las correcciones de datos de pacientes que esperan su autorización. */
export function CorreccionesPendientes() {
  const pendientes = useSolicitudesPendientes(true)
  const resolver = useResolverSolicitud()
  const [error, setError] = useState<string | null>(null)

  if (!pendientes.data || pendientes.data.length === 0) return null

  async function decidir(s: SolicitudCorreccion, aprobar: boolean) {
    setError(null)
    try {
      await resolver.mutateAsync({ id: s.id!, aprobar })
      toast(aprobar ? 'Corrección aprobada y aplicada' : 'Corrección rechazada')
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  return (
    <section aria-label="Correcciones pendientes" className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card">
      <div className="flex items-center gap-2">
        <h2 className="font-display text-lg font-bold text-on-surface">Correcciones de datos por autorizar</h2>
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
            <p className="font-semibold text-on-surface">
              {s.nombresAnterior} {s.apellidosAnterior} <span className="font-normal text-on-surface-variant">· DNI {s.dniAnterior}</span>
            </p>
            <ul className="mt-1 list-disc pl-5 text-on-surface">
              {cambiosDe(s).map((linea) => (
                <li key={linea}>{linea}</li>
              ))}
            </ul>
            <p className="mt-1 text-on-surface-variant">
              Motivo: {s.motivo} · Pide: {s.solicitadoPorNombre}
            </p>
            <div className="mt-2 flex gap-2">
              <Button type="button" onClick={() => void decidir(s, true)} disabled={resolver.isPending}>
                Aprobar
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
