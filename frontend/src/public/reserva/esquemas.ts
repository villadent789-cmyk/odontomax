import { z } from 'zod'
import { hoyEnLima } from '@/shared/format'
import type { DefinicionPaso } from './Pasos'

export const TURNOS = ['MANANA', 'TARDE'] as const
export type Turno = (typeof TURNOS)[number]

export const TEXTO_TURNO: Record<string, string> = { MANANA: 'Mañana', TARDE: 'Tarde' }

export function textoTurno(turno: string | undefined): string {
  if (!turno) return '—'
  return TEXTO_TURNO[turno.toUpperCase()] ?? turno
}

const obligatorio = (mensaje: string) => z.string().trim().min(1, mensaje)

export const NEGOCIOS = ['VILLADENT', 'VILLAMAX'] as const
export type Negocio = (typeof NEGOCIOS)[number]

export const esquemaReserva = z.object({
  negocio: z.enum(NEGOCIOS).default('VILLADENT'),
  sedeId: z
    .string({ error: 'Selecciona una sede.' })
    .min(1, 'Selecciona una sede.')
    .regex(/^sede-[a-z0-9-]{1,60}$/, 'Sede inválida.'),
  doctorId: z.string().optional(),
  doctorNombre: z.string().optional(),
  /** Solo del frontend: el contrato no recibe el servicio, se envía su nombre como `motivoConsulta`. */
  servicioId: z.string({ error: 'Selecciona un servicio.' }).min(1, 'Selecciona un servicio.'),
  fechaPreferencia: z
    .string({ error: 'Elige una fecha.' })
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige una fecha.')
    .refine((f) => f >= hoyEnLima(), 'La fecha no puede ser anterior a hoy.'),
  turno: z.enum(TURNOS, { error: 'Elige el turno de mañana o de tarde.' }),
  horaPreferencia: z.string().optional(),
  /** Campo trampa anti-bots: oculto y siempre vacío para una persona. */
  sitioWeb: z.string().optional(),
  pacienteNuevo: z.boolean().default(false),
  dni: z
    .string()
    .trim()
    .min(1, 'Ingresa tu número de documento.')
    .regex(/^[0-9]{8,12}$/, 'El DNI o documento debe contener entre 8 y 12 dígitos.'),
  nombres: obligatorio('Ingresa tus nombres.'),
  apellidos: obligatorio('Ingresa tus apellidos.'),
  telefono: z
    .string()
    .trim()
    .min(1, 'Ingresa tu celular o WhatsApp.')
    .regex(/^\+?[0-9]{9,15}$/, 'Ingresa un número de teléfono o WhatsApp válido (9 a 15 dígitos, sin espacios).'),
})

/** Lo que contiene el formulario mientras se llena (los campos con valor por defecto pueden faltar). */
export type DatosReserva = z.input<typeof esquemaReserva>
/** Lo que entrega el formulario ya validado. */
export type DatosReservaValidos = z.output<typeof esquemaReserva>

/** Campos que se validan al pulsar "Continuar" en cada paso (el 4.º es la revisión y el envío). */
export const CAMPOS_POR_PASO: (keyof DatosReserva)[][] = [
  ['negocio', 'sedeId', 'servicioId'],
  ['fechaPreferencia', 'turno'],
  ['dni', 'nombres', 'apellidos', 'telefono'],
  [],
]

export const esquemaConsulta = z.object({
  ticket: z
    .string()
    .trim()
    .min(1, 'Ingresa el código de tu reserva.')
    .transform((t) => t.toUpperCase()),
  dni: z
    .string()
    .trim()
    .min(1, 'Ingresa el DNI del paciente.')
    .regex(/^\d{8}$/, 'El DNI debe tener 8 dígitos numéricos.'),
})

export type DatosConsultaEntrada = z.input<typeof esquemaConsulta>
export type DatosConsulta = z.output<typeof esquemaConsulta>

export const PASOS_RESERVA: DefinicionPaso[] = [
  { pregunta: '¿Dónde y qué?', titulo: 'Sede y servicio' },
  { pregunta: '¿Cuándo?', titulo: 'Fecha y turno' },
  { pregunta: '¿Quién asiste?', titulo: 'Tus datos' },
  { pregunta: '¿Todo bien?', titulo: 'Confirmar' },
]
