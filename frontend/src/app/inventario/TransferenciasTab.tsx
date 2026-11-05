import { textoDeError } from '@/shared/http'
import { useState } from 'react'
import {
  ArrowRightLeft,
  Plus,
  CheckCircle2,
  XCircle,
  AlertCircle,
  ArrowRight,
} from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import { Card } from '@/design-system/components/Card'
import { EmptyState } from '@/design-system/components/EmptyState'
import { EstadoBadge } from '@/design-system/components/EstadoBadge'
import { Skeleton } from '@/design-system/components/Skeleton'
import { formatFechaHora } from '@/shared/format'
import {
  useRecibirTransferenciaMutation,
  useCancelarTransferenciaMutation,
  type TransferenciaResponseDTO,
} from './api-inventario'

interface TransferenciasTabProps {
  transferencias: TransferenciaResponseDTO[]
  cargando: boolean
  sedeActivaId: string
  rolUsuario: string
  onSolicitarTransferencia: () => void
}

export function TransferenciasTab({
  transferencias,
  cargando,
  sedeActivaId,
  rolUsuario,
  onSolicitarTransferencia,
}: TransferenciasTabProps) {
  const [filtroEstado, setFiltroEstado] = useState<string>('TODOS')
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const recibirMutation = useRecibirTransferenciaMutation()
  const cancelarMutation = useCancelarTransferenciaMutation()

  const esAdminGeneral = rolUsuario === 'ADMIN_GENERAL'

  const transferenciasFiltradas = transferencias.filter((t) => {
    if (filtroEstado === 'TODOS') return true
    return t.estado === filtroEstado
  })

  const handleRecibir = async (id: string) => {
    setErrorAccion(null)
    try {
      await recibirMutation.mutateAsync(id)
    } catch (err) {
      setErrorAccion(textoDeError(err, 'Error al recibir transferencia'))
    }
  }

  const handleCancelar = async (id: string) => {
    if (!window.confirm('¿Está seguro de cancelar este traslado? El stock retenido volverá a la sede de origen.')) {
      return
    }
    setErrorAccion(null)
    try {
      await cancelarMutation.mutateAsync(id)
    } catch (err) {
      setErrorAccion(textoDeError(err, 'Error al cancelar transferencia'))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {errorAccion && (
        <div className="flex items-center gap-2 rounded-xl border border-error/30 bg-error-container/20 p-3 text-body-sm text-error">
          <AlertCircle className="size-4 shrink-0" />
          <span>{errorAccion}</span>
        </div>
      )}

      {/* Barra de herramientas */}
      <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
          >
            <option value="TODOS">Todos los estados</option>
            <option value="EN_TRANSITO">En tránsito</option>
            <option value="RECIBIDO">Recibidos</option>
            <option value="CANCELADO">Cancelados</option>
          </select>
          <span className="text-body-sm text-on-surface-variant">
            {transferenciasFiltradas.length} transferencias registradas
          </span>
        </div>

        <Button
          variant="primario"
          onClick={onSolicitarTransferencia}
          className="gap-1.5"
        >
          <Plus className="size-4" />
          Solicitar Traslado de Insumos
        </Button>
      </Card>

      {/* Tabla de transferencias */}
      <Card className="overflow-hidden p-0">
        {cargando ? (
          <div className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
          </div>
        ) : transferenciasFiltradas.length === 0 ? (
          <EmptyState
            icono={<ArrowRightLeft className="size-8" />}
            titulo="No hay transferencias registradas"
            descripcion="Puedes solicitar el traslado de materiales o instrumental entre las sedes de OdontoMax."
            accion={
              <Button variant="primario" onClick={onSolicitarTransferencia}>
                <Plus className="size-4" /> Solicitar Primer Traslado
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-body-sm">
              <thead className="border-b border-outline-variant/40 bg-surface-container/60 text-label-md font-semibold text-on-surface-variant">
                <tr>
                  <th className="px-4 py-3.5">Insumo</th>
                  <th className="px-4 py-3.5">Cantidad</th>
                  <th className="px-4 py-3.5">Ruta de Traslado</th>
                  <th className="px-4 py-3.5">Estado</th>
                  <th className="px-4 py-3.5">Fecha / Solicitante</th>
                  <th className="px-4 py-3.5 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {transferenciasFiltradas.map((t) => {
                  const enTransito = t.estado === 'EN_TRANSITO'
                  // La recepción la puede confirmar la sede destino o admin general
                  const puedeRecibir = enTransito && (esAdminGeneral || t.sede_destino_id === sedeActivaId)
                  // La cancelación la puede hacer la sede origen o admin general
                  const puedeCancelar = enTransito && (esAdminGeneral || t.sede_origen_id === sedeActivaId)

                  return (
                    <tr
                      key={t.id}
                      className="group transition-colors hover:bg-surface-container-high/40"
                    >
                      {/* Insumo */}
                      <td className="px-4 py-3.5">
                        <span className="font-semibold text-on-surface">
                          {t.producto_nombre}
                        </span>
                        {t.motivo && (
                          <span className="block text-label-sm text-on-surface-variant line-clamp-1">
                            Motivo: {t.motivo}
                          </span>
                        )}
                      </td>

                      {/* Cantidad */}
                      <td className="whitespace-nowrap px-4 py-3.5 font-bold text-on-surface">
                        {t.cantidad} uds
                      </td>

                      {/* Ruta: Origen -> Destino */}
                      <td className="whitespace-nowrap px-4 py-3.5">
                        <div className="flex items-center gap-1.5 text-body-sm">
                          <span className="font-medium text-on-surface">
                            {t.sede_origen_nombre || t.sede_origen_id}
                          </span>
                          <ArrowRight className="size-3.5 text-on-surface-variant" />
                          <span className="font-medium text-primary">
                            {t.sede_destino_nombre || t.sede_destino_id}
                          </span>
                        </div>
                      </td>

                      {/* Estado */}
                      <td className="whitespace-nowrap px-4 py-3.5">
                        <EstadoBadge estado={t.estado} />
                      </td>

                      {/* Fecha y solicitante */}
                      <td className="whitespace-nowrap px-4 py-3.5 text-on-surface-variant">
                        <span>{formatFechaHora(t.creado_en ?? '')}</span>
                        {t.usuario_solicita && (
                          <span className="block text-label-sm text-on-surface-variant">
                            Por: {t.usuario_solicita}
                          </span>
                        )}
                      </td>

                      {/* Acciones */}
                      <td className="whitespace-nowrap px-4 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {puedeRecibir && (
                            <Button
                              variant="secundario"
                              size="sm"
                              className="gap-1 text-xs"
                              title="Confirmar llegada y recepción de insumos en destino"
                              onClick={() => handleRecibir(t.id)}
                              disabled={recibirMutation.isPending}
                            >
                              <CheckCircle2 className="size-3.5" />
                              Recibir en sede
                            </Button>
                          )}

                          {puedeCancelar && (
                            <Button
                              variant="secundario"
                              size="sm"
                              className="gap-1 text-xs text-error hover:bg-error-container/20"
                              title="Cancelar traslado y devolver insumos al stock de origen"
                              onClick={() => handleCancelar(t.id)}
                              disabled={cancelarMutation.isPending}
                            >
                              <XCircle className="size-3.5" />
                              Cancelar
                            </Button>
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
