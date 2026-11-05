/**
 * Datos y manejadores MSW del módulo de agenda (B2-B5), con la forma exacta del contrato.
 * Separado de msw.ts para no mezclar pantallas; usa el mismo prefijo y el mismo cuerpo de error.
 */
import { HttpResponse, http } from 'msw'
import type { EstadoCita, Esquema } from '@/shared/api'
import { API, errorBackend } from './msw'

type Cita = Esquema<'CitaResponseDTO'>
type Especialista = Esquema<'EspecialistaResponseDTO'>
type Horario = Esquema<'HorarioEspecialistaResponseDTO'>
type Disponibilidad = Esquema<'DisponibilidadResponseDTO'>
type Paciente = Esquema<'PacienteResponseDTO'>

export const ID_PAUCAR = '3f0c7a52-1d2e-4b8a-9c3d-5e6f7a8b9c0d'
export const ID_ROJAS = '7b1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f'
export const ID_PACIENTE = 'a1b2c3d4-0000-4000-8000-000000000001'

export function cita(extra: Partial<Cita> & { estado?: EstadoCita } = {}): Cita {
  return {
    id: 'c0000000-0000-4000-8000-000000000001',
    pacienteId: ID_PACIENTE,
    pacienteNombre: 'Beatriz Tovar Lazo',
    pacienteTelefono: '964123456',
    doctorId: ID_PAUCAR,
    doctorNombre: 'Dra. Elena Paucar',
    sedeId: 'sede-chilca',
    sillonNumero: 1,
    motivoConsulta: 'Carillas de resina',
    fecha: '2026-10-08',
    hora: '09:00:00',
    duracionMinutos: 30,
    estado: 'PROGRAMADA',
    ...extra,
  }
}

export const ESPECIALISTAS_SEDE: Especialista[] = [
  {
    id: ID_PAUCAR,
    nombre: 'Dra. Elena Paucar',
    especialidad: 'Rehabilitación oral',
    sedes: ['sede-chilca'],
    activo: true,
    sillonesPorSede: { 'sede-chilca': 1 },
  },
  {
    id: ID_ROJAS,
    nombre: 'Dr. Luis Rojas',
    especialidad: 'Ortodoncia',
    sedes: ['sede-chilca', 'sede-centro'],
    activo: true,
    usuarioId: '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a',
    sillonesPorSede: { 'sede-chilca': 2, 'sede-centro': 2 },
  },
]

/** Lunes a sábado de 08:00 a 13:00 en sede-chilca. */
export function horarios(): Horario[] {
  return [1, 2, 3, 4, 5, 6].map((dia) => ({
    id: `h0000000-0000-4000-8000-00000000000${dia}`,
    diaSemana: dia,
    horaInicio: '08:00:00',
    horaFin: '13:00:00',
    sedeId: 'sede-chilca',
  }))
}

export function disponibilidad(extra: Partial<Disponibilidad> = {}): Disponibilidad {
  return {
    especialistaId: ID_PAUCAR,
    especialistaNombre: 'Dra. Elena Paucar',
    fecha: '2026-10-09',
    sedeId: 'sede-chilca',
    atiendeEseDia: true,
    mensaje: 'Turnos disponibles calculados con éxito.',
    slots: [
      { hora: '08:00:00', disponible: true },
      { hora: '08:30:00', disponible: false, motivoNoDisponible: 'OCUPADO' },
      { hora: '09:00:00', disponible: true },
      { hora: '09:30:00', disponible: true },
    ],
    ...extra,
  }
}

export const PACIENTES: Paciente[] = [
  {
    id: ID_PACIENTE,
    dni: '42891023',
    nombres: 'Beatriz',
    apellidos: 'Tovar Lazo',
    nombreCompleto: 'Beatriz Tovar Lazo',
    telefono: '964123456',
    sedeId: 'sede-chilca',
    alergias: ['Penicilina'],
    tieneDeuda: true,
    saldoPendiente: 120,
    edad: 34,
  },
]

type Responder<B = never> = (info: { url: URL; cuerpo: B; id?: string }) => Response | Promise<Response>

const lista = (datos: Cita[]) => HttpResponse.json<Cita[]>(datos)

