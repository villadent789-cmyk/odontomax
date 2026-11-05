import { useState } from 'react'
import type { Cita } from './api-agenda'
import type { PrefijoCita } from './NuevaCitaDialog'

/**
 * Estado de los diálogos de una cita compartidos por la bandeja (B2) y la agenda (B3): detalle (B4), cancelar y
 * reprogramar (B5). El detalle se actualiza con la cita que devuelve el backend tras cada acción.
 */
export function useDialogosCita() {
  const [detalle, setDetalle] = useState<Cita | null>(null)
  const [cancelando, setCancelando] = useState<Cita | null>(null)
  const [reprogramando, setReprogramando] = useState<Cita | null>(null)
  const [nueva, setNueva] = useState<PrefijoCita | null>(null)

  function actualizada(c: Cita) {
    setDetalle((d) => (d && d.id === c.id ? c : d))
  }

  return {
    detalle,
    cancelando,
    reprogramando,
    nueva,
    verDetalle: (c: Cita) => setDetalle(c),
    cerrarDetalle: () => setDetalle(null),
    cancelar: (c: Cita) => setCancelando(c),
    cerrarCancelar: () => setCancelando(null),
    reprogramar: (c: Cita) => setReprogramando(c),
    cerrarReprogramar: () => setReprogramando(null),
    crear: (prefijo: PrefijoCita = {}) => setNueva(prefijo),
    cerrarNueva: () => setNueva(null),
    actualizada,
  }
}

export type EstadoDialogosCita = ReturnType<typeof useDialogosCita>
