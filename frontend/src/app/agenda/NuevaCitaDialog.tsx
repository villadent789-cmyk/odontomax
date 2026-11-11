import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { listarDoctoresDerivadores, crearDoctorDerivador } from '../radiologia/api-radiologia'
import { ModalNuevoDerivador } from '../radiologia/ModalNuevoDerivador'

import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
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
  Field,
  Input,
  Label,
  toast,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { cn } from '@/design-system/cn'
import { useSesion } from '@/shared/auth'
import { formatFecha, hoyEnLima } from '@/shared/format'
import { formatoSaldo } from '@/shared/saldo'
import { HttpError, textoDeError } from '@/shared/http'
import { useCrearTratamientoMutation, useServiciosCatalogo, useTratamientosPaciente } from '../tratamientos/api-tratamientos'
import { crearSolicitudPlanSimple } from '../tratamientos/planSimple'
import { nombrePaciente, useCrearCita, useEspecialistas, useHorarios, useSedesActivas, type Cita, type Paciente } from './api-agenda'
import { BuscadorPaciente } from './BuscadorPaciente'
import { mensajeConflicto } from './errores'
import { DURACIONES, esquemaNuevaCita, type DatosNuevaCita } from './esquemas'
import { proximasFechasAtencion, resumenAtencion } from './horarioDoctor'
import { SelectorHueco } from './SelectorHueco'

export interface PrefijoCita {
  fecha?: string
  hora?: string
  doctorId?: string
  /** Paciente ya elegido (p. ej. al agendar desde su ficha): el buscador aparece con él seleccionado. */
  paciente?: Paciente
}

interface Props {
  prefijo: PrefijoCita | null
  onCerrar: () => void
  onCreada?: (cita: Cita) => void
}

/** B5 · Crear cita (POST /citas). Sin captura: diálogo con el lenguaje de B3. */
export function NuevaCitaDialog({ prefijo, onCerrar, onCreada }: Props) {
  return (
    <Dialog open={!!prefijo} onOpenChange={(abierto) => !abierto && onCerrar()}>
      {prefijo && <Formulario prefijo={prefijo} onCerrar={onCerrar} onCreada={onCreada} />}
    </Dialog>
  )
}

const selectClases = cn(inputClases, 'appearance-auto')

const OTRO_MOTIVO = '__otro__'

/** Servicios agrupados por especialidad para el desplegable: [["Odontología general", ["Limpieza", …]], …]. */
function agruparPorEspecialidad(servicios: { nombre?: string; especialidad?: string }[]): [string, string[]][] {
  const grupos = new Map<string, string[]>()
  for (const s of servicios) {
    if (!s.nombre) continue
    const clave = s.especialidad?.trim() || 'Otros'
    grupos.set(clave, [...(grupos.get(clave) ?? []), s.nombre])
  }
  return [...grupos.entries()]
}

type Plan = { nombre?: string; sesionesRealizadas?: number; totalSesiones?: number }

/** Sesión que toca agendar: la siguiente a las realizadas, sin pasar del total del plan. */
function sesionPendiente(plan: Plan): { numero: number; total?: number } {
  const total = plan.totalSesiones && plan.totalSesiones > 0 ? plan.totalSesiones : undefined
  const siguiente = (plan.sesionesRealizadas ?? 0) + 1
  return { numero: total ? Math.min(siguiente, total) : siguiente, total }
}

/** " (Sesión 3/5)", o " (Sesión 3)" si el plan no tiene total de sesiones. */
function etiquetaSesion(plan: Plan): string {
  const { numero, total } = sesionPendiente(plan)
  return ` (Sesión ${numero}${total ? `/${total}` : ''})`
}

/** Motivo propuesto para la cita: "Ortodoncia · sesión 3/5". */
function motivoDelPlan(plan: Plan): string {
  const { numero, total } = sesionPendiente(plan)
  return `${plan.nombre ?? 'Tratamiento'} · sesión ${numero}${total ? `/${total}` : ''}`
}

