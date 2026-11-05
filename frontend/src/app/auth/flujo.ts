import { createContext, useContext } from 'react'
import type { ErrorAcceso } from './api-auth'

/** Login con contraseña correcta y 2FA activo: falta el segundo factor. Solo vive en memoria (nunca en storage). */
export interface Pendiente2fa {
  tempToken: string
  /** Correo del usuario (para mostrar a quién se verifica). */
  email?: string
}

export type AvisoAcceso = ErrorAcceso | { tipo: 'info' | 'exito'; titulo?: string; mensaje: string }

export interface FlujoAcceso {
  pendiente2fa: Pendiente2fa | null
  setPendiente2fa: (p: Pendiente2fa | null) => void
  /** Aviso que debe mostrar la pestaña Login al volver a ella (p. ej. token temporal caducado). */
  aviso: AvisoAcceso | null
  setAviso: (a: AvisoAcceso | null) => void
  /** Aviso para la pestaña Ajustes 2FA al entrar con requiereConfigurar2fa. */
  requiereConfigurar: boolean
  setRequiereConfigurar: (v: boolean) => void
}

export const FlujoAccesoContext = createContext<FlujoAcceso | null>(null)

export function useFlujoAcceso(): FlujoAcceso {
  const flujo = useContext(FlujoAccesoContext)
  if (!flujo) throw new Error('useFlujoAcceso debe usarse dentro de <AccesoLayout>')
  return flujo
}
