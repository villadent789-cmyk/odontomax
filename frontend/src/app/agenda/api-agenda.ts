/**
 * Datos del módulo de agenda (B2-B5). Todo sale de la API real (docs/api/openapi.json); no se calcula nada que el
 * backend no devuelva salvo conteos y agrupaciones de las citas ya recibidas.
 */
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Cuerpo, type EstadoCita, type Esquema } from '@/shared/api'

export type Cita = Esquema<'CitaResponseDTO'>
export type Especialista = Esquema<'EspecialistaResponseDTO'>
export type Horario = Esquema<'HorarioEspecialistaResponseDTO'>
export type Disponibilidad = Esquema<'DisponibilidadResponseDTO'>
export type Sede = Esquema<'SedeResponseDTO'>
export type Paciente = Esquema<'PacienteResponseDTO'>
export type CuerpoCita = Esquema<'CitaRequestDTO'>
export type CuerpoReprogramar = Esquema<'CitaReprogramarRequestDTO'>
export type CuerpoPaciente = Esquema<'PacienteRequestDTO'>
export type BandejaTareas = Esquema<'BandejaTareasResponseDTO'>
export type ItemTareaBandeja = Esquema<'ItemTareaBandejaDTO'>
export type CuerpoRegistrarAdelanto = Esquema<'RegistrarAdelantoRequestDTO'>
export type CuerpoRegistrarContacto = Esquema<'RegistrarContactoRequestDTO'>

export function nombrePaciente(p: Paciente): string {
  return p.nombreCompleto || [p.nombres, p.apellidos].filter(Boolean).join(' ') || 'Paciente sin nombre'
}

/** Claves de caché. Todo lo que cuelga de 'citas' se invalida tras cualquier cambio de una cita. */
export const claves = {
  citas: ['citas'] as const,
  citasDia: (sedeId: string | null, fecha: string) => ['citas', 'dia', sedeId, fecha] as const,
  citasRango: (sedeId: string | null, desde: string, hasta: string) => ['citas', 'rango', sedeId, desde, hasta] as const,
  reservasWeb: (sedeId: string | null) => ['citas', 'reservas-web', sedeId] as const,
  listaEspera: (sedeId: string | null) => ['citas', 'lista-espera', sedeId] as const,
  bandejaTareas: (sedeId: string | null, fecha?: string) => ['citas', 'bandeja-tareas', sedeId, fecha] as const,
  especialistas: (sedeId: string | null) => ['especialistas', sedeId] as const,
  horarios: (id: string) => ['especialistas', 'horarios', id] as const,
  disponibilidad: (id: string, fecha: string, sedeId: string | null) => ['disponibilidad', id, fecha, sedeId] as const,
  sedes: ['sedes'] as const,
  pacientes: (sedeId: string | null, busqueda: string) => ['pacientes', sedeId, busqueda] as const,
  paciente: (id: string) => ['pacientes', 'detalle', id] as const,
}

const sede = (sedeId: string | null) => sedeId ?? undefined

export function useCitasDelDia(sedeId: string | null, fecha: string, habilitado = true) {
  return useQuery({
    queryKey: claves.citasDia(sedeId, fecha),
    queryFn: () => apiTipada.get('/citas', { query: { fecha, sedeId: sede(sedeId) } }),
    enabled: habilitado,
    refetchInterval: import.meta.env.MODE === 'test' ? false : 10_000,
  })
}

export function useCitasRango(sedeId: string | null, desde: string, hasta: string, habilitado = true) {
  return useQuery({
    queryKey: claves.citasRango(sedeId, desde, hasta),
    queryFn: () => apiTipada.get('/citas', { query: { desde, hasta, sedeId: sede(sedeId) } }),
    enabled: habilitado,
  })
}

export function useReservasWebPendientes(sedeId: string | null) {
  return useQuery({
    queryKey: claves.reservasWeb(sedeId),
    queryFn: () => apiTipada.get('/citas/reservas-web-pendientes', { query: { sedeId: sede(sedeId) } }),
    refetchInterval: import.meta.env.MODE === 'test' ? false : 15_000,
  })
}

export function useListaEspera(sedeId: string | null) {
  return useQuery({
    queryKey: claves.listaEspera(sedeId),
    queryFn: () => apiTipada.get('/citas/lista-espera', { query: { sedeId: sede(sedeId) } }),
    refetchInterval: import.meta.env.MODE === 'test' ? false : 15_000,
  })
}

export function useEspecialistas(sedeId: string | null, habilitado = true) {
  return useQuery({
    queryKey: claves.especialistas(sedeId),
    queryFn: () => apiTipada.get('/especialistas', { query: { sedeId: sede(sedeId) } }),
    enabled: habilitado,
  })
}

export function useHorarios(id: string | undefined) {
  return useQuery({
    queryKey: claves.horarios(id ?? ''),
    queryFn: () => apiTipada.get('/especialistas/{id}/horarios', { path: { id: id! } }),
    enabled: !!id,
  })
}

export function useDisponibilidad(id: string | undefined, fecha: string | undefined, sedeId: string | null) {
  return useQuery({
    queryKey: claves.disponibilidad(id ?? '', fecha ?? '', sedeId),
    queryFn: () =>
      apiTipada.get('/especialistas/{id}/disponibilidad', {
        path: { id: id! },
        query: { fecha: fecha!, sedeId: sede(sedeId) },
      }),
    enabled: !!id && !!fecha && /^\d{4}-\d{2}-\d{2}$/.test(fecha),
  })
}

