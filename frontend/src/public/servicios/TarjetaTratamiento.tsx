import { Link } from '@tanstack/react-router'
import { ArrowRight, MessageCircle } from 'lucide-react'
import { buttonVariants } from '@/design-system'
import { enlaceWhatsApp } from '@/public/contacto'
import type { TratamientoGeneral } from './tratamientosGenerales'

/** Tarjeta de un tratamiento general: foto, título, descripción y «Reservar cita» (abre WhatsApp con el mensaje escrito). */
export function TarjetaTratamiento({ tratamiento: t }: { tratamiento: TratamientoGeneral }) {
  return (
    <article className="flex flex-col overflow-hidden rounded-3xl border border-outline-variant/60 bg-surface-container-lowest shadow-card transition-shadow hover:shadow-raised">
      <img
        src={t.imagen}
        alt=""
        loading="lazy"
        onError={(e) => {
          // Aún no hay foto de este tratamiento: se muestra su ilustración.
          if (!e.currentTarget.src.endsWith(t.respaldo)) e.currentTarget.src = t.respaldo
        }}
        width={800}
        height={500}
        className="aspect-[16/10] w-full object-cover"
      />
      <div className="flex flex-1 flex-col gap-3 p-5">
        <h3 className="font-display text-xl font-bold text-primary">{t.titulo}</h3>
        <p className="text-sm leading-relaxed text-on-surface-variant">{t.descripcion}</p>
        <Link
          to="/servicios/$tratamientoId"
          params={{ tratamientoId: t.id }}
          aria-label={`Más información sobre ${t.titulo}`}
          className="flex-1 text-sm font-bold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
        >
          Más información
          <ArrowRight className="ml-1 inline size-3.5" aria-hidden />
        </Link>
        <a
          href={enlaceWhatsApp(`Hola Villadent, quiero reservar una cita para ${t.titulo}.`)}
          target="_blank"
          rel="noreferrer"
          aria-label={`Reservar cita de ${t.titulo} por WhatsApp`}
          className={buttonVariants({ variant: 'accion', size: 'sm', className: 'mt-1 w-full font-bold' })}
        >
          <MessageCircle className="mr-1.5 size-4" />
          Reservar cita
        </a>
      </div>
    </article>
  )
}
