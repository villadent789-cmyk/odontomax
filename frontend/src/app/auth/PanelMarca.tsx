import { BadgeCheck, ScanLine, ShieldPlus } from 'lucide-react'

/** Panel izquierdo del acceso: Villadent como marca principal y Villamax 3D como segunda. Informativo, sin datos de la API. */
export function PanelMarca() {
  return (
    <section
      aria-label="Villadent y Villamax"
      className="relative hidden flex-col gap-8 overflow-hidden rounded-card border border-brand-gold/30 bg-primary p-10 text-on-primary shadow-raised lg:flex"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_15%,rgb(172_132_73/0.28),transparent_55%)]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-28 -right-24 size-80 rounded-full bg-tertiary/25 blur-3xl"
      />

      <div className="relative flex items-center gap-4">
        <img src="/logo-villadent-512.png" alt="Villadent" className="size-24 rounded-full object-contain drop-shadow-xl" />
        <div className="flex flex-col leading-tight">
          <span className="font-display text-2xl font-bold tracking-tight text-white">Villadent</span>
          <span className="text-xs font-semibold tracking-etiqueta text-brand-gold-light uppercase">Red odontológica</span>
        </div>
      </div>

      <div className="relative flex flex-col gap-3">
        <p className="font-display text-headline-lg font-bold leading-tight text-white">Gestión clínica en un solo lugar</p>
        <p className="text-base leading-relaxed text-primary-fixed">
          Agenda, historias clínicas, caja e inventario de todas las sedes. Acceso exclusivo para el personal autorizado.
        </p>
      </div>

      <ul className="relative flex flex-col gap-3">
        <li className="flex items-center gap-3 rounded-panel border border-white/10 bg-white/5 px-4 py-3">
          <BadgeCheck aria-hidden className="size-5 shrink-0 text-brand-gold-light" />
          <span className="flex flex-col leading-tight">
            <span className="font-display text-sm font-bold text-white">Villadent · Odontología</span>
            <span className="text-xs text-primary-fixed">Chilca y Huancayo Centro</span>
          </span>
        </li>
        <li className="flex items-center gap-3 rounded-panel border border-white/10 bg-white/5 px-4 py-3">
          <ScanLine aria-hidden className="size-5 shrink-0 text-tertiary-fixed" />
          <span className="flex flex-col leading-tight">
            <span className="font-display text-sm font-bold text-white">Villamax 3D · Radiología</span>
            <span className="text-xs text-primary-fixed">Tomografía y radiografías en Chilca</span>
          </span>
        </li>
      </ul>

      <p className="relative mt-auto flex items-start gap-3 text-xs leading-relaxed text-primary-fixed">
        <ShieldPlus aria-hidden className="size-5 shrink-0 text-brand-gold-light" />
        Tus datos y los de los pacientes están protegidos conforme a la Ley N.° 29733 de Protección de Datos Personales.
      </p>
    </section>
  )
}
