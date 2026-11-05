import {
  AlertTriangle,
  ArrowRightLeft,
  Boxes,
  Package,
  ArrowDownLeft,
  ArrowUpRight,
  Clock,
} from 'lucide-react'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { Skeleton } from '@/design-system/components/Skeleton'
import { formatFechaHora } from '@/shared/format'
import type { ProductoInventarioDTO, InventarioMovimientoDTO, TransferenciaResponseDTO } from './api-inventario'

interface DashboardInventarioProps {
  productos: ProductoInventarioDTO[]
  cargandoProductos: boolean
  movimientos: InventarioMovimientoDTO[]
  cargandoMovimientos: boolean
  transferencias: TransferenciaResponseDTO[]
  cargandoTransferencias: boolean
  sedeSeleccionada?: string
  onVerProducto: (prod: ProductoInventarioDTO) => void
  onAbrirMovimiento: (prod?: ProductoInventarioDTO, tipo?: 'ENTRADA' | 'SALIDA' | 'AJUSTE') => void
  onAbrirTransferencia: (prod?: ProductoInventarioDTO) => void
  onIrATab: (tab: 'productos' | 'movimientos' | 'transferencias') => void
}

export function DashboardInventario({
  productos,
  cargandoProductos,
  movimientos,
  cargandoMovimientos,
  transferencias,
  cargandoTransferencias,
  sedeSeleccionada,
  onVerProducto,
  onAbrirMovimiento,
  onAbrirTransferencia,
  onIrATab,
}: DashboardInventarioProps) {
  // Cálculos de métricas
  const totalProductos = productos.length

  const productosStockBajo = productos.filter((p) => {
    if (sedeSeleccionada) {
      const stockSede = p.stockPorSede?.[sedeSeleccionada] ?? 0
      return stockSede > 0 && stockSede <= p.stock_minimo
    }
    return p.stockTotal > 0 && p.stockTotal <= p.stock_minimo
  })

  const productosAgotados = productos.filter((p) => {
    if (sedeSeleccionada) {
      const stockSede = p.stockPorSede?.[sedeSeleccionada] ?? 0
      return stockSede <= 0
    }
    return p.stockTotal <= 0
  })

  const transferenciasPendientes = transferencias.filter(
    (t) => t.estado === 'EN_TRANSITO' || t.estado === 'PENDIENTE'
  )

  const ultimosMovimientos = movimientos.slice(0, 6)

  return (
    <div className="flex flex-col gap-6">
      {/* 4 Indicadores principales (KPIs) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          etiqueta="Total de Insumos"
          valor={totalProductos}
          complemento={sedeSeleccionada ? 'Catálogo activo' : 'Todas las sedes'}
          icono={<Boxes />}
          tonoIcono="primario"
          cargando={cargandoProductos}
        />

        <StatCard
          etiqueta="Stock Bajo"
          valor={productosStockBajo.length}
          complemento="Por debajo del mínimo"
          insignia={
            productosStockBajo.length > 0 ? (
              <Badge tono="accion" punto>
                Atención requerida
              </Badge>
            ) : undefined
          }
          icono={<AlertTriangle />}
          tonoIcono="accion"
          cargando={cargandoProductos}
        />

        <StatCard
          etiqueta="Insumos Agotados"
          valor={productosAgotados.length}
          complemento="Sin existencias"
          insignia={
            productosAgotados.length > 0 ? (
              <Badge tono="peligro" punto>
                Reposición urgente
              </Badge>
            ) : undefined
          }
          icono={<Package />}
          tonoIcono="villamax"
          cargando={cargandoProductos}
        />

        <StatCard
          etiqueta="Traslados Activos"
          valor={transferenciasPendientes.length}
          complemento="En tránsito entre sedes"
          icono={<ArrowRightLeft />}
          tonoIcono="villamax"
          cargando={cargandoTransferencias}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Panel izquierdo: Alertas de Stock Clínico */}
        <div className="flex flex-col gap-4 lg:col-span-7">
          <Card className="flex flex-col gap-4 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="flex size-9 items-center justify-center rounded-xl bg-secondary-fixed text-on-secondary-fixed">
                  <AlertTriangle className="size-5" />
                </div>
                <div>
                  <h3 className="font-display text-title-md font-bold text-primary">
                    Alertas de Reabastecimiento
                  </h3>
                  <p className="text-body-sm text-on-surface-variant">
                    Insumos odontológicos y radiológicos con stock crítico
                  </p>
                </div>
              </div>
              <Button
                variant="secundario"
                size="sm"
                onClick={() => onIrATab('productos')}
              >
                Ver catálogo completo
              </Button>
            </div>

            {cargandoProductos ? (
              <div className="flex flex-col gap-2">
                <Skeleton className="h-14 w-full rounded-xl" />
                <Skeleton className="h-14 w-full rounded-xl" />
                <Skeleton className="h-14 w-full rounded-xl" />
              </div>
            ) : productosStockBajo.length === 0 && productosAgotados.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-8 text-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-surface-container text-primary">
                  <Boxes className="size-6" />
                </div>
                <p className="font-semibold text-primary">Niveles de stock óptimos</p>
                <p className="text-body-sm text-on-surface-variant">
                  No hay insumos agotados ni por debajo del umbral mínimo en esta sede.
                </p>
              </div>
            ) : (
              <div className="flex flex-col divide-y divide-outline-variant/30">
                {[...productosAgotados, ...productosStockBajo].slice(0, 5).map((prod) => {
                  const stockActual = sedeSeleccionada
                    ? prod.stockPorSede?.[sedeSeleccionada] ?? 0
                    : prod.stockTotal
                  const agotado = stockActual <= 0

                  return (
                    <div
                      key={prod.id}
                      className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
                    >
                      <div className="flex flex-col gap-0.5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-label-sm text-on-surface-variant">
                            {prod.codigo}
                          </span>
                          <button
                            type="button"
                            onClick={() => onVerProducto(prod)}
                            className="text-left font-semibold text-on-surface hover:text-primary hover:underline focus:outline-none"
                          >
                            {prod.nombre}
                          </button>
                        </div>
                        <div className="flex items-center gap-2 text-body-sm text-on-surface-variant">
                          <span>{prod.categoria || 'Insumo'}</span>
                          <span>•</span>
                          <span>{prod.unidad || 'Unidad'}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <div className="text-right">
                          <span className="block font-bold text-on-surface">
                            {stockActual} / mín. {prod.stock_minimo}
                          </span>
                          <Badge
                            tono={agotado ? 'peligro' : 'accion'}
                            punto
                            className="text-xs"
                          >
                            {agotado ? 'Agotado' : 'Stock bajo'}
                          </Badge>
                        </div>

                        <div className="flex items-center gap-1">
                          <Button
                            variant="secundario"
                            size="sm"
                            className="size-8 p-0"
                            title="Registrar entrada de insumo"
                            onClick={() => onAbrirMovimiento(prod, 'ENTRADA')}
                          >
                            <ArrowDownLeft className="size-4" />
                          </Button>
                          <Button
                            variant="secundario"
                            size="sm"
                            className="size-8 p-0"
                            title="Solicitar traslado de otra sede"
                            onClick={() => onAbrirTransferencia(prod)}
                          >
                            <ArrowRightLeft className="size-4" />
                          </Button>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </Card>
        </div>

        {/* Panel derecho: Movimientos recientes y Traslados */}
        <div className="flex flex-col gap-4 lg:col-span-5">
          <Card className="flex flex-col gap-4 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="flex size-9 items-center justify-center rounded-xl bg-surface-container text-primary">
                  <Clock className="size-5" />
                </div>
                <div>
                  <h3 className="font-display text-title-md font-bold text-primary">
                    Movimientos Recientes
                  </h3>
                  <p className="text-body-sm text-on-surface-variant">
                    Entradas, salidas y consumos clínicos
                  </p>
                </div>
              </div>
              <Button
                variant="secundario"
                size="sm"
                onClick={() => onIrATab('movimientos')}
              >
                Ver historial
              </Button>
            </div>

            {cargandoMovimientos ? (
              <div className="flex flex-col gap-2">
                <Skeleton className="h-12 w-full rounded-xl" />
                <Skeleton className="h-12 w-full rounded-xl" />
                <Skeleton className="h-12 w-full rounded-xl" />
              </div>
            ) : ultimosMovimientos.length === 0 ? (
              <p className="py-6 text-center text-body-sm text-on-surface-variant">
                No se registran movimientos recientes.
              </p>
            ) : (
              <div className="flex flex-col divide-y divide-outline-variant/30">
                {ultimosMovimientos.map((mov) => {
                  const esEntrada = mov.tipo === 'ENTRADA' || mov.tipo === 'TRANSFERENCIA_ENTRADA'
                  const esSalida = mov.tipo === 'SALIDA' || mov.tipo === 'TRANSFERENCIA_SALIDA'

                  return (
                    <div
                      key={mov.id}
                      className="flex items-center justify-between gap-3 py-2.5 first:pt-0 last:pb-0"
                    >
                      <div className="flex items-center gap-2.5">
                        <div
                          className={`flex size-8 shrink-0 items-center justify-center rounded-full ${
                            esEntrada
                              ? 'bg-secondary-fixed text-on-secondary-fixed'
                              : esSalida
                              ? 'bg-surface-container-high text-on-surface-variant'
                              : 'bg-primary/10 text-primary'
                          }`}
                        >
                          {esEntrada ? (
                            <ArrowDownLeft className="size-4" />
                          ) : esSalida ? (
                            <ArrowUpRight className="size-4" />
                          ) : (
                            <Boxes className="size-4" />
                          )}
                        </div>
                        <div className="flex flex-col gap-0.5">
                          <span className="text-body-sm font-medium text-on-surface line-clamp-1">
                            {mov.producto_nombre}
                          </span>
                          <span className="text-label-sm text-on-surface-variant">
                            {mov.sede_id} • {formatFechaHora(mov.creado_en ?? '')}
                          </span>
                        </div>
                      </div>

                      <div className="text-right">
                        <span
                          className={`font-semibold ${
                            esEntrada
                              ? 'text-primary'
                              : esSalida
                              ? 'text-error'
                              : 'text-on-surface'
                          }`}
                        >
                          {mov.cantidad > 0 ? `+${mov.cantidad}` : mov.cantidad}
                        </span>
                        <span className="block text-label-sm text-on-surface-variant">
                          {mov.tipo}
                        </span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </Card>

          {/* Tarjeta de Traslados rápidos */}
          <Card className="flex flex-col gap-3 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ArrowRightLeft className="size-5 text-primary" />
                <span className="font-bold text-primary">Transferencias entre Sedes</span>
              </div>
              <Button
                variant="secundario"
                size="sm"
                onClick={() => onIrATab('transferencias')}
              >
                Gestionar ({transferenciasPendientes.length})
              </Button>
            </div>
            <p className="text-body-sm text-on-surface-variant">
              Reasigna insumos entre sedes para balancear stock y evitar roturas de abastecimiento.
            </p>
          </Card>
        </div>
      </div>
    </div>
  )
}
