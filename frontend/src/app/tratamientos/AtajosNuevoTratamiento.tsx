import { Link } from '@tanstack/react-router'
import { CalendarCheck, ClipboardList, FileText, Stethoscope } from 'lucide-react'
import type { ReactNode } from 'react'
import { Badge, Card, Skeleton } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatFecha, formatSoles, hoyEnLima } from '@/shared/format'
import { useCitasDelDia } from '../agenda/api-agenda'
import { usePacientesSinPlan, useTratamientosDeSedes } from './api-tratamientos'
import { buscarHerramienta } from '../clinica/herramientasOdontograma'
import { usePresupuestosPendientes } from './api-presupuestos'

function Seccion({ icono, titulo, ayuda, vacio, cargando, children }: { icono: ReactNode; titulo: string; ayuda: string; vacio: string; cargando: boolean; children: ReactNode[] }) {
  return (
    <Card className="flex flex-col gap-3 p-4">
      <div>
        <h2 className="flex items-center gap-2 font-display text-base font-bold text-on-surface">
          {icono}
          {titulo}
        </h2>
        <p className="text-xs text-on-surface-variant">{ayuda}</p>
      </div>
      {cargando ? <Skeleton className="h-16 w-full" /> : children.length === 0 ? <p className="text-sm italic text-on-surface-variant">{vacio}</p> : <ul className="flex flex-col gap-2">{children}</ul>}
    </Card>
  )
}

const filaClase = 'flex w-full flex-wrap items-center justify-between gap-2 rounded-panel border border-outline-variant/50 px-3 py-2 text-left text-sm transition hover:border-primary/60 hover:bg-surface-container-low'

/**
 * Lo que el doctor suele necesitar al abrir "Nuevo tratamiento": los pacientes de hoy, los presupuestos que quedaron sin
 * aceptar y los planes que siguen en curso. Cada fila lleva al paciente en un clic.
 */
