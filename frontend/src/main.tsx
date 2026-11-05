import '@fontsource/bricolage-grotesque/latin-600.css'
import '@fontsource/bricolage-grotesque/latin-700.css'
import '@fontsource/plus-jakarta-sans/latin-400.css'
import '@fontsource/plus-jakarta-sans/latin-400-italic.css'
import '@fontsource/plus-jakarta-sans/latin-500.css'
import '@fontsource/plus-jakarta-sans/latin-600.css'
import '@fontsource/plus-jakarta-sans/latin-700.css'
import './index.css'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RUTA_LOGIN, RUTA_SIN_PERMISO, limpiarSesion } from './shared/auth'
import { BloqueoError, NoAutorizadoError, SinPermisoError, registrarManejadoresHttp } from './shared/http'
import { router } from './routes/router'

registrarManejadoresHttp({
  alNoAutorizado: () => {
    limpiarSesion()
    void router.navigate({
      to: RUTA_LOGIN,
      search: { redirect: router.state.location.href, aviso: 'sesion-vencida' },
    })
  },
  alProhibido: () => {
    void router.navigate({ to: RUTA_SIN_PERMISO })
  },
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 0,
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
      // No se reintenta lo que no va a cambiar solo: sesión vencida, sin permiso o bloqueo.
      retry: (intentos, error) =>
        !(error instanceof NoAutorizadoError || error instanceof SinPermisoError || error instanceof BloqueoError) &&
        intentos < 1,
    },
  },
})

// La sesión es siempre la real (login B1). En desarrollo, la sesión de ejemplo de src/dev-fixtures solo se usa si
// se pide explícitamente desde la franja coral de la pantalla de acceso.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
