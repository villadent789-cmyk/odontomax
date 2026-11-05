import { useState, useMemo } from 'react'
import { esOpcion } from '@/shared/opciones'
import {
  Search,
  Plus,
  Boxes,
  Eye,
  ChevronDown,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowRightLeft,
  SlidersHorizontal,
  Edit2,
  Trash2,
} from 'lucide-react'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Skeleton,
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '@/design-system'
import { formatFecha, formatSoles } from '@/shared/format'
import type { ProductoInventarioDTO } from './api-inventario'
import { VencimientoProducto } from './VencimientoProducto'

interface ProductosInventarioTabProps {
  productos: ProductoInventarioDTO[]
  cargando: boolean
  esAdminGeneral: boolean
  sedeSeleccionada?: string
  sedesDisponibles?: Array<{ id: string; nombre: string }>
  onVerProducto: (prod: ProductoInventarioDTO) => void
  onAbrirMovimiento: (prod: ProductoInventarioDTO, tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE') => void
  onAbrirTransferencia: (prod: ProductoInventarioDTO) => void
  onCrearProducto: () => void
  onEditarProducto: (prod: ProductoInventarioDTO) => void
  onDarDeBajaProducto: (prod: ProductoInventarioDTO) => void
}

const ESTADOS_FILTRO = ['TODOS', 'BAJO', 'AGOTADO', 'OPTIMO'] as const
type EstadoFiltro = (typeof ESTADOS_FILTRO)[number]

export function ProductosInventarioTab({
  productos,
  cargando,
  esAdminGeneral,
  sedeSeleccionada,
  onVerProducto,
  onAbrirMovimiento,
  onAbrirTransferencia,
  onCrearProducto,
  onEditarProducto,
  onDarDeBajaProducto,
}: ProductosInventarioTabProps) {
  const [busqueda, setBusqueda] = useState('')
  const [categoriaFiltro, setCategoriaFiltro] = useState<string>('TODAS')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoFiltro>('TODOS')

  // Obtener categorías únicas
  const categorias = useMemo(() => {
    const set = new Set<string>()
    productos.forEach((p) => {
      if (p.categoria) set.add(p.categoria)
    })
    return Array.from(set).sort()
  }, [productos])

  // Filtrado de productos
  const productosFiltrados = useMemo(() => {
    return productos.filter((prod) => {
      // Búsqueda por texto
      const texto = `${prod.codigo} ${prod.nombre} ${prod.categoria || ''}`.toLowerCase()
      if (busqueda && !texto.includes(busqueda.toLowerCase())) {
        return false
      }

      // Filtro de categoría
      if (categoriaFiltro !== 'TODAS' && prod.categoria !== categoriaFiltro) {
        return false
      }

      // Stock de referencia (sede activa o global)
      const stock = sedeSeleccionada
        ? prod.stockPorSede?.[sedeSeleccionada] ?? 0
        : prod.stockTotal

      // Filtro de estado de stock
      if (estadoFiltro === 'AGOTADO' && stock > 0) return false
      if (estadoFiltro === 'BAJO' && (stock <= 0 || stock > prod.stock_minimo)) return false
      if (estadoFiltro === 'OPTIMO' && stock <= prod.stock_minimo) return false

      return true
    })
  }, [productos, busqueda, categoriaFiltro, estadoFiltro, sedeSeleccionada])

  return (
    <div className="flex flex-col gap-4">
      {/* Barra de herramientas y filtros */}
      <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-wrap items-center gap-2">
          {/* Buscador */}
          <div className="relative min-w-[240px] flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-on-surface-variant" />
            <input
              type="text"
              placeholder="Buscar por código, nombre o categoría..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="h-10 w-full rounded-xl border border-outline-variant/60 bg-surface-container-lowest pl-9 pr-4 text-body-sm text-on-surface transition-colors placeholder:text-on-surface-variant focus:border-primary focus:outline-none"
            />
          </div>

          {/* Filtro de Categoría */}
          <select
            value={categoriaFiltro}
            onChange={(e) => setCategoriaFiltro(e.target.value)}
            className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
          >
            <option value="TODAS">Todas las categorías</option>
            {categorias.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>

          {/* Filtro de Nivel de Stock */}
          <select
            value={estadoFiltro}
            onChange={(e) => {
              if (esOpcion(ESTADOS_FILTRO, e.target.value)) setEstadoFiltro(e.target.value)
            }}
            className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
          >
            <option value="TODOS">Todos los niveles</option>
            <option value="OPTIMO">Stock óptimo</option>
            <option value="BAJO">Stock bajo / Alerta</option>
            <option value="AGOTADO">Agotados</option>
          </select>
        </div>

        {esAdminGeneral && (
          <Button
            variant="primario"
            onClick={onCrearProducto}
            className="shrink-0 gap-1.5"
          >
            <Plus className="size-4" />
            Nuevo Insumo
          </Button>
        )}
      </Card>

      {/* Contenido: Tabla de Productos */}
      <Card className="overflow-hidden p-0">
        {cargando ? (
          <div className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
          </div>
        ) : productosFiltrados.length === 0 ? (
          <EmptyState
            icono={<Boxes className="size-8" />}
            titulo="No se encontraron insumos"
            descripcion={
              busqueda || categoriaFiltro !== 'TODAS' || estadoFiltro !== 'TODOS'
                ? 'Intenta ajustar los filtros de búsqueda para ver más resultados.'
                : 'Aún no hay productos registrados en el almacén.'
            }
            accion={
              esAdminGeneral ? (
                <Button variant="primario" onClick={onCrearProducto}>
                  <Plus className="size-4" /> Registrar Primer Insumo
                </Button>
              ) : undefined
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-body-sm">
              <thead className="border-b border-outline-variant/40 bg-surface-container/60 text-label-md font-semibold text-on-surface-variant">
                <tr>
                  <th className="px-4 py-3.5">Código</th>
                  <th className="px-4 py-3.5">Insumo Odontológico</th>
                  <th className="px-4 py-3.5">Categoría</th>
                  <th className="px-4 py-3.5">Presentación</th>
                  <th className="px-4 py-3.5 text-center">
                    {sedeSeleccionada ? 'Stock en Sede' : 'Stock Total'}
                  </th>
                  <th className="px-4 py-3.5">Próximo vencimiento</th>
                  <th className="px-4 py-3.5">Última entrega</th>
                  <th className="px-4 py-3.5">Estado</th>
                  <th className="px-4 py-3.5 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {productosFiltrados.map((prod) => {
                  const stockActual = sedeSeleccionada
                    ? prod.stockPorSede?.[sedeSeleccionada] ?? 0
                    : prod.stockTotal

                  const agotado = stockActual <= 0
                  const stockBajo = !agotado && stockActual <= prod.stock_minimo

                  return (
                    <tr
                      key={prod.id}
                      className="group transition-colors hover:bg-surface-container-high/40"
                    >
                      {/* Código */}
                      <td className="whitespace-nowrap px-4 py-3 font-mono text-label-md font-medium text-primary">
                        {prod.codigo}
                      </td>

                      {/* Nombre del insumo */}
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          onClick={() => onVerProducto(prod)}
                          className="text-left font-semibold text-on-surface hover:text-primary hover:underline focus:outline-none"
                        >
                          {prod.nombre}
                        </button>
                        {prod.precio_unitario != null && (
                          <span className="block text-label-sm text-on-surface-variant">
                            Ref: {formatSoles(prod.precio_unitario)}
                          </span>
                        )}
                      </td>

                      {/* Categoría */}
                      <td className="whitespace-nowrap px-4 py-3 text-on-surface-variant">
                        {prod.categoria || '—'}
                      </td>

                      {/* Presentación / Unidad */}
                      <td className="whitespace-nowrap px-4 py-3 text-on-surface-variant">
                        {prod.unidad || 'Unidad'}
                      </td>

                      {/* Cantidad y Stock Mínimo */}
                      <td className="whitespace-nowrap px-4 py-3 text-center">
                        <div className="flex flex-col items-center">
                          <span className="font-bold text-on-surface text-body-md">
                            {stockActual}
                          </span>
                          <span className="text-label-sm text-on-surface-variant">
                            Mín. {prod.stock_minimo}
                          </span>
                        </div>
                      </td>

                      {/* Próximo vencimiento y última entrega */}
                      <td className="whitespace-nowrap px-4 py-3">
                        <VencimientoProducto fecha={prod.proximoVencimiento} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-on-surface-variant">
                        {formatFecha(prod.ultimaEntrega)}
                      </td>

                      {/* Badge de Estado */}
                      <td className="whitespace-nowrap px-4 py-3">
                        {agotado ? (
                          <Badge tono="peligro" punto className="font-medium">
                            Agotado
                          </Badge>
                        ) : stockBajo ? (
                          <Badge tono="accion" punto className="font-medium">
                            Stock bajo
                          </Badge>
                        ) : (
                          <Badge tono="primario" punto className="font-medium">
                            Óptimo
                          </Badge>
                        )}
                      </td>

                      {/* Acciones */}
                      <td className="whitespace-nowrap px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5" data-testid="acciones-insumo">
                          {/* 1. Botón Ver */}
                          <Button
                            variant="fantasma"
                            size="sm"
                            className="gap-1.5 font-medium"
                            title="Ver ficha y movimientos"
                            onClick={() => onVerProducto(prod)}
                          >
                            <Eye className="size-4" />
                            <span>Ver</span>
                          </Button>

                          {/* 2. Botón Movimiento con menú desplegable */}
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="secundario"
                                size="sm"
                                className="gap-1 font-medium"
                                aria-label={`Movimiento para ${prod.nombre}`}
                              >
                                <span>Movimiento</span>
                                <ChevronDown className="size-3.5 opacity-70" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem
                                onSelect={() => onAbrirMovimiento(prod, 'ENTRADA')}
                                onClick={() => onAbrirMovimiento(prod, 'ENTRADA')}
                              >
                                <ArrowDownLeft className="size-4 text-emerald-600" />
                                <span>Entrada</span>
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onSelect={() => onAbrirMovimiento(prod, 'SALIDA')}
                                onClick={() => onAbrirMovimiento(prod, 'SALIDA')}
                              >
                                <ArrowUpRight className="size-4 text-error" />
                                <span>Salida</span>
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onSelect={() => onAbrirMovimiento(prod, 'AJUSTE')}
                                onClick={() => onAbrirMovimiento(prod, 'AJUSTE')}
                              >
                                <SlidersHorizontal className="size-4 text-amber-600" />
                                <span>Ajuste</span>
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onSelect={() => onAbrirTransferencia(prod)}
                                onClick={() => onAbrirTransferencia(prod)}
                              >
                                <ArrowRightLeft className="size-4 text-primary" />
                                <span>Transferir</span>
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>

                          {/* 3. Menú "⋯" con Editar y Eliminar (solo administrador general) */}
                          {esAdminGeneral && (
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <Button
                                  variant="fantasma"
                                  size="sm"
                                  className="size-8 p-0 font-bold text-on-surface-variant hover:text-on-surface"
                                  aria-label={`Más opciones para ${prod.nombre}`}
                                  title="Más opciones"
                                >
                                  <span aria-hidden="true" className="text-base tracking-widest leading-none">⋯</span>
                                </Button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end">
                                <DropdownMenuItem
                                  onSelect={() => onEditarProducto(prod)}
                                  onClick={() => onEditarProducto(prod)}
                                >
                                  <Edit2 className="size-4" />
                                  <span>Editar</span>
                                </DropdownMenuItem>
                                <DropdownMenuItem
                                  className="text-error focus:text-error data-[highlighted]:text-error"
                                  onSelect={() => onDarDeBajaProducto(prod)}
                                  onClick={() => onDarDeBajaProducto(prod)}
                                >
                                  <Trash2 className="size-4" />
                                  <span>Eliminar</span>
                                </DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
