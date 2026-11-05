/**
 * A6 · Consulta tu reserva: POST /reservas-publicas/consulta con { ticket, dni } (DNI de 8 dígitos, como
 * ConsultaReservaRequestDTO). El código y el DNI van en el cuerpo, nunca en la URL de la consulta.
 *
 * La respuesta solo trae ticket, estado, fecha, turno y sede: lo demás que muestra la captura (servicio, especialista,
 * resumen financiero, cuenta regresiva) no existe en la API (brechas-api.md, A6).
 */
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from '@tanstack/react-router'
import { CalendarDays, IdCard, MapPin, MessageCircle, Search, ShieldCheck, Ticket } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import {
  Alert,
  Card,
  Button,
  EmptyState,
  EstadoBadge,
  Eyebrow,
  Field,
  Input,
  Skeleton,
  SoloLector,
  buttonVariants,
} from '@/design-system'
import type { ConsultaReserva } from '@/shared/api'
import { consultarReserva, interpretarErrorReserva, useSedes, type ErrorReserva } from './api-reserva'
import { esquemaConsulta, textoTurno, type DatosConsulta, type DatosConsultaEntrada } from './esquemas'
import { fechaConDia } from './fechas'

const ERROR_GENERICO = 'No pudimos consultar tu reserva. Revisa tu conexión e inténtalo de nuevo en unos minutos.'

type Resultado = { tipo: 'encontrada'; reserva: ConsultaReserva } | ErrorReserva

