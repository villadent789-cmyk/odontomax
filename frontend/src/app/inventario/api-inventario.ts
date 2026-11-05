import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

/* ─────────── Modelos DTO ─────────── */

export interface ProductoInventarioDTO {
  id: string
  codigo: string
  nombre: string
  categoria?: string | null
  unidad?: string | null
  precio_unitario?: number | null
  stock_minimo: number
  activo: boolean
  stockPorSede: Record<string, number>
  stockTotal: number
  alertaStockBajo: boolean
  actualizado_en?: string
  proximoVencimiento?: string | null
  ultimaEntrega?: string | null
}

export interface ProductoInventarioCreateRequestDTO {
  codigo?: string
  nombre: string
  categoria?: string
  unidad?: string
  precio_unitario?: number
  stock_minimo: number
}

export interface ProductoInventarioUpdateRequestDTO {
  nombre: string
  categoria?: string
  unidad?: string
  precio_unitario?: number
  stock_minimo: number
  activo?: boolean
}

export interface InventarioMovimientoDTO {
  id: number
  producto_id: string
  producto_nombre: string
  sede_id: string
  tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE' | 'TRANSFERENCIA_ENTRADA' | 'TRANSFERENCIA_SALIDA'
  cantidad: number
  lote?: string | null
  fecha_vencimiento?: string | null
  fecha_entrega?: string | null
  proveedor?: string | null
  motivo?: string | null
  registrado_por?: string | null
  creado_en?: string
}

export interface InventarioMovimientoRequestDTO {
  producto_id: string
  sede_id: string
  tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE'
  cantidad: number
  lote?: string
  fecha_vencimiento?: string
  fecha_entrega?: string
  proveedor?: string
  motivo?: string
}

export type EstadoTransferencia = 'PENDIENTE' | 'EN_TRANSITO' | 'RECIBIDO' | 'CANCELADO'

export interface TransferenciaResponseDTO {
  id: string
  producto_id: string
  producto_nombre: string
  cantidad: number
  sede_origen_id: string
  sede_origen_nombre?: string
  sede_destino_id: string
  sede_destino_nombre?: string
  estado: EstadoTransferencia
  motivo?: string | null
  solicitado_por?: string | null
  usuario_solicita?: string | null
  fecha?: string
  creado_en?: string
  recibido_en?: string | null
}

export interface TransferenciaCreateRequestDTO {
  producto_id: string
  cantidad: number
  sede_origen_id: string
  sede_destino_id: string
  motivo?: string
}

/* ─────────── Consultas HTTP ─────────── */

export async function listarProductos(sedeId?: string, alertaStockBajo?: boolean): Promise<ProductoInventarioDTO[]> {
  const params = new URLSearchParams()
  if (sedeId) params.set('sedeId', sedeId)
  if (alertaStockBajo !== undefined) params.set('alertaStockBajo', String(alertaStockBajo))
  const qs = params.toString()
  return api.get<ProductoInventarioDTO[]>(qs ? `/inventario/productos?${qs}` : '/inventario/productos')
}

export async function obtenerProducto(id: string): Promise<ProductoInventarioDTO> {
  return api.get<ProductoInventarioDTO>(`/inventario/productos/${id}`)
}

export async function crearProducto(dto: ProductoInventarioCreateRequestDTO): Promise<ProductoInventarioDTO> {
  return api.post<ProductoInventarioDTO>('/inventario/productos', dto)
}

export async function actualizarProducto(id: string, dto: ProductoInventarioUpdateRequestDTO): Promise<ProductoInventarioDTO> {
  return api.put<ProductoInventarioDTO>(`/inventario/productos/${id}`, dto)
}

export async function darDeBajaProducto(id: string): Promise<void> {
  return api.delete<void>(`/inventario/productos/${id}`)
}

export async function listarMovimientos(productoId?: string, sedeId?: string): Promise<InventarioMovimientoDTO[]> {
  const params = new URLSearchParams()
  if (productoId) params.set('productoId', productoId)
  if (sedeId) params.set('sedeId', sedeId)
  const qs = params.toString()
  return api.get<InventarioMovimientoDTO[]>(qs ? `/inventario/movimientos?${qs}` : '/inventario/movimientos')
}

export async function registrarMovimiento(dto: InventarioMovimientoRequestDTO): Promise<InventarioMovimientoDTO> {
  return api.post<InventarioMovimientoDTO>('/inventario/movimientos', dto)
}

export async function listarTransferencias(sedeId?: string): Promise<TransferenciaResponseDTO[]> {
  const url = sedeId ? `/inventario/transferencias?sedeId=${encodeURIComponent(sedeId)}` : '/inventario/transferencias'
  return api.get<TransferenciaResponseDTO[]>(url)
}

export async function solicitarTransferencia(dto: TransferenciaCreateRequestDTO): Promise<TransferenciaResponseDTO> {
  return api.post<TransferenciaResponseDTO>('/inventario/transferencias', dto)
}

export async function recibirTransferencia(id: string): Promise<TransferenciaResponseDTO> {
  return api.patch<TransferenciaResponseDTO>(`/inventario/transferencias/${id}/recibir`)
}

