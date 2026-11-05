import { Link } from '@tanstack/react-router'
import {
  ArrowRight,
  BadgeCheck,
  Calendar,
  Clock,
  HeartPulse,
  MapPin,
  MessageCircle,
  Phone,
  Scan,
  ShieldCheck,
  Sparkles,
  Stethoscope,
} from 'lucide-react'
import { useState } from 'react'
import { Badge, buttonVariants, Card, CardContent } from '@/design-system'
import { IconoDiente } from '@/app/layout/Marca'

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
  destacados: string[]
  color: string
}

const SEDES_OFICIALES: SedeLocal[] = [
  {
    id: 'sede-chilca-1',
    nombre: 'Villadent Chilca',
    negocio: 'VILLADENT',
    tipo: 'Local Chilca',
    direccion: 'Parque de los Héroes',
    referencia: 'Chilca, Parque de los Héroes (Local Chilca)',
    horario: 'Lun - Sáb: 9:00 am - 8:00 pm',
    telefono: '943 672 062',
    whatsapp: '51943672062',
    destacados: ['Ortodoncia & Brackets', 'Odontopediatría', '4 sillones clínicos', 'Ambiente adaptado para niños'],
    color: 'border-primary/30 bg-surface-container-lowest',
  },
  {
    id: 'sede-eltambo',
    nombre: 'Villadent Huancayo Centro',
    negocio: 'VILLADENT',
    tipo: 'Centro de Especialidades',
    direccion: 'Calle Real 450',
    referencia: 'Centro de Huancayo, a pocos metros de la Plaza Constitución',
    horario: 'Lun - Sáb: 9:00 am - 8:00 pm',
    telefono: '943 672 062',
    whatsapp: '51943672062',
    destacados: ['Rehabilitación Oral', 'Estética Dental & Carillas', 'Implantes Dentales', '3 sillones de alta gama'],
    color: 'border-primary/30 bg-surface-container-lowest',
  },
  {
    id: 'sede-villamax-chilca',
    nombre: 'Villamax Chilca',
    negocio: 'VILLAMAX',
    tipo: 'Centro Radiológico',
    direccion: 'Parque de los Héroes',
    referencia: 'Chilca, Parque de los Héroes (Centro Radiológico)',
    horario: 'Lun - Sáb: 8:00 am - 7:30 pm',
    telefono: '960 112 257',
    whatsapp: '51960112257',
    destacados: ['Tomografía Cone Beam 3D', 'Panorámica Digital', 'Sin cita previa ni adelanto', 'Entrega digital inmediata'],
    color: 'border-tertiary/40 bg-surface-container-lowest ring-1 ring-tertiary/20',
  },
]

