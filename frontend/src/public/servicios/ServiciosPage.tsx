import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { ArrowRight, Calendar, Clock, Info, Scan, Search, Sparkles } from 'lucide-react'
import { useMemo, useState } from 'react'
import {
  Badge,
  buttonVariants,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  EmptyState,
  ErrorState,
  Input,
  Skeleton,
} from '@/design-system'
import { agruparPorEspecialidad, clavesReserva, listarServicios, textoPrecioDesde, type ServicioPublico } from '@/public/reserva/api-reserva'
import { DetalleServicioModal } from './DetalleServicioModal'

/**
 * Pantalla A2 · Catálogo Oficial de Servicios Odontológicos Villadent.
 * Basada en el diseño de Stitch (Servicios Odontológicos - Villadent Huancayo).
 */
export function ServiciosPage() {
  const [busqueda, setBusqueda] = useState('')
  const [especialidadSeleccionada, setEspecialidadSeleccionada] = useState<string>('TODAS')
  const [servicioDetalle, setServicioDetalle] = useState<ServicioPublico | null>(null)

  const { data: servicios = [], isLoading, isError, refetch } = useQuery({
    queryKey: clavesReserva.servicios,
    queryFn: () => listarServicios(),
  })

  // Obtener lista única de especialidades
  const especialidades = useMemo(() => {
    const set = new Set<string>()
    servicios.forEach((s) => {
      if (s.especialidad?.trim()) set.add(s.especialidad.trim())
    })
    return ['TODAS', ...Array.from(set)]
  }, [servicios])

  // Filtrado reactivo por término y especialidad
  const serviciosFiltrados = useMemo(() => {
    return servicios.filter((s) => {
      const coincideEsp =
        especialidadSeleccionada === 'TODAS' || s.especialidad?.trim() === especialidadSeleccionada
      const q = busqueda.trim().toLowerCase()
      const coincideBusqueda =
        !q ||
        s.nombre?.toLowerCase().includes(q) ||
        s.descripcion?.toLowerCase().includes(q) ||
        s.especialidad?.toLowerCase().includes(q)
      return coincideEsp && coincideBusqueda
    })
  }, [servicios, especialidadSeleccionada, busqueda])

  // Secciones por especialidad: el título y, debajo, sus tratamientos.
  const grupos = useMemo(() => agruparPorEspecialidad(serviciosFiltrados), [serviciosFiltrados])

  return (
    <div className="flex flex-col gap-10 md:gap-14">
      {/* ─── Cabecera de Página ─── */}
      <section className="flex flex-col gap-4 text-center items-center">
        <span className="inline-flex items-center gap-1.5 rounded-pill bg-primary-fixed px-3 py-1 text-xs font-bold tracking-etiqueta text-on-primary-fixed uppercase">
          <Sparkles className="size-3.5" />
          Especialistas Certificados
        </span>
        <h1 className="font-display text-headline-xl font-bold text-primary md:text-5xl">
          Servicios Odontológicos Villadent
        </h1>
        <p className="max-w-2xl text-body-lg text-on-surface-variant">
          Conoce nuestros tratamientos odontologicos y procedimientos principales. Separa tu horario y recibe una
          orientacion personalizada segun tu evaluacion.
        </p>
      </section>

      {/* ─── Buscador y Filtros por Especialidad ─── */}
      <section className="flex flex-col gap-4 rounded-card bg-surface-container-low p-4 md:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full max-w-md">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-outline" aria-hidden />
            <Input
              type="search"
              placeholder="Buscar por tratamiento o especialidad..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="pl-10"
              aria-label="Buscar servicios odontológicos"
            />
          </div>

          <span className="text-xs text-on-surface-variant font-medium">
            Mostrando <strong>{serviciosFiltrados.length}</strong> de {servicios.length} tratamientos
          </span>
        </div>

        {especialidades.length > 1 && (
          <div className="flex flex-wrap items-center gap-1.5 pt-2 border-t border-outline-variant/30">
            {especialidades.map((esp) => {
              const activo = especialidadSeleccionada === esp
              return (
                <button
                  key={esp}
                  type="button"
                  onClick={() => setEspecialidadSeleccionada(esp)}
                  className={`rounded-pill px-3 py-1.5 text-xs font-bold transition-all ${
                    activo
                      ? 'bg-acento text-on-acento shadow-sm'
                      : 'bg-surface-container-lowest text-on-surface-variant hover:text-primary'
                  }`}
                >
                  {esp === 'TODAS' ? 'Todos los tratamientos' : esp}
                </button>
              )
            })}
          </div>
        )}
      </section>

      {/* ─── Listado de Servicios ─── */}
      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i} className="p-6 flex flex-col gap-4">
              <Skeleton className="h-6 w-24 rounded-pill" />
              <Skeleton className="h-8 w-3/4" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-10 w-full mt-auto" />
            </Card>
          ))}
        </div>
      ) : isError ? (
        <ErrorState
          titulo="No pudimos cargar los servicios"
          descripcion="Hubo un inconveniente al consultar el catálogo. Intenta nuevamente."
          onReintentar={() => void refetch()}
        />
      ) : serviciosFiltrados.length === 0 ? (
        <EmptyState
          titulo="No encontramos servicios con ese criterio"
          descripcion="Intenta con otro término de búsqueda o selecciona otra categoría."
          icono={<Search className="size-8" />}
        />
      ) : (
        <div className="flex flex-col gap-10">
          {grupos.map((grupo) => (
          <section key={grupo.especialidad} aria-labelledby={`esp-${grupo.especialidad}`} className="flex flex-col gap-4">
            <h2 id={`esp-${grupo.especialidad}`} className="flex items-baseline gap-3 border-b border-outline-variant/50 pb-2 font-display text-2xl font-bold text-primary">
              {grupo.especialidad}
              <span className="text-sm font-medium text-on-surface-variant">
                {grupo.servicios.length} {grupo.servicios.length === 1 ? 'tratamiento' : 'tratamientos'}
              </span>
            </h2>
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {grupo.servicios.map((servicio) => {
            const duracion = servicio.duracionMin ? `${servicio.duracionMin} min` : '30-45 min'
            const desde = textoPrecioDesde(servicio.precio)

            return (
              <Card
                key={servicio.id}
                className="flex flex-col justify-between transition-all hover:shadow-raised border-outline-variant/60"
              >
                <CardHeader className="flex flex-col gap-2 pb-3">
                  <div className="flex items-center justify-between">
                    {desde ? (
                      <Badge tono="primario" className="text-xs">{desde}</Badge>
                    ) : (
                      <span />
                    )}
                    <span className="flex items-center gap-1 text-xs text-on-surface-variant font-medium">
                      <Clock className="size-3.5" />
                      {duracion}
                    </span>
                  </div>
                  <CardTitle className="font-display text-xl font-bold text-primary">
                    {servicio.nombre}
                  </CardTitle>
                </CardHeader>

                <CardContent className="flex flex-col gap-3 flex-1 pb-4">
                  <p className="text-sm text-on-surface-variant line-clamp-3 leading-relaxed">
                    {servicio.descripcion ||
                      'Tratamiento odontológico integral realizado bajo estrictos protocolos de bioseguridad.'}
                  </p>

                  <div className="mt-auto pt-3 border-t border-outline-variant/30">
                    <span className="text-xs font-semibold text-secondary">
                      {desde ? 'El precio final se define en la evaluación' : 'Evaluacion y presupuesto en clinica'}
                    </span>
                  </div>
                </CardContent>

                <CardFooter className="flex gap-2 pt-0">
                  <button
                    type="button"
                    onClick={() => setServicioDetalle(servicio)}
                    className={buttonVariants({
                      variant: 'secundario',
                      size: 'sm',
                      className: 'flex-1 text-xs',
                    })}
                  >
                    <Info className="mr-1.5 size-3.5" />
                    Ver detalles
                  </button>

                  <Link
                    to="/reservar"
                    search={{ servicioId: servicio.id }}
                    className={buttonVariants({
                      variant: 'accion',
                      size: 'sm',
                      className: 'flex-1 text-xs font-bold',
                    })}
                  >
                    <Calendar className="mr-1.5 size-3.5" />
                    Reservar
                  </Link>
                </CardFooter>
              </Card>
            )
          })}
        </div>
          </section>
          ))}
        </div>
      )}

      {/* ─── Banner Villamax Rayos X 3D ─── */}
      <section className="flex flex-col items-center justify-between gap-6 rounded-card border-2 border-tertiary/30 bg-surface-container-lowest p-8 md:flex-row md:p-10 shadow-card">
        <div className="flex flex-col gap-2 max-w-xl">
          <Badge tono="villamax" className="w-fit">
            Centro Radiológico Asociado
          </Badge>
          <h2 className="font-display text-headline-md font-bold text-tertiary">
            ¿Requieres Tomografía 3D o Radiografía Panorámica?
          </h2>
          <p className="text-body-md text-on-surface-variant">
            Nuestro centro especializado <strong>Villamax Chilca</strong> cuenta con tomógrafo de alta definición Cone Beam.
            Atendemos sin cita previa y con entrega digital inmediata.
          </p>
        </div>
        <Link
          to="/villamax"
          className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'text-tertiary font-bold shrink-0' })}
        >
          <Scan className="mr-2 size-5" />
          Ver estudios Villamax
          <ArrowRight className="ml-2 size-4" />
        </Link>
      </section>

      {/* Modal de Detalle */}
      <DetalleServicioModal
        servicio={servicioDetalle}
        abierto={servicioDetalle !== null}
        onCerrar={() => setServicioDetalle(null)}
      />
    </div>
  )
}