export const agenda = {
  /** GET /citas: `responder` recibe la URL para mirar fecha/desde/hasta/sedeId. */
  citas: (responder: Responder | Cita[]) =>
    http.get(`${API}/citas`, ({ request }) =>
      Array.isArray(responder) ? lista(responder) : responder({ url: new URL(request.url), cuerpo: undefined as never }),
    ),
  reservasWeb: (datos: Cita[] = []) => http.get(`${API}/citas/reservas-web-pendientes`, () => lista(datos)),
  listaEspera: (datos: Cita[] = []) => http.get(`${API}/citas/lista-espera`, () => lista(datos)),
  especialistas: (datos: Especialista[] = ESPECIALISTAS_SEDE) =>
    http.get(`${API}/especialistas`, () => HttpResponse.json<Especialista[]>(datos)),
  horarios: (datos: Horario[] = horarios()) =>
    http.get(`${API}/especialistas/:id/horarios`, () => HttpResponse.json<Horario[]>(datos)),
  disponibilidad: (responder: Responder | Disponibilidad = disponibilidad()) =>
    http.get(`${API}/especialistas/:id/disponibilidad`, ({ request, params }) =>
      typeof responder === 'function'
        ? responder({ url: new URL(request.url), cuerpo: undefined as never, id: String(params.id) })
        : HttpResponse.json<Disponibilidad>(responder),
    ),
  pacientes: (datos: Paciente[] = PACIENTES) =>
    http.get(`${API}/pacientes`, () => HttpResponse.json<Paciente[]>(datos)),
  paciente: (dato: Paciente = PACIENTES[0]) => http.get(`${API}/pacientes/:id`, () => HttpResponse.json<Paciente>(dato)),
  pacienteError: (status: number, mensaje: string) =>
    http.get(`${API}/pacientes/:id`, () => errorBackend(status, mensaje)),
  crearPaciente: (responder: Responder<Esquema<'PacienteRequestDTO'>>) =>
    http.post<never, Esquema<'PacienteRequestDTO'>>(`${API}/pacientes`, async ({ request }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json() }),
    ),
  cambiarEstado: (responder: Responder<{ estado: EstadoCita; motivo?: string }>) =>
    http.patch<{ id: string }, { estado: EstadoCita; motivo?: string }>(`${API}/citas/:id/estado`, async ({ request, params }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json(), id: params.id }),
    ),
  iniciarAtencion: (responder: Responder<undefined>) =>
    http.post<{ id: string }>(`${API}/citas/:id/iniciar-atencion`, async ({ request, params }) =>
      responder({ url: new URL(request.url), cuerpo: undefined, id: params.id }),
    ),
  cancelar: (responder: Responder<Esquema<'CitaCancelarRequestDTO'>>) =>
    http.patch<{ id: string }, Esquema<'CitaCancelarRequestDTO'>>(`${API}/citas/:id/cancelar`, async ({ request, params }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json(), id: params.id }),
    ),
  reprogramar: (responder: Responder<Esquema<'CitaReprogramarRequestDTO'>>) =>
    http.patch<{ id: string }, Esquema<'CitaReprogramarRequestDTO'>>(
      `${API}/citas/:id/reprogramar`,
      async ({ request, params }) => responder({ url: new URL(request.url), cuerpo: await request.json(), id: params.id }),
    ),
  crearCita: (responder: Responder<Esquema<'CitaRequestDTO'>>) =>
    http.post<never, Esquema<'CitaRequestDTO'>>(`${API}/citas`, async ({ request }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json() }),
    ),
  bandejaTareas: (datos: Partial<Esquema<'BandejaTareasResponseDTO'>> = {}) =>
    http.get(`${API}/citas/bandeja-tareas`, () =>
      HttpResponse.json<Esquema<'BandejaTareasResponseDTO'>>({
        fecha: '2026-10-08',
        sedeId: 'sede-chilca',
        sedeNombre: 'Sede Chilca',
        telefonoWhatsappSede: '943 672 062',
        totalPendientes: 0,
        totalRecordatoriosPendientes: 0,
        totalLlamadasPendientes: 0,
        totalAdelantosPorExpirar: 0,
        totalCumpleanosPendientes: 0,
        tareas: [],
        ...datos,
      }),
    ),
  registrarAdelanto: (responder: Responder<Esquema<'RegistrarAdelantoRequestDTO'>>) =>
    http.post<{ id: string }, Esquema<'RegistrarAdelantoRequestDTO'>>(`${API}/citas/:id/adelanto`, async ({ request, params }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json(), id: params.id }),
    ),
  registrarContacto: (responder: Responder<Esquema<'RegistrarContactoRequestDTO'>>) =>
    http.post<{ id: string }, Esquema<'RegistrarContactoRequestDTO'>>(`${API}/citas/:id/contacto`, async ({ request, params }) =>
      responder({ url: new URL(request.url), cuerpo: await request.json(), id: params.id }),
    ),
  procesarExpiradas: () =>
    http.post(`${API}/citas/procesar-expiradas`, () => HttpResponse.json({ mensaje: 'Expiración procesada', totalExpiradas: 0 })),
}

export const respuestaCita = (c: Cita, status = 200) => HttpResponse.json<Cita>(c, { status })
export const respuestaPaciente = (p: Paciente) => HttpResponse.json<Paciente>(p, { status: 201 })
