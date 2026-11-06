import { Link } from '@tanstack/react-router'
import {
  CalendarDays,
  CheckCircle2,
  Clock,
  Copy,
  ExternalLink,
  MapPin,
  MessageCircle,
  Stethoscope,
  Users,
  Wallet,
} from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, Card, Eyebrow, Skeleton } from '@/design-system'
import type { TicketReserva } from '@/shared/api'
import { useSedes } from './api-reserva'
import { textoTurno } from './esquemas'
import { fechaConDia } from './fechas'
import { WHATSAPP_VILLADENT, WHATSAPP_VILLAMAX } from '@/public/contacto'

function Fila({ icono, etiqueta, children }: { icono: ReactNode; etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0">
      <dt className="flex items-center gap-2 text-on-surface-variant [&_svg]:size-4">
        {icono}
        {etiqueta}
      </dt>
      <dd className="text-right font-bold text-on-surface">{children}</dd>
    </div>
  )
}

function CopiarCodigo({ codigo }: { codigo: string }) {
  const [aviso, setAviso] = useState('')
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(codigo)
      setAviso('Código copiado.')
      setTimeout(() => setAviso(''), 2500)
    } catch {
      setAviso('No se pudo copiar. Anota el código.')
    }
  }
  return (
    <>
      <Button
        variant="secundario"
        size="sm"
        onClick={() => void copiar()}
        aria-label={`Copiar código ${codigo}`}
        className="gap-1.5 h-8 px-2.5 text-xs"
      >
        <Copy aria-hidden className="size-3.5" />
        Copiar código
      </Button>
      <span role="status" className="sr-only">
        {aviso}
      </span>
    </>
  )
}

/**
 * Pantalla de confirmación de reserva pública.
 * Refleja el flujo real de recepción de Villadent: la recepcionista o el doctor asignado
 * ven la solicitud en su agenda y coordinan por llamada o WhatsApp para confirmar el horario.
 * Sin cobros forzados de adelanto por internet ni pasarelas simuladas.
 */
