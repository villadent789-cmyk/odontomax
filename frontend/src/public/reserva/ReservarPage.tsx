/**
 * A4 · /reservar: asistente de reserva pública en 4 pasos (mobile-first; en escritorio añade un resumen lateral).
 *
 * Sigue el modelo de la API, no el del diseño: POST /reservas-publicas recibe sede + fecha de preferencia + turno
 * (MANANA/TARDE) y la clínica confirma la hora exacta por WhatsApp. No se elige especialista ni hueco exacto
 * (brechas-api.md, A4-1 y A4-2). El servicio elegido viaja como `motivoConsulta` (su nombre).
 */
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, CalendarCheck, Info } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Alert, Button, Card, Eyebrow, cn } from '@/design-system'
import { guardarTicket } from './ticket-local'
import {
  interpretarErrorReserva,
  registrarReserva,
  useSedes,
  useServicios,
  type CuerpoReserva,
  type ErrorReserva,
} from './api-reserva'
import { CAMPOS_POR_PASO, PASOS_RESERVA, esquemaReserva, type DatosReserva, type DatosReservaValidos } from './esquemas'
import { Pasos } from './Pasos'
import { PasoDatos, PasoFechaTurno, PasoSedeServicio, Resumen } from './PasosReserva'

const TEXTO_CONTINUAR = ['Continuar a selección de fecha', 'Continuar a tus datos', 'Revisar mi reserva']

const ERROR_GENERICO = 'No pudimos registrar tu reserva. Revisa tu conexión e inténtalo de nuevo en unos minutos.'

