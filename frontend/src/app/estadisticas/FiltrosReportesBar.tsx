import { Calendar, RefreshCw, Printer } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import type { FiltrosReportes } from './types'

export type RangoPreset = 'hoy' | '7dias' | 'esteMes' | 'mesAnterior' | 'personalizado'

interface FiltrosReportesBarProps {
  filtros: FiltrosReportes
  presetRango: RangoPreset
  onChangePreset: (preset: RangoPreset) => void
  onChangeFiltros: (filtros: Partial<FiltrosReportes>) => void
  onRefrescar: () => void
  onImprimir: () => void
  cargando?: boolean
  esAdminGeneral?: boolean
}

export function FiltrosReportesBar({
  filtros,
  presetRango,
  onChangePreset,
  onChangeFiltros,
  onRefrescar,
  onImprimir,
  cargando,
}: FiltrosReportesBarProps) {
  return (
    <div className="flex flex-col gap-3 rounded-card border border-outline-variant/40 bg-surface-container-lowest p-4 shadow-card print:hidden">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {/* Presets de fecha */}
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
            <Calendar className="size-3.5" /> Periodo:
          </span>
          <button
            type="button"
            onClick={() => onChangePreset('hoy')}
            className={`rounded-panel px-3 py-1.5 text-xs font-medium transition-colors ${
              presetRango === 'hoy'
                ? 'bg-acento text-on-acento font-semibold shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Hoy
          </button>
          <button
            type="button"
            onClick={() => onChangePreset('7dias')}
            className={`rounded-panel px-3 py-1.5 text-xs font-medium transition-colors ${
              presetRango === '7dias'
                ? 'bg-acento text-on-acento font-semibold shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Últimos 7 días
          </button>
          <button
            type="button"
            onClick={() => onChangePreset('esteMes')}
            className={`rounded-panel px-3 py-1.5 text-xs font-medium transition-colors ${
              presetRango === 'esteMes'
                ? 'bg-acento text-on-acento font-semibold shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Este mes
          </button>
          <button
            type="button"
            onClick={() => onChangePreset('mesAnterior')}
            className={`rounded-panel px-3 py-1.5 text-xs font-medium transition-colors ${
              presetRango === 'mesAnterior'
                ? 'bg-acento text-on-acento font-semibold shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Mes anterior
          </button>
          <button
            type="button"
            onClick={() => onChangePreset('personalizado')}
            className={`rounded-panel px-3 py-1.5 text-xs font-medium transition-colors ${
              presetRango === 'personalizado'
                ? 'bg-acento text-on-acento font-semibold shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Personalizado
          </button>
        </div>

        {/* Acciones: Refrescar e Imprimir */}
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="secundario"
            size="sm"
            onClick={onRefrescar}
            disabled={cargando}
          >
            <RefreshCw className={`size-3.5 ${cargando ? 'animate-spin' : ''}`} />
            Actualizar
          </Button>
          <Button
            type="button"
            variant="secundario"
            size="sm"
            onClick={onImprimir}
          >
            <Printer className="size-3.5" />
            Imprimir
          </Button>
        </div>
      </div>

      {/* Rango personalizado */}
      {presetRango === 'personalizado' && (
        <div className="flex flex-wrap items-center gap-3 pt-1 border-t border-outline-variant/20 text-xs">
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-1 font-medium text-on-surface-variant">
              Desde:
              <input
                type="date"
                value={filtros.desde ?? ''}
                onChange={(e) => onChangeFiltros({ desde: e.target.value })}
                className="rounded-panel border border-outline-variant/60 bg-surface px-2 py-1 text-xs text-on-surface focus:border-primary focus:outline-none"
              />
            </label>
            <label className="flex items-center gap-1 font-medium text-on-surface-variant">
              Hasta:
              <input
                type="date"
                value={filtros.hasta ?? ''}
                onChange={(e) => onChangeFiltros({ hasta: e.target.value })}
                className="rounded-panel border border-outline-variant/60 bg-surface px-2 py-1 text-xs text-on-surface focus:border-primary focus:outline-none"
              />
            </label>
          </div>
        </div>
      )}
    </div>
  )
}