function Formulario({ prefijo, onCerrar, onCreada }: { prefijo: PrefijoCita } & Omit<Props, 'prefijo'>) {
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId === 'sede-chilca-2' ? 'sede-chilca-1' : (sesion?.sedeActivaId ?? null)
  const qc = useQueryClient()
  const especialistas = useEspecialistas(sedeId)
  const sedes = useSedesActivas()
  const crear = useCrearCita()
  const crearTratamiento = useCrearTratamientoMutation()
  const [paciente, setPaciente] = useState<Paciente | null>(prefijo.paciente ?? null)
  /** Enlazar la cita al plan en proceso (por defecto sí): al atenderla, el backend suma la sesión. */
  const [vincularPlan, setVincularPlan] = useState(true)
  const [crearPlanConPresupuesto, setCrearPlanConPresupuesto] = useState(false)
  const [nombrePlan, setNombrePlan] = useState('')
  const [costoPlan, setCostoPlan] = useState('')
  const [sesionesPlan, setSesionesPlan] = useState('1')
  const [planCreado, setPlanCreado] = useState<{
    id: string
    pacienteId: string
    especialistaId: string
    sedeId: string
  } | null>(null)
  const [errorPresupuesto, setErrorPresupuesto] = useState<string | null>(null)
  const esVillamax = sesion?.negocioActivo === 'VILLAMAX'
  const [doctorDerivador, setDoctorDerivador] = useState('')
  const [errorDerivador, setErrorDerivador] = useState<string | null>(null)
  const [modalNuevoDerivadorAbierto, setModalNuevoDerivadorAbierto] = useState(false)

  const derivadores = useQuery({
    queryKey: ['doctores-derivadores-activos'],
    queryFn: () => listarDoctoresDerivadores(true),
    enabled: esVillamax,
  })
  const [descuentoPlan, setDescuentoPlan] = useState('')

  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<DatosNuevaCita>({
    resolver: zodResolver(esquemaNuevaCita),
    defaultValues: {
      pacienteId: prefijo.paciente?.id ?? '',
      doctorId: prefijo.doctorId ?? '',
      fecha: prefijo.fecha ?? hoyEnLima(),
      duracionMinutos: '30',
      hora: prefijo.hora ?? '',
      motivoConsulta: '',
      notas: '',
      montoAdelantoInicial: '',
      medioPagoAdelanto: 'EFECTIVO',
    },
  })
  const [doctorId, fecha, duracion, hora] = useWatch({ control, name: ['doctorId', 'fecha', 'duracionMinutos', 'hora'] })
  const metodoAdelanto = useWatch({ control, name: 'medioPagoAdelanto' })

  // Procedimiento: catálogo de servicios activos (GET /servicios) o un motivo escrito a mano.
  const servicios = useServiciosCatalogo(sedeId ?? undefined, true, sesion?.negocioActivo)
  const [eligeOtro, setEligeOtro] = useState(false)
  const motivo = useWatch({ control, name: 'motivoConsulta' })
  const nombresCatalogo = (servicios.data ?? []).map((s) => s.nombre!).filter(Boolean)
  const porEspecialidad = agruparPorEspecialidad(servicios.data ?? [])
  const esDelCatalogo = nombresCatalogo.includes(motivo)
  // Se escribe a mano si se eligió "Otro", si el motivo ya es uno propio (p. ej. el del tratamiento en curso) o si el
  // catálogo no está disponible.
  const escribeMotivo =
    eligeOtro || (!!motivo && !esDelCatalogo) || (!servicios.isPending && nombresCatalogo.length === 0)
  const valorProcedimiento = esDelCatalogo ? motivo : ''
  const servicioElegido = (servicios.data ?? []).find((s) => s.nombre === motivo)
  const precioEstudio = servicioElegido?.precio != null ? Number(servicioElegido.precio) : 0
  const descNum = Number(descuentoPlan) || 0
  const errorDescuentoVillamax = esVillamax && descuentoPlan !== '' && (isNaN(Number(descuentoPlan)) || descNum < 0 || descNum > precioEstudio)
  const errorDescuentoVilladent = !esVillamax && descuentoPlan !== '' && (isNaN(Number(descuentoPlan)) || descNum < 0 || descNum > (Number(costoPlan) || 0))

  // Horario del especialista en esta sede y plan de tratamiento en proceso del paciente elegido.
  const horarios = useHorarios(doctorId || undefined)
  const atencion = resumenAtencion(horarios.data, sedeId)
  const proximosDias = proximasFechasAtencion(horarios.data, sedeId)
  const tratamientos = useTratamientosPaciente(paciente?.id)
  const planEnCurso = (tratamientos.data ?? []).find((t) => t.estado === 'EN_PROCESO')
  // Si el motivo sigue vacío se propone el del tratamiento ("Ortodoncia · sesión 3/5"); lo que escriba el usuario no se pisa.
  useEffect(() => {
    if (planEnCurso && !getValues('motivoConsulta')) setValue('motivoConsulta', motivoDelPlan(planEnCurso))
  }, [planEnCurso, getValues, setValue])

  const sede = sedes.data?.find((s) => s.id === sedeId)
  const sedeInactiva = sedes.isSuccess && !sede
  const activos = (especialistas.data ?? []).filter((e) => e.activo !== false)
  useEffect(() => {
    if (activos.length === 1 && !getValues('doctorId')) {
      setValue('doctorId', activos[0].id ?? '')
    }
  }, [activos, getValues, setValue])
  const especialistaElegido = especialistas.data?.find((e) => e.id === doctorId)
  const sillonFijo = especialistaElegido?.sillonesPorSede?.[sedeId ?? ''] ?? undefined
  // Doctor invitado: viene por cita previa, sin horario ni sillón fijo (el sistema usa el primer sillón libre a esa hora).
  const esInvitado = especialistaElegido?.invitado === true
  const especialistaSinSillon = Boolean(doctorId && especialistas.isSuccess && !sillonFijo && !esInvitado)

  const enviar = handleSubmit(async (d) => {
    setErrorPresupuesto(null)
    const esp = activos.find((e) => e.id === d.doctorId)
    const adelanto = d.montoAdelantoInicial ? parseFloat(d.montoAdelantoInicial) : undefined
    if (esVillamax && !doctorDerivador) {
      setErrorDerivador("Selecciona el doctor que deriva o 'Paciente directo, sin derivación'.")
      return
    }
    let tratamientoId = planEnCurso && vincularPlan ? planEnCurso.id : undefined
    if (esVillamax && !planEnCurso) {
      if (errorDescuentoVillamax) {
        setErrorPresupuesto('El descuento no puede superar el precio del estudio ni ser negativo.')
        return
      }
      if (servicioElegido) {
        if (planCreado
          && planCreado.pacienteId === d.pacienteId
          && planCreado.especialistaId === d.doctorId
          && planCreado.sedeId === (sedeId ?? 'sede-chilca-1')) {
          tratamientoId = planCreado.id
        } else {
          try {
            const nuevo = await crearTratamiento.mutateAsync(crearSolicitudPlanSimple({
              pacienteId: d.pacienteId,
              sedeId: sedeId ?? 'sede-chilca-1',
              especialistaId: d.doctorId,
              nombre: d.motivoConsulta.trim(),
              costoTotal: precioEstudio,
              descuentoTotal: descNum,
              totalSesiones: 1,
            }))
            if (!nuevo.id) {
              setErrorPresupuesto('El servidor no devolvió el identificador del plan. No reintentes sin revisar el plan creado.')
              return
            }
            setPlanCreado({
              id: nuevo.id,
              pacienteId: d.pacienteId,
              especialistaId: d.doctorId,
              sedeId: sedeId ?? 'sede-chilca-1',
            })
            tratamientoId = nuevo.id
          } catch {
            return
          }
        }
      }
    } else if (crearPlanConPresupuesto && !planEnCurso) {
      if (!tratamientos.isSuccess) return
      const costo = Number(costoPlan)
      const sesiones = Number(sesionesPlan)
      if (!nombrePlan.trim() || !costoPlan.trim() || !Number.isFinite(costo) || costo < 0 ||
          !Number.isInteger(sesiones) || sesiones < 1) {
        setErrorPresupuesto('Indica el nombre, el costo y un número válido de sesiones.')
        return
      }
      if (errorDescuentoVilladent) {
        setErrorPresupuesto('El descuento debe ser entre 0 y el costo total.')
        return
      }
      setErrorPresupuesto(null)
      if (planCreado
        && planCreado.pacienteId === d.pacienteId
        && planCreado.especialistaId === d.doctorId
        && planCreado.sedeId === (sedeId ?? 'sede-chilca-1')) {
        tratamientoId = planCreado.id
      } else {
        try {
          const nuevo = await crearTratamiento.mutateAsync(crearSolicitudPlanSimple({
            pacienteId: d.pacienteId,
            sedeId: sedeId ?? 'sede-chilca-1',
            especialistaId: d.doctorId,
            nombre: nombrePlan.trim(),
            costoTotal: costo,
            descuentoTotal: descNum,
            totalSesiones: sesiones,
          }))
          if (!nuevo.id) {
            setErrorPresupuesto('El servidor no devolvió el identificador del plan. No reintentes sin revisar el plan creado.')
            return
          }
          setPlanCreado({
            id: nuevo.id,
            pacienteId: d.pacienteId,
            especialistaId: d.doctorId,
            sedeId: sedeId ?? 'sede-chilca-1',
          })
          tratamientoId = nuevo.id
        } catch {
          return
        }
      }
    }
    try {
      const c = await crear.mutateAsync({
        pacienteId: d.pacienteId,
        pacienteNombre: paciente ? nombrePaciente(paciente) : undefined,
        pacienteTelefono: paciente?.telefono || undefined,
        doctorId: d.doctorId,
        doctorNombre: esp?.nombre,
        sedeId: sedeId ?? undefined,
        fecha: d.fecha,
        hora: d.hora,
        duracionMinutos: Number(d.duracionMinutos),
        sillonNumero: sillonFijo,
        motivoConsulta: d.motivoConsulta,
        notas: d.notas || undefined,
        tratamientoId,
        doctorDerivadorId: esVillamax && doctorDerivador !== 'PACIENTE_DIRECTO' ? doctorDerivador : undefined,
        pacienteDirecto: esVillamax ? doctorDerivador === 'PACIENTE_DIRECTO' : undefined,
        montoAdelantoInicial: adelanto && adelanto > 0 ? adelanto : undefined,
        medioPagoAdelanto: adelanto && adelanto > 0 ? (d.medioPagoAdelanto || 'EFECTIVO') : undefined,
      })
      toast('Cita agendada', { description: `${c.pacienteNombre ?? 'Paciente'} · ${formatFecha(c.fecha)} ${d.hora}` })
      onCreada?.(c)
      onCerrar()
    } catch (e) {
      if (e instanceof HttpError && e.status === 409) {
        setValue('hora', '')
        void qc.invalidateQueries({ queryKey: ['disponibilidad'] })
      }
    }
  })

  return (
    <DialogContent className="max-w-2xl">
      <DialogHeader>
        <DialogTitle>Nueva cita</DialogTitle>
        <DialogDescription>
          {sede?.nombre ?? 'Sede activa'} · Elige paciente, especialista y el horario de la cita entre los disponibles.
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={enviar} noValidate className="flex flex-col gap-5">
        {sedeInactiva && (
          <Alert tono="error" titulo="La sede activa no está disponible">
            La sede seleccionada no figura entre las sedes activas. Cambia de sede para agendar.
          </Alert>
        )}
        {crear.isError && (
          <Alert tono="error" titulo="No se pudo agendar la cita">
            {mensajeConflicto(crear.error)}
          </Alert>
        )}
        {crearTratamiento.isError && (
          <Alert tono="error" titulo="No se pudo crear el plan">
            {textoDeError(crearTratamiento.error, 'Revisa los datos del presupuesto e inténtalo otra vez.')}
          </Alert>
        )}
        {errorPresupuesto && (
          <Alert tono="error" titulo={errorPresupuesto} />
        )}
        {planCreado && crear.isError && (
          <p role="status" className="text-sm text-on-surface-variant">
            El plan ya está creado. Al reintentar se usará el mismo plan.
          </p>
        )}

        <Controller
          control={control}
          name="pacienteId"
          render={({ field, fieldState }) => (
            <BuscadorPaciente
              sedeId={sedeId}
              seleccionado={paciente}
              error={fieldState.error?.message}
              onSeleccionar={(p) => {
                setPaciente(p)
                field.onChange(p?.id ?? '')
              }}
            />
          )}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Especialista" error={errors.doctorId?.message} className="sm:col-span-3">
            <select className={selectClases} {...register('doctorId', { onChange: () => setValue('hora', '') })}>
              <option value="">Elige un especialista</option>
              {activos.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.nombre}
                  {e.especialidad ? ` · ${e.especialidad}` : ''}
                  {e.invitado ? ' (invitado)' : ''}
                </option>
              ))}
            </select>
          </Field>
          {doctorId && (
            <div className="sm:col-span-3">
              {esInvitado && especialistaElegido ? (
                <p role="status" className="rounded-panel bg-surface-container p-3 text-sm">
                  Doctor invitado: viene por cita previa. Elige la fecha y la hora acordadas; se usa el primer sillón libre.
                </p>
              ) : sillonFijo && especialistaElegido ? (
                <p role="status" className="rounded-panel bg-surface-container p-3 text-sm">
                  Sillón {sillonFijo} · {especialistaElegido.nombre}
                </p>
              ) : especialistaSinSillon ? (
                <Alert tono="error" titulo="El doctor no tiene sillón asignado en esta sede">
                  Asígnalo en Administración para poder agendar.
                </Alert>
              ) : null}
            </div>
          )}
          {doctorId && (
            <div className="space-y-2 sm:col-span-3">
              <p
                data-testid="atencion-especialista"
                className={cn('rounded-panel bg-surface-container p-3 text-sm', !atencion && 'text-on-surface-variant')}
              >
                {horarios.isPending
                  ? 'Consultando el horario del especialista…'
                  : horarios.isError
                    ? 'No se pudo consultar el horario del especialista.'
                    : atencion
                      ? `Atiende en ${sede?.nombre ?? 'esta sede'}: ${atencion}`
                      : esInvitado
                        ? 'Doctor invitado: sin horario fijo, se agenda en la fecha y hora acordadas.'
                        : 'El especialista no tiene horario de atención en esta sede.'}
              </p>
              {proximosDias.length > 0 && (
                <div className="flex flex-wrap items-center gap-1.5 text-xs">
                  <span className="font-semibold text-on-surface-variant">Próximos días en sede:</span>
                  {proximosDias.map(({ fecha: f, label }) => (
                    <button
                      key={f}
                      type="button"
                      onClick={() => {
                        setValue('fecha', f, { shouldValidate: true })
                        setValue('hora', '')
                      }}
                      className={cn(
                        'rounded-pill border px-2.5 py-1 text-xs font-semibold transition-colors',
                        fecha === f
                          ? 'border-primary bg-acento text-on-acento'
                          : 'border-outline-variant bg-surface-container-lowest text-on-surface hover:border-primary/60 hover:bg-primary-fixed/40'
                      )}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          <Field label="Fecha" error={errors.fecha?.message}>
            <Input type="date" min={hoyEnLima()} {...register('fecha', { onChange: () => setValue('hora', '') })} />
          </Field>
          <Field label="Duración" error={errors.duracionMinutos?.message}>
            <select className={selectClases} {...register('duracionMinutos', { onChange: () => setValue('hora', '') })}>
              {DURACIONES.map((d) => (
                <option key={d} value={d}>
                  {d} min
                </option>
              ))}
            </select>
          </Field>
        </div>

        <div className="flex flex-col gap-2">
          <Label asChild>
            <p>Horario de la cita</p>
          </Label>
          {doctorId && /^\d{4}-\d{2}-\d{2}$/.test(fecha) && fecha >= hoyEnLima() ? (
            <SelectorHueco
              especialistaId={doctorId}
              fecha={fecha}
              sedeId={sedeId}
              duracion={Number(duracion)}
              valor={hora}
              onElegir={(h) => setValue('hora', h, { shouldValidate: true })}
              error={errors.hora?.message}
              idError="error-hueco"
            />
          ) : (
            <p className="text-sm text-on-surface-variant">Elige especialista y fecha para ver los horarios libres.</p>
          )}
          {errors.hora && (
            <p id="error-hueco" className="text-sm font-bold text-error">
              {errors.hora.message}
            </p>
          )}
        </div>

        <section aria-label="¿Qué se le va a hacer?" className="flex flex-col gap-3 rounded-panel border border-outline-variant/60 p-3">
          <h3 className="text-sm font-semibold text-on-surface">
            {esVillamax ? 'Estudio radiológico' : '¿Qué se le va a hacer?'}
          </h3>
          {planEnCurso ? (
            <div role="status" className="flex flex-col gap-2 rounded-panel bg-primary-fixed/40 p-3 text-sm">
              <Badge tono="primario" className="w-fit">
                {`${planEnCurso.nombre ?? 'Plan de tratamiento'}${etiquetaSesion(planEnCurso)}`}
                {planEnCurso.saldoPendiente != null ? ` · falta ${formatoSaldo(planEnCurso.saldoPendiente)}` : ''}
              </Badge>
              <label className="flex cursor-pointer items-start gap-2 font-semibold text-on-surface">
                <input
                  type="checkbox"
                  checked={vincularPlan}
                  onChange={(e) => setVincularPlan(e.target.checked)}
                  className="mt-0.5 size-4 rounded border-outline text-primary focus:ring-primary"
                />
                <span>Esta cita es la sesión pendiente: al atenderla se suma al tratamiento</span>
              </label>
            </div>
          ) : (
            <>
              {!escribeMotivo && (
                <Field label={esVillamax ? "Estudio" : "Procedimiento"} error={errors.motivoConsulta?.message}>
                  <select
                    className={selectClases}
                    value={valorProcedimiento}
                    onChange={(e) => {
                      const v = e.target.value
                      setEligeOtro(v === OTRO_MOTIVO)
                      setValue('motivoConsulta', v === OTRO_MOTIVO ? '' : v, { shouldValidate: v !== OTRO_MOTIVO })
                      const srv = (servicios.data ?? []).find((s) => s.nombre === v)
                      if (srv?.precio != null) {
                        if (!costoPlan || costoPlan === '0') {
                          setCostoPlan(srv.precio.toString())
                        }
                      }
                    }}
                  >
                    <option value="">{servicios.isPending ? 'Cargando procedimientos…' : esVillamax ? 'Elige un estudio' : 'Elige un procedimiento'}</option>
                    {porEspecialidad.map(([especialidad, nombres]) => (
                      <optgroup key={especialidad} label={especialidad}>
                        {nombres.map((n) => (
                          <option key={n} value={n}>
                            {n}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                    {!esVillamax && <option value={OTRO_MOTIVO}>Otro motivo (escribir)</option>}
                  </select>
                </Field>
              )}
              {escribeMotivo && !esVillamax && (
                <div className="flex flex-col gap-2">
                  <Field label="Motivo o procedimiento" error={errors.motivoConsulta?.message}>
                    <Input autoComplete="off" placeholder="Ej.: Evaluación y diagnóstico" {...register('motivoConsulta')} />
                  </Field>
                  {nombresCatalogo.length > 0 && (
                    <Button
                      type="button"
                      variant="enlace"
                      size="sm"
                      className="self-start"
                      onClick={() => {
                        setEligeOtro(false)
                        setValue('motivoConsulta', '')
                      }}
                    >
                      Elegir del catálogo de procedimientos
                    </Button>
                  )}
                </div>
              )}

              {/* Flujo Villamax: Doctor derivador obligatorio */}
              {esVillamax && (
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="doctorDerivador" className="text-xs font-bold text-on-surface">
                      Doctor que deriva <span className="text-error">*</span>
                    </Label>
                    <Button
                      type="button"
                      variant="fantasma"
                      size="sm"
                      className="h-6 text-xs px-2 text-primary"
                      onClick={() => setModalNuevoDerivadorAbierto(true)}
                    >
                      + Doctor derivador
                    </Button>
                  </div>
                  <select
                    id="doctorDerivador"
                    className={selectClases}
                    value={doctorDerivador}
                    onChange={(e) => {
                      setDoctorDerivador(e.target.value)
                      if (e.target.value) setErrorDerivador(null)
                    }}
                  >
                    <option value="">{derivadores.isPending ? 'Cargando derivadores...' : 'Selecciona quién deriva o paciente directo...'}</option>
                    <option value="PACIENTE_DIRECTO">Paciente directo, sin derivación</option>
                    {(derivadores.data ?? []).map((doc) => (
                      <option key={doc.id} value={doc.id}>
                        {doc.nombre}{doc.clinicaOrigen ? ` (${doc.clinicaOrigen})` : ''}
                      </option>
                    ))}
                  </select>
                  {errorDerivador && (
                    <p className="text-xs font-bold text-error">{errorDerivador}</p>
                  )}
                </div>
              )}

              {/* Flujo Villamax: precio fijo del catálogo + descuento opcional + total en vivo (oculta presupuesto y sesiones) */}
              {esVillamax && servicioElegido && (
                <div className="flex flex-col gap-3 rounded-panel bg-surface-container p-3 text-sm">
                  <div className="flex justify-between items-center">
                    <span className="text-on-surface-variant font-medium">Precio del estudio:</span>
                    <span className="font-bold text-on-surface">{formatoSaldo(precioEstudio)}</span>
                  </div>
                  <Field
                    label="Descuento (S/) (opcional)"
                    error={errorDescuentoVillamax ? 'El descuento debe ser entre 0 y el precio del estudio.' : undefined}
                  >
                    <Input
                      id="descuentoEstudio"
                      type="number"
                      min="0"
                      max={precioEstudio}
                      step="0.01"
                      placeholder="0.00"
                      value={descuentoPlan}
                      onChange={(e) => setDescuentoPlan(e.target.value)}
                    />
                  </Field>
                  <div className="flex justify-between items-center text-sm font-bold text-primary border-t border-outline-variant pt-2">
                    <span>Total a cobrar:</span>
                    <span>{formatoSaldo(Math.max(0, precioEstudio - descNum))}</span>
                  </div>
                </div>
              )}

              {/* Flujo Villadent: presupuesto con sesiones y autocompletado de costo + descuento opcional */}
              {paciente && !esVillamax && (
                <>
                  {tratamientos.isPending && (
                    <p role="status" className="text-sm text-on-surface-variant">
                      Consultando tratamientos del paciente…
                    </p>
                  )}
                  {tratamientos.isError && (
                    <Alert tono="error" titulo="No se pudo consultar el tratamiento del paciente">
                      {textoDeError(tratamientos.error, 'No se puede validar el plan activo ahora.')}
                    </Alert>
                  )}
                  <label className="flex cursor-pointer items-start gap-2 text-sm font-semibold text-on-surface">
                    <input
                      type="checkbox"
                      checked={crearPlanConPresupuesto}
                      disabled={!tratamientos.isSuccess}
                      onChange={(e) => {
                        setCrearPlanConPresupuesto(e.target.checked)
                        if (e.target.checked) {
                          setNombrePlan(motivo)
                          if (servicioElegido?.precio != null && !costoPlan) {
                            setCostoPlan(servicioElegido.precio.toString())
                          }
                        }
                        setErrorPresupuesto(null)
                      }}
                      className="mt-0.5 size-4 rounded border-outline text-primary focus:ring-primary"
                    />
                    <span>Es un tratamiento con presupuesto</span>
                  </label>
                  {crearPlanConPresupuesto && (
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field label="Nombre del plan">
                        <Input value={nombrePlan} onChange={(e) => setNombrePlan(e.target.value)} />
                      </Field>
                      <Field label="Costo total (S/)">
                        <Input
                          type="number"
                          min="0"
                          step="0.01"
                          value={costoPlan}
                          onChange={(e) => setCostoPlan(e.target.value)}
                        />
                      </Field>
                      <Field label="Descuento (S/) (opcional)" error={errorDescuentoVilladent ? 'El descuento debe ser entre 0 y el costo total.' : undefined}>
                        <Input
                          type="number"
                          min="0"
                          max={Number(costoPlan) || 0}
                          step="0.01"
                          placeholder="0.00"
                          value={descuentoPlan}
                          onChange={(e) => setDescuentoPlan(e.target.value)}
                        />
                      </Field>
                      <Field label="Sesiones estimadas">
                        <Input
                          type="number"
                          min="1"
                          step="1"
                          value={sesionesPlan}
                          onChange={(e) => setSesionesPlan(e.target.value)}
                        />
                      </Field>
                      {Number(costoPlan) > 0 && (
                        <div className="col-span-full flex justify-between items-center text-sm font-semibold text-primary bg-surface-container p-2.5 rounded-panel">
                          <span>Total a cobrar:</span>
                          <span>{formatoSaldo(Math.max(0, (Number(costoPlan) || 0) - (Number(descuentoPlan) || 0)))}</span>
                        </div>
                      )}
                      {errorPresupuesto && <p role="alert" className="text-sm text-error sm:col-span-full">{errorPresupuesto}</p>}
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </section>
        <details className="rounded-xl border border-outline-variant/60 p-3 bg-surface-container-low/40">
          <summary className="cursor-pointer text-xs font-semibold text-on-surface flex items-center justify-between">
            <span>Cobro / Adelanto inicial al reservar (opcional)</span>
            <span className="text-xs text-on-surface-variant font-normal">Registra pago en ventanilla</span>
          </summary>
          <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Field label="Monto adelanto (S/)">
              <Input
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                {...register('montoAdelantoInicial')}
              />
            </Field>
            <Field label="Medio de pago">
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {([
                  ['EFECTIVO', 'Efectivo'],
                  ['YAPE', 'Yape'],
                  ['PLIN', 'Plin'],
                  ['TARJETA_DEBITO', 'Tarjeta de débito'],
                  ['TARJETA_CREDITO', 'Tarjeta de crédito'],
                ] as const).map(([metodo, label]) => (
                  <button
                    key={metodo}
                    type="button"
                    aria-pressed={metodoAdelanto === metodo}
                    onClick={() => setValue('medioPagoAdelanto', metodo)}
                    className={`rounded-lg border px-3 py-2 text-sm font-semibold ${
                    metodoAdelanto === metodo
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-outline bg-surface-container-low'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </Field>
          </div>
        </details>

        <Field label="Notas (opcional)">
          <textarea rows={2} className={cn(inputClases, 'h-auto py-3')} {...register('notas')} />
        </Field>

        <DialogFooter>
          <Button variant="secundario" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button
            type="submit"
            variant="accion"
            cargando={crear.isPending || crearTratamiento.isPending}
            disabled={sedeInactiva || especialistaSinSillon}
          >
            Agendar cita
          </Button>
        </DialogFooter>
      </form>

      <ModalNuevoDerivador
        abierto={modalNuevoDerivadorAbierto}
        onCerrar={() => setModalNuevoDerivadorAbierto(false)}
        onGuardar={async (req) => {
          const nuevo = await crearDoctorDerivador(req)
          await qc.invalidateQueries({ queryKey: ['doctores-derivadores-activos'] })
          if (nuevo?.id) {
            setDoctorDerivador(nuevo.id)
            setErrorDerivador(null)
          }
        }}
      />
    </DialogContent>
  )
}
