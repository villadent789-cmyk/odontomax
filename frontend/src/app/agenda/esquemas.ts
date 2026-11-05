/**
 * Validaciones de B5 con las reglas del backend:
 * - CitaRequestDTO: pacienteId, fecha y hora obligatorios (el especialista lo exige el frontend para ofrecer huecos
 *   reales de GET /especialistas/{id}/disponibilidad; ver brechas-api.md B5-1).
 * - CitaReprogramarRequestDTO: nuevaFecha, nuevoHorario y motivo (5+ caracteres, sin máximo).
 * - PacienteRequestDTO: DNI de 8 dígitos, nombres y apellidos obligatorios, celular vacío o 9 dígitos empezando por 9,
 *   fecha de nacimiento no futura.
 */
import { z } from 'zod'
import { hoyEnLima } from '@/shared/format'

const FECHA = /^\d{4}-\d{2}-\d{2}$/
const HORA = /^\d{2}:\d{2}$/

export const DURACIONES = ['30', '45', '60', '90', '120'] as const

const fechaFutura = z
  .string()
  .regex(FECHA, 'Elige la fecha.')
  .refine((f) => f >= hoyEnLima(), 'La fecha no puede ser anterior a hoy.')

export const esquemaNuevaCita = z.object({
  pacienteId: z.string().min(1, 'Busca y selecciona un paciente, o regístralo.'),
  doctorId: z.string().min(1, 'Elige el especialista.'),
  fecha: fechaFutura,
  duracionMinutos: z.enum(DURACIONES),
  hora: z.string().regex(HORA, 'Elige el horario de la cita.'),
  // citas.motivo_consulta es NOT NULL en la base aunque el DTO lo deja opcional: sin él, el backend responde 409.
  motivoConsulta: z.string().trim().min(1, 'Indica el motivo o procedimiento.'),
  notas: z.string().trim(),
  montoAdelantoInicial: z.string().optional(),
  medioPagoAdelanto: z.string().optional(),
  numeroOperacionAdelanto: z.string().optional(),
})
export type DatosNuevaCita = z.infer<typeof esquemaNuevaCita>

export const esquemaReprogramar = z.object({
  nuevaFecha: fechaFutura,
  nuevoHorario: z.string().regex(HORA, 'Elige el nuevo horario.'),
  motivo: z.string().trim().min(5, 'El motivo debe tener al menos 5 caracteres.'),
})
export type DatosReprogramar = z.infer<typeof esquemaReprogramar>

export const esquemaPaciente = z.object({
  dni: z.string().trim().regex(/^\d{8}$/, 'El DNI debe tener exactamente 8 dígitos numéricos.'),
  nombres: z.string().trim().min(1, 'Los nombres son obligatorios.'),
  apellidos: z.string().trim().min(1, 'Los apellidos son obligatorios.'),
  telefono: z
    .string()
    .trim()
    .regex(/^(9\d{8})?$/, 'El celular debe tener 9 dígitos y empezar con 9.'),
  fechaNacimiento: z
    .string()
    .refine((f) => f === '' || (FECHA.test(f) && f <= hoyEnLima()), 'La fecha de nacimiento no puede ser futura.'),
})
export type DatosPaciente = z.infer<typeof esquemaPaciente>
