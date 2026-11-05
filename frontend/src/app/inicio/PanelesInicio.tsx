import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { BellRing, CalendarClock, CircleCheck, MessageCircle, Phone, TrendingUp } from 'lucide-react'
import { EstadoBadge, buttonVariants } from '@/design-system'
import { puede, type Rol } from '@/shared/auth'
import { formatFecha, formatSoles } from '@/shared/format'
import { useBandejaTareas, useReservasWebPendientes, type Cita } from '../agenda/api-agenda'
import { mensajeError } from '@/shared/errores'
import { horaCorta } from '../agenda/fechas'
import { useReporteFinanciero, useReportePacientes } from '../estadisticas/api-estadisticas'
import { useProductosInventario } from '../inventario/api-inventario'
import { Tarjeta } from './Tarjeta'
import { ModalRecordatorios, ModalReservasWeb, ModalValidarAdelantos } from './ModalesAtencionRequerida'

/** Citas que cuentan como "de hoy": todas menos las canceladas y las expiradas. */
const SIN_CONTAR = ['CANCELADA', 'EXPIRADA']
const MAX_FILAS = 8

/* ───────── Agenda de hoy ───────── */

export function PanelAgendaHoy({
  citas,
  cargando,
  error,
  reintentar,
  verTodas,
  className,
  sedes,
  esTodas,
}: {
  citas: Cita[] | undefined
  cargando: boolean
  error: string | null
  reintentar: () => void
  verTodas: { ruta: '/app/bandeja' | '/app/agenda'; texto: string }
  className?: string
  sedes?: { id: string; nombre: string }[]
  esTodas?: boolean
}) {
  const sedeMap = new Map((sedes ?? []).map((s) => [s.id, s.nombre]))
  const delDia = (citas ?? []).filter((c) => !SIN_CONTAR.includes(c.estado ?? '')).sort((a, b) => (a.hora ?? '').localeCompare(b.hora ?? ''))
  const visibles = delDia.slice(0, MAX_FILAS)
  return (
    <Tarjeta id="panel-agenda-hoy" titulo="Agenda de hoy" icono={<CalendarClock aria-hidden />} cargando={cargando} error={error} reintentar={reintentar} className={className}>
      {delDia.length === 0 ? (
        <p className="rounded bg-surface-container-low p-3.5 text-sm text-on-surface-variant">
          {esTodas ? 'No hay citas programadas para hoy en ninguna sede.' : 'No hay citas programadas para hoy en esta sede.'}
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-outline-variant/30">
          {visibles.map((c) => {
            const nombreSede = esTodas && c.sedeId ? sedeMap.get(c.sedeId) : null
            return (
              <li key={c.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2.5">
                <span className="w-14 shrink-0 font-mono text-sm font-bold text-primary">{horaCorta(c.hora)}</span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {c.pacienteId ? (
                      <Link to="/app/pacientes/$id" params={{ id: c.pacienteId }} className="font-bold text-on-surface hover:text-primary hover:underline">
                        {c.pacienteNombre || 'Paciente sin nombre'}
                      </Link>
                    ) : (
                      <span className="font-bold text-on-surface">{c.pacienteNombre || 'Paciente sin nombre'}</span>
                    )}
                    {nombreSede && (
                      <span className="rounded-full bg-surface-container-high px-2 py-0.5 text-xs font-semibold text-primary">
                        {nombreSede}
                      </span>
                    )}
                  </div>
                  <p className="truncate text-xs text-on-surface-variant">
                    {[c.doctorNombre, c.motivoConsulta].filter(Boolean).join(' · ') || 'Sin detalle'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {c.pacienteTelefono && (
                    <div className="flex items-center gap-1">
                      <a
                        href={`https://wa.me/51${c.pacienteTelefono.replace(/\D/g, '').slice(-9)}?text=${encodeURIComponent(
                          `Hola ${c.pacienteNombre?.split(/\s+/)[0] ?? 'estimado(a) paciente'}, le recordamos su cita hoy a las ${horaCorta(c.hora)}${c.doctorNombre ? ` con ${c.doctorNombre}` : ''}${nombreSede ? ` en ${nombreSede}` : ' en Villadent Odontología'}. Por favor confírmenos si viene en camino.`
                        )}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={buttonVariants({ variant: 'fantasma', size: 'sm', className: 'size-7 p-0 text-emerald-600 hover:text-emerald-700' })}
                        title={`WhatsApp a ${c.pacienteNombre ?? 'paciente'}`}
                        aria-label={`WhatsApp a ${c.pacienteNombre ?? 'paciente'}`}
                      >
                        <MessageCircle className="size-4" aria-hidden />
                      </a>
                      <a
                        href={`tel:+51${c.pacienteTelefono.replace(/\D/g, '').slice(-9)}`}
                        className={buttonVariants({ variant: 'fantasma', size: 'sm', className: 'size-7 p-0 text-primary hover:text-primary-hover' })}
                        title={`Llamar a ${c.pacienteNombre ?? 'paciente'}`}
                        aria-label={`Llamar a ${c.pacienteNombre ?? 'paciente'}`}
                      >
                        <Phone className="size-4" aria-hidden />
                      </a>
                    </div>
                  )}
                  <EstadoBadge estado={c.estado ?? ''} />
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {delDia.length > MAX_FILAS && <p className="text-xs text-on-surface-variant">y {delDia.length - MAX_FILAS} cita(s) más hoy.</p>}
      <Link to={verTodas.ruta} className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-auto w-fit' })}>
        {verTodas.texto}
      </Link>
    </Tarjeta>
  )
}

/* ───────── Atención requerida ───────── */

interface Alerta {
  clave: string
  texto: string
  ruta?: '/app/inventario' | '/app/agenda'
  accion: string
  n: number
}

export function PanelAlertas({ sedeId, hoy, rol }: { sedeId: string | null; hoy: string; rol: Rol }) {
  const puedeBandeja = puede(rol, 'bandeja')
  const reservas = useReservasWebPendientes(sedeId)
  const stock = useProductosInventario(sedeId ?? undefined, true)
  // La bandeja de tareas devuelve 403 a odontólogos: sin permiso ni se consulta (un 403 redirigiría a "Sin permiso").
  const tareas = useBandejaTareas(sedeId, hoy, puedeBandeja)
  const [modalAbierto, setModalAbierto] = useState<'reservas' | 'adelantos' | 'recordatorios' | null>(null)

  const consultas = [reservas, stock, ...(puedeBandeja ? [tareas] : [])]
  const cargando = consultas.some((q) => q.isLoading)
  const fallas = consultas.filter((q) => q.isError)

  const nReservas = reservas.data?.length ?? 0
  const nStock = (stock.data ?? []).filter((p) => p.alertaStockBajo === true).length
  const listaTareas = puedeBandeja ? (tareas.data?.tareas ?? []) : []
  const nAdelantos = listaTareas.filter((t) => t.tipo === 'ADELANTO_POR_EXPIRAR').length
  const nRecordatorios = listaTareas.filter((t) => t.tipo === 'RECORDATORIO_24H').length

  const todas: Alerta[] = [
    { clave: 'adelantos', texto: `${nAdelantos} adelanto(s) Yape por validar`, accion: 'Validar', n: nAdelantos },
    {
      clave: 'reservas',
      texto: `${nReservas} reserva(s) web por aceptar`,
      ruta: puedeBandeja ? undefined : '/app/agenda',
      accion: 'Revisar',
      n: nReservas,
    },
    { clave: 'recordatorios', texto: `${nRecordatorios} recordatorio(s) de mañana por enviar`, accion: 'Enviar', n: nRecordatorios },
    { clave: 'stock', texto: `${nStock} producto(s) con stock bajo`, ruta: '/app/inventario', accion: 'Ver inventario', n: nStock },
  ]
  const alertas = todas.filter((a) => a.n > 0)

  return (
    <>
      <Tarjeta id="panel-alertas" titulo="Atención requerida" icono={<BellRing aria-hidden />} cargando={cargando} error={null} reintentar={() => {}}>
        {alertas.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {alertas.map((a) => (
              <li key={a.clave} className="flex items-center justify-between gap-3 rounded border border-outline-variant/40 bg-surface-container-low p-2.5 text-sm">
                <span className="font-semibold text-on-surface">{a.texto}</span>
                {a.ruta ? (
                  <Link to={a.ruta} className={buttonVariants({ variant: 'secundario', size: 'sm' })}>
                    {a.accion}
                  </Link>
                ) : (
                  <button
                    type="button"
                    onClick={() => setModalAbierto(a.clave as 'reservas' | 'adelantos' | 'recordatorios')}
                    className={buttonVariants({ variant: 'secundario', size: 'sm' })}
                  >
                    {a.accion}
                  </button>
                )}
              </li>
            ))}
          </ul>
        ) : fallas.length === 0 ? (
          <p className="flex items-center gap-2 rounded bg-primary-fixed p-3.5 text-sm font-semibold text-on-primary-fixed [&_svg]:size-5">
            <CircleCheck aria-hidden />
            Todo en orden: no hay alertas pendientes.
          </p>
        ) : null}
        {fallas.length > 0 && (
          <div role="alert" className="flex flex-col items-start gap-2 text-sm text-on-surface-variant">
            <p>
              No se pudieron revisar {fallas.length === consultas.length ? 'las alertas' : 'algunas alertas'}. {mensajeError(fallas[0].error)}
            </p>
            <button type="button" className={buttonVariants({ variant: 'secundario', size: 'sm' })} onClick={() => fallas.forEach((q) => void q.refetch())}>
              Reintentar
            </button>
          </div>
        )}
      </Tarjeta>

      {puedeBandeja && modalAbierto === 'reservas' && (
        <ModalReservasWeb
          abierto={true}
          onCerrar={() => setModalAbierto(null)}
          sedeId={sedeId}
        />
      )}
      {puedeBandeja && modalAbierto === 'adelantos' && (
        <ModalValidarAdelantos
          abierto={true}
          onCerrar={() => setModalAbierto(null)}
          sedeId={sedeId}
          hoy={hoy}
        />
      )}
      {puedeBandeja && modalAbierto === 'recordatorios' && (
        <ModalRecordatorios
          abierto={true}
          onCerrar={() => setModalAbierto(null)}
          sedeId={sedeId}
          hoy={hoy}
        />
      )}
    </>
  )
}

/* ───────── Mes en curso (solo administración) ───────── */

export function PanelMes({ sedeId, hoy, nombreSede }: { sedeId: string | null; hoy: string; nombreSede?: string }) {
  const desde = `${hoy.slice(0, 8)}01`
  const filtros = { sedeId: sedeId ?? undefined, desde, hasta: hoy }
  const financiero = useReporteFinanciero(filtros)
  const pacientes = useReportePacientes(filtros)
  const error = financiero.error ?? pacientes.error
  const f = financiero.data
  const balance = f?.balanceNeto ?? 0

  return (
    <Tarjeta
      id="panel-mes"
      titulo="Mes en curso"
      icono={<TrendingUp aria-hidden />}
      cargando={financiero.isPending || pacientes.isPending}
      error={error ? mensajeError(error) : null}
      reintentar={() => {
        void financiero.refetch()
        void pacientes.refetch()
      }}
    >
      <p className="text-xs text-on-surface-variant">
        Del {formatFecha(desde)} al {formatFecha(hoy)}
        {nombreSede ? ` · ${nombreSede}` : ' · Red Odontológica (todas las sedes)'}
      </p>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3 xl:grid-cols-5">
        <Bloque etiqueta="Ingresos" valor={formatSoles(f?.totalIngresos ?? 0)} />
        <Bloque etiqueta="Gastos" valor={formatSoles(f?.totalGastos ?? 0)} />
        <Bloque etiqueta="Balance neto" valor={formatSoles(balance)} peligro={balance < 0} />
        <Bloque etiqueta="Por cobrar" valor={formatSoles(f?.cuentasPorCobrar ?? 0)} nota={`${f?.tratamientosConSaldoPendiente ?? 0} tratamiento(s) con saldo`} />
        <Bloque etiqueta="Pacientes nuevos" valor={String(pacientes.data?.pacientesNuevos ?? 0)} />
      </dl>
      <Link to="/app/estadisticas" className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'w-fit' })}>
        Ver estadísticas completas
      </Link>
    </Tarjeta>
  )
}

function Bloque({ etiqueta, valor, nota, peligro = false }: { etiqueta: string; valor: string; nota?: string; peligro?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-sm text-on-surface-variant">{etiqueta}</dt>
      <dd className={`font-display text-xl font-bold ${peligro ? 'text-error' : 'text-on-surface'}`}>{valor}</dd>
      {nota && <p className="text-xs text-on-surface-variant">{nota}</p>}
    </div>
  )
}
