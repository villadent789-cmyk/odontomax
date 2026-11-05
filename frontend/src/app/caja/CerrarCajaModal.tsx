import { Calculator, Eye, EyeOff, MessageCircle } from 'lucide-react'
import { useState } from 'react'
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { Alert } from '@/design-system/components/Alert'
import { formatSoles } from '@/shared/format'
import { useCerrarCajaMutation, type CajaSesionDTO } from './api-caja'
import { textoDeError } from '@/shared/http'

interface Props {
  abierto: boolean
  alCerrar: () => void
  caja: CajaSesionDTO
  alGuardar?: (cerrada: CajaSesionDTO) => void
}

const DENOMINACIONES = [
  { valor: 200, label: 'Billetes S/ 200' },
  { valor: 100, label: 'Billetes S/ 100' },
  { valor: 50, label: 'Billetes S/ 50' },
  { valor: 20, label: 'Billetes S/ 20' },
  { valor: 10, label: 'Billetes S/ 10' },
  { valor: 5, label: 'Monedas S/ 5' },
  { valor: 2, label: 'Monedas S/ 2' },
  { valor: 1, label: 'Monedas S/ 1' },
  { valor: 0.5, label: 'Monedas S/ 0.50' },
]

/** Lo contado arranca en lo que espera el sistema, pero nunca en negativo. */
function inicial(monto?: number | null): string {
  return String(Math.max(0, Number(monto ?? 0)))
}

/** Sin signos: «Faltan» si hay menos de lo que el sistema espera, «Sobran» si hay más. */
function textoDiferencia(diferencia: number): string {
  if (Math.abs(diferencia) < 0.01) return 'Cuadra'
  return `${diferencia > 0 ? 'Sobran' : 'Faltan'} ${formatSoles(Math.abs(diferencia))}`
}