export async function cancelarTransferencia(id: string): Promise<TransferenciaResponseDTO> {
  return api.patch<TransferenciaResponseDTO>(`/inventario/transferencias/${id}/cancelar`)
}

/* ─────────── React Query Hooks ─────────── */

export const INVENTARIO_KEYS = {
  productos: (sedeId?: string, alerta?: boolean) => ['inventario', 'productos', { sedeId, alerta }] as const,
  producto: (id: string) => ['inventario', 'producto', id] as const,
  movimientos: (productoId?: string, sedeId?: string) => ['inventario', 'movimientos', { productoId, sedeId }] as const,
  transferencias: (sedeId?: string) => ['inventario', 'transferencias', { sedeId }] as const,
}

export function useProductosInventario(sedeId?: string, alertaStockBajo?: boolean) {
  return useQuery({
    queryKey: INVENTARIO_KEYS.productos(sedeId, alertaStockBajo),
    queryFn: () => listarProductos(sedeId, alertaStockBajo),
  })
}

export function useProductoInventario(id?: string) {
  return useQuery({
    queryKey: id ? INVENTARIO_KEYS.producto(id) : ['inventario', 'producto', 'none'],
    queryFn: () => (id ? obtenerProducto(id) : Promise.reject(new Error('ID requerido'))),
    enabled: Boolean(id),
  })
}

export function useMovimientosInventario(productoId?: string, sedeId?: string) {
  return useQuery({
    queryKey: INVENTARIO_KEYS.movimientos(productoId, sedeId),
    queryFn: () => listarMovimientos(productoId, sedeId),
  })
}

export function useTransferenciasInventario(sedeId?: string) {
  return useQuery({
    queryKey: INVENTARIO_KEYS.transferencias(sedeId),
    queryFn: () => listarTransferencias(sedeId),
  })
}

export function useCrearProductoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (dto: ProductoInventarioCreateRequestDTO) => crearProducto(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
    },
  })
}

export function useActualizarProductoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: ProductoInventarioUpdateRequestDTO }) => actualizarProducto(id, dto),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
      qc.invalidateQueries({ queryKey: INVENTARIO_KEYS.producto(variables.id) })
    },
  })
}

export function useDarDeBajaProductoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => darDeBajaProducto(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
    },
  })
}

export function useRegistrarMovimientoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (dto: InventarioMovimientoRequestDTO) => registrarMovimiento(dto),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
      qc.invalidateQueries({ queryKey: INVENTARIO_KEYS.producto(variables.producto_id) })
      qc.invalidateQueries({ queryKey: ['inventario', 'movimientos'] })
    },
  })
}

export function useSolicitarTransferenciaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (dto: TransferenciaCreateRequestDTO) => solicitarTransferencia(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'transferencias'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'movimientos'] })
    },
  })
}

export function useRecibirTransferenciaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => recibirTransferencia(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'transferencias'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'movimientos'] })
    },
  })
}

export function useCancelarTransferenciaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => cancelarTransferencia(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventario', 'productos'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'transferencias'] })
      qc.invalidateQueries({ queryKey: ['inventario', 'movimientos'] })
    },
  })
}

/* ─────────── Consumo de insumos por procedimiento ─────────── */

export interface RecetaInsumoDTO {
  productoId: string
  productoNombre?: string
  unidad?: string | null
  cantidad: number
}

export interface AlertaConsumoDTO {
  id: string
  citaId: string
  fechaCita?: string
  motivoCita?: string
  sedeId: string
  productoId: string
  productoNombre: string
  cantidadRequerida: number
  cantidadDescontada: number
  faltante: number
}

/** Insumos que consume un servicio de Villadent (solo ADMIN_GENERAL). */
export function useRecetaServicio(servicioId?: string) {
  return useQuery({
    queryKey: ['inventario', 'receta', servicioId] as const,
    queryFn: () => api.get<RecetaInsumoDTO[]>(`/inventario/servicios/${encodeURIComponent(servicioId!)}/insumos`),
    enabled: Boolean(servicioId),
  })
}

export function useGuardarRecetaMutation(servicioId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (lineas: RecetaInsumoDTO[]) =>
      api.put<RecetaInsumoDTO[]>(
        `/inventario/servicios/${encodeURIComponent(servicioId)}/insumos`,
        lineas.map(({ productoId, cantidad }) => ({ productoId, cantidad })),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventario', 'receta', servicioId] }),
  })
}

/** Atenciones que consumieron más insumo del que había en stock y siguen sin resolverse. */
export function useAlertasConsumo(sedeId?: string) {
  return useQuery({
    queryKey: ['inventario', 'alertas-consumo', sedeId] as const,
    queryFn: () => api.get<AlertaConsumoDTO[]>(`/inventario/alertas-consumo?sedeId=${encodeURIComponent(sedeId!)}`),
    enabled: Boolean(sedeId),
  })
}

export function useResolverAlertaConsumoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.post<void>(`/inventario/alertas-consumo/${id}/resolver`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventario', 'alertas-consumo'] }),
  })
}
