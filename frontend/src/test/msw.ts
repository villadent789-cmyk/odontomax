/**
 * Red simulada para los tests (MSW). Los manejadores se tipan con los tipos generados del contrato
 * (src/shared/api/schema.d.ts) y con tipos-manuales.ts, así que una respuesta con forma distinta no compila.
 */
import { HttpResponse, delay, http } from 'msw'
import { setupServer } from 'msw/node'
import type { Esquema } from '@/shared/api'
import type { ConsultaReserva, RespuestaEstado2fa, RespuestaMensaje, TicketReserva } from '@/shared/api'

/** Mismo prefijo que usa el cliente en los tests (vite.config.ts → test.env.VITE_API_URL). */
export const API = 'http://localhost:3000/api/v1'

type LoginResponse = Esquema<'LoginResponseDTO'>
type UsuarioDTO = Esquema<'UsuarioDTO'>
type SedeResponse = Esquema<'SedeResponseDTO'>
type ServicioResponse = Esquema<'ServicioResponseDTO'>
type EspecialistaResponse = Esquema<'EspecialistaResponseDTO'>

/** Cuerpo de error de GlobalExceptionHandler (brechas-api.md A.4). */
export function errorBackend(status: number, mensaje: string) {
  const error = { 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 429: 'Too Many Requests' }[status] ?? 'Error'
  return HttpResponse.json(
    { timestamp: '2026-10-07T10:00:00-05:00', status, error, message: mensaje, mensaje },
    { status },
  )
}

/* ───────── Datos de prueba con la forma exacta del contrato ───────── */

export const USUARIO_RECEPCION: UsuarioDTO = {
  id: '0b6f3c1e-2a51-4f0e-9d7a-1c2b3d4e5f60',
  email: 'recepcion.chilca@villadent.pe',
  nombre: 'Katherin Mendoza',
  rol: 'RECEPCION',
  sedeId: 'sede-chilca',
}

export const USUARIO_ADMIN: UsuarioDTO = {
  id: '6a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d',
  email: 'admin@villadent.pe',
  nombre: 'Andrea Quispe',
  rol: 'ADMIN_GENERAL',
}

export const SEDES: SedeResponse[] = [
  { id: 'sede-chilca', nombre: 'Sede Chilca', activa: true, sillones: 4, direccion: 'Av. 9 de Diciembre 482' },
  { id: 'sede-centro', nombre: 'Sede Huancayo Centro', activa: true, sillones: 3, direccion: 'Calle Real 520' },
]

export const SEDE_VILLAMAX: SedeResponse = {
  id: 'sede-villamax-chilca',
  nombre: 'Villamax Chilca (Radiología 3D)',
  activa: true,
  sillones: 1,
  direccion: 'Parque de los Héroes (Centro Radiológico)',
  unidadDeNegocio: 'VILLAMAX',
}

/** Catálogo público (A4). */
export const SERVICIOS: ServicioResponse[] = [
  {
    id: 'srv-limpieza',
    nombre: 'Limpieza & Profilaxis profunda',
    descripcion: 'Ultrasonido, destartraje y pulido con flúor',
    especialidad: 'Odontología general',
    precio: 70,
    duracionMin: 45,
    orden: 2,
    activo: true,
  },
  {
    id: 'srv-evaluacion',
    nombre: 'Evaluación & Diagnóstico',
    descripcion: 'Incluye odontograma digital, cámara intraoral e informe médico inicial.',
    especialidad: 'Odontología general',
    precio: 30,
    duracionMin: 30,
    orden: 1,
    activo: true,
  },
  { id: 'srv-retirado', nombre: 'Servicio retirado', especialidad: 'Otra', duracionMin: 30, orden: 3, activo: false },
]