export function CerrarCajaModal({ abierto, alCerrar, caja, alGuardar }: Props) {
  const [efectivoFisico, setEfectivoFisico] = useState(inicial(caja.montoEsperado))
  const [yapeContado, setYapeContado] = useState(inicial(caja.montoContadoYape ?? caja.montoEsperadoYape))
  const [plinContado, setPlinContado] = useState(inicial(caja.montoContadoPlin ?? caja.montoEsperadoPlin))
  const [tarjetaContado, setTarjetaContado] = useState(inicial(caja.montoContadoTarjeta ?? caja.montoEsperadoTarjeta))
  const [observaciones, setObservaciones] = useState('')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)
  const [modoCiego, setModoCiego] = useState(false)
  const [mostrarCalculadora, setMostrarCalculadora] = useState(false)
  const [cantidades, setCantidades] = useState<Record<number, number>>({})

  const cerrarMutation = useCerrarCajaMutation()

  // Si los egresos superan a los ingresos de un método, el sistema espera 0 (no un negativo) y se avisa aparte.
  const crudoEfectivo = Number(caja.montoEsperado ?? 0)
  const crudoYape = Number(caja.montoEsperadoYape ?? caja.totalIngresosYape ?? caja.totalYape ?? 0)
  const crudoPlin = Number(caja.montoEsperadoPlin ?? 0)
  const crudoTarjeta = Number(caja.montoEsperadoTarjeta ?? 0)
  const esperadoEfectivo = Math.max(0, crudoEfectivo)
  const esperadoYape = Math.max(0, crudoYape)
  const esperadoPlin = Math.max(0, crudoPlin)
  const esperadoTarjeta = Math.max(0, crudoTarjeta)
  const egresosDeMas = [
    ['Efectivo', crudoEfectivo],
    ['Yape', crudoYape],
    ['Plin', crudoPlin],
    ['Tarjetas', crudoTarjeta],
  ].filter(([, monto]) => (monto as number) < 0) as [string, number][]
  const contadoEfectivo = parseFloat(efectivoFisico) || 0
  const contadoYape = parseFloat(yapeContado) || 0
  const contadoPlin = parseFloat(plinContado) || 0
  const contadoTarjeta = parseFloat(tarjetaContado) || 0
  const diferenciaEfectivo = contadoEfectivo - esperadoEfectivo
  const diferenciaYape = contadoYape - esperadoYape
  const diferenciaPlin = contadoPlin - esperadoPlin
  const diferenciaTarjeta = contadoTarjeta - esperadoTarjeta
  const hayDiferencia = [diferenciaEfectivo, diferenciaYape, diferenciaPlin, diferenciaTarjeta].some((diferencia) => diferencia !== 0)

  function actualizarCantidad(valor: number, cant: number) {
    const nuevo = { ...cantidades, [valor]: Math.max(0, cant) }
    setCantidades(nuevo)
    const total = Object.entries(nuevo).reduce((acc, [v, c]) => acc + Number(v) * c, 0)
    setEfectivoFisico(total.toFixed(2))
  }

  function compartirLiquidacionWhatsapp() {
    const fechaInicio = caja.abiertaEn
      ? new Date(caja.abiertaEn).toLocaleDateString('es-PE', { timeZone: 'America/Lima' })
      : '—'

    const mensaje = encodeURIComponent(
      `💰 *LIQUIDACIÓN DE CIERRE DE CAJA*\n` +
      `*Sede:* ${caja.sedeNombre ?? 'Clínica'}\n` +
      `*Cajero(a):* ${caja.abiertaPorNombre ?? 'Recepción'}\n` +
      `*Periodo:* Desde ${fechaInicio} hasta ${new Date().toLocaleDateString('es-PE', { timeZone: 'America/Lima' })}\n\n` +
      `• *Efectivo:* Sistema ${formatSoles(esperadoEfectivo)} · Contado ${formatSoles(contadoEfectivo)} · Diferencia ${textoDiferencia(diferenciaEfectivo)}\n` +
      `• *Yape:* Sistema ${formatSoles(esperadoYape)} · Contado ${formatSoles(contadoYape)} · Diferencia ${textoDiferencia(diferenciaYape)}\n` +
      `• *Plin:* Sistema ${formatSoles(esperadoPlin)} · Contado ${formatSoles(contadoPlin)} · Diferencia ${textoDiferencia(diferenciaPlin)}\n` +
      `• *Tarjetas (débito ${formatSoles(Number(caja.totalIngresosTarjetaDebito ?? 0))} + crédito ${formatSoles(Number(caja.totalIngresosTarjetaCredito ?? 0))}):* Sistema ${formatSoles(esperadoTarjeta)} · Contado ${formatSoles(contadoTarjeta)} · Diferencia ${textoDiferencia(diferenciaTarjeta)}\n` +
      (observaciones.trim() ? `\n*Observaciones:* ${observaciones.trim()}` : '')
    )
    window.open(`https://wa.me/?text=${mensaje}`, '_blank', 'noopener,noreferrer')
  }

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (isNaN(contadoEfectivo) || contadoEfectivo < 0) {
      setErrorLocal('El monto de efectivo contado físicamente no puede ser negativo.')
      return
    }
    if (contadoYape < 0 || contadoPlin < 0 || contadoTarjeta < 0) {
      setErrorLocal('Los montos contados de Yape, Plin y tarjetas no pueden ser negativos.')
      return
    }
    if (hayDiferencia && !observaciones.trim()) {
      setErrorLocal('Escribe las observaciones para explicar las diferencias del cierre.')
      return
    }

    cerrarMutation.mutate(
      {
        sesionId: caja.id!,
        dto: {
          montoContado: contadoEfectivo,
          montoContadoYape: contadoYape,
          montoContadoPlin: contadoPlin,
          montoContadoTarjeta: contadoTarjeta,
          observaciones: observaciones.trim() || undefined,
        },
      },
      {
        onSuccess: (cerrada) => {
          alGuardar?.(cerrada)
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al cerrar el turno de caja'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <DialogTitle>Cierre de caja</DialogTitle>
            <button
              type="button"
              onClick={() => {
                const activar = !modoCiego
                if (activar) {
                  setEfectivoFisico('')
                  setYapeContado('')
                  setPlinContado('')
                  setTarjetaContado('')
                  setCantidades({})
                }
                setModoCiego(activar)
              }}
              className="flex items-center gap-1.5 rounded-full border border-outline px-2.5 py-1 text-xs font-medium text-on-surface hover:bg-surface-container-low transition-colors"
              title={modoCiego ? 'Revelar saldo esperado' : 'Activar conteo ciego sin sesgo'}
            >
              {modoCiego ? <Eye className="size-3.5 text-primary" /> : <EyeOff className="size-3.5" />}
              {modoCiego ? 'Revelar Esperado' : 'Arqueo Ciego'}
            </button>
          </div>
          <DialogDescription>
            Periodo: desde {caja.abiertaEn
              ? new Date(caja.abiertaEn).toLocaleDateString('es-PE', { timeZone: 'America/Lima' })
              : '—'} hasta hoy. Los egresos por método ya están descontados.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          <div className="overflow-x-auto rounded-lg border border-outline">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-container-low text-on-surface-variant">
                <tr>
                  <th className="p-2.5 font-semibold">Método</th>
                  <th className="p-2.5 font-semibold">Sistema</th>
                  <th className="p-2.5 font-semibold">Tú cuentas</th>
                  <th className="p-2.5 font-semibold">Diferencia</th>
                </tr>
              </thead>
              <tbody>
                {[
                  { nombre: 'Efectivo', esperado: esperadoEfectivo, contado: contadoEfectivo, diferencia: diferenciaEfectivo, valor: efectivoFisico, cambiar: setEfectivoFisico, id: 'efectivoFisico' },
                  { nombre: 'Yape', esperado: esperadoYape, contado: contadoYape, diferencia: diferenciaYape, valor: yapeContado, cambiar: setYapeContado, id: 'yapeContado' },
                  { nombre: 'Plin', esperado: esperadoPlin, contado: contadoPlin, diferencia: diferenciaPlin, valor: plinContado, cambiar: setPlinContado, id: 'plinContado' },
                  {
                    nombre: 'Tarjetas',
                    detalle: `Débito ${formatSoles(Number(caja.totalIngresosTarjetaDebito ?? 0))} · Crédito ${formatSoles(Number(caja.totalIngresosTarjetaCredito ?? 0))}`,
                    esperado: esperadoTarjeta, contado: contadoTarjeta, diferencia: diferenciaTarjeta, valor: tarjetaContado, cambiar: setTarjetaContado, id: 'tarjetaContado',
                  },
                ].map((fila) => (
                  <tr key={fila.nombre} className="border-t border-outline">
                    <th scope="row" className="p-2.5 font-medium">
                      {fila.nombre}
                      {'detalle' in fila && !modoCiego && <span className="block text-[11px] font-normal text-on-surface-variant">{fila.detalle}</span>}
                    </th>
                    <td className="p-2.5 font-mono">
                      {modoCiego ? '••••••' : formatSoles(fila.esperado)}
                    </td>
                    <td className="p-2.5">
                      <Input
                        id={fila.id}
                        type="number"
                        step="0.01"
                        min="0"
                        value={fila.valor}
                        onKeyDown={(event) => {
                          // Lo contado es lo que hay en mano: nunca negativo.
                          if (event.key === '-' || event.key === 'e' || event.key === 'E') event.preventDefault()
                        }}
                        onChange={(event) => fila.cambiar(event.target.value.replace(/^-+/, ''))}
                        aria-label={`${fila.nombre} contado`}
                        required
                        ref={fila.nombre === 'Efectivo' ? enfocarAlMontar : undefined}
                      />
                    </td>
                    <td
                      className={`p-2.5 font-mono font-semibold ${
                        Math.abs(fila.diferencia) < 0.01 ? 'text-emerald-700' : fila.diferencia > 0 ? 'text-amber-700' : 'text-error'
                      }`}
                    >
                      {modoCiego ? '••••••' : textoDiferencia(fila.diferencia)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {egresosDeMas.length > 0 && !modoCiego && (
            <Alert tono="info" titulo="Los egresos superan a los ingresos">
              {egresosDeMas.map(([nombre, monto]) => `${nombre}: ${formatSoles(Math.abs(monto))} más de egresos que de ingresos`).join(' · ')}.
              Cuenta solo lo que tienes en mano.
            </Alert>
          )}

          <div className="flex items-center justify-end">
            <button
              type="button"
              onClick={() => setMostrarCalculadora(!mostrarCalculadora)}
              className="flex items-center gap-1 text-xs text-primary font-semibold hover:underline"
            >
              <Calculator className="size-3.5" />
              {mostrarCalculadora ? 'Ocultar desglose' : 'Contar efectivo por billetes y monedas'}
            </button>
          </div>

          {mostrarCalculadora && (
            <div className="rounded-lg border border-primary/20 bg-primary-fixed/10 p-3 space-y-2">
              <span className="text-xs font-bold text-primary uppercase tracking-wider block">
                Calculadora de Arqueo Físico (Billetes y Monedas)
              </span>
              <div className="grid grid-cols-3 gap-2">
                {DENOMINACIONES.map((d) => (
                  <div key={d.valor} className="space-y-0.5">
                    <label className="text-xs text-on-surface-variant block font-medium">
                      {d.label}
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={cantidades[d.valor] || ''}
                      onChange={(e) => actualizarCantidad(d.valor, parseInt(e.target.value) || 0)}
                      placeholder="0"
                      className="w-full rounded border border-outline bg-surface-container-lowest px-2 py-1 text-xs font-mono text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
                    />
                  </div>
                ))}
              </div>
            </div>
          )}

          <Field label={`Observaciones de Cierre${hayDiferencia ? ' *' : ' (opcional)'}`}>
            <Input
              id="observacionesCierre"
              value={observaciones}
              onChange={(e) => setObservaciones(e.target.value)}
              placeholder={hayDiferencia ? 'Explica las diferencias por método' : 'Opcional'}
            />
          </Field>

          <div className="flex flex-wrap items-center justify-between gap-2 pt-3 border-t border-outline">
            <Button
              type="button"
              variant="secundario"
              onClick={compartirLiquidacionWhatsapp}
              className="text-xs text-emerald-700 border-emerald-300 hover:bg-emerald-50 dark:text-emerald-400"
              title="Compartir liquidación de cierre por WhatsApp al administrador"
            >
              <MessageCircle className="size-3.5" aria-hidden />
              Enviar Liquidación por WhatsApp
            </Button>
            <div className="flex items-center gap-2">
              <Button type="button" variant="secundario" onClick={alCerrar} disabled={cerrarMutation.isPending}>
                Cancelar
              </Button>
              <Button type="submit" variant="peligro" disabled={cerrarMutation.isPending}>
                {cerrarMutation.isPending ? 'Cerrando caja...' : 'Confirmar cierre'}
              </Button>
            </div>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
