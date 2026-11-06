import { Link } from '@tanstack/react-router'
import { ArrowRight, Scan, Sparkles } from 'lucide-react'
import { Badge, buttonVariants } from '@/design-system'
import { ListaTratamientos } from './ListaTratamientos'

/**
 * Pantalla A2 · Tratamientos de Villadent. Muestra las familias de tratamientos (no cada procedimiento del tarifario)
 * con una imagen, una descripción corta y «Reservar cita», que abre WhatsApp con el mensaje ya escrito.
 */
export function ServiciosPage() {
  return (
    <div className="flex flex-col gap-10 md:gap-14">
      {/* ─── Cabecera de Página ─── */}
      <section className="flex flex-col gap-4 text-center items-center">
        <span className="inline-flex items-center gap-1.5 rounded-pill bg-primary-fixed px-3 py-1 text-xs font-bold tracking-etiqueta text-on-primary-fixed uppercase">
          <Sparkles className="size-3.5" />
          Especialistas Certificados
        </span>
        <h1 className="font-display text-headline-xl font-bold text-primary md:text-5xl">
          Servicios Odontológicos Villadent
        </h1>
        <p className="max-w-2xl text-body-lg text-on-surface-variant">
          Cuidamos tu sonrisa con tratamientos para toda la familia. Reserva tu cita por WhatsApp y recibe una orientación
          personalizada según tu evaluación.
        </p>
      </section>

      {/* ─── Tratamientos generales ─── */}
      <ListaTratamientos />

      {/* ─── Banner Villamax Rayos X 3D ─── */}
      <section className="flex flex-col items-center justify-between gap-6 rounded-card border-2 border-tertiary/30 bg-surface-container-lowest p-8 md:flex-row md:p-10 shadow-card">
        <div className="flex flex-col gap-2 max-w-xl">
          <Badge tono="villamax" className="w-fit">
            Centro Radiológico Asociado
          </Badge>
          <h2 className="font-display text-headline-md font-bold text-tertiary">
            ¿Requieres Tomografía 3D o Radiografía Panorámica?
          </h2>
          <p className="text-body-md text-on-surface-variant">
            Nuestro centro especializado <strong>Villamax Chilca</strong> cuenta con tomógrafo de alta definición Cone Beam.
            Atendemos sin cita previa y con entrega digital inmediata.
          </p>
        </div>
        <Link
          to="/villamax"
          className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'text-tertiary font-bold shrink-0' })}
        >
          <Scan className="mr-2 size-5" />
          Ver estudios Villamax
          <ArrowRight className="ml-2 size-4" />
        </Link>
      </section>
    </div>
  )
}
