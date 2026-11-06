/**
 * Datos de contacto de la web pública. Es el único lugar donde viven los números y las direcciones: la página de inicio,
 * el pie de página, Villamax, la privacidad y los botones de WhatsApp leen de aquí.
 * Los números de WhatsApp van con código de país y sin símbolos (51912192005).
 */

/** WhatsApp al que llegan las reservas de Villadent (consultorios). */
export const WHATSAPP_VILLADENT = '51912192005'
/** WhatsApp de Villamax (centro radiológico). */
export const WHATSAPP_VILLAMAX = '51983246878'

/** Número tal como se muestra en pantalla: 51912192005 → 912 192 005. */
export function telefonoVisible(whatsapp: string): string {
  const nacional = whatsapp.replace(/^51/, '')
  return nacional.replace(/(\d{3})(\d{3})(\d{3})/, '$1 $2 $3')
}

export interface ContactoSede {
  direccion: string
  referencia: string
  horario: string
  whatsapp: string
  /** Texto que busca Google Maps al pulsar «Cómo llegar». */
  mapa: string
}

export const CONTACTO_SEDES = {
  'sede-chilca-1': {
    direccion: 'Av. 9 de Diciembre 791-B, Chilca',
    referencia: 'Chilca, Huancayo · frente al Parque de los Héroes',
    horario: 'Lun - Sáb: 9:00 am - 8:00 pm',
    whatsapp: WHATSAPP_VILLADENT,
    mapa: 'Av. 9 de Diciembre 791-B, Chilca, Huancayo, Perú',
  },
  'sede-eltambo': {
    direccion: 'Jr. Huánuco y Mantaro',
    referencia: 'Huancayo · al costado de Gamarra',
    horario: 'Lun - Sáb: 9:00 am - 8:00 pm',
    whatsapp: WHATSAPP_VILLADENT,
    mapa: 'Jr. Huánuco y Mantaro, Huancayo, Perú',
  },
  'sede-villamax-chilca': {
    direccion: 'Av. 9 de Diciembre 789, 2.º piso, Chilca',
    referencia: 'Chilca, Huancayo · frente al Parque de los Héroes',
    horario: 'Lun - Sáb: 8:00 am - 7:30 pm',
    whatsapp: WHATSAPP_VILLAMAX,
    mapa: 'Av. 9 de Diciembre 789, Chilca, Huancayo, Perú',
  },
} as const satisfies Record<string, ContactoSede>

/** Enlace que abre WhatsApp con un mensaje ya escrito para que el paciente solo lo envíe. */
export function enlaceWhatsApp(mensaje: string, numero: string = WHATSAPP_VILLADENT): string {
  return `https://wa.me/${numero}?text=${encodeURIComponent(mensaje)}`
}

/** Enlace que abre Google Maps buscando la dirección. */
export function enlaceMapa(consulta: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(consulta)}`
}

/**
 * Mensaje que se deja escrito al consultar por un estudio de Villamax: «Hola, deseo información y agendar una
 * Radiografía panorámica.». Radiografías, tomografías y cefalometrías son femeninas; cualquier otro nombre va con «el estudio».
 */
export function mensajeEstudio(nombre: string): string {
  const limpio = nombre.trim()
  const femenino = /^(radiograf|tomograf|telerradiograf|cefalometr|ortopantomograf)/i.test(limpio)
  return `Hola, deseo información y agendar ${femenino ? 'una' : 'el estudio'} ${limpio}.`
}
