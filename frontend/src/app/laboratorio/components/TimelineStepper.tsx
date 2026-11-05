import React from 'react'
import { CheckCircle2, FlaskConical, RotateCcw, Sparkles, XCircle } from 'lucide-react'
import type { EstadoOrden, PruebaLaboratorio } from '../types'

interface Props {
  estadoActual: EstadoOrden
  /** Pruebas ya registradas: si hay y el trabajo está en el laboratorio, volvió por un ajuste. */
  pruebasHechas?: number
  /** Detalle de las pruebas: se muestran bajo el avance. */
  pruebas?: PruebaLaboratorio[]
}

interface Paso {
  key: EstadoOrden
  etiqueta: string
  icono: React.ElementType
}

const PASOS: Paso[] = [
  { key: 'ENVIADO', etiqueta: '1. En laboratorio', icono: FlaskConical },
  { key: 'EN_PRUEBA', etiqueta: '2. Pruebas con el paciente', icono: Sparkles },
  { key: 'FINALIZADO', etiqueta: '3. Finalizado', icono: CheckCircle2 },
]

export function TimelineStepper({ estadoActual, pruebasHechas = 0, pruebas = [] }: Props) {
  if (estadoActual === 'CANCELADO') {
    return (
      <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-sm">
        <XCircle className="w-5 h-5 shrink-0" />
        <span className="font-semibold">Esta orden fue cancelada.</span>
      </div>
    )
  }

  const indiceActual = PASOS.findIndex((p) => p.key === estadoActual)
  const volvioPorAjuste = estadoActual === 'ENVIADO' && pruebasHechas > 0

  return (
    <div className="py-2">
      {volvioPorAjuste && (
        <div className="mb-3 flex items-center gap-2 p-2.5 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs">
          <RotateCcw className="w-4 h-4 shrink-0 text-amber-600" />
          <span>
            <strong>Prueba {pruebasHechas} hecha: necesitó un ajuste y volvió al laboratorio.</strong> Cuando llegue de nuevo, se hará la prueba {pruebasHechas + 1} con el paciente.
          </span>
        </div>
      )}

      <div className="relative flex items-center justify-between">
        <div className="absolute left-6 right-6 top-1/2 -translate-y-1/2 h-1 bg-slate-200 rounded-full z-0" />

        {PASOS.map((paso, i) => {
          const completado = i < indiceActual || estadoActual === 'FINALIZADO'
          const actual = i === indiceActual && estadoActual !== 'FINALIZADO'
          const Icono = paso.icono

          return (
            <div key={paso.key} className="relative z-10 flex flex-col items-center">
              <div
                className={`w-9 h-9 rounded-full flex items-center justify-center transition-all shadow-sm ${
                  actual
                    ? 'bg-indigo-600 text-white ring-4 ring-indigo-100 font-bold scale-110'
                    : completado
                      ? 'bg-emerald-600 text-white'
                      : 'bg-white text-slate-400 border-2 border-slate-300'
                }`}
              >
                {completado ? <CheckCircle2 className="w-5 h-5" /> : <Icono className="w-4 h-4" />}
              </div>
              <span
                className={`text-xs mt-1.5 font-medium whitespace-nowrap text-center ${
                  actual ? 'text-indigo-900 font-bold' : completado ? 'text-slate-800' : 'text-slate-400'
                }`}
              >
                {paso.etiqueta}
              </span>
            </div>
          )
        })}
      </div>

      {(pruebas.length > 0 || estadoActual === 'EN_PRUEBA') && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label="Pruebas con el paciente">
          {pruebas.map((p) => (
            <li
              key={p.id}
              className={`rounded-full border px-2.5 py-1 text-xs font-semibold ${
                p.resultado === 'CORRECTO' ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-amber-300 bg-amber-50 text-amber-800'
              }`}
            >
              Prueba {p.numero}: {p.resultado === 'CORRECTO' ? 'correcta' : 'necesitó ajuste'}
            </li>
          ))}
          {estadoActual === 'EN_PRUEBA' && (
            <li className="rounded-full border border-indigo-300 bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-800">
              Prueba {pruebas.length + 1}: en curso con el paciente
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
