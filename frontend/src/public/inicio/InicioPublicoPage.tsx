import { Link } from '@tanstack/react-router'
import {
  ArrowRight,
  BadgeCheck,
  Calendar,
  Clock,
  MapPin,
  MessageCircle,
  Scan,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { Badge, buttonVariants } from '@/design-system'
import { IconoDiente } from '@/app/layout/Marca'
import { CONTACTO_SEDES, enlaceMapa, enlaceWhatsApp, telefonoVisible } from '@/public/contacto'
import { ListaTratamientos } from '@/public/servicios/ListaTratamientos'

/** Devuelve el estado operativo de una sede a partir de su string de horario. */
function estadoSede(horario: string): { etiqueta: string; clase: string } {
  const ahora = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/Lima' }))
  const dia = ahora.getDay() // 0=Dom, 6=Sáb
  const hora = ahora.getHours() + ahora.getMinutes() / 60

  // Horario es solo Lun-Sáb (días 1-6)
  if (dia === 0) return { etiqueta: 'Cerrado hoy (Dom)', clase: 'text-xs font-semibold text-red-600 flex items-center gap-1' }

  // Parsear "H:MM am/pm" → decimal hours
  const rango = horario.match(/(\d{1,2}):(\d{2})\s*(am|pm)\s*-\s*(\d{1,2}):(\d{2})\s*(am|pm)/i)
  if (!rango) return { etiqueta: 'Abierto', clase: 'text-xs font-semibold text-emerald-600 flex items-center gap-1' }

  const toH = (h: string, m: string, p: string) => {
    let n = parseInt(h, 10) + parseInt(m, 10) / 60
    if (p.toLowerCase() === 'pm' && parseInt(h, 10) !== 12) n += 12
    if (p.toLowerCase() === 'am' && parseInt(h, 10) === 12) n = parseInt(m, 10) / 60
    return n
  }
  const apertura = toH(rango[1], rango[2], rango[3])
  const cierre = toH(rango[4], rango[5], rango[6])

  if (hora < apertura) return { etiqueta: `Abre a las ${rango[1]}:${rango[2]} ${rango[3]}`, clase: 'text-xs font-semibold text-amber-600 flex items-center gap-1' }
  if (hora >= cierre) return { etiqueta: 'Cerrado', clase: 'text-xs font-semibold text-red-600 flex items-center gap-1' }
  if (hora >= cierre - 1) return { etiqueta: `Cierra a las ${rango[4]}:${rango[5]} ${rango[6]}`, clase: 'text-xs font-semibold text-amber-600 flex items-center gap-1' }
  return { etiqueta: 'Abierto ahora', clase: 'text-xs font-semibold text-emerald-600 flex items-center gap-1' }
}

interface SedeLocal {
  id: string
  nombre: string
  negocio: 'VILLADENT' | 'VILLAMAX'
  tipo: string
  direccion: string
  referencia: string
  horario: string
  telefono: string
  whatsapp: string
  mapa: string
  destacados: string[]
  color: string
}

/** Dirección, horario y contacto de una sede, tal como están en el archivo central de contacto. */
function datosSede(id: keyof typeof CONTACTO_SEDES) {
  const c = CONTACTO_SEDES[id]
  return { direccion: c.direccion, referencia: c.referencia, horario: c.horario, telefono: telefonoVisible(c.whatsapp), whatsapp: c.whatsapp, mapa: c.mapa }
}

const SEDES_OFICIALES: SedeLocal[] = [
  {
    id: 'sede-chilca-1',
    nombre: 'Villadent Chilca',
    negocio: 'VILLADENT',
    tipo: 'Local Chilca',
    ...datosSede('sede-chilca-1'),
    destacados: ['Ortodoncia & Brackets', 'Odontopediatría', '4 sillones clínicos', 'Ambiente adaptado para niños'],
    color: 'border-primary/30 bg-surface-container-lowest',
  },
  {
    id: 'sede-eltambo',
    nombre: 'Villadent Huancayo Centro',
    negocio: 'VILLADENT',
    tipo: 'Centro de Especialidades',
    ...datosSede('sede-eltambo'),
    destacados: ['Rehabilitación Oral', 'Estética Dental & Carillas', 'Implantes Dentales', '3 sillones de alta gama'],
    color: 'border-primary/30 bg-surface-container-lowest',
  },
  {
    id: 'sede-villamax-chilca',
    nombre: 'Villamax Chilca',
    negocio: 'VILLAMAX',
    tipo: 'Centro Radiológico',
    ...datosSede('sede-villamax-chilca'),
    destacados: ['Tomografía Cone Beam 3D', 'Panorámica Digital', 'Sin cita previa ni adelanto', 'Entrega digital inmediata'],
    color: 'border-tertiary/40 bg-surface-container-lowest ring-1 ring-tertiary/20',
  },
]

/**
 * Pantalla A1 · Landing Page Oficial de Villadent & Villamax.
 * Basada en el diseño de Stitch (Villadent Digital Experience Design).
 */
export function InicioPublicoPage() {
  const [filtroSede, setFiltroSede] = useState<'TODAS' | 'VILLADENT' | 'VILLAMAX'>('TODAS')

  const sedesFiltradas = SEDES_OFICIALES.filter((s) => {
    if (filtroSede === 'TODAS') return true
    return s.negocio === filtroSede
  })

  return (
    <div className="flex flex-col gap-16 md:gap-24">
      {/* ─── Hero Section ─── */}
      <section className="relative overflow-hidden rounded-card bg-gradient-to-br from-primary to-primary-container p-8 text-on-primary shadow-raised md:p-14 lg:p-16">
        <div className="relative z-10 flex max-w-3xl flex-col gap-6">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-pill bg-primary-fixed px-3 py-1 text-xs font-bold tracking-etiqueta text-on-primary-fixed uppercase">
              <Sparkles className="size-3.5" />
              Villadent + Villamax 3D
            </span>
            <span className="inline-flex items-center gap-1 rounded-pill bg-white/15 px-3 py-1 text-xs font-semibold text-white">
              <Scan className="size-3.5 text-primary-fixed-dim" />
              Huancayo · Chilca
            </span>
          </div>

          <h1 className="font-display text-headline-xl font-bold tracking-tight text-white md:text-5xl lg:text-6xl">
            Sonrisas saludables, confianza para toda la vida.
          </h1>

          <p className="font-display text-2xl font-semibold text-acento md:text-3xl">
            Tu salud dental está en manos de especialistas.
          </p>

          <p className="max-w-2xl text-body-lg text-primary-fixed/90 md:text-xl">
            Te escuchamos, te orientamos y cuidamos de tu sonrisa con tratamientos personalizados, profesionales
            especializados y tecnología de diagnóstico 3D.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <a
              href={enlaceWhatsApp('Hola Villadent, quiero reservar una cita.')}
              target="_blank"
              rel="noreferrer"
              className={buttonVariants({
                variant: 'accion',
                size: 'lg',
                className: 'shadow-accion text-base font-bold',
              })}
            >
              <Calendar className="mr-2 size-5" />
              Agenda tu cita por WhatsApp
            </a>

            <Link
              to="/servicios"
              className={buttonVariants({
                variant: 'secundario',
                size: 'lg',
                className: 'border border-white/40 bg-white/10 text-white hover:bg-white/20 text-base',
              })}
            >
              Ver catálogo de servicios
              <ArrowRight className="ml-2 size-4" />
            </Link>
          </div>

          <div className="grid grid-cols-2 gap-4 border-t border-white/20 pt-6 sm:grid-cols-3">
            <div>
              <p className="font-display text-2xl font-bold text-white">2 Sedes</p>
              <p className="text-xs text-primary-fixed/80">Clínicas Villadent en Chilca y Centro</p>
            </div>
            <div>
              <p className="font-display text-2xl font-bold text-white">Centro 3D</p>
              <p className="text-xs text-primary-fixed/80">Tomografía Cone Beam en Villamax</p>
            </div>
            <div className="col-span-2 sm:col-span-1">
              <p className="font-display text-2xl font-bold text-white">Paga como prefieras</p>
              <p className="text-xs text-primary-fixed/80">Efectivo, Yape, Plin o tarjeta en recepción</p>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Ecosistema Dual: Villadent & Villamax ─── */}
      <section className="grid gap-6 md:grid-cols-2">
        <div className="flex flex-col justify-between rounded-card border border-outline-variant/60 bg-surface-container-lowest p-6 shadow-card md:p-8">
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="flex size-10 items-center justify-center rounded-panel bg-primary/10 text-primary">
                <IconoDiente className="size-6" />
              </span>
              <Badge tono="primario">Clínica Dental</Badge>
            </div>
            <h2 className="font-display text-headline-md font-bold text-primary">Villadent Odontología</h2>
            <p className="text-body-md text-on-surface-variant">
              Atención odontológica para niños, jóvenes y adultos: prevención, restauraciones, endodoncia, prótesis,
              implantes, ortodoncia y estética dental.
            </p>
            <ul className="mt-2 flex flex-col gap-2 text-sm text-on-surface">
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Reserva tu cita por WhatsApp
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Te confirmamos el día y la hora por el mismo medio
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Recordatorio por WhatsApp antes de tu cita
              </li>
            </ul>
          </div>
          <div className="mt-6 pt-4 border-t border-outline-variant/40">
            <a
              href={enlaceWhatsApp('Hola Villadent, quiero reservar una cita.')}
              target="_blank"
              rel="noreferrer"
              className={buttonVariants({ variant: 'primario', size: 'md', className: 'w-full' })}
            >
              Agendar en Villadent
            </a>
          </div>
        </div>

        <div className="flex flex-col justify-between rounded-card border-2 border-tertiary/30 bg-surface-container-lowest p-6 shadow-card md:p-8">
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="flex size-10 items-center justify-center rounded-panel bg-tertiary/10 text-tertiary">
                <Scan className="size-6" />
              </span>
              <Badge tono="villamax">Centro Radiológico 3D</Badge>
            </div>
            <h2 className="font-display text-headline-md font-bold text-tertiary">Villamax Radiología</h2>
            <p className="text-body-md text-on-surface-variant">
              Centro especializado en imágenes dentales y maxilofaciales. Tomografías Cone Beam 3D de alta definición,
              panorámicas digitales y trazados cefalométricos para cualquier paciente o especialista.
            </p>
            <ul className="mt-2 flex flex-col gap-2 text-sm text-on-surface">
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-tertiary shrink-0" />
                Atención rápida: sin cita previa ni adelanto
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-tertiary shrink-0" />
                Pago al momento de la toma del estudio
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-tertiary shrink-0" />
                Entrega digital inmediata para tu doctor
              </li>
            </ul>
          </div>
          <div className="mt-6 pt-4 border-t border-outline-variant/40">
            <Link to="/villamax" className={buttonVariants({ variant: 'secundario', size: 'md', className: 'w-full text-tertiary' })}>
              Conoce los estudios Villamax
            </Link>
          </div>
        </div>
      </section>

      {/* ─── Especialidades Clínicas ─── */}
      <section className="flex flex-col gap-8">
        <div className="flex flex-col gap-2 text-center md:text-left">
          <span className="text-xs font-bold tracking-etiqueta text-secondary uppercase">Tratamientos a tu medida</span>
          <h2 className="font-display text-headline-lg font-bold text-primary">Nuestras Especialidades Odontológicas</h2>
          <p className="text-body-md text-on-surface-variant max-w-2xl">
            Cada tratamiento es planificado por odontólogos certificados, combinando salud y estética. Reserva tu cita por WhatsApp.
          </p>
        </div>

        <ListaTratamientos />

        <div className="flex justify-center pt-2">
          <Link to="/servicios" className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'border border-outline' })}>
            Explorar todos los servicios
            <ArrowRight className="ml-2 size-4" />
          </Link>
        </div>
      </section>

      {/* ─── Contáctenos / Sedes ─── */}
      <section id="contacto" className="scroll-mt-28 flex flex-col gap-8 rounded-[2rem] bg-surface-container-lowest p-6 shadow-card md:p-10">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <span className="text-xs font-bold tracking-etiqueta text-secondary uppercase">Presencia regional</span>
            <h2 className="font-display text-headline-lg font-bold text-primary">Contáctenos</h2>
            <p className="text-body-md text-on-surface-variant max-w-2xl">
              Si tiene alguna pregunta, escríbanos por WhatsApp o visítenos directamente en cualquiera de nuestras 3 sedes en
              Huancayo.
            </p>
          </div>

          <div className="flex items-center gap-1 rounded-pill bg-surface-container-high p-1">
            <button
              type="button"
              onClick={() => setFiltroSede('TODAS')}
              className={`rounded-pill px-3 py-1.5 text-xs font-bold transition-all ${
                filtroSede === 'TODAS' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-on-surface-variant'
              }`}
            >
              Todas
            </button>
            <button
              type="button"
              onClick={() => setFiltroSede('VILLADENT')}
              className={`rounded-pill px-3 py-1.5 text-xs font-bold transition-all ${
                filtroSede === 'VILLADENT' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-on-surface-variant'
              }`}
            >
              Villadent (Dental)
            </button>
            <button
              type="button"
              onClick={() => setFiltroSede('VILLAMAX')}
              className={`rounded-pill px-3 py-1.5 text-xs font-bold transition-all ${
                filtroSede === 'VILLAMAX' ? 'bg-surface-container-lowest text-tertiary shadow-sm' : 'text-on-surface-variant'
              }`}
            >
              Villamax (Rayos X)
            </button>
          </div>
        </div>

        <div className="grid gap-6 md:grid-cols-3">
          {sedesFiltradas.map((sede) => {
            const est = estadoSede(sede.horario)
            const acento = sede.negocio === 'VILLADENT' ? 'text-primary' : 'text-tertiary'
            return (
              <article
                key={sede.id}
                className={`flex flex-col gap-4 rounded-3xl border p-5 shadow-card transition-all hover:shadow-raised ${sede.color}`}
              >
                <div className="flex items-center justify-between">
                  <Badge tono={sede.negocio === 'VILLADENT' ? 'primario' : 'villamax'}>{sede.tipo}</Badge>
                  <span className={est.clase}>
                    <Clock className="size-3.5" aria-hidden />
                    {est.etiqueta}
                  </span>
                </div>

                <h3 className="font-display text-2xl font-bold text-on-surface">{sede.nombre}</h3>

                <div className="rounded-2xl bg-surface-container-low p-4">
                  <p className="text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">Información de contacto</p>
                  <ul className="mt-3 flex flex-col gap-3 text-sm text-on-surface-variant">
                    <li className="flex items-start gap-2.5">
                      <MessageCircle className={`mt-0.5 size-4 shrink-0 ${acento}`} aria-hidden />
                      <span>
                        <strong className="block text-on-surface">WhatsApp</strong>
                        {sede.telefono}
                      </span>
                    </li>
                    <li className="flex items-start gap-2.5">
                      <MapPin className={`mt-0.5 size-4 shrink-0 ${acento}`} aria-hidden />
                      <span>
                        <strong className="block text-on-surface">Dirección</strong>
                        {sede.direccion}
                        <span className="block text-xs">{sede.referencia}</span>
                      </span>
                    </li>
                  </ul>
                </div>

                <div className="rounded-2xl bg-surface-container-low p-4">
                  <p className="text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">Horario de atención</p>
                  <p className="mt-2 flex items-center gap-2 text-sm text-on-surface">
                    <Clock className={`size-4 shrink-0 ${acento}`} aria-hidden />
                    {sede.horario}
                  </p>
                </div>

                <div className="mt-auto grid gap-2 pt-1">
                  <a
                    href={enlaceMapa(sede.mapa)}
                    target="_blank"
                    rel="noreferrer"
                    className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'w-full' })}
                  >
                    <MapPin className="mr-1.5 size-4" />
                    Cómo llegar
                  </a>
                  <a
                    href={enlaceWhatsApp(`Hola, quisiera información sobre la sede ${sede.nombre}.`, sede.whatsapp)}
                    target="_blank"
                    rel="noreferrer"
                    className={buttonVariants({ variant: 'accion', size: 'sm', className: 'w-full font-bold' })}
                  >
                    <MessageCircle className="mr-1.5 size-4" />
                    Escribir por WhatsApp
                  </a>
                </div>
              </article>
            )
          })}
        </div>
      </section>

      {/* ─── Call to Action Final ─── */}
      <section className="flex flex-col items-center justify-between gap-6 rounded-card border-2 border-secondary/30 bg-surface-container-lowest p-8 text-center md:flex-row md:text-left md:p-12 shadow-card">
        <div className="flex flex-col gap-2 max-w-xl">
          <Badge tono="accion" className="w-fit self-center md:self-start">
            Atención Inmediata
          </Badge>
          <h2 className="font-display text-headline-md font-bold text-primary md:text-headline-lg">
            ¿Listo para sonreír con total confianza?
          </h2>
          <p className="text-body-md text-on-surface-variant">
            Separa tu cita en menos de 2 minutos. Elige sede, especialidad, horario y confirma tu turno de manera segura.
          </p>
        </div>
        <div className="flex flex-col sm:flex-row gap-3">
          <a
            href={enlaceWhatsApp('Hola Villadent, quiero reservar una cita.')}
            target="_blank"
            rel="noreferrer"
            className={buttonVariants({ variant: 'accion', size: 'lg', className: 'font-bold shadow-accion' })}
          >
            <Calendar className="mr-2 size-5" />
            Reserva tu cita hoy
          </a>
          <Link to="/villamax" className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'border border-outline' })}>
            Estudios de rayos X
          </Link>
        </div>
      </section>
    </div>
  )
}
