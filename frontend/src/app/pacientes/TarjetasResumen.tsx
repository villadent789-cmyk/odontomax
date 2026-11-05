import { Link } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { Badge, Button, EstadoBadge, Skeleton, buttonVariants } from '@/design-system'
import { formatFecha, formatSoles, hoyEnLima } from '@/shared/format'
import { esSaldoAFavor, formatoSaldo } from '@/shared/saldo'
import { useCitasRango, type Cita } from '../agenda/api-agenda'
import { capitalizar, horaCorta, nombreDia, sumarDias } from '../agenda/fechas'
import type { RadiografiaDTO } from '../clinica/api-radiografias'
import { TIPOS_ESTUDIO } from '../clinica/tiposEstudio'
import type { EstadoCarga } from './estadoCarga'
import type { TratamientoDTO } from '../tratamientos/api-tratamientos'

/** Días hacia adelante en los que se busca la próxima cita (GET /citas no filtra por paciente: ver brechas #28). */
export const DIAS_PROXIMA_CITA = 30

const ESTADOS_SIN_ATENCION_PENDIENTE = ['CANCELADA', 'EXPIRADA', 'NO_ASISTIO', 'ATENDIDA']

const CAJA = 'rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card'

/** Marco común: título, estados de carga y de error (nunca se muestra "vacío" si en realidad falló la consulta). */
export function MarcoResumen({
  titulo,
  estado,
  derecha,
  children,
}: {
  titulo: string
  estado: EstadoCarga
  derecha?: ReactNode
  children: ReactNode
}) {
  return (
    <section className={`${CAJA} space-y-4`} aria-label={titulo}>
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-display text-base font-bold text-on-surface">{titulo}</h3>
        {estado === 'ok' && derecha}
      </div>
      {estado === 'cargando' ? (
        <div role="status" aria-label={`Cargando ${titulo.toLowerCase()}`} className="space-y-2">
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-5 w-1/2" />
        </div>
      ) : estado === 'error' ? (
        <p role="alert" className="text-sm text-on-surface-variant">
          No se pudo cargar esta información. Vuelve a abrir la ficha o inténtalo en unos momentos.
        </p>
      ) : (
        children
      )}
    </section>
  )
}

const Vacio = ({ children }: { children: ReactNode }) => (
  <p className="rounded bg-surface-container-low p-3.5 text-sm text-on-surface-variant">{children}</p>
)

const humanizar = (estado: string | undefined) => capitalizar((estado ?? '—').toLowerCase().replaceAll('_', ' '))

/* ───────── Próxima cita ───────── */

export function TarjetaProximaCita({ pacienteId, puedeAgendar }: { pacienteId: string; puedeAgendar: boolean }) {
  const hoy = hoyEnLima()
  const consulta = useCitasRango(null, hoy, sumarDias(hoy, DIAS_PROXIMA_CITA))
  const proximas = (consulta.data ?? [])
    .filter((c) => c.pacienteId === pacienteId && !ESTADOS_SIN_ATENCION_PENDIENTE.includes(c.estado ?? ''))
    .sort((a, b) => `${a.fecha} ${a.hora}`.localeCompare(`${b.fecha} ${b.hora}`))
  const cita = proximas[0]
  const estado: EstadoCarga = consulta.isPending ? 'cargando' : consulta.isError ? 'error' : 'ok'

  return (
    <MarcoResumen titulo="Próxima atención programada" estado={estado}>
      {cita ? (
        <div className="space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="font-display text-lg font-bold text-on-surface">{cita.motivoConsulta || 'Consulta'}</p>
              <p className="mt-1 text-xs text-on-surface-variant">
                {cita.doctorNombre || 'Sin doctor asignado'}
                {cita.sillonNumero != null ? ` · Sillón ${cita.sillonNumero}` : ''}
              </p>
            </div>
            <EstadoBadge estado={cita.estado ?? ''} />
          </div>
          <p className="text-sm font-semibold text-primary">
            {capitalizar(nombreDia(cita.fecha ?? ''))} {formatFecha(cita.fecha)} · {horaCorta(cita.hora)}
          </p>
          <AdelantoCita cita={cita} />
          <div className="flex flex-wrap items-center gap-3">
            <Link to="/app/agenda" className={buttonVariants({ variant: 'secundario', size: 'sm' })}>
              Ver en la agenda
            </Link>
            {proximas.length > 1 && <span className="text-xs text-on-surface-variant">y {proximas.length - 1} cita(s) más</span>}
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <Vacio>Sin citas programadas en los próximos {DIAS_PROXIMA_CITA} días.</Vacio>
          {puedeAgendar && (
            <Link to="/app/agenda" className={buttonVariants({ variant: 'accion', size: 'sm' })}>
              Agendar cita
            </Link>
          )}
        </div>
      )}
    </MarcoResumen>
  )
}

function AdelantoCita({ cita }: { cita: Cita }) {
  if (!cita.adelantoRequerido) return null
  const pagado = cita.adelantoEstado === 'PAGADO'
  return (
    <Badge tono={pagado ? 'primario' : 'accion'}>
      Adelanto {formatSoles(cita.adelantoMonto ?? 0)} · {humanizar(cita.adelantoEstado ?? 'PENDIENTE')}
      {pagado && cita.adelantoMedioPago ? ` (${humanizar(cita.adelantoMedioPago)})` : ''}
    </Badge>
  )
}

/* ───────── Tratamiento activo ───────── */