/** El servidor aún no envía la unidad de negocio de cada servicio; el mock la incluye para las pruebas que filtran por negocio. */
export const ESTUDIOS_VILLAMAX: (ServicioResponse & { unidadDeNegocio?: string })[] = [
  {
    id: 'vmx-panoramica',
    nombre: 'Radiografía Panorámica',
    descripcion: 'Estudio digital panorámico de alta resolución',
    especialidad: 'Radiología',
    precio: 60,
    duracionMin: 15,
    orden: 1,
    activo: true,
    unidadDeNegocio: 'VILLAMAX',
    comisionPorcentaje: 20,
  },
  {
    id: 'vmx-tomografia-3d',
    nombre: 'Tomografía Cone Beam 3D',
    descripcion: 'Tomografía computarizada volumétrica',
    especialidad: 'Radiología',
    precio: 150,
    duracionMin: 20,
    orden: 2,
    activo: true,
    unidadDeNegocio: 'VILLAMAX',
    comisionPorcentaje: 20,
  },
]

export const ESPECIALISTAS: EspecialistaResponse[] = [
  {
    id: '10000000-0000-0000-0000-000000000001',
    nombre: 'Doctor A',
    especialidad: 'Ortodoncia & Ortopedia Maxilar',
    sedes: ['sede-chilca', 'sede-chilca-1'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000002',
    nombre: 'Doctor B',
    especialidad: 'Odontopediatría & Cirugía Bucal',
    sedes: ['sede-chilca', 'sede-chilca-1'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000003',
    nombre: 'Doctor C',
    especialidad: 'Endodoncia & Rehabilitación Oral',
    sedes: ['sede-chilca', 'sede-chilca-1'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000004',
    nombre: 'Doctor D',
    especialidad: 'Periodoncia & Implantología',
    sedes: ['sede-chilca', 'sede-chilca-1'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000005',
    nombre: 'Doctor E',
    especialidad: 'Odontología General & Estética',
    sedes: ['sede-centro', 'sede-eltambo'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000006',
    nombre: 'Doctor F',
    especialidad: 'Cirugía Bucal & Maxilofacial',
    sedes: ['sede-centro', 'sede-eltambo'],
    activo: true,
  },
  {
    id: '10000000-0000-0000-0000-000000000007',
    nombre: 'Doctor Villamax',
    especialidad: 'Radiología Bucal & Maxilofacial',
    sedes: ['sede-villamax-chilca'],
    activo: true,
  },
]

/** 201 de POST /reservas-publicas (TicketReservaDTO). El backend devuelve el id de la sede como `sedeNombre`. */
export function ticketReserva(extra: Partial<TicketReserva> = {}): TicketReserva {
  return {
    codigoTicket: 'OM-7K9M2P4X',
    sedeId: 'sede-chilca',
    sedeNombre: 'sede-chilca',
    fecha: '2026-10-09',
    turno: 'MANANA',
    estado: 'PENDIENTE_WEB',
    especialidad: 'Evaluación & Diagnóstico',
    mensaje: '¡Reserva registrada con éxito! La clínica se comunicará contigo vía WhatsApp para confirmar tu turno exacto.',
    ...extra,
  }
}

/** 200 de POST /reservas-publicas/consulta (ConsultaCitaResponseDTO). */
export function consultaReserva(extra: Partial<ConsultaReserva> = {}): ConsultaReserva {
  return {
    ticket: 'OM-7K9M2P4X',
    estado: 'PENDIENTE_WEB',
    fecha: '2026-10-09',
    turno: 'MANANA',
    sedeId: 'sede-chilca',
    sedeNombre: 'sede-chilca',
    ...extra,
  }
}

/** 429 de la reserva pública (ReservaPublicaController: cuerpo propio, sin Retry-After). */
export function limiteReserva(mensaje: string) {
  return HttpResponse.json({ status: 429, error: 'Too Many Requests', mensaje }, { status: 429 })
}

export function loginOk(usuario: UsuarioDTO, extra: Partial<LoginResponse> = {}): LoginResponse {
  return { token: 'jwt-final', tokenTipo: 'Bearer', expiracionMs: 28_800_000, usuario, ...extra }
}

export function login2faPendiente(usuario: UsuarioDTO): LoginResponse {
  return { token: 'jwt-temporal-2fa', tokenTipo: '2FA_PENDING', expiracionMs: 300_000, usuario }
}

/* ───────── Manejadores ───────── */

export const manejadores = {
  login: (responder: (cuerpo: Esquema<'LoginRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'LoginRequestDTO'>>(`${API}/auth/login`, async ({ request }) => responder(await request.json())),

  login2fa: (responder: (cuerpo: Esquema<'TotpLoginRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'TotpLoginRequestDTO'>>(`${API}/auth/2fa/login`, async ({ request }) =>
      responder(await request.json()),
    ),

  me: (usuario: UsuarioDTO, tokenEsperado = 'jwt-final') =>
    http.get(`${API}/auth/me`, ({ request }) =>
      request.headers.get('Authorization') === `Bearer ${tokenEsperado}`
        ? HttpResponse.json<UsuarioDTO>(usuario)
        : new HttpResponse(null, { status: 401 }),
    ),

  sedes: (sedes: SedeResponse[] = SEDES) =>
    http.get(`${API}/sedes`, ({ request }) => {
      const url = new URL(request.url)
      const negocio = url.searchParams.get('negocio')
      if (negocio === 'VILLAMAX') {
        return HttpResponse.json<SedeResponse[]>([SEDE_VILLAMAX])
      }
      return HttpResponse.json<SedeResponse[]>(sedes)
    }),

  catalogoServicios: (servicios: ServicioResponse[] = SERVICIOS) =>
    http.get(`${API}/reservas-publicas/catalogo/servicios`, ({ request }) => {
      const url = new URL(request.url)
      const negocio = url.searchParams.get('negocio')
      if (negocio === 'VILLAMAX') {
        return HttpResponse.json<ServicioResponse[]>(ESTUDIOS_VILLAMAX)
      }
      return HttpResponse.json<ServicioResponse[]>(servicios)
    }),

  catalogoEspecialistas: (especialistas: EspecialistaResponse[] = ESPECIALISTAS) =>
    http.get(`${API}/reservas-publicas/catalogo/especialistas`, ({ request }) => {
      const url = new URL(request.url)
      const sedeId = url.searchParams.get('sedeId')
      if (sedeId) {
        return HttpResponse.json<EspecialistaResponse[]>(
          especialistas.filter((e) => (e.sedes ?? []).includes(sedeId)),
        )
      }
      return HttpResponse.json<EspecialistaResponse[]>(especialistas)
    }),

  reservar: (responder: (cuerpo: Esquema<'ReservaPublicaRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'ReservaPublicaRequestDTO'>>(`${API}/reservas-publicas`, async ({ request }) =>
      responder(await request.json()),
    ),

  consultar: (responder: (cuerpo: Esquema<'ConsultaReservaRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'ConsultaReservaRequestDTO'>>(`${API}/reservas-publicas/consulta`, async ({ request }) =>
      responder(await request.json()),
    ),

  configurar2fa: (responder: (cuerpo: Esquema<'TotpCodigoRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'TotpCodigoRequestDTO'>>(`${API}/auth/2fa/configurar`, async ({ request }) =>
      responder(await request.json()),
    ),

  verificarActivar: (responder: (cuerpo: Esquema<'TotpVerificarRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'TotpVerificarRequestDTO'>>(`${API}/auth/2fa/verificar-activar`, async ({ request }) =>
      responder(await request.json()),
    ),

  desactivar2fa: (responder: (cuerpo: Esquema<'Desactivar2faRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'Desactivar2faRequestDTO'>>(`${API}/auth/2fa/desactivar`, async ({ request }) =>
      responder(await request.json()),
    ),

  cambiarPassword: (responder: (cuerpo: Esquema<'CambioPasswordRequestDTO'>) => Response | Promise<Response>) =>
    http.post<never, Esquema<'CambioPasswordRequestDTO'>>(`${API}/auth/cambiar-password`, async ({ request }) =>
      responder(await request.json()),
    ),
}

/** Respuestas 2xx tipadas de los endpoints sin esquema (tipos-manuales.ts). */
export const respuestas = {
  mensaje: (mensaje: string) => HttpResponse.json<RespuestaMensaje>({ mensaje }),
  estado2fa: (mensaje: string, activo: boolean) => HttpResponse.json<RespuestaEstado2fa>({ mensaje, activo }),
  ticket: (ticket: TicketReserva = ticketReserva()) => HttpResponse.json<TicketReserva>(ticket, { status: 201 }),
  consulta: (consulta: ConsultaReserva = consultaReserva()) => HttpResponse.json<ConsultaReserva>(consulta),
}

export { HttpResponse, delay, http }

export const servidor = setupServer(
  manejadores.sedes(),
  manejadores.catalogoServicios(),
  manejadores.catalogoEspecialistas(),
  // La pantalla de inicio (/app) consulta citas del día, caja activa y especialistas de la sede: por defecto, vacíos.
  http.get(`${API}/citas`, () => HttpResponse.json([])),
  http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/especialistas`, () => HttpResponse.json([])),
  // Paneles del inicio: reservas web, tareas de la bandeja, stock bajo y (solo administración) reportes del mes.
  http.get(`${API}/citas/reservas-web-pendientes`, () => HttpResponse.json([])),
  http.get(`${API}/citas/lista-espera`, () => HttpResponse.json([])),
  http.get(`${API}/citas/espera-hueco`, () => HttpResponse.json([])),
  http.get(`${API}/citas/bandeja-tareas`, () => HttpResponse.json({ tareas: [] })),
  http.get(`${API}/inventario/productos`, () => HttpResponse.json([])),
  // Al agendar, se consulta si el paciente elegido tiene un tratamiento en curso: por defecto, ninguno.
  http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([])),
  // Catálogo interno de servicios (selector de procedimiento al agendar y al armar tratamientos).
  http.get(`${API}/servicios`, ({ request }) => {
    const url = new URL(request.url)
    const sedeId = url.searchParams.get('sedeId')
    const negocio = url.searchParams.get('negocio')
    const headerNegocio = request.headers.get('X-Negocio')
    if (negocio === 'VILLAMAX' || headerNegocio === 'VILLAMAX' || sedeId === 'sede-villamax-chilca') {
      return HttpResponse.json(ESTUDIOS_VILLAMAX)
    }
    return HttpResponse.json(SERVICIOS)
  }),
  // Endpoints de Radiología Villamax
  http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([])),
  http.get(`${API}/radiologia/derivadores`, () => HttpResponse.json([])),
  http.get(`${API}/radiologia/comisiones/resumen`, () =>
    HttpResponse.json({
      totalMontoPendiente: 0,
      cantidadPendientes: 0,
      totalMontoLiquidado: 0,
      cantidadLiquidados: 0,
      totalDoctoresDerivadoresActivos: 0,
    }),
  ),
  // El directorio de pacientes consulta los tratamientos por sede (botón "Reactivar"): por defecto, ninguno.
  http.get(`${API}/tratamientos/sede/:sedeId`, () => HttpResponse.json([])),
  // Los presupuestos del paciente se piden al abrir su pestaña de tratamientos: por defecto, ninguno.
  http.get(`${API}/presupuestos/paciente/:id`, () => HttpResponse.json([])),
  http.get(`${API}/reportes/financiero`, () =>
    HttpResponse.json({ totalIngresos: 0, totalGastos: 0, balanceNeto: 0, cuentasPorCobrar: 0, tratamientosConSaldoPendiente: 0 }),
  ),
  http.get(`${API}/reportes/pacientes`, () => HttpResponse.json({ totalPacientes: 0, pacientesNuevos: 0 })),
)
