import { Fragment } from 'react'
import { Alert } from '@/design-system'
import type { AvisoAcceso } from './flujo'

/** Resalta "N minutos" del texto del backend, como la captura ("Intenta de nuevo en **15 minutos**"). */
function conMinutosResaltados(texto: string) {
  const partes = texto.split(/(\d+\s+minutos?)/i)
  return partes.map((parte, i) =>
    /^\d+\s+minutos?$/i.test(parte) ? <strong key={i}>{parte}</strong> : <Fragment key={i}>{parte}</Fragment>,
  )
}

const TITULOS = {
  error: 'Error de autenticación',
  bloqueo: 'Bloqueo preventivo del sistema',
  politica: 'Operación no permitida por política',
  info: 'Aviso',
  exito: 'Listo',
} as const

/**
 * Aviso de las pantallas de acceso. error/bloqueo/política usan role="alert" (se anuncian al aparecer); info usa
 * role="status". El texto es siempre el del backend: no se inventan minutos ni causas.
 */
export function AvisoAccesoAlert({
  aviso,
  titulo,
  onCerrar,
}: {
  aviso: AvisoAcceso
  titulo?: string
  onCerrar?: () => void
}) {
  const tituloFinal = titulo ?? ((aviso.tipo === 'info' || aviso.tipo === 'exito') && aviso.titulo ? aviso.titulo : TITULOS[aviso.tipo])
  const tono = aviso.tipo === 'politica' ? 'error' : aviso.tipo
  return (
    <Alert tono={tono} titulo={tituloFinal} onCerrar={onCerrar}>
      {aviso.tipo === 'bloqueo' ? conMinutosResaltados(aviso.mensaje) : aviso.mensaje}
    </Alert>
  )
}
