import { Lock, MapPin, Pencil, Plus, Power } from 'lucide-react'
import { useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  ErrorState,
  Skeleton,
  toast,
} from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { esSedeFija, etiquetaUnidad, useCambiarEstadoSede, useSedesAdmin, type SedeAdmin } from './api-sedes'
import { SedeFormModal } from './SedeFormModal'

/** Sedes de la clínica: alta, edición y activar/desactivar (solo ADMIN_GENERAL). */
export function SedesPanel() {
  const sedes = useSedesAdmin()
  const cambiarEstado = useCambiarEstadoSede()
  const [formulario, setFormulario] = useState<{ sede: SedeAdmin | null } | null>(null)
  const [aDesactivar, setADesactivar] = useState<SedeAdmin | null>(null)
  const [errorEstado, setErrorEstado] = useState<string | null>(null)

  async function aplicar(sede: SedeAdmin, activa: boolean) {
    setErrorEstado(null)
    try {
      await cambiarEstado.mutateAsync({ id: sede.id!, activa })
      toast(`${sede.nombre}: ${activa ? 'activada' : 'desactivada'}`)
    } catch (e) {
      setErrorEstado(mensajeError(e))
    } finally {
      setADesactivar(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="text-on-surface-variant">Sedes de la clínica: dónde atienden, cuántos sillones tienen y si están activas.</p>
        <Button onClick={() => setFormulario({ sede: null })}>
          <Plus aria-hidden />
          Nueva sede
        </Button>
      </div>

      {errorEstado && (
        <Alert tono="error" titulo="No se pudo cambiar el estado de la sede" onCerrar={() => setErrorEstado(null)}>
          {errorEstado}
        </Alert>
      )}

      <section aria-label="Directorio de sedes" className="rounded-card bg-surface-container-lowest shadow-card">
        {sedes.isPending ? (
          <div role="status" aria-label="Cargando sedes" className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : sedes.isError ? (
          <ErrorState titulo="No pudimos cargar las sedes" descripcion={mensajeError(sedes.error)} onReintentar={() => void sedes.refetch()} />
        ) : sedes.data.length === 0 ? (
          <EmptyState
            icono={<MapPin />}
            titulo="Aún no hay sedes"
            descripcion="Crea la primera para poder asignarle doctores, citas y caja."
            accion={<Button onClick={() => setFormulario({ sede: null })}>Nueva sede</Button>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[52rem] text-left text-sm">
              <caption className="sr-only">Sedes registradas</caption>
              <thead className="text-on-surface-variant">
                <tr className="border-b border-outline-variant/40">
                  <th scope="col" className="px-4 py-3 font-bold">Sede</th>
                  <th scope="col" className="px-4 py-3 font-bold">Dirección</th>
                  <th scope="col" className="px-4 py-3 font-bold">Sillones</th>
                  <th scope="col" className="px-4 py-3 font-bold">Contacto</th>
                  <th scope="col" className="px-4 py-3 font-bold">Estado</th>
                  <th scope="col" className="px-4 py-3 font-bold"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {sedes.data.filter((s) => s.id !== 'sede-chilca-2').map((s) => (
                  <tr key={s.id} className="border-b border-outline-variant/20 last:border-0">
                    <th scope="row" className="px-4 py-3 font-bold text-on-surface">
                      {s.nombre}
                      <span className="block text-xs font-normal text-on-surface-variant">{etiquetaUnidad(s.unidadDeNegocio)}</span>
                    </th>
                    <td className="px-4 py-3">{s.direccion || '—'}</td>
                    <td className="px-4 py-3">{s.sillones ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-col text-xs">
                        <span>{s.telefono || '—'}</span>
                        {s.email && <span className="text-on-surface-variant">{s.email}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge punto tono={s.activa ? 'primario' : 'neutro'}>{s.activa ? 'Activa' : 'Inactiva'}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button size="sm" variant="secundario" onClick={() => setFormulario({ sede: s })} aria-label={`Editar sede ${s.nombre}`}>
                          <Pencil aria-hidden />
                          Editar
                        </Button>
                        {s.activa && esSedeFija(s.id) ? (
                          <span className="inline-flex items-center gap-1.5 px-2 text-xs font-semibold text-on-surface-variant">
                            <Lock aria-hidden className="size-3.5" />
                            Sede fija
                          </span>
                        ) : (
                          <Button
                            size="sm"
                            variant={s.activa ? 'peligro' : 'primario'}
                            disabled={cambiarEstado.isPending}
                            onClick={() => (s.activa ? setADesactivar(s) : void aplicar(s, true))}
                            aria-label={`${s.activa ? 'Desactivar' : 'Activar'} sede ${s.nombre}`}
                          >
                            <Power aria-hidden />
                            {s.activa ? 'Desactivar' : 'Activar'}
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {formulario && <SedeFormModal abierto sede={formulario.sede} alCerrar={() => setFormulario(null)} />}

      <Dialog open={!!aDesactivar} onOpenChange={(v) => !v && setADesactivar(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>¿Desactivar la sede {aDesactivar?.nombre}?</DialogTitle>
            <DialogDescription>
              Dejará de mostrarse en la web pública, en las reservas y en el selector de sedes. Sus datos, usuarios y citas se conservan;
              puedes reactivarla cuando quieras.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secundario" onClick={() => setADesactivar(null)}>
              Cancelar
            </Button>
            <Button variant="peligro" onClick={() => aDesactivar && void aplicar(aDesactivar, false)} cargando={cambiarEstado.isPending}>
              Desactivar sede
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
