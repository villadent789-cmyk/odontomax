import { Link, useParams } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, CheckCircle2, MessageCircle } from 'lucide-react'
import { buttonVariants, EmptyState } from '@/design-system'
import { enlaceWhatsApp } from '@/public/contacto'
import { buscarTratamiento, TRATAMIENTOS_GENERALES } from './tratamientosGenerales'

/**
 * Explicación de un tratamiento general (`/servicios/<id>`): foto grande, etiqueta, título, introducción y «Reservar cita»,
 * y debajo el recuadro «Datos importantes» con su nota. Reservar abre WhatsApp con el mensaje ya escrito.
 */
export function DetalleTratamientoPage() {
  const { tratamientoId } = useParams({ strict: false })
  const t = buscarTratamiento(tratamientoId)

  if (!t) {
    return (
      <EmptyState
        titulo="No encontramos ese tratamiento"
        descripcion="Revisa la lista de tratamientos y elige el que te interesa."
        accion={
          <Link to="/servicios" className={buttonVariants({ variant: 'secundario', size: 'md' })}>
            Ver todos los tratamientos
          </Link>
        }
      />
    )
  }

  const reservar = enlaceWhatsApp(`Hola Villadent, quiero reservar una cita para ${t.titulo}.`)
  const otros = TRATAMIENTOS_GENERALES.filter((x) => x.id !== t.id).slice(0, 3)

  return (
    <div className="flex flex-col gap-10 md:gap-14">
      <Link
        to="/servicios"
        className="inline-flex w-fit items-center gap-1.5 text-sm font-bold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Todos los tratamientos
      </Link>

      {/* ─── Foto + presentación ─── */}
      <section className="grid items-center gap-8 md:grid-cols-2 md:gap-12">
        <img
          src={t.imagen}
          alt={`Tratamiento: ${t.titulo}`}
          onError={(e) => {
            if (!e.currentTarget.src.endsWith(t.respaldo)) e.currentTarget.src = t.respaldo
          }}
          width={800}
          height={500}
          className="aspect-[16/10] w-full rounded-3xl object-cover shadow-raised"
        />
        <div className="flex flex-col items-center gap-4 text-center md:items-start md:text-left">
          <span className="text-sm font-semibold uppercase tracking-etiqueta text-on-surface-variant">{t.categoria}</span>
          <h1 className="font-display text-headline-xl font-bold text-primary md:text-5xl">{t.titulo}</h1>
          <p className="max-w-xl text-body-lg text-on-surface-variant">{t.introduccion}</p>
          <a
            href={reservar}
            target="_blank"
            rel="noreferrer"
            className={buttonVariants({ variant: 'accion', size: 'lg', className: 'mt-2 w-full font-bold sm:w-auto' })}
          >
            <MessageCircle className="mr-2 size-5" aria-hidden />
            Reservar cita
            <ArrowRight className="ml-2 size-5" aria-hidden />
          </a>
        </div>
      </section>

      {/* ─── Datos importantes ─── */}
      <section aria-labelledby="datos-importantes" className="mx-auto w-full max-w-3xl rounded-[2rem] bg-primary-fixed/40 p-6 md:p-10">
        <h2 id="datos-importantes" className="text-center font-display text-headline-md font-bold text-primary">
          Datos importantes
        </h2>
        <ul className="mt-6 flex flex-col gap-4">
          {t.datos.map((dato) => (
            <li key={dato} className="flex items-start gap-3 text-body-md text-on-surface">
              <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-emerald-600" aria-hidden />
              <span>{dato}</span>
            </li>
          ))}
        </ul>
        <hr className="my-6 border-outline-variant/60" />
        <p className="text-center text-body-md text-on-surface">{t.nota}</p>
      </section>

      {/* ─── Otros tratamientos ─── */}
      <section aria-label="Otros tratamientos" className="flex flex-col gap-4">
        <h2 className="font-display text-xl font-bold text-primary">Otros tratamientos</h2>
        <ul className="grid gap-4 sm:grid-cols-3">
          {otros.map((o) => (
            <li key={o.id}>
              <Link
                to="/servicios/$tratamientoId"
                params={{ tratamientoId: o.id }}
                className="flex h-full flex-col gap-1 rounded-2xl border border-outline-variant/60 bg-surface-container-lowest p-4 transition-shadow hover:shadow-raised focus-visible:outline-2 focus-visible:outline-focus"
              >
                <span className="text-xs font-semibold uppercase tracking-etiqueta text-on-surface-variant">{o.categoria}</span>
                <span className="font-display text-lg font-bold text-primary">{o.titulo}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