export function ConsultaReservaPage({ codigoInicial = '' }: { codigoInicial?: string }) {
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<DatosConsultaEntrada, unknown, DatosConsulta>({
    resolver: zodResolver(esquemaConsulta),
    defaultValues: { ticket: codigoInicial.toUpperCase(), dni: '' },
  })

  const buscar = async (datos: DatosConsulta) => {
    setResultado(null)
    try {
      const reserva = await consultarReserva({ ticket: datos.ticket, dni: datos.dni })
      setResultado({ tipo: 'encontrada', reserva })
    } catch (e) {
      setResultado(interpretarErrorReserva(e, ERROR_GENERICO))
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-3">
        <Eyebrow className="text-primary">Portal de pacientes · Huancayo</Eyebrow>
        <h1 className="font-display text-headline-lg font-bold text-primary sm:text-headline-xl">
          Consulta el estado de tu reserva
        </h1>
        <p className="max-w-3xl text-body-lg text-on-surface-variant">
          Ingresa tu código de reserva y el DNI del paciente para ver el estado de tu cita en nuestras sedes de Huancayo.
        </p>
      </header>

      <Card className="gap-6 p-6 sm:p-8">
        <div className="flex items-center gap-3">
          <span aria-hidden className="flex size-12 items-center justify-center rounded-panel bg-acento text-on-acento">
            <Search className="size-6" />
          </span>
          <div>
            <h2 className="font-display text-xl font-bold text-primary">Localizador de turnos</h2>
            <p className="text-sm text-on-surface-variant">Busca tu reserva con el código y el DNI.</p>
          </div>
        </div>
        <form
          noValidate
          onSubmit={handleSubmit(buscar)}
          aria-label="Buscar reserva"
          className="grid gap-5 md:grid-cols-[1fr_1fr_auto] md:items-start"
        >
          <Field label="Código de reserva" error={errors.ticket?.message} ayuda="Empieza con OM-, por ejemplo OM-7K9M2P4X.">
            <Input icono={<Ticket />} autoComplete="off" autoCapitalize="characters" spellCheck={false} {...register('ticket')} />
          </Field>
          <Field label="DNI del paciente" error={errors.dni?.message} ayuda="8 dígitos, el mismo que usaste al reservar.">
            <Input icono={<IdCard />} inputMode="numeric" autoComplete="off" maxLength={8} {...register('dni')} />
          </Field>
          <Button type="submit" variant="accion" size="lg" cargando={isSubmitting} className="w-full md:mt-7 md:w-auto">
            <Search aria-hidden />
            Buscar reserva
          </Button>
        </form>
        <p className="flex gap-3 rounded-panel bg-surface-container-low p-4 text-sm text-on-surface">
          <ShieldCheck aria-hidden className="size-5 shrink-0 text-primary" />
          La consulta solo muestra el estado, la fecha, el turno y la sede de la cita; no muestra datos personales.
        </p>
      </Card>

      <section aria-label="Resultado de la búsqueda" aria-live="polite" aria-busy={isSubmitting} className="empty:hidden">
        {isSubmitting ? <CargandoResultado /> : resultado && <VistaResultado resultado={resultado} />}
      </section>
    </div>
  )
}

function CargandoResultado() {
  return (
    <Card className="gap-4 p-6 sm:p-8">
      <SoloLector role="status">Buscando tu reserva…</SoloLector>
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-8 w-64" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Skeleton className="h-24 rounded-panel" />
        <Skeleton className="h-24 rounded-panel" />
      </div>
    </Card>
  )
}

function VistaResultado({ resultado }: { resultado: Resultado }) {
  if (resultado.tipo === 'encontrada') return <ReservaEncontrada reserva={resultado.reserva} />
  if (resultado.tipo === 'bloqueo') {
    return (
      <Alert tono="bloqueo" titulo="Demasiadas consultas">
        {resultado.mensaje}
      </Alert>
    )
  }
  if (resultado.tipo === 'rechazo') {
    return (
      <Card role="alert" className="p-2">
        <EmptyState
          icono={<Search />}
          titulo="No encontramos tu reserva"
          descripcion={
            <>
              {resultado.mensaje} Revisa el código y el DNI. Si reservaste con carné de extranjería, comunícate con la
              clínica.
            </>
          }
          accion={
            <Link to="/reservar" className={buttonVariants({ variant: 'secundario' })}>
              Hacer una reserva nueva
            </Link>
          }
        />
      </Card>
    )
  }
  return (
    <Alert tono="error" titulo="No se pudo consultar">
      {resultado.mensaje}
    </Alert>
  )
}

function ReservaEncontrada({ reserva }: { reserva: ConsultaReserva }) {
  const sedes = useSedes()
  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => titulo.current?.focus(), [])
  // El backend devuelve el id de la sede como `sedeNombre` (brechas-api.md, A5-9): el nombre sale de GET /sedes.
  const sede = sedes.data?.find((s) => s.id === reserva.sedeId)
  const nombreSede = sede?.nombre ?? (sedes.isPending ? null : reserva.sedeNombre)

  return (
    <Card className="gap-6 p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <Eyebrow>Identificador de cita</Eyebrow>
          <h2 ref={titulo} tabIndex={-1} className="font-mono text-2xl font-bold tracking-wider text-primary focus:outline-none">
            {reserva.ticket}
          </h2>
        </div>
        <div className="flex flex-col items-end gap-1">
          <SoloLector>Estado:</SoloLector>
          <EstadoBadge estado={reserva.estado} />
        </div>
      </div>

      {reserva.estado === 'PENDIENTE_DE_PAGO' && (
        <Alert tono="info" titulo="Pendiente de confirmación con adelanto">
          <p>
            Tu horario está reservado temporalmente. Para confirmarlo de forma definitiva, realiza el adelanto vía Yape indicando tu código <strong>{reserva.ticket}</strong> y envía el comprobante por WhatsApp a la sede asignada.
          </p>
        </Alert>
      )}

      {reserva.estado === 'PENDIENTE_WEB' && (
        <Alert tono="info" titulo="Tu reserva aún no está confirmada">
          La clínica se comunicará contigo vía WhatsApp para confirmar tu hora exacta de atención. El pago se realiza en la clínica.
        </Alert>
      )}

      {(reserva.estado === 'CONFIRMADA' || reserva.estado === 'PROGRAMADA' || reserva.estado === 'CONFIRMADA_CON_ADELANTO') && (
        <Alert tono="exito" titulo="¡Tu cita está confirmada!">
          Te esperamos en la sede en la fecha y turno indicados. Por favor asiste con 10 minutos de anticipación.
        </Alert>
      )}

      <dl className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1 rounded-panel bg-surface-container-low p-5">
          <dt className="flex items-center gap-2 text-sm font-bold text-primary">
            <MapPin aria-hidden className="size-4" />
            Sede asignada
          </dt>
          <dd className="font-bold text-on-surface">
            {nombreSede ?? <Skeleton className="h-5 w-32" />}
            {sede?.direccion && <span className="block font-normal text-on-surface-variant">{sede.direccion}</span>}
          </dd>
        </div>
        <div className="flex flex-col gap-1 rounded-panel bg-surface-container-low p-5">
          <dt className="flex items-center gap-2 text-sm font-bold text-primary">
            <CalendarDays aria-hidden className="size-4" />
            Fecha y turno
          </dt>
          <dd className="font-bold text-on-surface">
            {fechaConDia(reserva.fecha)}
            <span className="block text-secondary">Turno: {textoTurno(reserva.turno)}</span>
          </dd>
        </div>
      </dl>

      {sede?.whatsapp && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-panel border border-emerald-200 bg-emerald-50/70 p-4 text-emerald-950">
          <div className="flex items-center gap-3">
            <MessageCircle className="size-5 text-emerald-700" />
            <span className="text-sm font-semibold">¿Consultas o comprobante de tu cita? Escríbenos:</span>
          </div>
          <a
            href={`https://wa.me/${sede.whatsapp}?text=${encodeURIComponent(`Hola, consulto por mi reserva ${reserva.ticket}.`)}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-pill bg-emerald-600 px-4 py-2 text-xs font-bold text-white shadow-sm hover:bg-emerald-700 transition-colors"
          >
            WhatsApp {sede.nombre}
          </a>
        </div>
      )}
    </Card>
  )
}
