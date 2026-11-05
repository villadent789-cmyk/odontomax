import { useState } from 'react'
import {
  Boxes,
  LayoutDashboard,
  ArrowRightLeft,
  Clock,
  ArrowDownLeft,
} from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import { Card } from '@/design-system/components/Card'
import { ErrorState } from '@/design-system/components/ErrorState'
import { Skeleton } from '@/design-system/components/Skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/design-system/components/Tabs'
import { formatFechaHora } from '@/shared/format'
import { useSesion } from '@/shared/auth'
import {
  useProductosInventario,
  useMovimientosInventario,
  useTransferenciasInventario,
  useDarDeBajaProductoMutation,
  type ProductoInventarioDTO,
} from './api-inventario'
import { AlertasConsumo } from './AlertasConsumo'
import { DashboardInventario } from './DashboardInventario'
import { ProductosInventarioTab } from './ProductosInventarioTab'
import { TransferenciasTab } from './TransferenciasTab'
import { DetalleProductoModal } from './DetalleProductoModal'
import { RegistrarMovimientoModal } from './RegistrarMovimientoModal'
import { SolicitarTransferenciaModal } from './SolicitarTransferenciaModal'
import { ProductoFormModal } from './ProductoFormModal'
import { RotuloMostrando } from '@/app/layout/RotuloMostrando'