/** Horarios de varios especialistas (una petición por especialista, en caché). */
export function useHorariosDe(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: claves.horarios(id),
      queryFn: () => apiTipada.get('/especialistas/{id}/horarios', { path: { id } }),
      staleTime: 10_000,
    })),
  })
}

/** Disponibilidad de varios especialistas para una fecha y sede. */
export function useDisponibilidadDe(ids: string[], fecha: string, sedeId: string | null, habilitado = true) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: claves.disponibilidad(id, fecha, sedeId),
      queryFn: () =>
        apiTipada.get('/especialistas/{id}/disponibilidad', { path: { id }, query: { fecha, sedeId: sede(sedeId) } }),
      enabled: habilitado,
    })),
  })
}

/** GET /sedes es público y solo devuelve sedes activas (de ahí salen los sillones 1..sillones). */
export function useSedesActivas() {
  return useQuery({
    queryKey: claves.sedes,
    queryFn: () => apiTipada.get('/sedes'),
    staleTime: 10_000,
  })
}

/** Sin `page`, GET /pacientes devuelve un arreglo (brechas-api.md A.1). Se acepta también la forma paginada. */
export function usePacientes(sedeId: string | null, busqueda: string) {
  const texto = busqueda.trim()
  return useQuery({
    queryKey: claves.pacientes(sedeId, texto),
    queryFn: async () => {
      const r = await apiTipada.get('/pacientes', { query: { busqueda: texto, sedeId: sede(sedeId) } })
      return Array.isArray(r) ? r : (r?.content ?? [])
    },
    enabled: texto.length >= 3,
  })
}

export function usePaciente(id: string | undefined) {
  return useQuery({
    queryKey: claves.paciente(id ?? ''),
    queryFn: () => apiTipada.get('/pacientes/{id}', { path: { id: id! }, sinRedireccion: true }),
    enabled: !!id,
    retry: false,
  })
}

/* ───────── Mutaciones ───────── */

function useInvalidarCitas() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: claves.citas }),
      qc.invalidateQueries({ queryKey: ['disponibilidad'] }),
    ])
}

export function useCambiarEstado() {
  const invalidar = useInvalidarCitas()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...cuerpo }: { id: string } & Cuerpo<'patch', '/citas/{id}/estado'>) =>
      apiTipada.patch('/citas/{id}/estado', { path: { id }, cuerpo }),
    onSuccess: async (cita) => {
      await Promise.all([
        invalidar(),
        // Atender o marcar inasistencia cambia la "última atención" y las inasistencias del directorio de pacientes.
        qc.invalidateQueries({ queryKey: ['pacientes'] }),
        // Atender una cita que es sesión de un plan suma la sesión en el backend: se refrescan los tratamientos.
        cita?.tratamientoId ? qc.invalidateQueries({ queryKey: ['tratamientos'] }) : undefined,
      ])
    },
  })
}

export function useIniciarAtencion() {
  const invalidar = useInvalidarCitas()
  return useMutation({
    mutationFn: ({ id }: { id: string }) =>
      apiTipada.post('/citas/{id}/iniciar-atencion', { path: { id } }),
    onSuccess: invalidar,
  })
}

export function useCancelarCita() {
  const invalidar = useInvalidarCitas()
  return useMutation({
    mutationFn: ({ id, motivo }: { id: string; motivo: string }) =>
      apiTipada.patch('/citas/{id}/cancelar', { path: { id }, cuerpo: { motivo } }),
    onSuccess: invalidar,
  })
}

export function useReprogramarCita() {
  const invalidar = useInvalidarCitas()
  return useMutation({
    mutationFn: ({ id, ...cuerpo }: { id: string } & CuerpoReprogramar) =>
      apiTipada.patch('/citas/{id}/reprogramar', { path: { id }, cuerpo }),
    onSuccess: invalidar,
  })
}

export function useCrearCita() {
  const invalidar = useInvalidarCitas()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: CuerpoCita) => apiTipada.post('/citas', { cuerpo }),
    onSuccess: async () => {
      await invalidar()
      await qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}

export function useCrearPaciente() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: CuerpoPaciente) => apiTipada.post('/pacientes', { cuerpo }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pacientes'] }),
  })
}

export function useBandejaTareas(sedeId: string | null, fecha?: string, habilitado = true) {
  return useQuery({
    queryKey: claves.bandejaTareas(sedeId, fecha),
    queryFn: () => apiTipada.get('/citas/bandeja-tareas', { query: { sedeId: sede(sedeId), fecha } }),
    enabled: habilitado,
    refetchInterval: import.meta.env.MODE === 'test' ? false : 10_000,
  })
}

export function useRegistrarAdelanto() {
  const invalidar = useInvalidarCitas()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: CuerpoRegistrarAdelanto }) =>
      apiTipada.post('/citas/{id}/adelanto', { path: { id }, cuerpo }),
    onSuccess: () => {
      void invalidar()
      void qc.invalidateQueries({ queryKey: ['pagos'] })
      void qc.invalidateQueries({ queryKey: ['caja'] })
      void qc.invalidateQueries({ queryKey: ['reportes'] })
    },
  })
}

export function useRegistrarContacto() {
  const invalidar = useInvalidarCitas()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: CuerpoRegistrarContacto }) =>
      apiTipada.post('/citas/{id}/contacto', { path: { id }, cuerpo }),
    onSuccess: invalidar,
  })
}

export function useProcesarExpiradas() {
  const invalidar = useInvalidarCitas()
  return useMutation({
    mutationFn: () => apiTipada.post('/citas/procesar-expiradas'),
    onSuccess: invalidar,
  })
}

export type { EstadoCita }
