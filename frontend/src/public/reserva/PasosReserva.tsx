/** Contenido de cada paso del asistente /reservar. El formulario y la navegación viven en ReservarPage. */
import { Link } from '@tanstack/react-router'
import { Building2, Clock, MapPin, Moon, Sun } from 'lucide-react'
import type { ReactNode } from 'react'
import type { UseFormReturn } from 'react-hook-form'
import { EmptyState, ErrorState, Field, Input, Skeleton, SoloLector, cn } from '@/design-system'
import { formatFecha, hoyEnLima } from '@/shared/format'
import { agruparPorEspecialidad, doctoresParaSede, useEspecialistas, useSedes, useServicios, type SedePublica, type ServicioPublico } from './api-reserva'
import { textoTurno, type DatosReserva, type DatosReservaValidos } from './esquemas'
import { HORARIOS_SLOTS, diaSemanaCorto, fechaConDia, proximosDias } from './fechas'
import { ErrorGrupo, Opcion } from './Opcion'

type Form = UseFormReturn<DatosReserva, unknown, DatosReservaValidos>

function Seccion({
  titulo,
  extra,
  descripcion,
  children,
}: {
  titulo: string
  extra?: ReactNode
  descripcion?: ReactNode
  children: ReactNode
}) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-3">
      <legend className="contents">
        <span className="flex items-baseline justify-between gap-4">
          <span className="font-display text-xl sm:text-headline-sm font-bold text-primary">{titulo}</span>
          {extra && <span className="hidden shrink-0 text-xs font-bold text-on-surface-variant sm:inline">{extra}</span>}
        </span>
      </legend>
      {descripcion && <p className="-mt-1 text-xs sm:text-sm text-on-surface-variant">{descripcion}</p>}
      {children}
    </fieldset>
  )
}

function CargandoTarjetas({ cuantas, etiqueta }: { cuantas: number; etiqueta: string }) {
  return (
    <div aria-busy="true" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <SoloLector role="status">{etiqueta}</SoloLector>
      {Array.from({ length: cuantas }, (_, i) => (
        <Skeleton key={i} className="h-20 rounded-panel" />
      ))}
    </div>
  )
}

/* ───────── Paso 1 · Sede y servicio (diseño dinámico y compacto) ───────── */

