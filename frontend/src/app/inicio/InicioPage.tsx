import { Link } from '@tanstack/react-router'
import {
  CalendarCheck,
  CalendarDays,
  CalendarPlus,
  ShieldCheck,
  Stethoscope,
  UserPlus,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { motion } from 'motion/react'
import { Alert, Badge, buttonVariants, type ButtonProps } from '@/design-system'
import { NOMBRE_ROL, puede, useSesion, type Recurso } from '@/shared/auth'
import { formatFecha, formatSoles, hoyEnLima, saludoSegunHora } from '@/shared/format'
import { useEspecialistas, useCitasDelDia } from '../agenda/api-agenda'
import { mensajeError } from '@/shared/errores'
import { useCajaActiva } from '../caja/api-caja'
import { PanelAgendaHoy, PanelAlertas, PanelMes } from './PanelesInicio'
import { Dato, Tarjeta } from './Tarjeta'
import { TarjetaMiAvance } from './TarjetaMiAvance'
import { VillamaxResumenPage } from '../villamax/VillamaxResumenPage'
import { BandejaPage } from '../agenda/BandejaPage'
import { CorreccionesPendientes } from '../pacientes/CorreccionesPendientes'
import { EliminacionesPendientes } from '../pacientes/EliminacionesPendientes'
import { RotuloMostrando } from '../layout/RotuloMostrando'

interface Acceso {
  etiqueta: string
  ruta: '/app/bandeja' | '/app/agenda' | '/app/pacientes' | '/app/caja' | '/app/auditoria'
  icono: LucideIcon
  recurso: Recurso
  variante: ButtonProps['variant']
}

const ACCESOS: Acceso[] = [
  { etiqueta: 'Ver agenda', ruta: '/app/agenda', icono: CalendarDays, recurso: 'bandeja', variante: 'secundario' },
  { etiqueta: 'Agendar cita', ruta: '/app/agenda', icono: CalendarPlus, recurso: 'agendarCita', variante: 'primario' },
  { etiqueta: 'Registrar paciente', ruta: '/app/pacientes', icono: UserPlus, recurso: 'registrarPaciente', variante: 'secundario' },
  { etiqueta: 'Caja', ruta: '/app/caja', icono: Wallet, recurso: 'caja', variante: 'secundario' },
  { etiqueta: 'Auditoría', ruta: '/app/auditoria', icono: ShieldCheck, recurso: 'auditoria', variante: 'secundario' },
]

/**
 * Inicio (B0): pulso operativo del día de la sede activa. Todo sale de endpoints que ya existen (citas del día,
 * caja activa y especialistas de la sede); no se calcula nada que el backend no devuelva.
 */
export function InicioPage() {
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId ?? null
  const hoy = hoyEnLima()

  const citas = useCitasDelDia(sedeId, hoy, true)
  // Los totales y movimientos de la caja traen cobros de todos los doctores: el odontólogo no los consulta ni los ve.
  const verCaja = puede(sesion?.usuario.rol, 'caja')
  const caja = useCajaActiva(sedeId ?? undefined, verCaja)
  const equipo = useEspecialistas(sedeId, true)

  if (!sesion) return null
  if (sesion.negocioActivo === 'VILLAMAX') {
    return <VillamaxResumenPage />
  }
  const { usuario } = sesion
  const sede = usuario.sedes.find((s) => s.id === sedeId)
  const primerNombre = usuario.nombre.split(/\s+/)[0]
  const rol = usuario.rol
  const accesos = ACCESOS.filter((a) => puede(rol, a.recurso))

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="mx-auto flex max-w-6xl flex-col gap-6"
    >
      <section
        aria-labelledby="titulo-inicio"
        className="relative flex flex-col gap-3 overflow-hidden rounded-card border border-outline-variant/40 bg-surface-container-lowest px-6 py-7 shadow-card md:px-8"
      >
        <div aria-hidden className="pointer-events-none absolute -top-12 -right-12 size-48 rounded-full bg-primary/5 blur-2xl" />
        <div className="flex flex-wrap items-center gap-2">
          <Badge tono="primario" mayusculas>
            {NOMBRE_ROL[rol]}
          </Badge>
          <RotuloMostrando />
          <span className="text-xs font-medium text-on-surface-variant">• Red Odontológica Junín</span>
          <span className="ml-auto inline-flex items-center gap-2 rounded-full border border-outline-variant/30 bg-surface-container-low px-3.5 py-1 text-xs font-semibold text-on-surface-variant">
            <span className="size-2 animate-pulse rounded-full bg-emerald-500" />
            Hoy, {formatFecha(hoy)}
          </span>
        </div>
        <h1 id="titulo-inicio" className="font-display text-headline-lg font-bold tracking-tight text-primary md:text-headline-xl">
          {saludoSegunHora()}, {primerNombre}.
        </h1>
        <p className="max-w-2xl text-body-lg text-on-surface-variant">
          {sede ? (
            <>
              Así va el día en <strong className="font-semibold text-primary">{sede.nombre}</strong>.
            </>
          ) : (
            <>
              Resumen operativo consolidado de <strong className="font-semibold text-primary">todas las sedes</strong>.
            </>
          )}
        </p>
      </section>

      {accesos.length > 0 && (
        <nav aria-label="Accesos rápidos" className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
          {accesos.map(({ etiqueta, ruta, icono: Icono, variante }) => (
            <Link key={etiqueta} to={ruta} className={buttonVariants({ variant: variante, size: 'lg', className: 'w-full' })}>
              <Icono aria-hidden />
              {etiqueta}
            </Link>
          ))}
        </nav>
      )}

      <section aria-label="Pulso del día" className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <TarjetaCitas
          cargando={citas.isPending}
          error={citas.isError ? mensajeError(citas.error) : null}
          reintentar={() => void citas.refetch()}
          citas={citas.data}
          enlace={{ ruta: '/app/agenda', texto: 'Ver la agenda' }}
          esTodas={!sedeId}
        />
        {verCaja && (
          <TarjetaCaja
            cargando={caja.isPending}
            error={caja.isError ? mensajeError(caja.error) : null}
            reintentar={() => void caja.refetch()}
            sesionCaja={caja.data}
            puedeAbrir
            esTodas={!sedeId}
          />
        )}
        <TarjetaEquipo
          cargando={equipo.isPending}
          error={equipo.isError ? mensajeError(equipo.error) : null}
          reintentar={() => void equipo.refetch()}
          especialistas={equipo.data}
          enlaceAdministrar={rol === 'ADMIN_GENERAL'}
          esTodas={!sedeId}
        />
      </section>

      {rol === 'ADMIN_GENERAL' && <CorreccionesPendientes />}
      {rol === 'ADMIN_GENERAL' && <EliminacionesPendientes />}

      {puede(rol, 'bandeja') ? (
        // Recepción y administración: la bandeja del día (confirmar, reservas web, recordatorios, sala de espera) vive aquí.
        <>
          <BandejaPage />
          <PanelAlertas sedeId={sedeId} hoy={hoy} rol={rol} />
        </>
      ) : (
      <section aria-label="Operación de hoy" className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <PanelAgendaHoy
          className="lg:col-span-2"
          citas={citas.data}
          cargando={citas.isPending}
          error={citas.isError ? mensajeError(citas.error) : null}
          reintentar={() => void citas.refetch()}
          verTodas={{ ruta: '/app/agenda', texto: 'Abrir la agenda' }}
          sedes={usuario.sedes}
          esTodas={!sedeId}
        />
        <PanelAlertas sedeId={sedeId} hoy={hoy} rol={rol} />
      </section>
      )}

      {rol === 'ODONTOLOGO' && <TarjetaMiAvance hoy={hoy} />}

      {puede(rol, 'estadisticas') && <PanelMes sedeId={sedeId} hoy={hoy} nombreSede={sede?.nombre} />}
    </motion.div>
  )
}

