import {
  Boxes,
  AlertTriangle,
  Clock,
  ArrowRightLeft,
  DollarSign,
} from 'lucide-react'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { Badge } from '@/design-system/components/Badge'
import { formatSoles, formatFecha } from '@/shared/format'
import type { ReporteInventarioDTO } from './types'

interface InventarioTabProps {
  inventario?: ReporteInventarioDTO
  cargando?: boolean
}

export function InventarioTab({ inventario, cargando }: InventarioTabProps) {
  const alertas = inventario?.alertasStock ?? []
  const lotes = inventario?.lotesVencimiento ?? []
  const transferencias = inventario?.transferencias ?? []

  return (
    <div className="flex flex-col gap-6">
      {/* 1. KPIs de Almacén e Inventario */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
        <StatCard
          etiqueta="Catálogo Total de Insumos"
          valor={(inventario?.totalProductos ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Boxes className="text-teal-600" />}
          pie="Materiales odontológicos y radiológicos"
        />

        <StatCard
          etiqueta="Valorización Total del Stock"
          valor={formatSoles(inventario?.valorizacionTotal ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<DollarSign className="text-emerald-600" />}
          pie="Costo valorizado del inventario actual"
        />

        <StatCard
          etiqueta="Insumos con Stock Bajo/Crítico"
          valor={alertas.length.toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<AlertTriangle className="text-rose-600" />}
          insignia={
            alertas.length > 0 ? (
              <Badge tono="peligro">
                Reponer
              </Badge>
            ) : (
              <Badge tono="neutro">Normal</Badge>
            )
          }
          pie="Artículos en o por debajo del mínimo"
        />

        <StatCard
          etiqueta="Lotes por Vencer / Vencidos"
          valor={((inventario?.productosPorVencer ?? 0) + (inventario?.productosVencidos ?? 0)).toString()}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<Clock className="text-amber-600" />}
          insignia={
            (inventario?.productosVencidos ?? 0) > 0 ? (
              <Badge tono="peligro">
                {inventario?.productosVencidos} vencidos
              </Badge>
            ) : undefined
          }
          pie={`Próximos a caducar en 60 días: ${inventario?.productosPorVencer ?? 0}`}
        />

        <StatCard
          etiqueta="Transferencias Pendientes"
          valor={(inventario?.transferenciasPendientes ?? 0).toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<ArrowRightLeft className="text-sky-600" />}
          pie="En tránsito o por confirmar entre sedes"
        />
      </div>

      {/* 2. Sección de Alertas: Stock Bajo y Lotes por Vencer */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Tabla A: Insumos con Stock Crítico */}
        <Card className="p-5">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h3 className="font-display text-base font-semibold text-on-surface">
                Alertas de Stock Mínimo y Crítico
              </h3>
              <p className="text-xs text-on-surface-variant">
                Materiales clínicos con riesgo inminente de desabastecimiento
              </p>
            </div>
            <Badge tono="peligro">
              {alertas.length} en alerta
            </Badge>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-on-surface">
              <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3">Código</th>
                  <th className="py-2.5 px-3">Insumo</th>
                  <th className="py-2.5 px-3 text-center">Actual</th>
                  <th className="py-2.5 px-3 text-center">Mínimo</th>
                  <th className="py-2.5 px-3 text-center">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {alertas.map((p) => (
                  <tr key={p.productoId} className="hover:bg-surface-container/50 transition-colors">
                    <td className="py-2 px-3 font-mono text-xs text-on-surface-variant">{p.codigo}</td>
                    <td className="py-2 px-3 font-medium text-on-surface">
                      <div>
                        <span>{p.nombre}</span>
                        {p.categoria && <p className="text-xs text-on-surface-variant">{p.categoria}</p>}
                      </div>
                    </td>
                    <td className="py-2 px-3 text-center font-bold text-rose-600">
                      {p.stockActual}
                    </td>
                    <td className="py-2 px-3 text-center text-on-surface-variant">
                      {p.stockMinimo}
                    </td>
                    <td className="py-2 px-3 text-center">
                      <Badge
                        tono={p.estado === 'CRITICO' ? 'peligro' : 'villamax'}
                      >
                        {p.estado === 'CRITICO' ? 'Agotado' : 'Bajo Mínimo'}
                      </Badge>
                    </td>
                  </tr>
                ))}
                {alertas.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-xs text-on-surface-variant">
                      No hay insumos en nivel crítico. Todo el stock está dentro de los rangos óptimos.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        {/* Tabla B: Lotes con Vencimiento Próximo */}
        <Card className="p-5">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h3 className="font-display text-base font-semibold text-on-surface">
                Vencimiento de Lotes Farmacéuticos
              </h3>
              <p className="text-xs text-on-surface-variant">
                Control de fecha de caducidad de resinas, anestesias y reactivos
              </p>
            </div>
            <Badge tono="villamax">
              {lotes.length} lotes
            </Badge>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-on-surface">
              <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3">Producto</th>
                  <th className="py-2.5 px-3">Lote</th>
                  <th className="py-2.5 px-3">Vence</th>
                  <th className="py-2.5 px-3 text-center">Días</th>
                  <th className="py-2.5 px-3 text-center">Severidad</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {lotes.map((l) => (
                  <tr key={l.movimientoId} className="hover:bg-surface-container/50 transition-colors">
                    <td className="py-2 px-3 font-medium text-on-surface">{l.productoNombre}</td>
                    <td className="py-2 px-3 font-mono text-xs text-on-surface-variant">{l.lote}</td>
                    <td className="py-2 px-3 text-on-surface-variant">{formatFecha(l.fechaVencimiento)}</td>
                    <td className="py-2 px-3 text-center font-bold">
                      <span className={l.diasParaVencer < 0 ? 'text-rose-600' : 'text-amber-600'}>
                        {l.diasParaVencer < 0 ? 'Expirado' : `${l.diasParaVencer} d`}
                      </span>
                    </td>
                    <td className="py-2 px-3 text-center">
                      <Badge
                        tono={l.estado === 'VENCIDO' ? 'peligro' : 'villamax'}
                      >
                        {l.estado === 'VENCIDO' ? 'Vencido' : 'Por Vencer'}
                      </Badge>
                    </td>
                  </tr>
                ))}
                {lotes.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-xs text-on-surface-variant">
                      No hay lotes con fecha de caducidad en riesgo.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      {/* 3. Tabla de Transferencias entre Sedes Pendientes */}
      <Card className="p-5">
        <div className="mb-4">
          <h3 className="font-display text-base font-semibold text-on-surface">
            Transferencias de Insumos Inter-Sedes
          </h3>
          <p className="text-xs text-on-surface-variant">
            Seguimiento de envíos y recepciones entre sucursales
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-on-surface">
            <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
              <tr>
                <th className="py-2.5 px-3">Fecha</th>
                <th className="py-2.5 px-3">Insumo</th>
                <th className="py-2.5 px-3 text-center">Cantidad</th>
                <th className="py-2.5 px-3">Sede Origen</th>
                <th className="py-2.5 px-3">Sede Destino</th>
                <th className="py-2.5 px-3 text-center">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20">
              {transferencias.map((t) => (
                <tr key={t.id} className="hover:bg-surface-container/50 transition-colors">
                  <td className="py-2 px-3 text-on-surface-variant">{formatFecha(t.fecha)}</td>
                  <td className="py-2 px-3 font-medium text-on-surface">{t.productoNombre}</td>
                  <td className="py-2 px-3 text-center font-bold text-teal-700 dark:text-teal-300">{t.cantidad} unid.</td>
                  <td className="py-2 px-3 text-on-surface-variant">{t.sedeOrigenNombre}</td>
                  <td className="py-2 px-3 text-on-surface-variant">{t.sedeDestinoNombre}</td>
                  <td className="py-2 px-3 text-center">
                    <Badge tono="neutro">{t.estado}</Badge>
                  </td>
                </tr>
              ))}
              {transferencias.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-6 text-center text-xs text-on-surface-variant">
                    No hay transferencias activas o pendientes de recepción.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
