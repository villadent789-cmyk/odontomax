import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'
import type { PaginaSpring } from '@/shared/api/tipos-manuales'

export type PacienteDTO = Esquema<'PacienteResponseDTO'>
export type PacienteRequestDTO = Esquema<'PacienteRequestDTO'>

export interface FiltrosPacientes {
  busqueda?: string
  sedeId?: string
  page?: number
  size?: number
}

function normalizarSede(sedeId?: string): string | undefined {
  if (!sedeId || sedeId.trim() === '' || sedeId.toUpperCase() === 'ALL') {
    return undefined
  }
  return sedeId
}

export async function listarPacientes(filtros: FiltrosPacientes = {}): Promise<PacienteDTO[]> {
  const params: Record<string, string | number> = {}
  if (filtros.busqueda?.trim()) params.busqueda = filtros.busqueda.trim()
  const s = normalizarSede(filtros.sedeId)
  if (s) params.sedeId = s
  if (filtros.page !== undefined) params.page = filtros.page
  if (filtros.size !== undefined) params.size = filtros.size

  const res = await apiTipada.get('/pacientes', {
    query: params as { busqueda?: string; sedeId?: string },
  })

  // Si se envió paginación en el backend devuelve Page<PacienteResponseDTO>
  if (res && typeof res === 'object' && 'content' in res && Array.isArray((res as PaginaSpring<PacienteDTO>).content)) {
    return (res as PaginaSpring<PacienteDTO>).content
  }
  return Array.isArray(res) ? (res as PacienteDTO[]) : []
}

export async function obtenerPaciente(id: string): Promise<PacienteDTO> {
  return apiTipada.get('/pacientes/{id}', { path: { id } })
}

export async function crearPaciente(cuerpo: PacienteRequestDTO): Promise<PacienteDTO> {
  return apiTipada.post('/pacientes', { cuerpo })
}

export async function actualizarPaciente(id: string, cuerpo: PacienteRequestDTO): Promise<PacienteDTO> {
  return apiTipada.put('/pacientes/{id}', { path: { id }, cuerpo })
}

export async function archivarPaciente(id: string, motivo: string): Promise<{ mensaje: string }> {
  return apiTipada.post('/pacientes/{id}/archivar', {
    path: { id },
    cuerpo: { motivo },
  })
}

export const CLAVES_PACIENTES = {
  todos: ['pacientes'] as const,
  lista: (filtros: FiltrosPacientes) => ['pacientes', 'lista', filtros] as const,
  detalle: (id: string) => ['pacientes', 'detalle', id] as const,
}

export function usePacientesQuery(filtros: FiltrosPacientes = {}) {
  return useQuery({
    queryKey: CLAVES_PACIENTES.lista(filtros),
    queryFn: () => listarPacientes(filtros),
    staleTime: 5_000,
  })
}

export function usePacienteQuery(id?: string) {
  return useQuery({
    queryKey: CLAVES_PACIENTES.detalle(id ?? ''),
    queryFn: () => (id ? obtenerPaciente(id) : Promise.reject('ID requerido')),
    enabled: Boolean(id),
  })
}

/**
 * El paciente recién creado aparece al instante en las listas que ya están en pantalla (las que no filtran por texto y
 * son de su sede o de todas). Después se vuelve a consultar al servidor en segundo plano para traer los datos exactos;
 * el modal no espera esa consulta para cerrarse.
 */
export function useCrearPacienteMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: crearPaciente,
    onSuccess: (creado) => {
      for (const [clave, lista] of qc.getQueriesData<PacienteDTO[]>({ queryKey: ['pacientes', 'lista'] })) {
        const filtros = (clave[2] ?? {}) as FiltrosPacientes
        const sede = normalizarSede(filtros.sedeId)
        if (!Array.isArray(lista) || filtros.busqueda?.trim() || (sede && sede !== creado.sedeId)) continue
        qc.setQueryData<PacienteDTO[]>(clave, [creado, ...lista.filter((p) => p.id !== creado.id)])
      }
      void qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos })
    },
  })
}

export function useActualizarPacienteMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: PacienteRequestDTO }) =>
      actualizarPaciente(id, cuerpo),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos })
      qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.detalle(id) })
    },
  })
}

/** GET /pacientes/{id}/exportar: expediente completo del paciente (derecho de acceso, Ley 29733). Solo administración. */
export function useExportarPaciente() {
  return useMutation({
    mutationFn: (id: string) => apiTipada.get('/pacientes/{id}/exportar', { path: { id } }),
  })
}

/**
 * DELETE /pacientes/{id}: eliminación PERMANENTE (base de datos y archivos). El backend la rechaza con 409 si el paciente
 * ya tiene notas, antecedentes, accesos a la historia o pagos: en ese caso solo se puede archivar. El motivo queda
 * registrado en la auditoría junto con el usuario y el paciente.
 */
export function useEliminarPaciente() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, motivo }: { id: string; motivo: string }) =>
      apiTipada.delete('/pacientes/{id}', { path: { id }, query: { confirmacion: true, motivo } }),
    onSuccess: (_, { id }) => quitarDeLasListas(qc, id),
  })
}

/** Quita al paciente de las listas en pantalla al instante y vuelve a consultar en segundo plano. */
function quitarDeLasListas(qc: ReturnType<typeof useQueryClient>, id: string) {
  qc.setQueriesData<PacienteDTO[]>({ queryKey: ['pacientes', 'lista'] }, (lista) =>
    Array.isArray(lista) ? lista.filter((p) => p.id !== id) : lista,
  )
  void qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos })
}

/** POST /pacientes/{id}/archivar: conserva la historia clínica intacta y guarda el motivo del archivo. */
export function useArchivarPaciente() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, motivo }: { id: string; motivo: string }) => archivarPaciente(id, motivo),
    onSuccess: (_, { id }) => quitarDeLasListas(qc, id),
  })
}
