import { Link, Outlet, useRouterState } from '@tanstack/react-router'
import { Calendar, Clock, MapPin, MessageCircle, Phone, Scan } from 'lucide-react'
import type { ReactNode } from 'react'
import { buttonVariants } from '@/design-system'
import { Marca } from '@/app/layout/Marca'
import { CONTACTO_SEDES, WHATSAPP_VILLADENT, WHATSAPP_VILLAMAX, enlaceWhatsApp, telefonoVisible } from '@/public/contacto'

const claseEnlace =
  'rounded-pill px-3 py-2 font-bold text-on-surface-variant hover:text-primary focus-visible:outline-2 focus-visible:outline-focus aria-[current=page]:text-primary aria-[current=page]:underline underline-offset-4 text-sm'

/** Carcasa oficial de la web pública (Villadent & Villamax). */
export function PublicLayout() {
  // Villamax es otro negocio: en sus páginas el contacto, el botón de WhatsApp y los colores son los suyos.
  const enVillamax = useRouterState({ select: (s) => s.location.pathname.startsWith('/villamax') })
  const negocio = enVillamax ? 'Villamax' : 'Villadent'
  const numeroWhatsApp = enVillamax ? WHATSAPP_VILLAMAX : WHATSAPP_VILLADENT
  return (
    <div className="flex min-h-dvh flex-col bg-surface">
      {/* Header Principal */}
      <header className="sticky top-0 z-40 border-b border-outline-variant/60 bg-surface-container-lowest/95 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-3.5">
          <Link to="/" aria-label="Villadent, ir al inicio" className="rounded-panel focus-visible:outline-2 focus-visible:outline-focus">
            <Marca />
          </Link>
          <nav aria-label="Navegación principal">
            <ul className="flex flex-wrap items-center gap-1">
              <li>
                <Link to="/" activeOptions={{ exact: true }} className={claseEnlace}>
                  Inicio
                </Link>
              </li>
              <li>
                <Link to="/servicios" className={claseEnlace}>
                  Servicios
                </Link>
              </li>
              <li>
                <Link
                  to="/villamax"
                  className="rounded-pill px-3 py-2 font-bold text-tertiary hover:text-tertiary-container focus-visible:outline-2 focus-visible:outline-focus aria-[current=page]:underline underline-offset-4 text-sm flex items-center gap-1"
                >
                  <Scan className="size-3.5" />
                  Villamax 3D
                </Link>
              </li>
              <li>
                <Link to="/" hash="contacto" className={claseEnlace}>
                  Contacto
                </Link>
              </li>
              <li>
                <a
                  href={enlaceWhatsApp(
                    enVillamax ? 'Hola Villamax, quisiera consultar por un estudio radiológico.' : 'Hola Villadent, quiero reservar una cita.',
                    numeroWhatsApp,
                  )}
                  target="_blank"
                  rel="noreferrer"
                  className={buttonVariants({
                    variant: enVillamax ? 'villamax' : 'accion',
                    size: 'sm',
                    className: enVillamax ? 'ml-2 font-bold' : 'ml-2 font-bold shadow-accion',
                  })}
                >
                  <Calendar className="mr-1.5 size-4" />
                  {enVillamax ? 'Consultar por WhatsApp' : 'Reserva tu cita'}
                </a>
              </li>
            </ul>
          </nav>
        </div>
      </header>

      {/* Contenido Principal */}
      <main id="contenido" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 md:py-12">
        <Outlet />
      </main>

      {/* Footer Integral con las 3 Sedes Oficiales */}
      <footer className="border-t border-outline-variant/50 bg-surface-container-low">
        <div className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-10 md:py-12">
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            {/* Columna Marca */}
            <div className="flex flex-col gap-3">
              <Marca />
              <p className="text-xs text-on-surface-variant leading-relaxed mt-1">
                Ecosistema clínico odontológico y centro de imágenes 3D de alta precisión. Huancayo, Región Junín.
              </p>
              <div className="mt-2 text-xs text-on-surface-variant">
                <p>RUC de la empresa en trámite.</p>
                <p className="mt-1">Atención odontológica y radiológica con calidez humana.</p>
              </div>
            </div>

            {/* Columna Sede Chilca (Villadent) */}
            <div className="flex flex-col gap-2 text-xs text-on-surface-variant">
              <p className="font-display font-bold text-sm text-primary">Villadent Chilca</p>
              <p className="flex items-start gap-1.5">
                <MapPin className="size-3.5 text-primary shrink-0 mt-0.5" />
                <span>{CONTACTO_SEDES['sede-chilca-1'].direccion}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Phone className="size-3.5 text-primary shrink-0" />
                <span>{telefonoVisible(WHATSAPP_VILLADENT)}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Clock className="size-3.5 text-primary shrink-0" />
                <span>Lun - Sáb: 9:00 am - 8:00 pm</span>
              </p>
            </div>

            {/* Columna Sede Huancayo Centro (Villadent) */}
            <div className="flex flex-col gap-2 text-xs text-on-surface-variant">
              <p className="font-display font-bold text-sm text-primary">Villadent Huancayo Centro</p>
              <p className="flex items-start gap-1.5">
                <MapPin className="size-3.5 text-primary shrink-0 mt-0.5" />
                <span>{CONTACTO_SEDES['sede-eltambo'].direccion}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Phone className="size-3.5 text-primary shrink-0" />
                <span>{telefonoVisible(WHATSAPP_VILLADENT)}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Clock className="size-3.5 text-primary shrink-0" />
                <span>Lun - Sáb: 9:00 am - 8:00 pm</span>
              </p>
            </div>

            {/* Columna Sede Villamax Chilca */}
            <div className="flex flex-col gap-2 text-xs text-on-surface-variant">
              <p className="font-display font-bold text-sm text-tertiary">Villamax (Centro Radiológico)</p>
              <p className="flex items-start gap-1.5">
                <MapPin className="size-3.5 text-tertiary shrink-0 mt-0.5" />
                <span>{CONTACTO_SEDES['sede-villamax-chilca'].direccion}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Phone className="size-3.5 text-tertiary shrink-0" />
                <span>{telefonoVisible(WHATSAPP_VILLAMAX)}</span>
              </p>
              <p className="flex items-center gap-1.5">
                <Clock className="size-3.5 text-tertiary shrink-0" />
                <span>Lun - Sáb: 8:00 am - 7:30 pm</span>
              </p>
              <p className="text-[11px] font-semibold text-tertiary mt-1">
                Atención sin cita previa ni pago adelantado.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-outline-variant/40 pt-6 text-xs text-on-surface-variant">
            <p>© {new Date().getFullYear()} Villadent · Villamax. Todos los derechos reservados.</p>
            <Link to="/privacidad" className="font-semibold text-primary hover:underline">Política de privacidad</Link>
          </div>
        </div>
      </footer>

      {/* Botón flotante de WhatsApp, siempre a la mano */}
      <a
        href={enlaceWhatsApp(`Hola ${negocio}, quisiera información.`, numeroWhatsApp)}
        target="_blank"
        rel="noreferrer"
        aria-label={`Escribir a ${negocio} por WhatsApp`}
        className="fixed bottom-5 right-5 z-50 flex size-14 items-center justify-center rounded-full bg-[#25D366] text-white shadow-raised transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-focus"
      >
        <MessageCircle className="size-7" aria-hidden />
      </a>
    </div>
  )
}

export function PaginaPublica({ titulo, children }: { titulo: string; children?: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h1 className="font-display text-headline-xl font-bold">{titulo}</h1>
      <p className="text-body-lg text-on-surface-variant">Página en construcción.</p>
      {children}
    </section>
  )
}
