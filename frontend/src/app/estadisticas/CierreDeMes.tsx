import { CalendarCheck } from 'lucide-react'
import { useState } from 'react'
import { Alert, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Field, Input } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { api } from '@/shared/http'
import { imprimirDocumento } from '@/shared/imprimir'
import { mesActualISO, mesAnteriorISO, rangoDelMes } from './mesCierre'
import { documentoReporteGerencial } from './documentoReporteGerencial'
import type {
  ReporteCitasDTO,
  ReporteDeudoresDTO,
  ReporteDoctoresDTO,
  ReporteFinancieroDTO,
  ReporteInventarioDTO,
  ReportePacientesDTO,
} from './types'

/**
 * Cierre de mes de la sede activa: junta los reportes del mes elegido y los imprime (o guarda como PDF) en un solo
 * documento para el administrador. Solo se muestra en la pantalla de reportes, que ya exige rol de administración.
 */
export function CierreDeMes() {
  const sesion = useSesion()
  const [abierto, setAbierto] = useState(false)
  const [mes, setMes] = useState(() => mesAnteriorISO())
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const sede = sesion?.usuario.sedes.find((s) => s.id === sesion.sedeActivaId)
  const sedeNombre = sede?.nombre ?? 'Todas las sedes'
  const esVillamax = sesion?.negocioActivo === 'VILLAMAX'
  const mesValido = /^\d{4}-(0[1-9]|1[0-2])$/.test(mes)

  const generar = async () => {
    if (!mesValido) return
    setGenerando(true)
    setError(null)
    const { desde, hasta, nombre } = rangoDelMes(mes)
    const q = new URLSearchParams({ desde, hasta })
    if (sesion?.sedeActivaId) q.set('sedeId', sesion.sedeActivaId)
    const soloSede = sesion?.sedeActivaId ? `?sedeId=${encodeURIComponent(sesion.sedeActivaId)}` : ''
    try {
      const [financiero, citas, pacientes, doctores, inventario, deudores] = await Promise.all([
        api.get<ReporteFinancieroDTO>(`/reportes/financiero?${q}`),
        api.get<ReporteCitasDTO>(`/reportes/citas?${q}`),
        api.get<ReportePacientesDTO>(`/reportes/pacientes?${q}`),
        api.get<ReporteDoctoresDTO>(`/reportes/doctores?${q}`),
        api.get<ReporteInventarioDTO>(`/reportes/inventario${soloSede}`),
        api.get<ReporteDeudoresDTO>(`/reportes/deudores${soloSede}`),
      ])
      await imprimirDocumento(
        documentoReporteGerencial({
          negocio: esVillamax ? 'VILLAMAX' : 'VILLADENT',
          sedeNombre,
          titulo: `Cierre de mes · ${nombre}`,
          desde,
          hasta,
          financiero,
          citas,
          pacientes,
          doctores,
          inventario,
          deudores,
        }),
      )
      setAbierto(false)
    } catch {
      setError('No se pudo generar el cierre. Revisa tu conexión e inténtalo de nuevo.')
    } finally {
      setGenerando(false)
    }
  }

  return (
    <>
      <Button variant="secundario" onClick={() => setAbierto(true)}>
        <CalendarCheck aria-hidden />
        Cierre de mes
      </Button>
      <Dialog open={abierto} onOpenChange={(v) => !generando && setAbierto(v)}>
        <DialogContent className="max-w-md">
          <DialogTitle className="font-display text-lg font-bold text-primary">Cierre de mes</DialogTitle>
          <DialogDescription>
            Genera un solo documento con lo cobrado, gastado, atendido y pendiente de <strong>{sedeNombre}</strong> en el mes elegido.
            Puedes imprimirlo o guardarlo como PDF.
          </DialogDescription>
          <Field label="Mes a cerrar">
            <Input type="month" value={mes} max={mesActualISO()} onChange={(e) => setMes(e.target.value)} />
          </Field>
          {error && <Alert tono="error" titulo="No se generó el cierre">{error}</Alert>}
          <div className="flex justify-end gap-2">
            <Button variant="secundario" onClick={() => setAbierto(false)} disabled={generando}>
              Cancelar
            </Button>
            <Button onClick={() => void generar()} disabled={!mesValido || generando}>
              {generando ? 'Generando…' : 'Generar cierre'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