export function ReservarPage() {
  const navigate = useNavigate()
  const search = useSearch({ strict: false }) as { sedeId?: string; servicioId?: string }
  const [paso, setPaso] = useState(0)
  const [error, setError] = useState<ErrorReserva | null>(null)
  const tituloRef = useRef<HTMLHeadingElement>(null)
  const primerRender = useRef(true)

  const form = useForm<DatosReserva, unknown, DatosReservaValidos>({
    resolver: zodResolver(esquemaReserva),
    mode: 'onTouched',
    defaultValues: {
      negocio: 'VILLADENT',
      sedeId: search?.sedeId ?? '',
      doctorId: '',
      doctorNombre: '',
      servicioId: search?.servicioId ?? '',
      pacienteNuevo: false,
      fechaPreferencia: '',
      turno: undefined,
      horaPreferencia: '',
      dni: '',
      nombres: '',
      apellidos: '',
      telefono: '',
      sitioWeb: '',
    },
  })
  const datos = useWatch({ control: form.control })
  const sedes = useSedes(datos.negocio)
  const servicios = useServicios(datos.negocio)
  const sede = sedes.data?.find((s) => s.id === datos.sedeId)
  const servicio = servicios.data?.find((s) => s.id === datos.servicioId)

  // Al cambiar de paso, el foco va al título del paso (el lector anuncia "Paso N de 4: ...").
  useEffect(() => {
    if (primerRender.current) {
      primerRender.current = false
      return
    }
    tituloRef.current?.focus()
  }, [paso])

  const irA = (destino: number) => {
    setError(null)
    setPaso(destino)
  }

  const continuar = async () => {
    const valido = await form.trigger(CAMPOS_POR_PASO[paso], { shouldFocus: true })
    if (valido) irA(paso + 1)
  }

  const enviar = async (d: DatosReservaValidos) => {
    setError(null)
    if (!servicio?.nombre) {
      irA(0)
      return
    }
    const cuerpo: CuerpoReserva = {
      dni: d.dni,
      nombres: d.nombres,
      apellidos: d.apellidos,
      telefono: d.telefono,
      motivoConsulta: servicio.nombre,
      sedeId: d.sedeId,
      fechaPreferencia: d.fechaPreferencia,
      turno: d.turno,
      ...(d.doctorId ? { doctorId: d.doctorId } : {}),
      ...(d.pacienteNuevo ? { pacienteNuevo: true } : {}),
      ...(d.sitioWeb ? { sitioWeb: d.sitioWeb } : {}),
    }
    try {
      const ticket = await registrarReserva(cuerpo)
      guardarTicket(ticket)
      await navigate({ to: '/reserva/$codigo', params: { codigo: ticket.codigoTicket } })
    } catch (e) {
      setError(interpretarErrorReserva(e, ERROR_GENERICO))
    }
  }

  const alEnviar = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (paso < PASOS_RESERVA.length - 1) void continuar()
    else void form.handleSubmit(enviar)(e)
  }

  const ultimo = paso === PASOS_RESERVA.length - 1

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8">
      <header className="flex flex-col gap-2">
        <Eyebrow className={datos.negocio === 'VILLAMAX' ? 'text-indigo-600' : 'text-primary'}>
          {datos.negocio === 'VILLAMAX' ? 'Villamax 3D · Centro Radiológico' : 'Villadent Huancayo · Reserva en línea'}
        </Eyebrow>
        <div className="flex items-center gap-3">
          {datos.negocio === 'VILLADENT' ? (
            <img src="/logo-villadent-128.png" alt="Logo Villadent" className="size-10 rounded-full object-contain" />
          ) : (
            <span className="inline-flex items-center rounded-md bg-indigo-600 px-2.5 py-1 text-xs font-bold text-white shadow-sm">
              VILLAMAX 3D
            </span>
          )}
          <h1 className={cn('font-display text-headline-lg font-bold sm:text-headline-xl', datos.negocio === 'VILLAMAX' ? 'text-indigo-900' : 'text-primary')}>
            {datos.negocio === 'VILLAMAX' ? 'Reserva tu estudio radiológico' : 'Reserva tu cita'}
          </h1>
        </div>
        <p className="text-body-lg text-on-surface-variant">
          {datos.negocio === 'VILLAMAX'
            ? 'Elige la sede Villamax, el estudio radiológico y tu turno de preferencia. La clínica te contactará para confirmar tu cita.'
            : 'Elige sede, servicio, día y horario de preferencia. La clínica se comunicará contigo de inmediato por WhatsApp o llamada para confirmar tu cita.'}
        </p>
      </header>

      <Pasos pasos={PASOS_RESERVA} actual={paso} />

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <form noValidate onSubmit={alEnviar} aria-labelledby="titulo-paso" className="flex min-w-0 flex-col gap-8">
          <h2 id="titulo-paso" ref={tituloRef} tabIndex={-1} className="sr-only focus:outline-none">
            Paso {paso + 1} de {PASOS_RESERVA.length}: {PASOS_RESERVA[paso].titulo}
          </h2>

          {paso === 0 && <PasoSedeServicio form={form} />}
          {paso === 1 && <PasoFechaTurno form={form} sede={sede} servicio={servicio} />}
          {paso === 2 && <PasoDatos form={form} />}
          {paso === 3 && (
            <section aria-labelledby="titulo-revision" className="flex flex-col gap-6">
              <h3 id="titulo-revision" className="font-display text-headline-md font-bold text-primary">
                Revisa tu reserva
              </h3>
              <Card>
                <Resumen datos={datos} sede={sede} servicio={servicio} onCambiar={irA} />
              </Card>
              <Alert tono="info" titulo="No necesitas pagar ahora">
                El pago se realiza en la clínica. Por ahora no es posible pagar un adelanto en línea.
              </Alert>
            </section>
          )}

          <div aria-live="assertive" className="empty:hidden">
            {error && (
              <Alert
                tono={error.tipo === 'bloqueo' ? 'bloqueo' : 'error'}
                titulo={error.tipo === 'bloqueo' ? 'Demasiados intentos de reserva' : 'No se pudo registrar la reserva'}
                onCerrar={() => setError(null)}
              >
                <p>{error.mensaje}</p>
                {error.tipo === 'rechazo' && (
                  <Button variant="enlace" className="mt-1" onClick={() => irA(1)}>
                    Elegir otra fecha o turno
                  </Button>
                )}
              </Alert>
            )}
          </div>

          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
            {paso > 0 ? (
              <Button variant="secundario" size="lg" onClick={() => irA(paso - 1)}>
                <ArrowLeft aria-hidden />
                Atrás
              </Button>
            ) : (
              <span />
            )}
            <Button
              type="submit"
              variant="accion"
              size="lg"
              cargando={form.formState.isSubmitting}
              className="w-full sm:w-auto"
            >
              {ultimo ? (
                <>
                  <CalendarCheck aria-hidden />
                  Confirmar reserva
                </>
              ) : (
                <>
                  {TEXTO_CONTINUAR[paso]}
                  <ArrowRight aria-hidden />
                </>
              )}
            </Button>
          </div>
        </form>

        <aside aria-label="Resumen de tu reserva" className="hidden lg:block">
          <Card className="sticky top-6">
            <h2 className="font-display text-xl font-bold text-primary">Tu reserva</h2>
            <Resumen datos={datos} sede={sede} servicio={servicio} compacto />
            <p className="flex gap-2 text-sm text-on-surface-variant">
              <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
              La hora exacta te la confirma la clínica por WhatsApp.
            </p>
          </Card>
        </aside>
      </div>
    </div>
  )
}
