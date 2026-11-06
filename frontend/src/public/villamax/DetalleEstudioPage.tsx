import { Link, useParams } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, CheckCircle2, MessageCircle } from 'lucide-react'
import { buttonVariants, EmptyState } from '@/design-system'
import { WHATSAPP_VILLAMAX, enlaceWhatsApp, mensajeEstudio } from '@/public/contacto'
import { buscarEstudio, ESTUDIOS_VILLAMAX } from './estudiosVillamax'

/**
 * Explicación de un estudio de Villamax (`/villamax/<id>`): foto grande, etiqueta, título, introducción y botón de
 * WhatsApp, y debajo el recuadro «Datos importantes» con su nota. Va en los colores de Villamax (índigo), que es otro negocio.
 */
export function DetalleEstudioPage() {
  const { estudioId } = useParams({ strict: false })
  const e = buscarEstudio(estudioId)

  if (!e) {
    return (
      <EmptyState
        titulo="No encontramos ese estudio"
        descripcion="Revisa la lista de estudios de Villamax y elige el que necesitas."
        accion={
          <Link to="/villamax" className={buttonVariants({ variant: 'secundario', size: 'md' })}>
            Ver todos los estudios
          </Link>
        }
      />
    )
  }

  const consultar = enlaceWhatsApp(e.mensaje ?? mensajeEstudio(e.titulo), WHATSAPP_VILLAMAX)
  const otros = ESTUDIOS_VILLAMAX.filter((x) => x.id !== e.id).slice(0, 3)

  return (
    <div className="flex flex-col gap-10 md:gap-14">
      <Link
        to="/villamax"
        className="inline-flex w-fit items-center gap-1.5 text-sm font-bold text-tertiary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Todos los estudios de Villamax
      </Link>

      {/* ─── Foto + presentación ─── */}
      <section className="grid items-center gap-8 md:grid-cols-2 md:gap-12">
        <img
          src={e.imagen}
          alt={`Estudio: ${e.titulo}`}
          onError={(ev) => {
            if (!ev.currentTarget.src.endsWith(e.respaldo)) ev.currentTarget.src = e.respaldo
          }}
          width={800}
          height={500}
          className="aspect-[16/10] w-full rounded-3xl object-cover shadow-raised"
        />
        <div className="flex flex-col items-center gap-4 text-center md:items-start md:text-left">
          <span className="text-sm font-semibold uppercase tracking-etiqueta text-tertiary">{e.categoria}</span>
          <h1 className="font-display text-headline-xl font-bold text-tertiary md:text-5xl">{e.titulo}</h1>
          <p className="max-w-xl text-body-lg text-on-surface-variant">{e.introduccion}</p>
          <a
            href={consultar}
            target="_blank"
            rel="noreferrer"
            className={buttonVariants({ variant: 'villamax', size: 'lg', className: 'mt-2 w-full font-bold sm:w-auto' })}
          >
            <MessageCircle className="mr-2 size-5" aria-hidden />
            Consultar por WhatsApp
            <ArrowRight className="ml-2 size-5" aria-hidden />
          </a>
        </div>
      </section>

      {/* ─── Datos importantes ─── */}
      <section aria-labelledby="datos-importantes" className="mx-auto w-full max-w-3xl rounded-[2rem] bg-tertiary-fixed p-6 md:p-10">
        <h2 id="datos-importantes" className="text-center font-display text-headline-md font-bold text-tertiary">
          Datos importantes
        </h2>
        <ul className="mt-6 flex flex-col gap-4">
          {e.datos.map((dato) => (
            <li key={dato} className="flex items-start gap-3 text-body-md text-on-surface">
              <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-emerald-600" aria-hidden />
              <span>{dato}</span>
            </li>
          ))}
        </ul>
        <hr className="my-6 border-tertiary/20" />
        <p className="text-center text-body-md text-on-surface">{e.nota}</p>
      </section>

      {/* ─── Otros estudios ─── */}
      <section aria-label="Otros estudios" className="flex flex-col gap-4">
        <h2 className="font-display text-xl font-bold text-tertiary">Otros estudios</h2>
        <ul className="grid gap-4 sm:grid-cols-3">
          {otros.map((o) => (
            <li key={o.id}>
              <Link
                to="/villamax/$estudioId"
                params={{ estudioId: o.id }}
                className="flex h-full flex-col gap-1 rounded-2xl border-2 border-tertiary/20 bg-surface-container-lowest p-4 transition-shadow hover:border-tertiary/50 hover:shadow-raised focus-visible:outline-2 focus-visible:outline-focus"
              >
                <span className="text-xs font-semibold uppercase tracking-etiqueta text-on-surface-variant">{o.categoria}</span>
                <span className="font-display text-lg font-bold text-tertiary">{o.titulo}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
