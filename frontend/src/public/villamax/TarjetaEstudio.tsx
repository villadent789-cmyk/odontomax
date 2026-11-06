import { Link } from '@tanstack/react-router'
import { ArrowRight, MessageCircle } from 'lucide-react'
import { buttonVariants } from '@/design-system'
import { WHATSAPP_VILLAMAX, enlaceWhatsApp, mensajeEstudio } from '@/public/contacto'
import type { EstudioVillamax } from './estudiosVillamax'

/** Tarjeta de un estudio de Villamax: foto, título, descripción y «Consultar por WhatsApp» con el mensaje del estudio. */
export function TarjetaEstudio({ estudio: e }: { estudio: EstudioVillamax }) {
  return (
    <article className="flex flex-col overflow-hidden rounded-3xl border-2 border-tertiary/20 bg-surface-container-lowest shadow-card transition-all hover:border-tertiary/50 hover:shadow-raised">
      <img
        src={e.imagen}
        alt=""
        loading="lazy"
        onError={(ev) => {
          // Aún no hay foto de este estudio: se muestra su ilustración.
          if (!ev.currentTarget.src.endsWith(e.respaldo)) ev.currentTarget.src = e.respaldo
        }}
        width={800}
        height={500}
        className="aspect-[16/10] w-full object-cover"
      />
      <div className="flex flex-1 flex-col gap-3 p-5">
        <h3 className="font-display text-xl font-bold text-tertiary">{e.titulo}</h3>
        <p className="text-sm leading-relaxed text-on-surface-variant">{e.descripcion}</p>
        <Link
          to="/villamax/$estudioId"
          params={{ estudioId: e.id }}
          aria-label={`Más información sobre ${e.titulo}`}
          className="flex-1 text-sm font-bold text-tertiary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
        >
          Más información
          <ArrowRight className="ml-1 inline size-3.5" aria-hidden />
        </Link>
        <a
          href={enlaceWhatsApp(e.mensaje ?? mensajeEstudio(e.titulo), WHATSAPP_VILLAMAX)}
          target="_blank"
          rel="noreferrer"
          aria-label={`Consultar por ${e.titulo} en WhatsApp`}
          className={buttonVariants({ variant: 'villamax', size: 'sm', className: 'mt-1 w-full font-bold' })}
        >
          <MessageCircle className="mr-1.5 size-4" aria-hidden />
          Consultar por WhatsApp
        </a>
      </div>
    </article>
  )
}
