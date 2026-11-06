import { Link } from '@tanstack/react-router'
import {
  ArrowRight,
  Clock,
  FileText,
  MapPin,
  MessageCircle,
  Phone,
  Scan,
  ShieldCheck,
  Users,
} from 'lucide-react'
import { Badge, buttonVariants } from '@/design-system'
import { cn } from '@/design-system/cn'
import { WHATSAPP_VILLAMAX, enlaceWhatsApp, telefonoVisible } from '@/public/contacto'
import { ESTUDIOS_VILLAMAX } from './estudiosVillamax'
import { TarjetaEstudio } from './TarjetaEstudio'

/**
 * Pantalla A3 · Villamax Centro Radiológico e Imágenes 3D.
 * Basada en el diseño de Stitch (Villamax Centro Radiológico - Inicio y Estudios)
 * y en las especificaciones oficiales v2 (Sección 9: atiende a todo paciente, pago presencial sin adelanto, informe médico).
 */
export function VillamaxPage() {
  return (
    <div className="flex flex-col gap-14 md:gap-20">
      {/* ─── Hero Diagnóstico Villamax ─── */}
      <section className="relative overflow-hidden rounded-card bg-gradient-to-br from-[#1b266b] via-[#243385] to-[#145374] p-8 text-white shadow-raised md:p-14 lg:p-16">
        <div className="relative z-10 flex max-w-3xl flex-col gap-6">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-pill bg-[#22B8CF]/25 px-3 py-1 text-xs font-bold tracking-etiqueta text-[#abefeb] uppercase">
              <Scan className="size-3.5" />
              Centro de Diagnóstico por Imágenes 3D
            </span>
            <span className="rounded-pill bg-white/10 px-3 py-1 text-xs font-semibold text-white">
              Sede Chilca · Huancayo
            </span>
          </div>

          <h1 className="font-display text-headline-xl font-bold tracking-tight text-white md:text-5xl lg:text-6xl">
            Villamax Centro Radiológico
          </h1>

          <p className="max-w-2xl text-body-lg text-white/90 md:text-xl">
            Tomografía volumétrica Cone Beam 3D, panorámicas digitales y trazados cefalométricos con tecnología de mínima radiación.
            Informes redactados por especialista radiólogo.
          </p>

          {/* Reglas de atención destacadas de la especificación v2 */}
          <div className="grid gap-3 sm:grid-cols-3 pt-2">
            <div className="rounded-panel bg-white/10 p-3 backdrop-blur-sm border border-white/15">
              <p className="text-xs text-[#abefeb] font-bold uppercase tracking-wider">Atención Inmediata</p>
              <p className="text-sm font-semibold text-white mt-1">Sin cita previa</p>
              <p className="text-xs text-white/70">Atendemos por orden de llegada con o sin orden médica.</p>
            </div>
            <div className="rounded-panel bg-white/10 p-3 backdrop-blur-sm border border-white/15">
              <p className="text-xs text-[#abefeb] font-bold uppercase tracking-wider">Sin Pago Adelantado</p>
              <p className="text-sm font-semibold text-white mt-1">Pagas en recepción</p>
              <p className="text-xs text-white/70">Abonas el costo al momento de realizar tu estudio.</p>
            </div>
            <div className="rounded-panel bg-white/10 p-3 backdrop-blur-sm border border-white/15">
              <p className="text-xs text-[#abefeb] font-bold uppercase tracking-wider">Entrega Digital</p>
              <p className="text-sm font-semibold text-white mt-1">Directo a tu médico</p>
              <p className="text-xs text-white/70">Visor seguro en la nube o placas impresas en mano.</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-4">
            <a
              href={enlaceWhatsApp('Hola Villamax, quisiera consultar por un estudio radiológico', WHATSAPP_VILLAMAX)}
              target="_blank"
              rel="noreferrer"
              className={cn(
                buttonVariants({ variant: 'villamax', size: 'lg', className: 'font-bold shadow-sm text-base' }),
                'bg-[#22B8CF] text-[#0b1740]',
              )}
            >
              <MessageCircle className="mr-2 size-5" />
              Consultar por WhatsApp
            </a>

            <a
              href="#estudios"
              className={buttonVariants({
                variant: 'secundario',
                size: 'lg',
                className: 'border border-white/40 bg-white/10 text-white hover:bg-white/20 text-base',
              })}
            >
              Ver estudios disponibles
              <ArrowRight className="ml-2 size-4" />
            </a>
          </div>
        </div>
      </section>

      {/* ─── Catálogo de Estudios 3D ─── */}
      <section id="estudios" className="flex flex-col gap-8">
        <div className="flex flex-col gap-2 text-center items-center">
          <span className="text-xs font-bold tracking-etiqueta text-tertiary uppercase">Precisión Milimétrica</span>
          <h2 className="font-display text-headline-lg font-bold text-tertiary">
            Estudios Radiológicos & Tomografías
          </h2>
          <p className="max-w-2xl text-body-md text-on-surface-variant">
            Equipamiento de última generación calibrado para máxima definición anatómica y mínima dosis de exposición.
          </p>
        </div>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {ESTUDIOS_VILLAMAX.map((estudio) => (
            <TarjetaEstudio key={estudio.id} estudio={estudio} />
          ))}
        </div>
      </section>

      {/* ─── Sección para Odontólogos y Médicos Derivadores ─── */}
      <section className="rounded-card border border-outline-variant/60 bg-surface-container-lowest p-8 md:p-12 shadow-card">
        <div className="grid gap-8 lg:grid-cols-2 lg:items-center">
          <div className="flex flex-col gap-4">
            <Badge tono="neutro" className="w-fit">
              Comunidad Odontológica
            </Badge>
            <h2 className="font-display text-headline-lg font-bold text-primary">
              ¿Eres Odontólogo o Cirujano en Huancayo?
            </h2>
            <p className="text-body-md text-on-surface-variant">
              En Villamax trabajamos de la mano con doctores de toda la región Junín. Recibe los estudios de tus pacientes
              directamente en tu correo o WhatsApp con visor 3D DICOM gratuito y trazados computarizados listos para tu plan de tratamiento.
            </p>
            <ul className="flex flex-col gap-2.5 text-sm text-on-surface">
              <li className="flex items-center gap-2">
                <ShieldCheck className="size-4 text-tertiary shrink-0" />
                Informes radiológicos firmados por especialista en radiología oral y maxilofacial.
              </li>
              <li className="flex items-center gap-2">
                <Users className="size-4 text-tertiary shrink-0" />
                Pacientes derivados reciben atención preferencial y entrega rápida.
              </li>
              <li className="flex items-center gap-2">
                <FileText className="size-4 text-tertiary shrink-0" />
                Órdenes radiológicas digitales o talonarios físicos para tu consultorio.
              </li>
            </ul>
          </div>

          <div className="flex flex-col gap-4 rounded-panel bg-surface-container-low p-6">
            <h3 className="font-display text-xl font-bold text-on-surface">
              Contacto para Profesionales & Derivaciones
            </h3>
            <p className="text-xs text-on-surface-variant">
              Coordina el envío de tus órdenes o solicita talonarios oficiales para tu consultorio:
            </p>
            <div className="flex flex-col gap-3 text-sm">
              <a
                href={enlaceWhatsApp('Estimado equipo Villamax, soy odontólogo y deseo coordinar órdenes radiológicas', WHATSAPP_VILLAMAX)}
                target="_blank"
                rel="noreferrer"
                className={buttonVariants({ variant: 'villamax', size: 'md', className: 'w-full' })}
              >
                <MessageCircle className="mr-2 size-4" />
                Línea Directa para Especialistas
              </a>
              <a
                href="mailto:contacto@villamax.pe"
                className={buttonVariants({ variant: 'secundario', size: 'md', className: 'w-full border border-outline' })}
              >
                <FileText className="mr-2 size-4" />
                contacto@villamax.pe
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Información de la Sede Villamax ─── */}
      <section className="rounded-card border-2 border-tertiary/30 bg-surface-container-lowest p-8 md:p-12 shadow-card">
        <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-col gap-3 max-w-xl">
            <Badge tono="villamax" className="w-fit">
              Ubicación Oficial
            </Badge>
            <h3 className="font-display text-headline-md font-bold text-tertiary">
              Sede Villamax Chilca
            </h3>
            <div className="flex flex-col gap-2 text-sm text-on-surface-variant">
              <p className="flex items-center gap-2">
                <MapPin className="size-4 text-tertiary shrink-0" />
                <span>
                  <strong>Chilca, Parque de los Héroes</strong> (Centro Radiológico, Huancayo)
                </span>
              </p>
              <p className="flex items-center gap-2">
                <Clock className="size-4 text-tertiary shrink-0" />
                <span>Lunes a Sábado: 8:00 a.m. - 7:30 p.m. (Horario corrido)</span>
              </p>
              <p className="flex items-center gap-2">
                <Phone className="size-4 text-tertiary shrink-0" />
                <span>{telefonoVisible(WHATSAPP_VILLAMAX)} · Atención continua</span>
              </p>
            </div>
          </div>

          <div className="flex flex-col gap-3 shrink-0">
            <a
              href={enlaceWhatsApp('Hola, deseo información sobre la ubicación de Villamax Chilca', WHATSAPP_VILLAMAX)}
              target="_blank"
              rel="noreferrer"
              className={buttonVariants({ variant: 'secundario', size: 'lg', className: 'text-tertiary font-bold' })}
            >
              <MessageCircle className="mr-2 size-4" />
              Abrir WhatsApp Villamax
            </a>
            <Link to="/" className={buttonVariants({ variant: 'secundario', size: 'md', className: 'border border-outline' })}>
              Volver al inicio Villadent
            </Link>
          </div>
        </div>
      </section>
    </div>
  )
}