const SEDES_DEFAULT = [
  { id: 'sede-chilca-1', nombre: 'Villadent Chilca' },
  { id: 'sede-eltambo', nombre: 'Villadent Huancayo Centro' },
  { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca' },
]

type PestañaInventario = 'dashboard' | 'productos' | 'movimientos' | 'transferencias'

export function InventarioPage() {
  const sesion = useSesion()
  const rol = sesion?.usuario.rol ?? 'RECEPCION'
  const esAdminGeneral = rol === 'ADMIN_GENERAL'

  // Sede activa por defecto desde el panel unificado
  const sedeUsuario = sesion?.sedeActivaId ?? 'sede-chilca-1'
  const sedeSeleccionada = (sesion?.sedeActivaId === null || (esAdminGeneral && sesion?.token === 'token-admin-general')) ? '' : (sesion?.sedeActivaId ?? '')
  const sedesDisponibles = SEDES_DEFAULT

  const [pestaña, setPestaña] = useState<PestañaInventario>('dashboard')

  // Modales
  const [productoSeleccionado, setProductoSeleccionado] = useState<ProductoInventarioDTO | null>(null)
  const [modalDetalle, setModalDetalle] = useState(false)
  const [modalMovimiento, setModalMovimiento] = useState(false)
  const [tipoMovimientoInicial, setTipoMovimientoInicial] = useState<'ENTRADA' | 'SALIDA' | 'AJUSTE'>('ENTRADA')
  const [modalTransferencia, setModalTransferencia] = useState(false)
  const [modalProductoForm, setModalProductoForm] = useState(false)
  const [productoEditar, setProductoEditar] = useState<ProductoInventarioDTO | null>(null)

  // Consultas React Query
  const {
    data: productos = [],
    isLoading: cargandoProductos,
    isError: errorProductos,
    refetch: recargarProductos,
  } = useProductosInventario(sedeSeleccionada || undefined)

  const {
    data: movimientos = [],
    isLoading: cargandoMovimientos,
    refetch: recargarMovimientos,
  } = useMovimientosInventario(undefined, sedeSeleccionada || undefined)

  const {
    data: transferencias = [],
    isLoading: cargandoTransferencias,
    refetch: recargarTransferencias,
  } = useTransferenciasInventario(sedeSeleccionada || undefined)

  const darDeBajaMutation = useDarDeBajaProductoMutation()

  // Handlers
  const handleVerProducto = (prod: ProductoInventarioDTO) => {
    setProductoSeleccionado(prod)
    setModalDetalle(true)
  }

  const handleAbrirMovimiento = (
    prod?: ProductoInventarioDTO,
    tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE' = 'ENTRADA'
  ) => {
    setProductoSeleccionado(prod ?? null)
    setTipoMovimientoInicial(tipo)
    setModalMovimiento(true)
  }

  const handleAbrirTransferencia = (prod?: ProductoInventarioDTO) => {
    setProductoSeleccionado(prod ?? null)
    setModalTransferencia(true)
  }

  const handleCrearProducto = () => {
    setProductoEditar(null)
    setModalProductoForm(true)
  }

  const handleEditarProducto = (prod: ProductoInventarioDTO) => {
    setProductoEditar(prod)
    setModalProductoForm(true)
  }

  const handleDarDeBaja = async (prod: ProductoInventarioDTO) => {
    if (
      window.confirm(
        `¿Desea dar de baja el insumo ${prod.codigo} - ${prod.nombre}? Dejará de aparecer en catálogo pero conservará su historial.`
      )
    ) {
      await darDeBajaMutation.mutateAsync(prod.id)
    }
  }

  if (errorProductos) {
    return (
      <div className="p-6">
        <ErrorState
          titulo="Error al cargar el módulo de inventario"
          descripcion="No fue posible conectar con el servicio de inventario y almacén. Verifique su conexión y permisos."
          onReintentar={() => {
            recargarProductos()
            recargarMovimientos()
            recargarTransferencias()
          }}
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 p-4 sm:p-6">
      {/* Encabezado Principal */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <Boxes className="size-6" />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="font-display text-headline-sm font-bold text-primary">
                Inventario & Almacén Odontológico
              </h1>
              <RotuloMostrando />
            </div>
            <p className="text-body-sm text-on-surface-variant">
              Control multisede de insumos clínicos, radiológicos, stock y transferencias
            </p>
          </div>
        </div>

        {/* Acciones rápidas */}
        <div className="flex flex-wrap items-center gap-2.5">
          <Button
            variant="secundario"
            size="sm"
            onClick={() => handleAbrirMovimiento(undefined, 'ENTRADA')}
            className="gap-1.5"
          >
            <ArrowDownLeft className="size-4" />
            <span className="hidden sm:inline">Registrar</span> Entrada
          </Button>

          <Button
            variant="secundario"
            size="sm"
            onClick={() => handleAbrirTransferencia()}
            className="gap-1.5"
          >
            <ArrowRightLeft className="size-4" />
            <span className="hidden sm:inline">Solicitar</span> Traslado
          </Button>
        </div>
      </div>

      {/* Pestañas de Navegación del Módulo */}
      <Tabs
        value={pestaña}
        onValueChange={(val) => setPestaña(val as PestañaInventario)}
        className="w-full"
      >
        <TabsList className="mb-2">
          <TabsTrigger value="dashboard" className="gap-2">
            <LayoutDashboard className="size-4" />
            Dashboard General
          </TabsTrigger>
          <TabsTrigger value="productos" className="gap-2">
            <Boxes className="size-4" />
            Insumos & Stock ({productos.length})
          </TabsTrigger>
          <TabsTrigger value="movimientos" className="gap-2">
            <Clock className="size-4" />
            Historial de Movimientos
          </TabsTrigger>
          <TabsTrigger value="transferencias" className="gap-2">
            <ArrowRightLeft className="size-4" />
            Traslados entre Sedes
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {(rol === 'ADMIN_GENERAL' || rol === 'ADMIN_SEDE') && <AlertasConsumo sedeId={sedeSeleccionada || sedeUsuario} />}

      {/* Contenido según pestaña */}
      {pestaña === 'dashboard' && (
        <DashboardInventario
          productos={productos}
          cargandoProductos={cargandoProductos}
          movimientos={movimientos}
          cargandoMovimientos={cargandoMovimientos}
          transferencias={transferencias}
          cargandoTransferencias={cargandoTransferencias}
          sedeSeleccionada={sedeSeleccionada}
          onVerProducto={handleVerProducto}
          onAbrirMovimiento={handleAbrirMovimiento}
          onAbrirTransferencia={handleAbrirTransferencia}
          onIrATab={(tab) => setPestaña(tab)}
        />
      )}

      {pestaña === 'productos' && (
        <ProductosInventarioTab
          productos={productos}
          cargando={cargandoProductos}
          esAdminGeneral={esAdminGeneral}
          sedeSeleccionada={sedeSeleccionada}
          sedesDisponibles={sedesDisponibles}
          onVerProducto={handleVerProducto}
          onAbrirMovimiento={handleAbrirMovimiento}
          onAbrirTransferencia={handleAbrirTransferencia}
          onCrearProducto={handleCrearProducto}
          onEditarProducto={handleEditarProducto}
          onDarDeBajaProducto={handleDarDeBaja}
        />
      )}

      {pestaña === 'movimientos' && (
        <Card className="overflow-hidden p-0">
          <div className="border-b border-outline-variant/30 p-4">
            <h3 className="font-display text-title-md font-bold text-primary">
              Registro Completo de Movimientos de Almacén
            </h3>
            <p className="text-body-sm text-on-surface-variant">
              Trazabilidad de entradas, salidas, consumos odontológicos y ajustes por sede
            </p>
          </div>

          {cargandoMovimientos ? (
            <div className="flex flex-col gap-3 p-6">
              <Skeleton className="h-10 w-full rounded-xl" />
              <Skeleton className="h-12 w-full rounded-xl" />
              <Skeleton className="h-12 w-full rounded-xl" />
            </div>
          ) : movimientos.length === 0 ? (
            <div className="p-8 text-center text-on-surface-variant">
              No hay movimientos registrados en este filtro.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-body-sm">
                <thead className="border-b border-outline-variant/40 bg-surface-container/60 text-label-md font-semibold text-on-surface-variant">
                  <tr>
                    <th className="px-4 py-3">Insumo</th>
                    <th className="px-4 py-3">Sede</th>
                    <th className="px-4 py-3">Operación</th>
                    <th className="px-4 py-3">Cantidad</th>
                    <th className="px-4 py-3">Detalle / Motivo</th>
                    <th className="px-4 py-3">Lote / Vencimiento</th>
                    <th className="px-4 py-3 text-right">Fecha / Hora</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {movimientos.map((m) => {
                    const esEntrada = m.tipo === 'ENTRADA' || m.tipo === 'TRANSFERENCIA_ENTRADA'
                    const esSalida = m.tipo === 'SALIDA' || m.tipo === 'TRANSFERENCIA_SALIDA'

                    return (
                      <tr key={m.id} className="hover:bg-surface-container-high/30">
                        <td className="px-4 py-3 font-semibold text-on-surface">
                          {m.producto_nombre}
                        </td>
                        <td className="px-4 py-3 text-on-surface-variant">
                          {m.sede_id}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-semibold ${
                              esEntrada
                                ? 'bg-secondary-fixed text-on-secondary-fixed'
                                : esSalida
                                ? 'bg-error-container/40 text-error'
                                : 'bg-surface-container-high text-on-surface'
                            }`}
                          >
                            {m.tipo}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-bold text-on-surface">
                          {m.cantidad > 0 ? `+${m.cantidad}` : m.cantidad}
                        </td>
                        <td className="px-4 py-3 text-on-surface-variant">
                          {m.motivo || m.proveedor || '—'}
                        </td>
                        <td className="px-4 py-3 text-label-sm text-on-surface-variant">
                          {m.lote ? `Lote: ${m.lote}` : ''}
                          {m.fecha_vencimiento ? ` • Vence: ${m.fecha_vencimiento}` : ''}
                          {m.fecha_entrega ? ` • Entrega: ${m.fecha_entrega}` : ''}
                          {!m.lote && !m.fecha_vencimiento && !m.fecha_entrega ? '—' : ''}
                        </td>
                        <td className="px-4 py-3 text-right text-label-sm text-on-surface-variant">
                          {formatFechaHora(m.creado_en ?? '')}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {pestaña === 'transferencias' && (
        <TransferenciasTab
          transferencias={transferencias}
          cargando={cargandoTransferencias}
          sedeActivaId={sedeUsuario}
          rolUsuario={rol}
          onSolicitarTransferencia={() => handleAbrirTransferencia()}
        />
      )}

      {/* Modales Compartidos */}
      <DetalleProductoModal
        abierto={modalDetalle}
        onCerrar={() => {
          setModalDetalle(false)
          setProductoSeleccionado(null)
        }}
        producto={productoSeleccionado}
        sedesDisponibles={sedesDisponibles}
        onAbrirMovimiento={(tipo) => {
          setModalDetalle(false)
          handleAbrirMovimiento(productoSeleccionado ?? undefined, tipo)
        }}
        onAbrirTransferencia={() => {
          setModalDetalle(false)
          handleAbrirTransferencia(productoSeleccionado ?? undefined)
        }}
      />

      <RegistrarMovimientoModal
        abierto={modalMovimiento}
        onCerrar={() => {
          setModalMovimiento(false)
          setProductoSeleccionado(null)
        }}
        productoInicial={productoSeleccionado}
        tipoInicial={tipoMovimientoInicial}
        productos={productos}
        sedeDefecto={sedeSeleccionada || sedeUsuario}
        sedesDisponibles={sedesDisponibles}
      />

      <SolicitarTransferenciaModal
        abierto={modalTransferencia}
        onCerrar={() => {
          setModalTransferencia(false)
          setProductoSeleccionado(null)
        }}
        productoInicial={productoSeleccionado}
        productos={productos}
        sedeOrigenDefecto={sedeSeleccionada || sedeUsuario}
        sedesDisponibles={sedesDisponibles}
      />

      <ProductoFormModal
        abierto={modalProductoForm}
        onCerrar={() => {
          setModalProductoForm(false)
          setProductoEditar(null)
        }}
        productoEditar={productoEditar}
      />
    </div>
  )
}
export default InventarioPage