/* ───────── Tarjetas ───────── */

type Citas = ReturnType<typeof useCitasDelDia>['data']

function TarjetaCitas({
  cargando,
  error,
  reintentar,
  citas,
  enlace,
  esTodas,
}: {
  cargando: boolean
  error: string | null
  reintentar: () => void
  citas: Citas
  enlace: { ruta: '/app/bandeja' | '/app/agenda'; texto: string }
  esTodas?: boolean
}) {
  // Mismo criterio que el total de la bandeja del día: todas las citas de hoy salvo las canceladas.
  const lista = citas ?? []
  const programadas = lista.filter((c) => c.estado !== 'CANCELADA').length
  const enSillon = lista.filter((c) => c.estado === 'EN_SALA').length
  const atendidas = lista.filter((c) => c.estado === 'ATENDIDA').length
  return (
    <Tarjeta
      id="tarjeta-citas"
      titulo={esTodas ? 'Citas de hoy (todas las sedes)' : 'Citas del día'}
      icono={<CalendarCheck aria-hidden />}
      cargando={cargando}
      error={error}
      reintentar={reintentar}
    >
      <dl className="flex flex-col gap-2">
        <Dato etiqueta="Programadas hoy" valor={programadas} destacado />
        <Dato etiqueta="En sillón / atención" valor={enSillon} />
        <Dato etiqueta="Atendidas" valor={atendidas} />
      </dl>
      {programadas === 0 && (
        <p className="text-sm text-on-surface-variant">
          {esTodas ? 'Aún no hay citas para hoy en ninguna sede.' : 'Aún no hay citas para hoy en esta sede.'}
        </p>
      )}
      <Link to={enlace.ruta} className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-auto w-fit' })}>
        {enlace.texto}
      </Link>
    </Tarjeta>
  )
}

