import { useMemo, useState } from 'react'
import { AlertCircle, Clock, Percent, ScanLine, Search } from 'lucide-react'
import { Badge, Card, Input, Skeleton } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatSoles } from '@/shared/format'
import { useServiciosCatalogo } from '../tratamientos/api-tratamientos'

const SEDE_VILLAMAX = 'sede-villamax-chilca'

/**
 * Catálogo de Estudios Radiológicos de Villamax 3D (Paso 6).
 * Muestra tarifas oficiales y porcentaje de comisión para doctores derivadores.
 * Regla de visibilidad: la comisión solo la ve ADMIN_GENERAL.
 */
export function EstudiosVillamaxPage() {
  const sesion = useSesion()
  const rol = sesion?.usuario.rol
  const esAdminGeneral = rol === 'ADMIN_GENERAL'
  const veComision = esAdminGeneral

  const [busqueda, setBusqueda] = useState('')
  const servicios = useServiciosCatalogo(SEDE_VILLAMAX, true)

  const lista = useMemo(() => {
    const todos = servicios.data ?? []
    const q = busqueda.toLowerCase().trim()
    if (!q) return todos
    return todos.filter(
      (s) =>
        (s.nombre ?? '').toLowerCase().includes(q) ||
        (s.descripcion && s.descripcion.toLowerCase().includes(q)),
    )
  }, [servicios.data, busqueda])

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      {/* ─── Encabezado Oficial ─── */}
      <div className="flex flex-col gap-3 rounded-panel border border-outline-variant/60 bg-surface-container-low p-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-xl bg-tertiary text-on-tertiary shadow-sm">
              <ScanLine className="size-5" />
            </span>
            <div className="flex items-center gap-2">
              <h1 className="font-display text-2xl font-bold tracking-tight text-on-surface">
                Estudios Radiológicos & Tomografía 3D
              </h1>
              <Badge tono="villamax">Catálogo Villamax</Badge>
            </div>
          </div>
          <p className="text-xs text-on-surface-variant">
            Tarifario oficial de estudios radiográficos y tomográficos volumétricos para diagnóstico odontológico.
          </p>
        </div>

        <div className="relative min-w-[240px]">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-on-surface-variant" />
          <Input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar estudio o descripción..."
            className="pl-9 text-xs"
          />
        </div>
      </div>

      {/* ─── Listado / Tabla de Estudios ─── */}
      <Card className="overflow-hidden p-0 shadow-card">
        {servicios.isPending ? (
          <div role="status" aria-label="Cargando catálogo" className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : servicios.isError ? (
          <div role="alert" className="flex items-center gap-2 p-6 text-sm text-error">
            <AlertCircle className="size-5 shrink-0" />
            <span>No se pudo cargar el catálogo de estudios radiológicos.</span>
          </div>
        ) : lista.length === 0 ? (
          <div className="p-8 text-center text-sm text-on-surface-variant">
            No se encontraron estudios que coincidan con la búsqueda.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Catálogo de estudios radiológicos</caption>
              <thead className="border-b border-outline-variant/40 bg-surface-container-lowest text-xs font-bold text-on-surface-variant uppercase tracking-wider">
                <tr>
                  <th scope="col" className="px-5 py-3.5">Estudio</th>
                  <th scope="col" className="px-5 py-3.5">Duración</th>
                  <th scope="col" className="px-5 py-3.5">Tarifa Oficial</th>
                  {veComision && <th scope="col" className="px-5 py-3.5">% Comisión</th>}
                  {esAdminGeneral && <th scope="col" className="px-5 py-3.5">Monto Comisión</th>}
                  <th scope="col" className="px-5 py-3.5">Disponibilidad</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 bg-surface-container-lowest">
                {lista.map((estudio) => {
                  const precio = estudio.precio ?? 0
                  const pct = estudio.comisionPorcentaje ?? 20
                  const montoComision = (precio * pct) / 100

                  return (
                    <tr key={estudio.id} className="transition-colors hover:bg-surface-container-low/60">
                      <td className="px-5 py-4">
                        <div className="flex flex-col">
                          <span className="font-bold text-on-surface">{estudio.nombre}</span>
                          {estudio.descripcion && (
                            <span className="text-xs text-on-surface-variant line-clamp-1">
                              {estudio.descripcion}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-5 py-4 text-xs text-on-surface-variant whitespace-nowrap">
                        <span className="inline-flex items-center gap-1">
                          <Clock className="size-3.5 text-tertiary" />
                          {estudio.duracionMin ? `${estudio.duracionMin} min` : '15 min'}
                        </span>
                      </td>
                      <td className="px-5 py-4 font-bold text-on-surface whitespace-nowrap">
                        {precio > 0 ? formatSoles(precio) : 'A cotizar'}
                      </td>
                      {veComision && (
                        <td className="px-5 py-4 whitespace-nowrap">
                          <span className="inline-flex items-center gap-1 rounded-full bg-tertiary/10 px-2.5 py-0.5 text-xs font-bold text-tertiary">
                            <Percent className="size-3" />
                            {pct}%
                          </span>
                        </td>
                      )}
                      {esAdminGeneral && (
                        <td className="px-5 py-4 text-xs font-semibold text-warning whitespace-nowrap">
                          {precio > 0 ? formatSoles(montoComision) : '—'}
                        </td>
                      )}
                      <td className="px-5 py-4 whitespace-nowrap">
                        <Badge punto tono="villamax">
                          Disponible
                        </Badge>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