const ESPECIALIDADES = [
  {
    titulo: 'Ortodoncia & Brackets',
    descripcion: 'Alineación dental con brackets metálicos, cerámicos y autoligados. Diagnóstico cefalométrico computarizado.',
    icono: Sparkles,
    badge: 'Popular',
  },
  {
    titulo: 'Odontopediatría con Calidez',
    descripcion: 'Atención lúdica y sin miedo para niños y adolescentes. Técnicas de manejo de conducta y prevención temprana.',
    icono: HeartPulse,
    badge: 'Familiar',
  },
  {
    titulo: 'Implantes & Rehabilitación',
    descripcion: 'Recupera piezas perdidas con implantes de titanio oseointegrados y coronas de zirconio de alta estética.',
    icono: Stethoscope,
    badge: 'Avanzado',
  },
  {
    titulo: 'Estética Dental & Sonrisa',
    descripcion: 'Diseño de sonrisa, blanqueamiento LED ultrarrápido y carillas de resina y porcelana.',
    icono: IconoDiente,
    badge: 'Estética',
  },
  {
    titulo: 'Tomografía & Rayos X 3D',
    descripcion: 'Centro radiológico Villamax integrado: tomografías volumétricas Cone Beam para cirugías e implantes seguros.',
    icono: Scan,
    badge: 'Villamax 3D',
  },
  {
    titulo: 'Cirugía Bucal & Endodoncia',
    descripcion: 'Extracción de terceras molares retenidas y tratamientos de conducto indoloros con tecnología rotatoria.',
    icono: ShieldCheck,
    badge: 'Precisión',
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
              Red Odontológica · Huancayo & Chilca
            </span>
            <span className="inline-flex items-center gap-1 rounded-pill bg-white/15 px-3 py-1 text-xs font-semibold text-white">
              <Scan className="size-3.5 text-primary-fixed-dim" />
              Centro Radiológico Villamax 3D
            </span>
          </div>

          <h1 className="font-display text-headline-xl font-bold tracking-tight text-white md:text-5xl lg:text-6xl">
            Odontología humana, honesta y de alta precisión
          </h1>

          <p className="max-w-2xl text-body-lg text-primary-fixed/90 md:text-xl">
            Cuidamos tu salud bucal con especialistas de primer nivel, tecnología diagnóstica 3D y la calidez que tu familia
            merece en el Valle del Mantaro.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <Link
              to="/reservar"
              className={buttonVariants({
                variant: 'accion',
                size: 'lg',
                className: 'shadow-accion text-base font-bold',
              })}
            >
              <Calendar className="mr-2 size-5" />
              Reserva tu cita en línea
            </Link>

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

            <Link
              to="/reserva"
              className="inline-flex items-center text-sm font-semibold text-primary-fixed hover:text-white underline-offset-4 hover:underline px-2"
            >
              ¿Ya tienes cita? Consulta tu reserva
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
              <p className="font-display text-2xl font-bold text-white">Yape & Efectivo</p>
              <p className="text-xs text-primary-fixed/80">Adelanto accesible y seguro</p>
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
              Tratamientos odontológicos integrales para niños, jóvenes y adultos. Ortodoncia avanzada, estética dental,
              curaciones estéticas y prótesis fijas con materiales certificados.
            </p>
            <ul className="mt-2 flex flex-col gap-2 text-sm text-on-surface">
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Reserva en línea en 4 sencillos pasos
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Separación de horario con adelanto vía Yape o presencial
              </li>
              <li className="flex items-center gap-2">
                <BadgeCheck className="size-4 text-primary shrink-0" />
                Recordatorio vía WhatsApp 24 horas antes
              </li>
            </ul>
          </div>
          <div className="mt-6 pt-4 border-t border-outline-variant/40">
            <Link to="/reservar" className={buttonVariants({ variant: 'primario', size: 'md', className: 'w-full' })}>
              Agendar en Villadent
            </Link>
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
            Cada tratamiento es planificado minuciosamente por odontólogos certificados, combinando salud biológica y estética.
          </p>
        </div>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {ESPECIALIDADES.map((esp) => {
            const Icono = esp.icono
            return (
              <Card key={esp.titulo} className="flex flex-col justify-between transition-all hover:shadow-raised">
                <CardContent className="flex flex-col gap-3 p-6">
                  <div className="flex items-center justify-between">
                    <span className="flex size-12 items-center justify-center rounded-panel bg-primary/10 text-primary">
                      <Icono className="size-6" />
                    </span>
                    <span className="rounded-pill bg-surface-container-high px-2.5 py-0.5 text-xs font-semibold text-on-surface-variant">
                      {esp.badge}
                    </span>
                  </div>
                  <h3 className="font-display text-xl font-bold text-primary mt-2">{esp.titulo}</h3>
                  <p className="text-sm text-on-surface-variant leading-relaxed">{esp.descripcion}</p>
                </CardContent>
              </Card>
            )
          })}
        </div>

        <div className="flex justify-center pt-2">
          <Link to="/servicios" className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'border border-outline' })}>
            Explorar todos los servicios
            <ArrowRight className="ml-2 size-4" />
          </Link>
        </div>
      </section>

      {/* ─── Sedes Oficiales ─── */}
      <section id="sedes" className="flex flex-col gap-8">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <span className="text-xs font-bold tracking-etiqueta text-secondary uppercase">Presencia regional</span>
            <h2 className="font-display text-headline-lg font-bold text-primary">Nuestras 3 Sedes en Huancayo</h2>
            <p className="text-body-md text-on-surface-variant">
              Instalaciones modernas equipadas con esterilización hospitalaria y sillones ergonómicos.
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
          {sedesFiltradas.map((sede) => (
            <div
              key={sede.id}
              className={`flex flex-col justify-between rounded-card border p-6 transition-all shadow-card hover:shadow-raised ${sede.color}`}
            >
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <Badge tono={sede.negocio === 'VILLADENT' ? 'primario' : 'villamax'}>{sede.tipo}</Badge>
                  {(() => {
                    const est = estadoSede(sede.horario)
                    return (
                      <span className={est.clase}>
                        <Clock className="size-3.5" aria-hidden />
                        {est.etiqueta}
                      </span>
                    )
                  })()}
                </div>

                <h3 className="font-display text-2xl font-bold text-on-surface">{sede.nombre}</h3>

                <div className="flex flex-col gap-2 text-sm text-on-surface-variant mt-1">
                  <p className="flex items-start gap-2">
                    <MapPin className="size-4 text-primary shrink-0 mt-0.5" />
                    <span>
                      <strong className="text-on-surface">{sede.direccion}</strong>
                      <span className="block text-xs">{sede.referencia}</span>
                    </span>
                  </p>
                  <p className="flex items-center gap-2">
                    <Clock className="size-4 text-primary shrink-0" />
                    <span>{sede.horario}</span>
                  </p>
                  <p className="flex items-center gap-2">
                    <Phone className="size-4 text-primary shrink-0" />
                    <span>{sede.telefono}</span>
                  </p>
                </div>

                <div className="mt-2 flex flex-wrap gap-1.5">
                  {sede.destacados.map((item) => (
                    <span
                      key={item}
                      className="rounded-panel bg-surface-container px-2 py-0.5 text-xs font-medium text-on-surface-variant"
                    >
                      {item}
                    </span>
                  ))}
                </div>
              </div>

              <div className="mt-6 flex flex-col gap-2 pt-4 border-t border-outline-variant/40">
                {sede.negocio === 'VILLADENT' ? (
                  <Link
                    to="/reservar"
                    search={{}}
                    className={buttonVariants({ variant: 'primario', size: 'sm', className: 'w-full' })}
                  >
                    Reservar en esta sede
                  </Link>
                ) : (
                  <Link
                    to="/villamax"
                    className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'w-full text-tertiary' })}
                  >
                    Ver estudios de rayos X
                  </Link>
                )}
                <a
                  href={`https://wa.me/${sede.whatsapp}?text=Hola,%20quisiera%20información%20sobre%20la%20sede%20${encodeURIComponent(sede.nombre)}`}
                  target="_blank"
                  rel="noreferrer"
                  className={buttonVariants({ variant: 'fantasma', size: 'sm', className: 'w-full text-xs text-primary' })}
                >
                  <MessageCircle className="mr-1.5 size-3.5" />
                  Contactar por WhatsApp
                </a>
              </div>
            </div>
          ))}
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
          <Link
            to="/reservar"
            className={buttonVariants({ variant: 'accion', size: 'lg', className: 'font-bold shadow-accion' })}
          >
            <Calendar className="mr-2 size-5" />
            Reserva tu cita hoy
          </Link>
          <Link to="/villamax" className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'border border-outline' })}>
            Estudios de rayos X
          </Link>
        </div>
      </section>
    </div>
  )
}
