import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { esSoloOdontologo } from '@/shared/auth/sesion'
import { CalendarDays, ChevronLeft, ChevronRight, DoorOpen, Globe, Plus } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Alert, Button, EmptyState, ErrorState, Skeleton, buttonVariants } from '@/design-system'
import { cn } from '@/design-system/cn'
import { puede, useSesion } from '@/shared/auth'
import { formatFecha, formatFechaLarga, hoyEnLima } from '@/shared/format'
import {
  useCitasDelDia,
  useCitasRango,
  useDisponibilidadDe,
  useEspecialistas,
  useHorariosDe,
  useListaEspera,
  useReservasWebPendientes,
  useSedesActivas,
  type Especialista,
} from './api-agenda'
import { DialogosCita } from './DialogosCita'
import { esSinPermiso, mensajeError } from './errores'
import { aMinutos, capitalizar, deMinutos, diaSemanaIso, diasDeLaSemana, minutosAhoraEnLima, sumarDias } from './fechas'
import { horariosDelDia, rangoDelDia } from './rejilla'
import { VistaDia, type ColumnaDia } from './VistaDia'
import { VistaDiaLista } from './VistaDiaLista'
import { useMediaQuery } from '@/shared/useMediaQuery'
import { VistaSemana } from './VistaSemana'
import { useDialogosCita } from './useDialogosCita'
import { RotuloMostrando } from '../layout/RotuloMostrando'

export type Vista = 'dia' | 'semana'

interface Props {
  vistaInicial?: Vista
}

function Segmentado<T extends string>({
  etiqueta,
  opciones,
  valor,
  onCambiar,
}: {
  etiqueta: string
  opciones: { valor: T; texto: string }[]
  valor: T
  onCambiar: (v: T) => void
}) {
  return (
    <div role="group" aria-label={etiqueta} className="inline-flex flex-wrap gap-1 rounded-pill bg-surface-container-low p-1">
      {opciones.map((o) => (
        <button
          key={o.valor}
          type="button"
          aria-pressed={valor === o.valor}
          onClick={() => onCambiar(o.valor)}
          className={cn(
            'h-9 max-md:h-11 pointer-coarse:h-11 rounded-pill px-4 text-sm font-bold text-on-surface-variant hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
            valor === o.valor && 'bg-acento text-on-acento hover:text-on-acento',
          )}
        >
          {o.texto}
        </button>
      ))}
    </div>
  )
}

/**
 * B3 · Agenda diaria y semanal por sede, sillón y especialista.
 * GET /citas (fecha o desde/hasta), GET /especialistas?sedeId, GET /especialistas/{id}/horarios,
 * GET /especialistas/{id}/disponibilidad (franjas libres/ocupadas del día) y GET /sedes (sillones de la sede activa).
 */
