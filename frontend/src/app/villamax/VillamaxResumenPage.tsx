import { useMemo, useState } from 'react'
import { ScanLine } from 'lucide-react'
import { useSesion } from '@/shared/auth'
import { useReporteFinanciero } from '../estadisticas/api-estadisticas'
import { VillamaxTab } from '../estadisticas/VillamaxTab'
import { RotuloMostrando } from '../layout/RotuloMostrando'

function formatearFechaISO(d: Date): string {
  const anio = d.getFullYear()
  const mes = String(d.getMonth() + 1).padStart(2, '0')
  const dia = String(d.getDate()).padStart(2, '0')
  return `${anio}-${mes}-${dia}`
}

/**
 * Resumen Operativo y Gerencial de Villamax 3D (Paso 6).
 * Reutiliza VillamaxTab directamente para no duplicar KPIs ni lógica financiera.
 */
export function VillamaxResumenPage() {
  const sesion = useSesion()
  const hoy = useMemo(() => new Date(), [])
  const fechaHoyISO = useMemo(() => formatearFechaISO(hoy), [hoy])
  const primerDiaMesISO = useMemo(() => {
    const d = new Date(hoy.getFullYear(), hoy.getMonth(), 1)
    return formatearFechaISO(d)
  }, [hoy])

  const [desde, setDesde] = useState(primerDiaMesISO)
  const [hasta, setHasta] = useState(fechaHoyISO)

  // El reporte financiero es solo de administradores; recepción y doctor ven el menú sin cifras.
  const rol = sesion?.usuario.rol
  const veFinanzas = rol === 'ADMIN_GENERAL' || rol === 'ADMIN_SEDE'
  const financiero = useReporteFinanciero(
    { sedeId: sesion?.sedeActivaId ?? undefined, desde, hasta, unidadNegocio: 'radiologia' },
    veFinanzas,
  )

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <ScanLine aria-hidden className="size-5 text-tertiary" />
            <h1 className="font-display text-2xl font-semibold tracking-tight text-on-surface">Resumen de Villamax 3D</h1>
            <RotuloMostrando />
          </div>
          <p className="text-sm text-on-surface-variant">Estudios cobrados, balance y comisiones del periodo.</p>
        </div>

        <div className="flex items-center gap-2">
          <label htmlFor="resumen-desde" className="sr-only">Fecha inicial</label>
          <input
            id="resumen-desde"
            type="date"
            value={desde}
            onChange={(e) => setDesde(e.target.value)}
            className="rounded-control border border-outline-variant/60 bg-surface-container-lowest px-2.5 py-1 text-xs text-on-surface"
          />
          <span className="text-xs text-on-surface-variant">a</span>
          <label htmlFor="resumen-hasta" className="sr-only">Fecha final</label>
          <input
            id="resumen-hasta"
            type="date"
            value={hasta}
            onChange={(e) => setHasta(e.target.value)}
            className="rounded-control border border-outline-variant/60 bg-surface-container-lowest px-2.5 py-1 text-xs text-on-surface"
          />
        </div>
      </div>

      {veFinanzas ? (
        <VillamaxTab
          financiero={financiero.data}
          desde={desde}
          hasta={hasta}
          cargando={financiero.isPending}
        />
      ) : (
        <p className="rounded-panel border border-outline-variant/60 bg-surface-container-lowest p-6 text-sm text-on-surface-variant">
          Usa el menú para registrar estudios, revisar la agenda y la caja de Villamax.
        </p>
      )}
    </div>
  )
}