export function TarjetaTratamientoActivo({
  tratamientos,
  estado,
  onVerPlanes,
  onNuevoPlan,
}: {
  tratamientos: TratamientoDTO[]
  estado: EstadoCarga
  onVerPlanes: () => void
  onNuevoPlan: () => void
}) {
  const activos = tratamientos
    .filter((t) => t.estado === 'EN_PROCESO' || t.estado === 'PLANIFICADO')
    .sort((a, b) => Number(b.estado === 'EN_PROCESO') - Number(a.estado === 'EN_PROCESO'))
  const t = activos[0]
  const total = t?.totalSesiones ?? 0
  const hechas = t?.sesionesRealizadas ?? 0
  const porcentaje = total > 0 ? Math.min(100, Math.round((hechas / total) * 100)) : 0
  const pagado = (t?.montoPagado ?? 0) + (t?.pagadoAntesSistema ?? 0)

  return (
    <MarcoResumen titulo={t ? `Tratamiento activo: ${t.nombre}` : 'Tratamiento activo'} estado={estado}>
      {t ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2 text-xs text-on-surface-variant">
            <Badge tono="primario">{humanizar(t.estado)}</Badge>
            <span>{t.aceptadoPorPaciente ? 'Presupuesto aceptado' : 'Presupuesto pendiente de aceptación'}</span>
            {t.modalidadPago && <span>· Pago {humanizar(t.modalidadPago).toLowerCase()}</span>}
            {t.especialistaNombre && <span>· {t.especialistaNombre}</span>}
          </div>

          {total > 0 && (
            <div className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="text-on-surface-variant">
                  Sesiones: <strong className="text-on-surface">{hechas}</strong> de <strong className="text-on-surface">{total}</strong>
                </span>
                <span className="font-bold text-primary">{porcentaje}%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-surface-container">
                <div className="h-full rounded-full bg-primary" style={{ width: `${porcentaje}%` }} />
              </div>
            </div>
          )}

          {(t.items?.length ?? 0) > 0 && (
            <ul className="grid gap-2 text-xs sm:grid-cols-2" aria-label="Procedimientos del plan">
              {t.items!.slice(0, 6).map((it) => (
                <li key={it.id} className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                  <p className="font-semibold text-on-surface">
                    {it.nombreProcedimiento}
                    {it.piezaFdi != null ? ` · pieza ${it.piezaFdi}` : ''}
                  </p>
                  <p className="text-xs text-on-surface-variant">{humanizar(it.estado)}</p>
                </li>
              ))}
            </ul>
          )}

          <dl className="grid grid-cols-3 gap-2 text-xs">
            {[
              ['Costo total', formatSoles(t.costoTotal ?? 0)],
              ['Pagado', formatSoles(pagado)],
              ['Saldo', formatoSaldo(t.saldoPendiente)],
            ].map(([etiqueta, valor]) => (
              <div key={etiqueta} className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                <dt className="text-xs text-on-surface-variant">{etiqueta}</dt>
                <dd className={`font-mono font-bold ${etiqueta === 'Saldo' && esSaldoAFavor(t.saldoPendiente) ? 'text-emerald-700' : 'text-on-surface'}`}>{valor}</dd>
              </div>
            ))}
          </dl>

          <div className="flex flex-wrap items-center gap-3">
            <Button variant="secundario" size="sm" onClick={onVerPlanes}>
              Ver planes de tratamiento
            </Button>
            {activos.length > 1 && <span className="text-xs text-on-surface-variant">y {activos.length - 1} plan(es) activo(s) más</span>}
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <Vacio>Este paciente no tiene un tratamiento activo.</Vacio>
          <Button variant="accion" size="sm" onClick={onNuevoPlan}>
            Crear plan de tratamiento
          </Button>
        </div>
      )}
    </MarcoResumen>
  )
}

/* ───────── Radiografías y estudios ───────── */

const etiquetaEstudio = (tipo: string) => TIPOS_ESTUDIO.find((t) => t.valor === tipo)?.etiqueta ?? humanizar(tipo)

export function TarjetaEstudios({
  radiografias,
  estado,
  onVerGaleria,
}: {
  radiografias: RadiografiaDTO[]
  estado: EstadoCarga
  onVerGaleria: () => void
}) {
  const recientes = [...radiografias].sort((a, b) => (b.fechaToma ?? '').localeCompare(a.fechaToma ?? '')).slice(0, 4)
  return (
    <MarcoResumen
      titulo="Radiografías y estudios"
      estado={estado}
      derecha={
        <Button variant="secundario" size="sm" onClick={onVerGaleria}>
          Ver galería completa
        </Button>
      }
    >
      {recientes.length > 0 ? (
        <ul className="grid gap-3 sm:grid-cols-2" aria-label="Estudios recientes">
          {recientes.map((r) => (
            <li key={r.id} className="space-y-1 rounded-lg border border-outline-variant/40 bg-surface-container-low p-3">
              <p className="text-xs font-semibold text-on-surface">{etiquetaEstudio(r.tipoEstudio)}</p>
              <p className="text-xs text-on-surface-variant">
                {formatFecha(r.fechaToma)} · {humanizar(r.etapa)}
                {r.piezaFdi != null ? ` · pieza ${r.piezaFdi}` : ''}
              </p>
              {r.diagnosticoRadiografico && <p className="line-clamp-2 text-xs text-on-surface-variant">{r.diagnosticoRadiografico}</p>}
            </li>
          ))}
        </ul>
      ) : (
        <div className="space-y-3">
          <Vacio>Aún no hay radiografías, tomografías ni fotografías de este paciente.</Vacio>
          <Button variant="secundario" size="sm" onClick={onVerGaleria}>
            Ir a la galería
          </Button>
        </div>
      )}
    </MarcoResumen>
  )
}
