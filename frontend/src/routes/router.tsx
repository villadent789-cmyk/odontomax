import { NuevoTratamientoPage } from '@/app/tratamientos/NuevoTratamientoPage'
import { createRoute, createRouter, lazyRouteComponent, redirect, type RouterHistory } from '@tanstack/react-router'
import { AdministracionPage } from '@/app/administracion/AdministracionPage'
import { AgendaPage } from '@/app/agenda/AgendaPage'
import { AuditoriaPage } from '@/app/auditoria/AuditoriaPage'
import { BandejaPage } from '@/app/agenda/BandejaPage'
import { SinPermisoPage } from '@/app/comun/SinPermisoPage'
import { InicioPage } from '@/app/inicio/InicioPage'
import { AppShell } from '@/app/layout/AppShell'
import { obtenerSesion, requerirAcceso } from '@/shared/auth'
import { AccesoLayout } from '@/app/auth/AccesoLayout'
import { Ajustes2faPage } from '@/app/auth/Ajustes2faPage'
import { LoginPage } from '@/app/auth/LoginPage'
import { destinoSeguro } from '@/app/auth/sesion-real'
import { esClaveAvisoLogin, type ClaveAvisoLogin } from '@/app/auth/rutas'
import { SegundoFactorPage } from '@/app/auth/SegundoFactorPage'
import { ConsultaReservaPage } from '@/public/reserva/ConsultaReservaPage'
import { ReservaPage } from '@/public/reserva/ReservaPage'
import { ReservarPage } from '@/public/reserva/ReservarPage'
import { InicioPublicoPage } from '@/public/inicio/InicioPublicoPage'
import { ServiciosPage } from '@/public/servicios/ServiciosPage'
import { VillamaxPage } from '@/public/villamax/VillamaxPage'
import { PrivacidadPage } from '@/public/privacidad/PrivacidadPage'
import { PacientesPage } from '@/app/pacientes/PacientesPage'
import { FichaPacientePage } from '@/app/pacientes/FichaPacientePage'
import { CajaPage } from '@/app/caja/CajaPage'
import { InventarioPage } from '@/app/inventario/InventarioPage'
import { LaboratorioPage } from '@/app/laboratorio/LaboratorioPage'
import { RadiologiaPage } from '@/app/radiologia/RadiologiaPage'
import { EstadisticasPage } from '@/app/estadisticas/EstadisticasPage'
import { VillamaxResumenPage } from '@/app/villamax/VillamaxResumenPage'
import { EstudiosVillamaxPage } from '@/app/villamax/EstudiosVillamaxPage'
import { PublicLayout } from './PaginasPublicas'
import { rootRoute } from './root'

/* ───────────── Web pública ───────────── */

const publicoRoute = createRoute({ getParentRoute: () => rootRoute, id: 'publico', component: PublicLayout })

const inicioPublicoRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: '/',
  // Quien ya inició sesión entra directo al dashboard. La web pública sigue disponible en /servicios, /reservar y /villamax.
  beforeLoad: () => {
    if (obtenerSesion()) throw redirect({ to: '/app' })
  },
  component: InicioPublicoPage,
})
const serviciosRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'servicios',
  component: ServiciosPage,
})
const villamaxPublicoRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'villamax',
  component: VillamaxPage,
})
const privacidadRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'privacidad',
  component: PrivacidadPage,
})
export const reservarRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'reservar',
  validateSearch: (search: Record<string, unknown>): { sedeId?: string; servicioId?: string } => ({
    sedeId: typeof search.sedeId === 'string' ? search.sedeId : undefined,
    servicioId: typeof search.servicioId === 'string' ? search.servicioId : undefined,
  }),
  component: ReservarPage,
})
const consultarReservaRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'reserva',
  validateSearch: (search: Record<string, unknown>): { codigo?: string } => ({
    codigo: typeof search.codigo === 'string' ? search.codigo : undefined,
  }),
  component: function ConsultarReserva() {
    const { codigo } = consultarReservaRoute.useSearch()
    return <ConsultaReservaPage key={codigo} codigoInicial={codigo} />
  },
})

const consultaReservaRoute = createRoute({
  getParentRoute: () => publicoRoute,
  path: 'reserva/$codigo',
  component: function Reserva() {
    const { codigo } = consultaReservaRoute.useParams()
    return <ReservaPage key={codigo} codigo={codigo} />
  },
})

/* ───────────── Sistema interno (/app) ───────────── */

/* B1 · Login, 2FA y recuperación. Las pestañas comparten el estado del flujo (AccesoLayout). */

interface BusquedaAcceso {
  redirect?: string
  aviso?: ClaveAvisoLogin
}

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/app/login',
  validateSearch: (search: Record<string, unknown>): BusquedaAcceso => ({
    redirect: typeof search.redirect === 'string' ? search.redirect : undefined,
    aviso: esClaveAvisoLogin(search.aviso) ? search.aviso : undefined,
  }),
  component: AccesoLayout,
})

const loginInicioRoute = createRoute({
  getParentRoute: () => loginRoute,
  path: '/',
  beforeLoad: ({ search }) => {
    if (obtenerSesion()) throw redirect({ href: destinoSeguro(search.redirect) })
  },
  component: function Login() {
    const { redirect, aviso } = loginRoute.useSearch()
    return <LoginPage redirect={redirect} avisoInicial={aviso} />
  },
})

