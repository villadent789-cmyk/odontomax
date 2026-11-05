import React from 'react'
import { AlertCircle, CheckCircle2, FlaskConical, Sparkles, XCircle } from 'lucide-react'
import type { EstadoOrden } from '../types'

interface Props {
  estado: EstadoOrden
  tamano?: 'sm' | 'md' | 'lg'
}

interface ConfigEstado {
  etiqueta: string
  clase: string
  icono: React.ElementType
}

const CONFIG_ESTADOS: Record<EstadoOrden, ConfigEstado> = {
  ENVIADO: {
    etiqueta: 'En laboratorio',
    clase: 'bg-amber-50 text-amber-800 border-amber-300/80',
    icono: FlaskConical,
  },
  EN_PRUEBA: {
    etiqueta: 'En prueba',
    clase: 'bg-indigo-50 text-indigo-700 border-indigo-200/80',
    icono: Sparkles,
  },
  FINALIZADO: {
    etiqueta: 'Finalizado',
    clase: 'bg-emerald-50 text-emerald-800 border-emerald-300/80',
    icono: CheckCircle2,
  },
  CANCELADO: {
    etiqueta: 'Cancelado',
    clase: 'bg-slate-100 text-slate-600 border-slate-200',
    icono: XCircle,
  },
}

export function EstadoOrdenBadge({ estado, tamano = 'md' }: Props) {
  const config = CONFIG_ESTADOS[estado] ?? {
    etiqueta: estado,
    clase: 'bg-slate-100 text-slate-700 border-slate-200',
    icono: AlertCircle,
  }

  const Icono = config.icono

  const clasesTamano = {
    sm: 'text-xs px-2 py-0.5 gap-1',
    md: 'text-xs px-2.5 py-1 gap-1.5 font-medium',
    lg: 'text-sm px-3 py-1.5 gap-2 font-medium',
  }[tamano]

  const iconoTamano = {
    sm: 'w-3 h-3',
    md: 'w-3.5 h-3.5',
    lg: 'w-4 h-4',
  }[tamano]

  return (
    <span
      className={`inline-flex items-center rounded-full border shadow-[0_1px_2px_rgba(0,0,0,0.03)] ${config.clase} ${clasesTamano}`}
    >
      <Icono className={`${iconoTamano} shrink-0`} />
      <span>{config.etiqueta}</span>
    </span>
  )
}