function TarjetaCaja({
  cargando,
  error,
  reintentar,
  sesionCaja,
  puedeAbrir,
  esTodas,
}: {
  cargando: boolean
  error: string | null
  reintentar: () => void
  sesionCaja: ReturnType<typeof useCajaActiva>['data']
  puedeAbrir: boolean
  esTodas?: boolean
}) {
  if (esTodas) {
    return (
      <Tarjeta
        id="tarjeta-caja"
        titulo="Caja y turnos"
        icono={<Wallet aria-hidden />}
        cargando={false}
        error={null}
        reintentar={reintentar}
      >
        <p className="text-sm text-on-surface-variant">
          La caja chica y los turnos operan por sede física individual. Selecciona una sede específica en la cabecera para abrir o cuadrar turno.
        </p>
        {puedeAbrir && (
          <Link to="/app/caja" className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-auto w-fit' })}>
            Ir a Caja
          </Link>
        )}
      </Tarjeta>
    )
  }

  const abierta = sesionCaja?.estado === 'ABIERTA'
  const efectivo = sesionCaja?.totalIngresosEfectivo ?? 0
  const yape = sesionCaja?.totalYape ?? 0
  return (
    <Tarjeta id="tarjeta-caja" titulo="Caja y turno de la sede" icono={<Wallet aria-hidden />} cargando={cargando} error={error} reintentar={reintentar}>
      {abierta ? (
        <>
          <Badge tono="primario" punto className="w-fit">
            Turno abierto
          </Badge>
          <dl className="flex flex-col gap-2">
            <Dato etiqueta="Recaudación del día" valor={formatSoles(efectivo + yape)} destacado />
            <Dato etiqueta="Efectivo" valor={formatSoles(efectivo)} />
            <Dato etiqueta="Yape" valor={formatSoles(yape)} />
            <Dato etiqueta="Fondo de apertura" valor={formatSoles(sesionCaja?.montoApertura ?? 0)} />
          </dl>
          {puedeAbrir && (
            <Link to="/app/caja" className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-auto w-fit' })}>
              Ir a Caja
            </Link>
          )}
        </>
      ) : (
        <>
          <Alert tono="aviso" titulo="No hay un turno de caja abierto">
            Para registrar cobros y anticipos de los pacientes, primero hay que abrir el turno de caja de esta sede.
          </Alert>
          {puedeAbrir && (
            <Link to="/app/caja" className={buttonVariants({ variant: 'accion', size: 'md', className: 'mt-auto w-fit' })}>
              Abrir Turno de Caja
            </Link>
          )}
        </>
      )}
    </Tarjeta>
  )
}

function TarjetaEquipo({
  cargando,
  error,
  reintentar,
  especialistas,
  enlaceAdministrar,
  esTodas,
}: {
  cargando: boolean
  error: string | null
  reintentar: () => void
  especialistas: ReturnType<typeof useEspecialistas>['data']
  enlaceAdministrar: boolean
  esTodas?: boolean
}) {
  const activos = (especialistas ?? []).filter((e) => e.activo !== false)
  const especialidades = [...new Set(activos.map((e) => e.especialidad?.trim()).filter((e): e is string => !!e))]
  return (
    <Tarjeta
      id="tarjeta-equipo"
      titulo={esTodas ? 'Equipo clínico de la red' : 'Equipo clínico en sede'}
      icono={<Stethoscope aria-hidden />}
      cargando={cargando}
      error={error}
      reintentar={reintentar}
    >
      <dl className="flex flex-col gap-2">
        <Dato etiqueta="Doctores activos" valor={activos.length} destacado />
      </dl>
      {especialidades.length > 0 ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-on-surface-variant">Especialidades presentes</p>
          <ul className="flex flex-wrap gap-1.5">
            {especialidades.map((e) => (
              <li key={e}>
                <Badge>{e}</Badge>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-sm text-on-surface-variant">
          {esTodas ? 'No hay doctores activos registrados en el sistema.' : 'No hay doctores activos registrados en esta sede.'}
        </p>
      )}
      {enlaceAdministrar && (
        <Link to="/app/configuracion" className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-auto w-fit' })}>
          Administrar equipo
        </Link>
      )}
    </Tarjeta>
  )
}