const segundoFactorRoute = createRoute({
  getParentRoute: () => loginRoute,
  path: '2fa',
  component: function SegundoFactor() {
    const { redirect } = loginRoute.useSearch()
    return <SegundoFactorPage redirect={redirect} />
  },
})

const ajustes2faRoute = createRoute({ getParentRoute: () => loginRoute, path: 'ajustes-2fa', component: Ajustes2faPage })

/** Catálogo de componentes. Solo existe en desarrollo: en producción la ruta no se registra ni se empaqueta. */
const designRoute = import.meta.env.DEV
  ? createRoute({
      getParentRoute: () => rootRoute,
      path: '/app/_design',
      component: lazyRouteComponent(() => import('@/app/design/DesignPage'), 'DesignPage'),
    })
  : null

const sistemaRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/app',
  beforeLoad: ({ location }) => requerirAcceso(undefined, location.href),
  component: AppShell,
})

const inicioSistemaRoute = createRoute({ getParentRoute: () => sistemaRoute, path: '/', component: InicioPage })
/** B2 · Bandeja del día de recepción. */
const bandejaRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'bandeja',
  beforeLoad: ({ location }) => requerirAcceso('bandeja', location.href),
  component: BandejaPage,
})

/** B3 · Agenda diaria y semanal (B4 detalle y B5 crear/reprogramar se abren desde aquí). */
const agendaRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'agenda',
  beforeLoad: ({ location }) => requerirAcceso('agenda', location.href),
  component: AgendaPage,
})

const nuevoTratamientoRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'nuevo-tratamiento',
  beforeLoad: ({ location }) => requerirAcceso('nuevoTratamiento', location.href),
  component: NuevoTratamientoPage,
})

const pacientesRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'pacientes',
  beforeLoad: ({ location }) => requerirAcceso('pacientes', location.href),
  component: PacientesPage,
})

const fichaPacienteRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'pacientes/$id',
  // ?tab=odontograma|clinica|… abre esa pestaña; ?desde=agenda|bandeja muestra "Volver" al llegar desde una cita.
  validateSearch: (search: Record<string, unknown>): { tab?: string; desde?: 'agenda' | 'bandeja' } => ({
    tab: typeof search.tab === 'string' ? search.tab : undefined,
    desde: search.desde === 'agenda' || search.desde === 'bandeja' ? search.desde : undefined,
  }),
  beforeLoad: ({ location }) => requerirAcceso('pacientes', location.href),
  component: FichaPacientePage,
})

const cajaRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'caja',
  beforeLoad: ({ location }) => requerirAcceso('caja', location.href),
  component: CajaPage,
})

const inventarioRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'inventario',
  beforeLoad: ({ location }) => requerirAcceso('inventario', location.href),
  component: InventarioPage,
})

const laboratorioRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'laboratorio',
  beforeLoad: ({ location }) => requerirAcceso('laboratorio', location.href),
  component: LaboratorioPage,
})

const radiologiaRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'radiologia',
  beforeLoad: ({ location }) => requerirAcceso('villamax', location.href),
  component: RadiologiaPage,
})

const villamaxResumenRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'villamax',
  beforeLoad: ({ location }) => requerirAcceso(undefined, location.href),
  component: VillamaxResumenPage,
})

const villamaxEstudiosRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'villamax/estudios',
  beforeLoad: ({ location }) => requerirAcceso(undefined, location.href),
  component: EstudiosVillamaxPage,
})

const estadisticasRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'estadisticas',
  beforeLoad: ({ location }) => requerirAcceso('estadisticas', location.href),
  component: EstadisticasPage,
})

/** Módulo 8 · Administración (doctores y horarios). Vive en /app/configuracion con el permiso 'configuracion'. */
const administracionRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'configuracion',
  beforeLoad: ({ location }) => requerirAcceso('configuracion', location.href),
  component: AdministracionPage,
})

/** Módulo 7 · Auditoría global (solo ADMIN_GENERAL). */
const auditoriaRoute = createRoute({
  getParentRoute: () => sistemaRoute,
  path: 'auditoria',
  beforeLoad: ({ location }) => requerirAcceso('auditoria', location.href),
  component: AuditoriaPage,
})

const sinPermisoRoute = createRoute({ getParentRoute: () => sistemaRoute, path: 'sin-permiso', component: SinPermisoPage })

export const routeTree = rootRoute.addChildren([
  publicoRoute.addChildren([inicioPublicoRoute, serviciosRoute, villamaxPublicoRoute, privacidadRoute, reservarRoute, consultarReservaRoute, consultaReservaRoute]),
  loginRoute.addChildren([loginInicioRoute, segundoFactorRoute, ajustes2faRoute]),
  ...(designRoute ? [designRoute] : []),
  sistemaRoute.addChildren([
    inicioSistemaRoute,
    bandejaRoute,
    agendaRoute,
    pacientesRoute,
    nuevoTratamientoRoute,
    fichaPacienteRoute,
    cajaRoute,
    inventarioRoute,
    laboratorioRoute,
    radiologiaRoute,
    villamaxResumenRoute,
    villamaxEstudiosRoute,
    estadisticasRoute,
    auditoriaRoute,
    administracionRoute,
    sinPermisoRoute,
  ]),
])

export function crearRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history, defaultPreload: 'intent', scrollRestoration: true })
}

export const router = crearRouter()

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
