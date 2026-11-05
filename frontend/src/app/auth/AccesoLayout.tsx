import { Outlet } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { FlujoAccesoContext, type AvisoAcceso, type FlujoAcceso, type Pendiente2fa } from './flujo'
import { PantallaAcceso } from './PantallaAcceso'

/**
 * Ruta padre de /app/login/*: guarda en memoria el estado del flujo (token temporal del 2FA, avisos) mientras se
 * pasa de una pestaña a otra. El token temporal nunca se escribe en storage ni en la URL.
 */
export function AccesoLayout() {
  const [pendiente2fa, setPendiente2fa] = useState<Pendiente2fa | null>(null)
  const [aviso, setAviso] = useState<AvisoAcceso | null>(null)
  const [requiereConfigurar, setRequiereConfigurar] = useState(false)

  const flujo = useMemo<FlujoAcceso>(
    () => ({ pendiente2fa, setPendiente2fa, aviso, setAviso, requiereConfigurar, setRequiereConfigurar }),
    [pendiente2fa, aviso, requiereConfigurar],
  )

  return (
    <FlujoAccesoContext.Provider value={flujo}>
      <PantallaAcceso>
        <Outlet />
      </PantallaAcceso>
    </FlujoAccesoContext.Provider>
  )
}