export function AgendaPage({ vistaInicial }: Props = {}) {
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId ?? null
  const rol = sesion?.usuario.rol
  const hoy = hoyEnLima()
  const search = useSearch({ strict: false }) as { vista?: Vista }
  const navegar = useNavigate()
  const esCelular = useMediaQuery('(max-width: 767px)')
  const irAlInicio = () => navegar({ to: '/app' })
  const [fecha, setFecha] = useState(hoy)
  const [vista, setVista] = useState<Vista>(() => {
    const pedida = vistaInicial ?? (search?.vista as string | undefined)
    return pedida === 'semana' ? 'semana' : 'dia'
  })
  const [filtro, setFiltro] = useState<string>('todos')
  const dialogos = useDialogosCita()
  const puedeAgendar = puede(rol, 'agendarCita')

  const dias = diasDeLaSemana(fecha)
  const especialistasQ = useEspecialistas(sedeId)
  const sedesQ = useSedesActivas()
  const citasDia = useCitasDelDia(sedeId, fecha)
  const citasSemana = useCitasRango(sedeId, dias[0], dias[6], vista === 'semana')
  const listaEspera = useListaEspera(sedeId)
  const reservasWeb = useReservasWebPendientes(sedeId)

  // Quien suma recepción o administración ve la agenda de todos los doctores; solo el odontólogo "puro" ve la suya.
  const esOdontologo = esSoloOdontologo(sesion?.usuario)
  const especialistas: Especialista[] = (especialistasQ.data ?? []).filter((e) => e.activo !== false)
  const propio = esOdontologo ? especialistas.find((e) => e.usuarioId && e.usuarioId === sesion?.usuario.id) : undefined
  const filtroEfectivo = esOdontologo ? (propio?.id ?? '__ninguno__') : filtro
  const visibles = filtroEfectivo === 'todos' ? especialistas : especialistas.filter((e) => e.id === filtroEfectivo)
  const ids = visibles.map((e) => e.id!).filter(Boolean)

  const horariosQ = useHorariosDe(ids)
  const disponibilidadQ = useDisponibilidadDe(ids, fecha, sedeId, vista === 'dia')

  const consulta = vista === 'dia' ? citasDia : citasSemana
  const todas = consulta.data ?? []
  const citas = filtroEfectivo === 'todos' ? todas : todas.filter((c) => c.doctorId === filtroEfectivo)
  const sede = sedesQ.data?.find((s) => s.id === sedeId)
  const nombreSede = sede?.nombre ?? sesion?.usuario.sedes.find((s) => s.id === sedeId)?.nombre

  if (!sesion) return null

  const horariosVisibles = horariosQ.flatMap((q) => q.data ?? [])
  const horariosRango =
    vista === 'dia' ? horariosDelDia(horariosVisibles, diaSemanaIso(fecha), sedeId) : horariosVisibles.filter((h) => !sedeId || h.sedeId === sedeId)
  const { inicio, fin } = rangoDelDia(horariosRango, citas)

  function mover(paso: number) {
    setFecha((f) => sumarDias(f, vista === 'dia' ? paso : paso * 7))
  }

  const columnas: ColumnaDia[] = []
  visibles.forEach((e, i) => {
    const disp = disponibilidadQ[i]?.data
    const delDia = horariosDelDia(horariosQ[i]?.data, diaSemanaIso(fecha), sedeId)
    const sillon = sedeId && e.sillonesPorSede ? e.sillonesPorSede[sedeId] : undefined
    const titulo = sillon != null ? `Sillón ${sillon} · ${e.nombre ?? 'Especialista'}` : (e.nombre ?? 'Especialista')
    columnas.push({
      id: e.id!,
      titulo,
      subtitulo: [
        e.especialidad,
        delDia.length ? delDia.map((h) => `${deMinutos(aMinutos(h.horaInicio))}–${deMinutos(aMinutos(h.horaFin))}`).join(', ') : 'No atiende este día',
      ]
        .filter(Boolean)
        .join(' · '),
      avatar: true,
      citas: citas.filter((c) => c.doctorId === e.id),
      franjasLibres: disp
        ? new Map(
            (disp.slots ?? []).map((s) => [
              aMinutos(s.hora),
              s.disponible ? true : s.motivoNoDisponible === 'PASADO' ? 'PASADO' : false,
            ]),
          )
        : null,
    })
  })
  const sinEsp = citas.filter((c) => !c.doctorId || !visibles.some((e) => e.id === c.doctorId))
  if (filtroEfectivo === 'todos' && sinEsp.length > 0) {
    columnas.push({ id: '__sin__', titulo: 'Sin especialista', citas: sinEsp, franjasLibres: null })
  }

  const activas = citas.filter((c) => c.estado !== 'CANCELADA')
  const contar = (estado: string) => citas.filter((c) => c.estado === estado).length
  const titulo =
    vista === 'dia' ? capitalizar(formatFechaLarga(fecha)) : `Semana del ${formatFecha(dias[0])} al ${formatFecha(dias[6])}`
  const leyenda = `Agenda ${vista === 'dia' ? 'del' : 'de la'} ${titulo.toLocaleLowerCase('es-PE')}${nombreSede ? `, ${nombreSede}` : ''}`

  return (
    <div className="mx-auto flex max-w-[100rem] flex-col gap-6">
      <section aria-labelledby="titulo-agenda" className="flex flex-col gap-4 rounded-card bg-surface-container-lowest p-5 shadow-card md:p-6">
        <div className="flex flex-wrap items-center gap-3">
          {(
            <div role="group" aria-label="Navegar fechas" className="inline-flex items-center gap-1 rounded-pill bg-surface-container-low p-1">
              <Button size="icono" variant="fantasma" className="size-9" onClick={() => mover(-1)} aria-label={vista === 'dia' ? 'Día anterior' : 'Semana anterior'}>
                <ChevronLeft aria-hidden />
              </Button>
              <Button size="sm" variant="fantasma" onClick={() => setFecha(hoy)} disabled={fecha === hoy && vista === 'dia'}>
                Hoy
              </Button>
              <Button size="icono" variant="fantasma" className="size-9" onClick={() => mover(1)} aria-label={vista === 'dia' ? 'Día siguiente' : 'Semana siguiente'}>
                <ChevronRight aria-hidden />
              </Button>
            </div>
          )}
          <h1 id="titulo-agenda" className="flex items-center gap-2 font-display text-headline-md font-bold text-on-surface">
            <CalendarDays aria-hidden className="size-6 text-primary" />
            {titulo}
          </h1>
          <RotuloMostrando />
          <Segmentado
            etiqueta="Vista"
            valor={vista}
            onCambiar={setVista}
            opciones={[
              { valor: 'dia' as const, texto: 'Sillones del Día' },
              { valor: 'semana' as const, texto: 'Semana' },
            ]}
          />
        </div>
        {(
          <div className="flex flex-wrap items-center gap-3">
            {puedeAgendar && (
              <Button variant="accion" onClick={() => dialogos.crear({ fecha: fecha >= hoy ? fecha : hoy })}>
                <Plus aria-hidden />
                Nueva cita
              </Button>
            )}
            {!esOdontologo && especialistas.length > 0 && (
              <Segmentado
                etiqueta="Especialista"
                valor={filtroEfectivo}
                onCambiar={setFiltro}
                opciones={[
                  { valor: 'todos', texto: `Todos (${especialistas.length})` },
                  ...especialistas.map((e) => ({ valor: e.id!, texto: `${e.nombre ?? 'Especialista'}${e.invitado ? ' (invitado)' : ''}` })),
                ]}
              />
            )}
          </div>
        )}
      </section>

      {(
        <div className="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)]">
          <section aria-label="Resumen de citas" className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-card bg-surface-container-lowest p-5 shadow-card">
            {consulta.isPending ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <>
                <Dato valor={activas.length} texto="citas" />
                <Dato valor={contar('CONFIRMADA')} texto="Confirmadas" punto="bg-primary" />
                <Dato valor={contar('PROGRAMADA')} texto="Programadas" punto="bg-outline" />
                <Dato valor={contar('PENDIENTE_WEB')} texto="Pendientes web" punto="bg-secondary" />
                <Dato valor={contar('EN_SALA')} texto="En sala" punto="bg-tertiary" />
              </>
            )}
          </section>
          <Resumen
            icono={<DoorOpen />}
            titulo="Sala de espera hoy"
            valor={listaEspera.data?.length}
            detalle={listaEspera.data?.[0]?.pacienteNombre}
            enlace={puede(rol, 'bandeja')}
            onRevisar={() => void irAlInicio()}
          />
          <Resumen
            icono={<Globe />}
            titulo="Reservas web"
            valor={reservasWeb.data?.length}
            detalle="pendientes de confirmación"
            enlace={puede(rol, 'bandeja')}
            onRevisar={() => void irAlInicio()}
          />
        </div>
      )}

      {esOdontologo && especialistasQ.isSuccess && !propio && (
        <Alert tono="info" titulo="Tu usuario no está vinculado a un especialista">
          No hay un especialista de esta sede asociado a tu cuenta, así que no hay agenda propia que mostrar. Pide a la
          administración que vincule tu usuario.
        </Alert>
      )}

      <p role="status" className="sr-only">
        {consulta.isSuccess ? `${activas.length} citas en la ${vista === 'dia' ? 'agenda del día' : 'semana'}` : ''}
      </p>

      {consulta.isPending || especialistasQ.isPending ? (
        <div aria-busy="true" className="flex flex-col gap-2 rounded-card bg-surface-container-lowest p-4 shadow-card">
          <span className="sr-only">Cargando la agenda…</span>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : consulta.isError ? (
        <div className="rounded-card bg-surface-container-lowest shadow-card">
          <ErrorState
            titulo={esSinPermiso(consulta.error) ? 'Sin permiso' : undefined}
            descripcion={mensajeError(consulta.error)}
            onReintentar={esSinPermiso(consulta.error) ? undefined : () => void consulta.refetch()}
          />
        </div>
      ) : vista === 'semana' ? (
        <VistaSemana
          dias={dias}
          hoy={hoy}
          citas={citas}
          inicio={inicio}
          fin={fin}
          horarios={filtroEfectivo === 'todos' ? null : (horariosQ[0]?.data ?? null)}
          sedeId={sedeId}
          citaSeleccionada={dialogos.detalle?.id}
          onAbrir={dialogos.verDetalle}
          onIrADia={(d) => {
            setFecha(d)
            setVista('dia')
          }}
          leyenda={leyenda}
        />
      ) : columnas.length === 0 ? (
        <div className="rounded-card bg-surface-container-lowest shadow-card">
          <EmptyState
            titulo="No hay especialistas en esta sede"
            descripcion="Cuando haya especialistas activos en la sede, la agenda se organizará por columnas."
          />
        </div>
      ) : (
        <>
          {citas.length === 0 && <p className="text-on-surface-variant">No hay citas para este día.</p>}
          {esCelular ? (
            <VistaDiaLista citas={citas} citaSeleccionada={dialogos.detalle?.id} onAbrir={dialogos.verDetalle} />
          ) : (
          <VistaDia
            columnas={columnas}
            inicio={inicio}
            fin={fin}
            ahora={fecha === hoy ? minutosAhoraEnLima() : null}
            leyenda={leyenda}
            citaSeleccionada={dialogos.detalle?.id}
            onAbrir={dialogos.verDetalle}
            onCrear={
              puedeAgendar && fecha >= hoy
                ? (col, hora) => dialogos.crear({ fecha, hora, doctorId: col.id })
                : undefined
            }
          />
          )}
        </>
      )}

      <DialogosCita dialogos={dialogos} />
    </div>
  )
}

