import { ClipboardList, FlaskConical, Pencil, Plus, Power } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Alert, Badge, Button, EmptyState, ErrorState, Skeleton, toast } from '@/design-system'
import { formatSoles } from '@/shared/format'
import { mensajeError } from '@/shared/errores'
import { useSesion } from '@/shared/auth'
import { RecetaInsumosModal } from './RecetaInsumosModal'
import { useCambiarEstadoServicio, useServiciosAdmin, type ServicioAdmin } from './api-servicios'
import { ServicioFormModal } from './ServicioFormModal'

/**
 * Catálogo de servicios de la clínica (solo ADMIN_GENERAL): alta y edición. Un servicio nuevo queda activo y aparece solo en
 * los tratamientos clínicos y en la web de reservas. No hay botón para desactivar: solo se puede reactivar uno que ya
 * estuviera inactivo, para que ninguno quede atrapado.
 */
export function ServiciosPanel() {
  const servicios = useServiciosAdmin()
  const cambiarEstado = useCambiarEstadoServicio()
  const [formulario, setFormulario] = useState<{ servicio: ServicioAdmin | null } | null>(null)
  const [errorEstado, setErrorEstado] = useState<string | null>(null)
  const [recetaDe, setRecetaDe] = useState<ServicioAdmin | null>(null)
  // Los insumos se descuentan solo en Villadent; los estudios de Villamax no tienen receta.
  const permiteInsumos = useSesion()?.negocioActivo !== 'VILLAMAX'

  const especialidades = useMemo(
    () => [...new Set((servicios.data ?? []).map((s) => s.especialidad).filter((e): e is string => !!e))].sort((a, b) => a.localeCompare(b, 'es')),
    [servicios.data],
  )

  async function activar(servicio: ServicioAdmin) {
    setErrorEstado(null)
    try {
      await cambiarEstado.mutateAsync({ id: servicio.id!, activo: true })
      toast(`${servicio.nombre}: activado`)
    } catch (e) {
      setErrorEstado(mensajeError(e))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="max-w-2xl text-on-surface-variant">
          Catálogo de servicios: lo que se puede agendar y presupuestar. Un servicio nuevo queda disponible de inmediato en los
          tratamientos clínicos y en la web de reservas en línea (la web puede tardar unos 5 minutos en mostrarlo).
        </p>
        <Button onClick={() => setFormulario({ servicio: null })}>
          <Plus aria-hidden />
          Nuevo servicio
        </Button>
      </div>

      {errorEstado && (
        <Alert tono="error" titulo="No se pudo activar el servicio" onCerrar={() => setErrorEstado(null)}>
          {errorEstado}
        </Alert>
      )}

      <section aria-label="Catálogo de servicios" className="rounded-card bg-surface-container-lowest shadow-card">
        {servicios.isPending ? (
          <div role="status" aria-label="Cargando servicios" className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : servicios.isError ? (
          <ErrorState titulo="No pudimos cargar los servicios" descripcion={mensajeError(servicios.error)} onReintentar={() => void servicios.refetch()} />
        ) : servicios.data.length === 0 ? (
          <EmptyState
            icono={<ClipboardList />}
            titulo="Aún no hay servicios"
            descripcion="Crea el primero para poder agendar citas y armar tratamientos."
            accion={<Button onClick={() => setFormulario({ servicio: null })}>Nuevo servicio</Button>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[46rem] text-left text-sm">
              <caption className="sr-only">Servicios registrados</caption>
              <thead className="text-on-surface-variant">
                <tr className="border-b border-outline-variant/40">
                  <th scope="col" className="px-4 py-3 font-bold">Servicio</th>
                  <th scope="col" className="px-4 py-3 font-bold">Especialidad</th>
                  <th scope="col" className="px-4 py-3 font-bold">Duración</th>
                  <th scope="col" className="px-4 py-3 font-bold">Precio</th>
                  <th scope="col" className="px-4 py-3 font-bold">Estado</th>
                  <th scope="col" className="px-4 py-3 font-bold"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {servicios.data.map((s) => (
                  <tr key={s.id} className="border-b border-outline-variant/20 last:border-0">
                    <th scope="row" className="px-4 py-3 font-bold text-on-surface">
                      {s.nombre}
                      {s.descripcion && <span className="block max-w-xs truncate text-xs font-normal text-on-surface-variant">{s.descripcion}</span>}
                    </th>
                    <td className="px-4 py-3">{s.especialidad || '—'}</td>
                    <td className="px-4 py-3">{s.duracionMin ? `${s.duracionMin} min` : '—'}</td>
                    <td className="px-4 py-3">{formatSoles(s.precio)}</td>
                    <td className="px-4 py-3">
                      <Badge punto tono={s.activo ? 'primario' : 'neutro'}>{s.activo ? 'Activo' : 'Inactivo'}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button size="sm" variant="secundario" onClick={() => setFormulario({ servicio: s })} aria-label={`Editar servicio ${s.nombre}`}>
                          <Pencil aria-hidden />
                          Editar
                        </Button>
                        {permiteInsumos && (
                          <Button size="sm" variant="secundario" onClick={() => setRecetaDe(s)} aria-label={`Insumos del servicio ${s.nombre}`}>
                            <FlaskConical aria-hidden />
                            Insumos
                          </Button>
                        )}
                        {!s.activo && (
                          <Button
                            size="sm"
                            variant="primario"
                            disabled={cambiarEstado.isPending}
                            onClick={() => void activar(s)}
                            aria-label={`Activar servicio ${s.nombre}`}
                          >
                            <Power aria-hidden />
                            Activar
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

      {recetaDe && <RecetaInsumosModal servicio={{ id: recetaDe.id!, nombre: recetaDe.nombre ?? '' }} alCerrar={() => setRecetaDe(null)} />}

      {formulario && (
        <ServicioFormModal abierto servicio={formulario.servicio} especialidades={especialidades} alCerrar={() => setFormulario(null)} />
      )}
    </div>
  )
}