export function AtajosNuevoTratamiento({ sedeId, alElegirPaciente }: { sedeId: string | null; alElegirPaciente: (pacienteId: string) => void }) {
  const sesion = useSesion()
  // Con una sede elegida se ve esa sede; con «Todas» (administrador general), todas las sedes del negocio activo.
  const sedes = (sesion?.usuario.sedes ?? []).filter((s) => s.marca === sesion?.negocioActivo)
  const sedeIds = sedeId ? [sedeId] : sedes.map((s) => s.id)
  const nombreSede = (id?: string) => (sedeId ? undefined : sedes.find((s) => s.id === id)?.nombre)
  const citas = useCitasDelDia(sedeId, hoyEnLima(), true)
  const presupuestos = usePresupuestosPendientes(sedeIds)
  const planes = useTratamientosDeSedes(sedeIds)
  const sinPlan = usePacientesSinPlan(sedeIds)

  const deHoy = (citas.data ?? []).filter((c) => c.pacienteId && c.estado !== 'CANCELADA')
  const pendientes = presupuestos.data ?? []
  const enCurso = (planes.data ?? []).filter((t) => t.estado === 'EN_PROCESO').slice(0, 6)

  return (
    <div className="flex flex-col gap-4">
      <Seccion icono={<CalendarCheck aria-hidden className="size-4 text-primary" />} titulo="Pacientes de hoy" ayuda="Toca uno para empezar su tratamiento." vacio="Hoy no hay citas." cargando={citas.isPending}>
        {deHoy.map((c) => (
          <li key={c.id}>
            <button type="button" className={filaClase} onClick={() => alElegirPaciente(c.pacienteId!)}>
              <span className="font-semibold text-on-surface">
                <span className="mr-2 font-mono text-xs text-on-surface-variant">{c.hora?.slice(0, 5)}</span>
                {c.pacienteNombre}
              </span>
              <span className="flex items-center gap-2 text-xs text-on-surface-variant">
                {c.motivoConsulta}
                {c.doctorNombre && <span>· {c.doctorNombre}</span>}
                {nombreSede(c.sedeId) && <Badge tono="neutro">{nombreSede(c.sedeId)}</Badge>}
                {c.estado && <Badge tono="neutro">{c.estado}</Badge>}
              </span>
            </button>
          </li>
        ))}
      </Seccion>

      <Seccion icono={<FileText aria-hidden className="size-4 text-primary" />} titulo="Presupuestos pendientes" ayuda="Entregados al paciente y todavía sin aceptar ni rechazar." vacio="No hay presupuestos pendientes." cargando={presupuestos.isPending}>
        {pendientes.map((p) => (
          <li key={p.id}>
            <Link to="/app/pacientes/$id" params={{ id: p.pacienteId }} search={{ tab: 'tratamientos' }} className={filaClase}>
              <span className="font-semibold text-on-surface">{p.pacienteNombre ?? 'Paciente'}</span>
              <span className="flex items-center gap-2 text-xs text-on-surface-variant">
                {nombreSede(p.sedeId) && <Badge tono="neutro">{nombreSede(p.sedeId)}</Badge>}
                {p.lineas} {p.lineas === 1 ? 'tratamiento' : 'tratamientos'} · {formatFecha(p.creadoEn)}
                <Badge tono="accion">{formatSoles(p.total)}</Badge>
                <span className="font-semibold text-primary">Convertir en plan</span>
              </span>
            </Link>
          </li>
        ))}
      </Seccion>

      <Seccion icono={<Stethoscope aria-hidden className="size-4 text-primary" />} titulo="Con hallazgos y sin plan" ayuda="Tienen piezas por tratar en el odontograma y nadie les armó un plan ni un presupuesto." vacio="Nadie con hallazgos pendientes sin plan." cargando={sinPlan.isPending}>
        {sinPlan.data.slice(0, 8).map((p) => (
          <li key={p.pacienteId}>
            <button type="button" className={filaClase} onClick={() => alElegirPaciente(p.pacienteId)}>
              <span className="font-semibold text-on-surface">{p.pacienteNombre}</span>
              <span className="flex flex-wrap items-center gap-2 text-xs text-on-surface-variant">
                {nombreSede(p.sedeId) && <Badge tono="neutro">{nombreSede(p.sedeId)}</Badge>}
                {p.tipos.map((t) => buscarHerramienta(t)?.label ?? t).join(', ')}
                <Badge tono="peligro">{p.hallazgos} {p.hallazgos === 1 ? 'hallazgo' : 'hallazgos'}</Badge>
              </span>
            </button>
          </li>
        ))}
      </Seccion>

      <Seccion icono={<ClipboardList aria-hidden className="size-4 text-primary" />} titulo="Planes en curso" ayuda="Pacientes con tratamiento en marcha: continúa su plan en vez de crear otro." vacio="No hay planes en curso." cargando={planes.isPending}>
        {enCurso.map((t) => (
          <li key={t.id}>
            <Link to="/app/pacientes/$id" params={{ id: t.pacienteId! }} search={{ tab: 'tratamientos' }} className={filaClase}>
              <span className="font-semibold text-on-surface">
                {t.pacienteNombre ?? 'Paciente'}
                <span className="ml-2 font-normal text-on-surface-variant">{t.nombre}</span>
              </span>
              <span className="flex items-center gap-2 text-xs text-on-surface-variant">
                {nombreSede(t.sedeId) && <Badge tono="neutro">{nombreSede(t.sedeId)}</Badge>}
                Sesión {t.sesionesRealizadas ?? 0} de {t.totalSesiones ?? '—'}
                {(t.saldoPendiente ?? 0) > 0 && <Badge tono="accion">Debe {formatSoles(t.saldoPendiente ?? 0)}</Badge>}
              </span>
            </Link>
          </li>
        ))}
      </Seccion>
    </div>
  )
}