function Dato({ valor, texto, punto }: { valor: number; texto: string; punto?: string }) {
  return (
    <p className="flex items-center gap-2">
      {punto && <span aria-hidden className={cn('size-2.5 rounded-full', punto)} />}
      <span className="font-display text-headline-md font-bold">{valor}</span>
      <span className="text-on-surface-variant">{texto}</span>
    </p>
  )
}

function Resumen({
  icono,
  titulo,
  valor,
  detalle,
  enlace,
  onRevisar,
}: {
  icono: ReactNode
  titulo: string
  valor: number | undefined
  detalle?: string
  enlace: boolean
  onRevisar?: () => void
}) {
  return (
    <section aria-label={titulo} className="flex min-w-0 flex-wrap items-center gap-3 rounded-card bg-surface-container-lowest p-5 shadow-card">
      <div aria-hidden className="flex size-11 shrink-0 items-center justify-center rounded-panel bg-tertiary-fixed text-on-tertiary-fixed [&_svg]:size-5">
        {icono}
      </div>
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="font-bold">{titulo}</p>
        <p className="truncate text-sm text-on-surface-variant">
          {valor === undefined ? 'Cargando…' : `${valor}${detalle && valor > 0 ? ` · ${detalle}` : ''}`}
        </p>
      </div>
      {enlace &&
        (onRevisar ? (
          <Button
            type="button"
            variant="secundario"
            size="sm"
            onClick={onRevisar}
            aria-label={`Revisar ${titulo.toLocaleLowerCase('es-PE')} en la bandeja`}
          >
            Revisar
          </Button>
        ) : (
          <Link
            to="/app/bandeja"
            className={buttonVariants({ variant: 'secundario', size: 'sm' })}
            aria-label={`Revisar ${titulo.toLocaleLowerCase('es-PE')} en la bandeja`}
          >
            Revisar
          </Link>
        ))}
    </section>
  )
}
