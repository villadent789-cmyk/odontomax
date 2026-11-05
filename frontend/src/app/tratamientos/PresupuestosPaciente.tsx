import { Check, FileText, MessageCircle, Printer, X } from 'lucide-react'
import { useState } from 'react'
import { Alert, Badge, Button, Card, Skeleton, toast } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { mensajeError } from '@/shared/errores'
import { formatFecha, formatSoles } from '@/shared/format'
import { imprimirDocumento } from '@/shared/imprimir'
import { useAceptarPresupuesto, usePresupuestosPaciente, useRechazarPresupuesto, type Presupuesto } from './api-presupuestos'
import { documentoPresupuesto } from './documentoPresupuesto'
import { NuevoPresupuestoModal } from './NuevoPresupuestoModal'

interface Props {
  pacienteId: string
  pacienteNombre: string
  pacienteDni?: string
  pacienteTelefono?: string
  sedeId: string
  sedeNombre: string
}

const TONO = { VIGENTE: 'accion', ACEPTADO: 'primario', RECHAZADO: 'neutro' } as const

function resumenWhatsapp(p: Presupuesto, nombre: string, sede: string): string {
  const lineas = p.items.map((i) => `• ${i.nombre}${i.piezaFdi ? ` (pieza ${i.piezaFdi})` : ''} ×${i.cantidad}: ${formatSoles(i.subtotal)}`).join('\n')
  return `Hola ${nombre.split(' ')[0]}, le enviamos su presupuesto de ${sede}:\n${lineas}\n*Total: ${formatSoles(p.total)}*\nCualquier duda, escríbanos por aquí.`
}

/**
 * Presupuestos del paciente: lo que se puede hacer. Se imprime, se manda por WhatsApp y, cuando el paciente decide, se
 * acepta todo o solo una parte para convertirlo en plan de tratamiento.
 */
