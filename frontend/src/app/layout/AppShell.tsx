import { Outlet } from '@tanstack/react-router'
import { lazy, Suspense, useEffect, useState } from 'react'
import { useSesion } from '@/shared/auth'
import { etiquetarTablas } from '@/shared/tablasMovil'
import { Cabecera } from './Cabecera'
import { EncabezadoImpresion } from './EncabezadoImpresion'
import { Sidebar } from './Sidebar'

// Franja de datos de ejemplo: solo existe en desarrollo (en producción esta rama se elimina del bundle).
const DevBanner = import.meta.env.DEV ? lazy(() => import('@/dev-fixtures/DevBanner')) : null

const CLAVE_CONTRAIDO = 'odontomax.menuContraido'

function leerContraido(): boolean {
  try {
    const guardado = window.localStorage.getItem(CLAVE_CONTRAIDO)
    if (guardado !== null) return guardado === '1'
    return false
  } catch {
    return false
  }
}

/** Carcasa del sistema interno (B0): barra lateral por rol + cabecera + contenido. */
export function AppShell() {
  const sesion = useSesion()
  const [contraido, setContraido] = useState(leerContraido)
  // En celular el menú es un panel que se abre con el botón de la cabecera.
  const [menuMovil, setMenuMovil] = useState(false)

  useEffect(() => {
    if (!menuMovil) return
    const alTeclear = (e: KeyboardEvent) => e.key === 'Escape' && setMenuMovil(false)
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [menuMovil])

  useEffect(() => {
    try {
      window.localStorage.setItem(CLAVE_CONTRAIDO, contraido ? '1' : '0')
    } catch {
      /* almacenamiento no disponible: se ignora */
    }
  }, [contraido])

  // En celular las tablas se ven como tarjetas: se rotulan sus celdas cada vez que cambia el contenido.
  useEffect(() => {
    const principal = document.getElementById('contenido')
    if (!principal) return
    let pendiente = 0
    const rotular = () => {
      cancelAnimationFrame(pendiente)
      pendiente = requestAnimationFrame(() => etiquetarTablas(principal))
    }
    rotular()
    const observador = new MutationObserver(rotular)
    observador.observe(principal, { childList: true, subtree: true })
    return () => {
      cancelAnimationFrame(pendiente)
      observador.disconnect()
    }
  }, [])

  // El color de acción depende del negocio; va en <html> para que también lo hereden los diálogos (se montan en <body>).
  const negocio = sesion?.negocioActivo
  useEffect(() => {
    if (negocio) document.documentElement.dataset.negocio = negocio
    return () => {
      delete document.documentElement.dataset.negocio
    }
  }, [negocio])

  // Sin sesión no se pinta nada: quien la cierra (logout o el manejador de 401) ya navega al login, y la
  // guarda de ruta (beforeLoad) protege cualquier navegación posterior.
  if (!sesion) return null

  return (
    <div className="flex min-h-dvh bg-surface text-on-surface">
      <a
        href="#contenido"
        className="sr-only z-50 rounded-pill bg-primary px-4 py-2 font-bold text-on-primary focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>
      <Sidebar className="hidden md:flex" rol={sesion.usuario.rol} negocio={sesion.negocioActivo} contraido={contraido} onAlternar={() => setContraido((c) => !c)} />
      {menuMovil && (
        <div className="fixed inset-0 z-50 flex md:hidden print:hidden" role="dialog" aria-modal="true" aria-label="Menú principal">
          <Sidebar
            className="relative z-10 w-72 max-w-[85vw] shadow-raised"
            rol={sesion.usuario.rol}
            negocio={sesion.negocioActivo}
            contraido={false}
            onAlternar={() => setMenuMovil(false)}
            onNavegar={() => setMenuMovil(false)}
          />
          <button type="button" aria-label="Cerrar menú" onClick={() => setMenuMovil(false)} className="flex-1 bg-primary/40 backdrop-blur-[1px]" />
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        {DevBanner && (
          <Suspense fallback={null}>
            <DevBanner />
          </Suspense>
        )}
        <Cabecera sesion={sesion} onAbrirMenu={() => setMenuMovil(true)} />
        <main id="contenido" tabIndex={-1} className="flex-1 px-4 py-6 focus:outline-none md:px-8 md:py-8 print:p-0 print:m-0 print:w-full">
          <EncabezadoImpresion sesion={sesion} />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