export function PasoSedeServicio({ form }: { form: Form }) {
  const negocioActual = form.watch('negocio') ?? 'VILLADENT'
  const sedeActual = form.watch('sedeId')
  const doctorIdActual = form.watch('doctorId')
  const sedes = useSedes(negocioActual)
  const servicios = useServicios(negocioActual)
  const especialistas = useEspecialistas(sedeActual)
  const doctores = doctoresParaSede(especialistas.data, sedeActual)
  const { register, formState, setValue } = form
  const errSede = formState.errors.sedeId?.message
  const errServicio = formState.errors.servicioId?.message

  const alCambiarNegocio = (nuevo: 'VILLADENT' | 'VILLAMAX') => {
    if (negocioActual !== nuevo) {
      setValue('negocio', nuevo, { shouldValidate: true })
      setValue('sedeId', '', { shouldValidate: true })
      setValue('doctorId', '', { shouldValidate: true })
      setValue('doctorNombre', '', { shouldValidate: true })
      setValue('servicioId', '', { shouldValidate: true })
      setValue('turno', undefined as unknown as DatosReserva['turno'], { shouldValidate: true })
      setValue('horaPreferencia', '', { shouldValidate: true })
    }
  }

  const alCambiarSede = (nuevaSedeId: string) => {
    setValue('sedeId', nuevaSedeId, { shouldValidate: true })
    setValue('doctorId', '', { shouldValidate: true })
    setValue('doctorNombre', '', { shouldValidate: true })
  }

  return (
    <div className="flex flex-col gap-7">
      <Seccion
        titulo="1. Tipo de atención"
        descripcion="Elige el servicio que necesitas: atención odontológica en Villadent o estudios de radiología 3D en Villamax."
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Opcion
            name="negocio"
            value="VILLADENT"
            checked={negocioActual === 'VILLADENT'}
            onChange={() => alCambiarNegocio('VILLADENT')}
            claseTarjeta={cn(
              'p-3.5 rounded-panel border transition-all',
              negocioActual === 'VILLADENT'
                ? 'border-primary bg-primary/5 text-primary shadow-sm font-semibold'
                : 'border-outline-variant/60 bg-surface-container-lowest hover:bg-surface-container-low text-on-surface',
            )}
          >
            <span className="font-display text-sm font-bold">Odontología (Villadent)</span>
            <span className="text-xs text-on-surface-variant font-normal">
              Consultas, curaciones, ortodoncia, estética y más
            </span>
          </Opcion>

          <Opcion
            name="negocio"
            value="VILLAMAX"
            checked={negocioActual === 'VILLAMAX'}
            onChange={() => alCambiarNegocio('VILLAMAX')}
            claseTarjeta={cn(
              'p-3.5 rounded-panel border transition-all',
              negocioActual === 'VILLAMAX'
                ? 'border-indigo-600 bg-indigo-50 text-indigo-900 shadow-sm font-semibold'
                : 'border-outline-variant/60 bg-surface-container-lowest hover:bg-surface-container-low text-on-surface',
            )}
          >
            <span className="font-display text-sm font-bold">Radiología 3D (Villamax)</span>
            <span className="text-xs text-on-surface-variant font-normal">
              Tomografías CBCT, panorámicas, cefalométricas y periapicales
            </span>
          </Opcion>
        </div>
      </Seccion>

      <Seccion
        titulo={negocioActual === 'VILLAMAX' ? '2. Sede radiológica' : '2. Selecciona tu sede'}
        extra="Huancayo, Junín"
        descripcion={
          negocioActual === 'VILLAMAX'
            ? 'Villamax cuenta con centro radiológico especializado y un solo sillón tomográfico.'
            : 'Escoge la ubicación más cercana a tu domicilio o trabajo.'
        }
      >
        {sedes.isPending ? (
          <CargandoTarjetas cuantas={negocioActual === 'VILLAMAX' ? 1 : 2} etiqueta="Cargando sedes…" />
        ) : sedes.isError ? (
          <ErrorState titulo="No pudimos cargar las sedes" onReintentar={() => void sedes.refetch()} />
        ) : sedes.data.length === 0 ? (
          <EmptyState
            icono={<Building2 />}
            titulo="No hay sedes disponibles"
            descripcion="Por ahora no es posible reservar en línea. Comunícate con la clínica para agendar tu cita."
          />
        ) : (
          <div>
            <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
              {sedes.data.map((sede) => (
                <TarjetaSede
                  key={sede.id}
                  sede={sede}
                  checked={sedeActual === sede.id}
                  onChange={() => alCambiarSede(sede.id ?? '')}
                />
              ))}
            </div>
            <ErrorGrupo id="error-sede" mensaje={errSede} />
          </div>
        )}
      </Seccion>

      <Seccion
        titulo="3. Doctor o especialista"
        extra="Profesional de tu preferencia"
        descripcion="Elige el doctor asignado a esta sede o selecciona sin preferencia para que la clínica te asigne uno disponible."
      >
        {!sedeActual ? (
          <p className="text-xs text-on-surface-variant italic">
            Selecciona primero una sede para ver los profesionales disponibles.
          </p>
        ) : especialistas.isPending ? (
          <CargandoTarjetas cuantas={2} etiqueta="Cargando profesionales…" />
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            <Opcion
              name="doctorId"
              value=""
              checked={!doctorIdActual}
              onChange={() => {
                setValue('doctorId', '', { shouldValidate: true })
                setValue('doctorNombre', '', { shouldValidate: true })
              }}
              claseTarjeta="p-3 rounded-panel border border-outline-variant/60 gap-1.5"
            >
              <span className="font-display text-sm font-semibold text-on-surface">Sin preferencia</span>
              <span className="text-xs text-on-surface-variant font-normal">
                Cualquier profesional disponible
              </span>
            </Opcion>
            {doctores.map((doctor) => (
              <Opcion
                key={doctor.id}
                name="doctorId"
                value={doctor.id}
                checked={doctorIdActual === doctor.id}
                onChange={() => {
                  setValue('doctorId', doctor.id, { shouldValidate: true })
                  setValue('doctorNombre', doctor.nombre ?? '', { shouldValidate: true })
                }}
                claseTarjeta="p-3 rounded-panel border border-outline-variant/60 gap-1.5"
              >
                <span className="font-display text-sm font-semibold text-on-surface">{doctor.nombre}</span>
                {doctor.especialidad && (
                  <span className="text-xs text-primary font-medium">{doctor.especialidad}</span>
                )}
              </Opcion>
            ))}
          </div>
        )}
      </Seccion>

      <Seccion
        titulo={negocioActual === 'VILLAMAX' ? '4. Estudio de radiología 3D' : '4. Servicio odontológico'}
        descripcion={
          negocioActual === 'VILLAMAX'
            ? 'Selecciona el estudio o tomografía que te indicó tu odontólogo.'
            : 'Selecciona la atención que necesitas o una evaluación si no sabes tu diagnóstico.'
        }
      >
        {servicios.isPending ? (
          <CargandoTarjetas cuantas={negocioActual === 'VILLAMAX' ? 2 : 6} etiqueta="Cargando servicios…" />
        ) : servicios.isError ? (
          <ErrorState titulo="No pudimos cargar los servicios" onReintentar={() => void servicios.refetch()} />
        ) : servicios.data.length === 0 ? (
          <EmptyState
            titulo="Aún no hay servicios publicados"
            descripcion="Comunícate con la clínica para agendar tu cita."
          />
        ) : (
          <div>
            <div className="flex flex-col gap-5">
              {agruparPorEspecialidad(servicios.data).map((grupo) => (
                <div key={grupo.especialidad} role="group" aria-label={grupo.especialidad} className="flex flex-col gap-2">
                  <h4 className="text-sm font-bold text-primary">{grupo.especialidad}</h4>
                  <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                    {grupo.servicios.map((servicio) => (
                      <TarjetaServicio key={servicio.id} servicio={servicio} {...register('servicioId')} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <ErrorGrupo id="error-servicio" mensaje={errServicio} />
          </div>
        )}
      </Seccion>
    </div>
  )
}

type PropsRadio = ReturnType<Form['register']>

function TarjetaSede({ sede, ...radio }: { sede: SedePublica; checked: boolean; onChange: () => void }) {
  const direccionOficial =
    sede.id === 'sede-chilca-1'
      ? 'Chilca, Parque de los Héroes (Local Chilca)'
      : sede.id === 'sede-villamax-chilca'
        ? 'Chilca, Parque de los Héroes (Centro Radiológico)'
        : sede.direccion ?? 'Huancayo'

  return (
    <Opcion value={sede.id} claseTarjeta="p-3.5 rounded-panel border border-outline-variant/60 gap-3" {...radio}>
      <span className="font-display text-sm font-bold text-on-surface truncate">{sede.nombre}</span>
      <span className="flex items-center gap-1 text-xs text-on-surface-variant">
        <MapPin className="size-3.5 shrink-0 text-primary" />
        <span className="truncate">{direccionOficial}</span>
      </span>
      {sede.horarioTexto && (
        <span className="mt-1 flex items-center gap-1 text-xs font-semibold text-primary">
          <Clock aria-hidden className="size-3 shrink-0" />
          {sede.horarioTexto}
        </span>
      )}
    </Opcion>
  )
}

function TarjetaServicio({
  servicio,
  ...radio
}: { servicio: ServicioPublico } & PropsRadio) {
  return (
    <Opcion value={servicio.id} claseTarjeta="p-3 rounded-panel border border-outline-variant/60 gap-2.5" {...radio}>
      <span className="font-display text-sm font-bold text-on-surface leading-tight">{servicio.nombre}</span>
      {servicio.descripcion && (
        <span className="text-xs text-on-surface-variant line-clamp-2 leading-relaxed">{servicio.descripcion}</span>
      )}
    </Opcion>
  )
}

/* ───────── Paso 2 · Fecha y horarios de 9:00 am a 8:00 pm ───────── */

export function PasoFechaTurno({ form, servicio: _servicio, sede: _sede }: { form: Form; servicio?: ServicioPublico; sede?: SedePublica }) {
  const { register, formState, watch, setValue } = form
  const hoy = hoyEnLima()
  const dias = proximosDias(7, hoy)
  const errFecha = formState.errors.fechaPreferencia?.message
  const errTurno = formState.errors.turno?.message

  const turnoActual = watch('turno')
  const horaActual = watch('horaPreferencia')

  return (
    <div className="flex flex-col gap-7">
      <Seccion
        titulo="Fecha de preferencia"
        descripcion="Elige el día en que deseas acudir a la clínica."
      >
        <div className="grid grid-cols-4 gap-2 sm:grid-cols-7">
          {dias.map((dia) => (
            <label
              key={dia}
              className={cn(
                'relative flex cursor-pointer flex-col items-center gap-0.5 rounded-panel border border-outline-variant/50 bg-surface-container-lowest px-2 py-2.5 text-center shadow-sm hover:bg-surface-container-low transition-colors',
                'has-[:checked]:border-primary has-[:checked]:bg-primary-container/20 has-[:checked]:text-primary',
                'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
              )}
            >
              <input
                type="radio"
                value={dia}
                className="absolute inset-0 size-full cursor-pointer opacity-0"
                aria-label={fechaConDia(dia) + (dia === hoy ? ' (hoy)' : '')}
                {...register('fechaPreferencia')}
              />
              <span aria-hidden className="text-xs font-bold uppercase">
                {dia === hoy ? 'Hoy' : diaSemanaCorto(dia)}
              </span>
              <span aria-hidden className="font-display text-base font-bold">{formatFecha(dia).slice(0, 5)}</span>
            </label>
          ))}
        </div>
        <ErrorGrupo id="error-fecha" mensaje={errFecha} />
      </Seccion>

      <Seccion
        titulo="Horarios disponibles (9:00 a.m. a 8:00 p.m.)"
        descripcion="Elige el turno u hora sugerida. La clínica te contactará para confirmar tu cita exacta."
      >
        <div className="grid gap-3 sm:grid-cols-2">
          {/* Bloque Mañana */}
          <div
            className={cn(
              'rounded-panel border p-3.5 transition-colors',
              turnoActual === 'MANANA'
                ? 'border-primary bg-primary/5 shadow-sm'
                : 'border-outline-variant/60 bg-surface-container-lowest hover:bg-surface-container-low',
            )}
          >
            <div className="flex items-center justify-between mb-2.5">
              <label className="flex items-center gap-2 cursor-pointer font-display text-sm font-bold text-on-surface">
                <input
                  type="radio"
                  value="MANANA"
                  aria-label="Mañana"
                  {...register('turno')}
                  className="size-4 cursor-pointer accent-primary"
                />
                <Sun aria-hidden className="size-4.5 text-secondary" />
                <span>Mañana</span>
                <span className="text-xs font-normal text-on-surface-variant">(9:00 am - 12:00 pm)</span>
              </label>
            </div>
            <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
              {HORARIOS_SLOTS.filter((s) => s.turno === 'MANANA').map((slot) => {
                const activo = horaActual === slot.etiqueta
                return (
                  <button
                    key={slot.valor}
                    type="button"
                    onClick={() => {
                      setValue('turno', 'MANANA', { shouldValidate: true })
                      setValue('horaPreferencia', slot.etiqueta, { shouldValidate: true })
                    }}
                    className={cn(
                      'flex items-center justify-center gap-1 rounded-md py-1.5 px-2 text-xs font-bold transition-all border',
                      activo
                        ? 'bg-acento text-on-acento border-primary shadow-sm'
                        : 'bg-surface hover:bg-surface-container border-outline-variant/50 text-on-surface',
                    )}
                  >
                    <Clock className="size-3 shrink-0" />
                    {slot.etiqueta}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Bloque Tarde */}
          <div
            className={cn(
              'rounded-panel border p-3.5 transition-colors',
              turnoActual === 'TARDE'
                ? 'border-primary bg-primary/5 shadow-sm'
                : 'border-outline-variant/60 bg-surface-container-lowest hover:bg-surface-container-low',
            )}
          >
            <div className="flex items-center justify-between mb-2.5">
              <label className="flex items-center gap-2 cursor-pointer font-display text-sm font-bold text-on-surface">
                <input
                  type="radio"
                  value="TARDE"
                  aria-label="Tarde"
                  {...register('turno')}
                  className="size-4 cursor-pointer accent-primary"
                />
                <Moon aria-hidden className="size-4.5 text-tertiary" />
                <span>Tarde</span>
                <span className="text-xs font-normal text-on-surface-variant">(1:00 pm - 8:00 pm)</span>
              </label>
            </div>
            <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
              {HORARIOS_SLOTS.filter((s) => s.turno === 'TARDE').map((slot) => {
                const activo = horaActual === slot.etiqueta
                return (
                  <button
                    key={slot.valor}
                    type="button"
                    onClick={() => {
                      setValue('turno', 'TARDE', { shouldValidate: true })
                      setValue('horaPreferencia', slot.etiqueta, { shouldValidate: true })
                    }}
                    className={cn(
                      'flex items-center justify-center gap-1 rounded-md py-1.5 px-2 text-xs font-bold transition-all border',
                      activo
                        ? 'bg-acento text-on-acento border-primary shadow-sm'
                        : 'bg-surface hover:bg-surface-container border-outline-variant/50 text-on-surface',
                    )}
                  >
                    <Clock className="size-3 shrink-0" />
                    {slot.etiqueta}
                  </button>
                )
              })}
            </div>
          </div>
        </div>
        <ErrorGrupo id="error-turno" mensaje={errTurno} />
      </Seccion>


    </div>
  )
}

/* ───────── Paso 3 · Datos del paciente ───────── */

export function PasoDatos({ form }: { form: Form }) {
  const {
    register,
    formState: { errors },
  } = form
  return (
    <Seccion titulo="Datos del paciente" descripcion="La recepción se comunicará a este celular para confirmar tu turno.">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2 flex items-start gap-3 rounded-panel bg-surface-container-low p-3.5 border border-outline-variant/60">
          <input
            id="pacienteNuevo"
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-outline text-primary focus:ring-primary cursor-pointer"
            {...register('pacienteNuevo')}
          />
          <label htmlFor="pacienteNuevo" className="text-xs text-on-surface cursor-pointer select-none">
            <span className="font-bold block text-sm text-primary">Soy paciente nuevo</span>
            Marca esta casilla si es tu primera vez en nuestra clínica o deseas registrar una solicitud sin turno fijo.
          </label>
        </div>
        <Field
          label="DNI o documento"
          error={errors.dni?.message}
          ayuda="DNI de 8 dígitos o carné de extranjería (hasta 12). Si ya estás registrado, usaremos los datos de tu ficha clínica."
          className="sm:col-span-2"
        >
          <Input placeholder="Ej: 45908123" inputMode="numeric" autoComplete="off" {...register('dni')} />
        </Field>
        <Field label="Nombres" error={errors.nombres?.message}>
          <Input autoComplete="given-name" placeholder="Ej: Carlos" {...register('nombres')} />
        </Field>
        <Field label="Apellidos" error={errors.apellidos?.message}>
          <Input autoComplete="family-name" placeholder="Ej: Huamán Rojas" {...register('apellidos')} />
        </Field>
        <Field
          label="Celular o WhatsApp"
          error={errors.telefono?.message}
          ayuda="Celular activo para llamada o WhatsApp de confirmación."
          className="sm:col-span-2"
        >
          <Input type="tel" inputMode="tel" autoComplete="tel" placeholder="Ej: 987654321" {...register('telefono')} />
        </Field>
        <p className="text-xs text-on-surface-variant sm:col-span-2">
          Usaremos estos datos solo para gestionar tu cita.{' '}
          <Link to="/privacidad" target="_blank" className="font-semibold text-primary hover:underline">Lee la política de privacidad</Link>.
        </p>
        {/* Campo trampa anti-bots: fuera de pantalla, sin foco y oculto para lectores de pantalla. Una persona no lo ve. */}
        <div aria-hidden="true" className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
          <label>
            No llenar este campo
            <input type="text" tabIndex={-1} autoComplete="off" {...register('sitioWeb')} />
          </label>
        </div>
      </div>
    </Seccion>
  )
}

/* ───────── Paso 4 · Revisión ───────── */

export function Resumen({
  datos,
  sede,
  servicio,
  onCambiar,
  compacto = false,
}: {
  datos: Partial<DatosReserva>
  sede?: SedePublica
  servicio?: ServicioPublico
  /** Si se indica, cada fila tiene un botón "Cambiar" que vuelve a su paso. */
  onCambiar?: (paso: number) => void
  compacto?: boolean
}) {
  const filas: { etiqueta: string; valor: ReactNode; paso: number; icono?: ReactNode }[] = [
    {
      etiqueta: 'Atención',
      paso: 0,
      valor: datos.negocio === 'VILLAMAX' ? 'Radiología 3D (Villamax)' : 'Odontología (Villadent)',
    },
    {
      etiqueta: 'Sede',
      paso: 0,
      icono: <MapPin aria-hidden className="size-4" />,
      valor: sede ? (
        <>
          {sede.nombre}
          {sede.direccion && !compacto && <span className="block font-normal text-on-surface-variant text-xs">{sede.direccion}</span>}
        </>
      ) : null,
    },
    {
      etiqueta: 'Doctor / Profesional',
      paso: 0,
      valor: datos.doctorNombre ? <>{datos.doctorNombre}</> : <>Sin preferencia (Asignado por la clínica)</>,
    },
    {
      etiqueta: 'Servicio',
      paso: 0,
      valor: servicio ? <>{servicio.nombre}</> : null,
    },
    { etiqueta: 'Fecha', paso: 1, valor: datos.fechaPreferencia ? fechaConDia(datos.fechaPreferencia) : null },
    {
      etiqueta: 'Turno',
      paso: 1,
      valor: datos.turno ? (
        <>
          <span>{textoTurno(datos.turno)}</span>
          {datos.horaPreferencia && (
            <span className="block text-xs font-normal text-on-surface-variant">Hora sugerida: {datos.horaPreferencia}</span>
          )}
        </>
      ) : null,
    },
  ]
  if (!compacto) {
    filas.push(
      {
        etiqueta: 'Paciente',
        paso: 2,
        valor: datos.nombres ? (
          <>
            {datos.nombres} {datos.apellidos}
            <span className="block font-normal text-on-surface-variant text-xs">Documento: {datos.dni}</span>
          </>
        ) : null,
      },
      { etiqueta: 'Celular', paso: 2, valor: datos.telefono || null },
    )
  }
  return (
    <dl className="flex flex-col divide-y divide-outline-variant/60">
      {filas.map((f) => (
        <div key={f.etiqueta} className="flex items-start justify-between gap-4 py-2.5 first:pt-0 last:pb-0">
          <dt className="text-xs sm:text-sm text-on-surface-variant">{f.etiqueta}</dt>
          <dd className="flex flex-col items-end gap-1 text-right text-xs sm:text-sm font-bold text-on-surface">
            <span>{f.valor ?? <span className="font-normal text-on-surface-variant">Sin elegir</span>}</span>
            {onCambiar && (
              <button
                type="button"
                onClick={() => onCambiar(f.paso)}
                aria-label={`Cambiar ${f.etiqueta.toLocaleLowerCase('es-PE')}`}
                className="text-xs font-bold text-primary underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-focus"
              >
                Cambiar
              </button>
            )}
          </dd>
        </div>
      ))}
    </dl>
  )
}