export function TicketConfirmacion({ ticket }: { ticket: TicketReserva }) {
  const sedes = useSedes()
  const sede = sedes.data?.find((s) => s.id === ticket.sedeId)
  const nombreSede = sede?.nombre ?? ticket.sedeNombre
  const tituloRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  const direccionSede =
    ticket.sedeId === 'sede-chilca-1' || sede?.id === 'sede-chilca-1'
      ? 'Chilca, Parque de los Héroes (Local Chilca)'
      : ticket.sedeId === 'sede-villamax-chilca' || sede?.id === 'sede-villamax-chilca'
        ? 'Chilca, Parque de los Héroes (Centro Radiológico)'
        : sede?.direccion ?? 'Huancayo'

  const telefonoWhatsapp =
    ticket.telefonoWhatsappSede ||
    sede?.whatsapp ||
    (ticket.sedeId === 'sede-villamax-chilca' ? WHATSAPP_VILLAMAX : WHATSAPP_VILLADENT)

  const telefonoLimpio = telefonoWhatsapp.replace(/\D/g, '')

  const mensajeWhatsApp = encodeURIComponent(
    `Hola Villadent, acabo de registrar una solicitud de cita en su web para *${ticket.especialidad}* en *${nombreSede}* para el día *${fechaConDia(ticket.fecha)}*. Quedo a la espera de su confirmación.`
  )
  const enlaceWhatsApp = `https://wa.me/${telefonoLimpio}?text=${mensajeWhatsApp}`

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-7 py-4">
      {/* Encabezado de confirmación con foco accesible */}
      <div className="flex flex-col items-center text-center gap-3">
        <div className="flex size-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 shadow-sm dark:bg-emerald-950 dark:text-emerald-300">
          <CheckCircle2 className="size-9" />
        </div>
        <Eyebrow className="text-emerald-700 dark:text-emerald-400">Solicitud Recibida</Eyebrow>
        <h1
          ref={tituloRef}
          tabIndex={-1}
          className="font-display text-headline-md sm:text-headline-lg font-bold text-on-surface focus:outline-none"
        >
          ¡Tu reserva está registrada!
        </h1>
        <p className="max-w-xl text-body-md text-on-surface-variant">
          Hemos recibido tus datos y la solicitud se encuentra registrada en la agenda de la clínica.
        </p>
        <span className="inline-flex items-center gap-1.5 rounded-pill bg-amber-50 dark:bg-amber-950/60 text-amber-800 dark:text-amber-200 border border-amber-200/80 px-3 py-1 text-xs font-bold">
          <Clock className="size-3.5" />
          Pendiente de confirmación
        </span>
      </div>

      {/* Proceso de confirmación real */}
      <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5 sm:p-6 shadow-sm">
        <div className="flex items-start gap-4">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-acento text-on-acento shadow-sm">
            <Users className="size-6" />
          </div>
          <div className="flex flex-col gap-1 text-sm">
            <h2 className="font-display font-bold text-base text-primary">
              ¿Cómo confirmamos tu cita?
            </h2>
            <p className="text-on-surface leading-relaxed">
              La clínica se comunicará contigo vía WhatsApp o llamada telefónica para confirmar tu turno exacto y resolver cualquier consulta previa. La recepcionista o el odontólogo asignado en la sede ya tiene tu solicitud en pantalla.
            </p>
          </div>
        </div>
      </div>

      {/* Resumen del turno */}
      <Card className="gap-5 p-6 sm:p-7 border border-outline-variant/60 shadow-card">
        <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
          <h2 className="flex items-center gap-2 font-display text-base font-bold text-primary">
            <CalendarDays className="size-5" />
            Detalle de tu solicitud
          </h2>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs font-bold text-on-surface-variant">Código: {ticket.codigoTicket}</span>
            <CopiarCodigo codigo={ticket.codigoTicket} />
          </div>
        </div>

        <dl className="flex flex-col divide-y divide-outline-variant/60 text-sm">
          <Fila icono={<Stethoscope />} etiqueta="Servicio">
            {ticket.especialidad}
          </Fila>
          <Fila icono={<MapPin />} etiqueta="Ubicación">
            <span>{nombreSede ?? <Skeleton className="h-5 w-32" />}</span>
            <span className="block font-normal text-xs text-on-surface-variant mt-0.5">
              {direccionSede}
            </span>
          </Fila>
          <Fila icono={<CalendarDays />} etiqueta="Fecha">
            {fechaConDia(ticket.fecha)}
          </Fila>
          <Fila icono={<Clock />} etiqueta="Turno">
            <span className="text-secondary font-bold">{textoTurno(ticket.turno)}</span>
            <span className="block text-xs font-normal text-on-surface-variant">
              Hora sugerida · Se afina por WhatsApp
            </span>
          </Fila>
        </dl>

        {/* Sección de Pago en la clínica */}
        <div className="mt-2 rounded-xl border border-outline-variant/60 bg-surface-container-low/50 p-4">
          <div className="flex items-center gap-2.5">
            <Wallet className="size-4.5 text-primary" />
            <h3 className="font-display text-sm font-bold text-primary">Pago en la clínica</h3>
          </div>
          <p className="mt-1 text-xs text-on-surface-variant leading-relaxed">
            No necesitas pagar nada ahora en línea. El pago de tu consulta o tratamiento se realiza directamente en la recepción de la clínica el día de tu atención (efectivo, tarjeta o POS).
          </p>
        </div>
      </Card>

      {/* Botón de WhatsApp prioritario */}
      <div className="flex flex-col gap-3 items-center text-center">
        <a
          href={enlaceWhatsApp}
          target="_blank"
          rel="noreferrer"
          className="group flex w-full sm:w-auto items-center justify-center gap-3 rounded-2xl bg-emerald-600 hover:bg-emerald-700 active:scale-[0.99] transition-all px-8 py-3.5 text-white font-bold text-sm sm:text-base shadow-lg shadow-emerald-600/25"
        >
          <MessageCircle className="size-5 shrink-0 transition-transform group-hover:scale-110" />
          <span>¿Deseas confirmarla de inmediato? Escríbenos por WhatsApp</span>
          <ExternalLink className="size-4 opacity-75" />
        </a>
        <p className="text-xs text-on-surface-variant">
          Te atenderá directamente la recepción de la sede para validar tu disponibilidad.
        </p>
        <Link
          to="/reserva"
          search={{ codigo: ticket.codigoTicket }}
          className="text-xs text-on-surface-variant hover:text-primary underline underline-offset-4 mt-1"
        >
          Consultar el estado de mi reserva
        </Link>
      </div>

      {/* Acciones de navegación */}
      <div className="flex flex-col-reverse sm:flex-row items-center justify-center gap-3 pt-2">
        <Link to="/" className="w-full sm:w-auto">
          <Button variant="secundario" size="lg" className="w-full sm:w-auto">
            Volver a la página principal
          </Button>
        </Link>
        <Link to="/reservar" className="w-full sm:w-auto">
          <Button variant="secundario" size="lg" className="w-full sm:w-auto">
            Solicitar otra cita
          </Button>
        </Link>
      </div>
    </div>
  )
}