export function PresupuestosPaciente({ pacienteId, pacienteNombre, pacienteDni, pacienteTelefono, sedeId, sedeNombre }: Props) {
  const sesion = useSesion()
  const consulta = usePresupuestosPaciente(pacienteId)
  const aceptar = useAceptarPresupuesto(pacienteId)
  const rechazar = useRechazarPresupuesto(pacienteId)
  const [nuevo, setNuevo] = useState(false)
  const [elegidas, setElegidas] = useState<Record<string, Set<string>>>({})
  const [error, setError] = useState<string | null>(null)
  const puedeEditar = ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'].includes(sesion?.usuario.rol ?? '')
  const negocio = sesion?.negocioActivo === 'VILLAMAX' ? 'VILLAMAX' : 'VILLADENT'

  if (consulta.isPending) return <Skeleton className="h-20 w-full" />
  if (consulta.isError) return <Alert tono="error" titulo="No se pudieron cargar los presupuestos">{mensajeError(consulta.error)}</Alert>
  const presupuestos = consulta.data
  if (presupuestos.length === 0 && !puedeEditar) return null

  const seleccion = (p: Presupuesto) => elegidas[p.id] ?? new Set(p.items.map((i) => i.id))
  const alternar = (p: Presupuesto, itemId: string) => {
    const actual = new Set(seleccion(p))
    if (actual.has(itemId)) actual.delete(itemId)
    else actual.add(itemId)
    setElegidas((e) => ({ ...e, [p.id]: actual }))
  }

  const aceptarSeleccion = async (p: Presupuesto) => {
    setError(null)
    const ids = [...seleccion(p)]
    if (ids.length === 0) return setError('Elige al menos un tratamiento que el paciente aceptó.')
    try {
      await aceptar.mutateAsync({ id: p.id, itemIds: ids.length === p.items.length ? undefined : ids })
      toast('Presupuesto aceptado: se creó el plan de tratamiento')
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  const imprimir = (p: Presupuesto) =>
    void imprimirDocumento(documentoPresupuesto({ presupuesto: p, pacienteNombre, pacienteDni, sedeNombre, negocio })).catch(() => window.print())

  return (
    <Card className="flex flex-col gap-3 p-4" aria-label="Presupuestos del paciente">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 font-display text-base font-bold text-on-surface">
          <FileText aria-hidden className="size-4 text-primary" />
          Presupuestos
        </h3>
        {puedeEditar && (
          <Button size="sm" variant="secundario" onClick={() => setNuevo(true)}>
            Nuevo presupuesto
          </Button>
        )}
      </div>
      {presupuestos.length === 0 && <p className="text-sm text-on-surface-variant">Todavía no hay presupuestos. Crea uno con lo que se puede hacer por el paciente.</p>}
      {error && <Alert tono="error" titulo={error} />}

      <ul className="flex flex-col gap-3">
        {presupuestos.map((p) => (
          <li key={p.id} className="flex flex-col gap-2 rounded-panel border border-outline-variant/50 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-semibold text-on-surface">
                {formatFecha(p.creadoEn)}{p.motivo ? ` · ${p.motivo}` : ''}
              </p>
              <Badge tono={TONO[p.estado]}>{p.estado === 'VIGENTE' ? 'Vigente' : p.estado === 'ACEPTADO' ? 'Aceptado' : 'Rechazado'}</Badge>
            </div>
            <table className="sin-apilar w-full text-left text-xs">
              <thead className="text-on-surface-variant">
                <tr>
                  {p.estado === 'VIGENTE' && puedeEditar && <th className="w-8 p-1"><span className="sr-only">Aceptado</span></th>}
                  <th className="p-1">Tratamiento</th>
                  <th className="p-1">Pieza</th>
                  <th className="p-1 text-right">Piezas × costo</th>
                  <th className="p-1 text-right">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {p.items.map((i) => (
                  <tr key={i.id} className={`border-t border-outline-variant/40 ${p.estado === 'ACEPTADO' && !i.aceptado ? 'text-on-surface-variant line-through' : ''}`}>
                    {p.estado === 'VIGENTE' && puedeEditar && (
                      <td className="p-1">
                        <input type="checkbox" aria-label={`Aceptó ${i.nombre}`} checked={seleccion(p).has(i.id)} onChange={() => alternar(p, i.id)} className="size-4" />
                      </td>
                    )}
                    <td className="p-1">{i.nombre}{i.gravedad ? ` · ${i.gravedad.toLowerCase()}` : ''}</td>
                    <td className="p-1">{i.piezaFdi ?? '—'}</td>
                    <td className="p-1 text-right font-mono">{i.cantidad} × {formatSoles(i.precioUnitario)}</td>
                    <td className="p-1 text-right font-mono">{formatSoles(i.subtotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-bold text-on-surface">
                Total {formatSoles(p.total)}
                {p.estado === 'ACEPTADO' && <span className="ml-2 font-normal text-on-surface-variant">· aceptado {formatSoles(p.totalAceptado)}</span>}
              </p>
              <div className="flex flex-wrap items-center gap-1.5">
                <Button size="sm" variant="secundario" onClick={() => imprimir(p)}>
                  <Printer aria-hidden />
                  Imprimir
                </Button>
                {pacienteTelefono && (
                  <a
                    href={`https://wa.me/51${pacienteTelefono.replace(/\D/g, '').slice(-9)}?text=${encodeURIComponent(resumenWhatsapp(p, pacienteNombre, sedeNombre))}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex h-10 items-center gap-1.5 rounded-control border border-emerald-600/40 bg-emerald-50 px-3 text-xs font-bold text-emerald-700 hover:bg-emerald-100"
                  >
                    <MessageCircle aria-hidden className="size-4" />
                    WhatsApp
                  </a>
                )}
                {p.estado === 'VIGENTE' && puedeEditar && (
                  <>
                    <Button size="sm" variant="secundario" onClick={() => void rechazar.mutateAsync(p.id).catch((e) => setError(mensajeError(e)))} disabled={rechazar.isPending}>
                      <X aria-hidden />
                      No aceptó
                    </Button>
                    <Button size="sm" onClick={() => void aceptarSeleccion(p)} cargando={aceptar.isPending}>
                      <Check aria-hidden />
                      Crear plan con lo aceptado
                    </Button>
                  </>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>

      <NuevoPresupuestoModal abierto={nuevo} alCerrar={() => setNuevo(false)} pacienteId={pacienteId} pacienteNombre={pacienteNombre} sedeId={sedeId} />
    </Card>
  )
}
