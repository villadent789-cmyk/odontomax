import { obtenerNegocioActivo, obtenerToken } from '../auth/sesion'
import { crearClienteHttp } from './cliente'

export { crearClienteHttp, type ClienteHttp, type OpcionesCliente, type OpcionesPeticion } from './cliente'
export {
  BloqueoError,
  HttpError,
  NoAutorizadoError,
  SinPermisoError,
  leerRetryAfter,
  mensajeBloqueo,
  textoDeError,
} from './errores'

interface Manejadores {
  alNoAutorizado: () => void
  alProhibido: () => void
}

let manejadores: Manejadores | null = null

/** Se llama una vez en main.tsx para conectar el cliente con el router y la sesión. */
export function registrarManejadoresHttp(m: Manejadores) {
  manejadores = m
}

/**
 * Cliente http de la API del sistema (sin tipos). Las pantallas usan `apiTipada` de src/shared/api, que se
 * apoya en este cliente y en los tipos generados desde docs/api/openapi.json.
 */
export const api = crearClienteHttp({
  baseUrl: import.meta.env.VITE_API_URL ?? '/api/v1',
  obtenerToken,
  obtenerNegocio: obtenerNegocioActivo,
  alNoAutorizado: () => manejadores?.alNoAutorizado(),
  alProhibido: () => manejadores?.alProhibido(),
})
