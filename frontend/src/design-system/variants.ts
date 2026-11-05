import { cva, type VariantProps } from 'class-variance-authority'

/** Variantes de botón. Separadas del componente para poder aplicarlas a enlaces (`<Link className={buttonVariants()}>`). */
export const buttonVariants = cva(
  'inline-flex shrink-0 items-center justify-center gap-2 rounded-control font-body font-semibold whitespace-nowrap transition-[background-color,box-shadow,transform,filter] duration-150 select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:scale-[0.98] disabled:pointer-events-none disabled:bg-surface-container disabled:text-on-surface-variant disabled:opacity-60 disabled:shadow-none aria-disabled:pointer-events-none aria-disabled:bg-surface-container aria-disabled:text-on-surface-variant aria-disabled:opacity-60 aria-disabled:shadow-none [&_svg]:size-[1.15em] [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        /** Coral: la acción principal de la pantalla (una por vista). */
        accion: 'bg-acento text-on-acento shadow-sm hover:brightness-105 active:brightness-95',
        /** Verde Villadent: acción primaria de navegación o confirmación. */
        primario: 'bg-acento text-on-acento shadow-sm hover:brightness-105 active:brightness-95',
        /** Tonal: acciones secundarias. */
        secundario: 'bg-surface-container-lowest text-primary border border-outline-variant hover:bg-surface-container-low hover:border-outline shadow-xs',
        /** Índigo: acciones de Villamax Radiología. */
        villamax: 'bg-tertiary text-on-tertiary hover:brightness-110 shadow-sm',
        /** Alerta o cancelación. */
        peligro: 'bg-error-container text-on-error-container hover:brightness-95',
        /** Sin fondo, para barras de herramientas e íconos. */
        fantasma: 'bg-transparent text-on-surface-variant hover:bg-surface-container-high hover:text-primary',
        enlace: 'rounded-none bg-transparent text-primary underline-offset-4 hover:underline',
      },
      size: {
        // En celular y tablet (pantalla táctil) los botones miden al menos 40-44 px para tocarlos con el dedo.
        sm: 'h-8 px-3 text-xs max-md:h-10 pointer-coarse:h-10',
        md: 'h-10 px-4 text-sm max-md:h-11 pointer-coarse:h-11',
        lg: 'h-12 px-6 text-base',
        icono: 'size-10 p-0 max-md:size-11 pointer-coarse:size-11',
      },
    },
    compoundVariants: [{ variant: 'enlace', className: 'h-auto px-0' }],
    defaultVariants: { variant: 'primario', size: 'md' },
  },
)

export type ButtonVariantProps = VariantProps<typeof buttonVariants>

export const badgeVariants = cva(
  'inline-flex w-fit shrink-0 items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap border border-current/10 [&_svg]:size-3.5',
  {
    variants: {
      tono: {
        neutro: 'bg-surface-container-high text-on-surface-variant',
        primario: 'bg-primary-fixed text-on-primary-fixed',
        accion: 'bg-secondary-fixed text-on-secondary-fixed',
        villamax: 'bg-tertiary-fixed text-on-tertiary-fixed',
        peligro: 'bg-error-container text-on-error-container',
        /** Fondo blanco, para chips sobre fondos de color (B0: "Caja Abierta"). */
        claro: 'bg-surface-container-lowest text-primary shadow-xs',
      },
      mayusculas: {
        true: 'text-[0.7rem] tracking-etiqueta uppercase font-bold',
        false: '',
      },
    },
    defaultVariants: { tono: 'neutro', mayusculas: false },
  },
)

export type BadgeVariantProps = VariantProps<typeof badgeVariants>
export type TonoBadge = NonNullable<BadgeVariantProps['tono']>
