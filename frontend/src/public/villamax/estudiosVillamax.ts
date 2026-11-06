/**
 * Estudios generales de Villamax (centro radiológico) que se muestran en la web pública. Son familias de estudios, no
 * cada procedimiento del tarifario, y no llevan precios: el costo se informa por WhatsApp o en recepción. Las fotos
 * viven en `public/estudios/<id>.webp`; mientras una no exista se muestra su ilustración `<id>.svg`.
 *
 * Cada estudio tiene su página de explicación (`/villamax/<id>`): introducción, «datos importantes» y una nota final.
 * Los textos son orientativos: no prometen resultados ni precios, y la indicación del estudio la da el odontólogo o médico.
 */
export interface EstudioVillamax {
  id: string
  titulo: string
  /** Etiqueta pequeña sobre el título en la página de detalle. */
  categoria: string
  /** Texto corto de la tarjeta. */
  descripcion: string
  /** Frase de entrada de la página de detalle. */
  introduccion: string
  /** Puntos del recuadro «Datos importantes». */
  datos: readonly string[]
  /** Aviso al pie del recuadro. */
  nota: string
  /** Mensaje de WhatsApp si el estudio no cabe en la frase habitual «Hola, deseo información y agendar una …». */
  mensaje?: string
  /** Foto del estudio: se busca `public/estudios/<id>.webp`. */
  imagen: string
  /** Ilustración que se muestra mientras no exista la foto. */
  respaldo: string
}

const IMG = (id: string) => ({ imagen: `/estudios/${id}.webp`, respaldo: `/estudios/${id}.svg` })

export const ESTUDIOS_VILLAMAX: readonly EstudioVillamax[] = [
  {
    id: 'panoramica',
    titulo: 'Radiografía panorámica',
    categoria: 'Radiografía extraoral',
    descripcion: 'Una sola imagen que muestra todos tus dientes, los maxilares y las articulaciones de la mandíbula.',
    introduccion: 'La radiografía panorámica muestra en una sola imagen todos tus dientes y los huesos de la boca.',
    datos: [
      'Es rápida y no duele: permaneces de pie unos segundos mientras el equipo gira alrededor de tu cabeza.',
      'Antes de la toma te pedimos retirar objetos metálicos como aretes, collares y lentes.',
      'Recibes el estudio en formato digital, con el informe del especialista radiólogo.',
    ],
    nota: 'Si estás embarazada o crees estarlo, avísanos antes de la toma.',
    ...IMG('panoramica'),
  },
  {
    id: 'cefalometrica',
    titulo: 'Radiografía cefalométrica',
    categoria: 'Estudio para ortodoncia',
    descripcion: 'Imagen de perfil de la cabeza que el ortodoncista usa para planificar y controlar los tratamientos.',
    introduccion: 'La radiografía cefalométrica es una imagen de perfil del cráneo que muestra cómo se relacionan tus huesos y tus dientes.',
    datos: [
      'Se usa sobre todo para planificar y controlar tratamientos de ortodoncia.',
      'La toma es rápida y no duele.',
      'Entregamos la imagen digital con su informe, lista para llevar a tu odontólogo.',
    ],
    nota: 'Si tu odontólogo te pidió un trazado o medidas específicas, indícalo al consultar.',
    ...IMG('cefalometrica'),
  },
  {
    id: 'intraorales',
    titulo: 'Radiografías pequeñas',
    categoria: 'Radiografías intraorales',
    descripcion: 'Imágenes de detalle de un diente o de una zona concreta: periapical, bite wing y oclusal.',
    introduccion: 'Las radiografías pequeñas muestran con detalle un diente o una zona concreta de la boca.',
    datos: [
      'La periapical muestra el diente completo, con su raíz y el hueso que lo rodea.',
      'La bite wing ayuda a detectar caries entre los dientes.',
      'La oclusal muestra una zona más amplia del piso o del techo de la boca.',
    ],
    nota: 'Tu odontólogo te indica cuál necesitas; si no estás seguro, consúltanos por WhatsApp.',
    mensaje: 'Hola, deseo información y agendar radiografías pequeñas (periapical, bite wing u oclusal).',
    ...IMG('intraorales'),
  },
  {
    id: 'tomografia',
    titulo: 'Tomografía 3D Cone Beam',
    categoria: 'Imagen 3D',
    descripcion: 'Imagen tridimensional de alta definición de dientes, huesos y estructuras de la cara, para planificar con precisión.',
    introduccion: 'La tomografía Cone Beam crea una imagen en 3D de tus dientes y maxilares, con gran nivel de detalle.',
    datos: [
      'Es útil para planificar implantes, cirugías, endodoncias complejas y ortodoncia.',
      'El estudio dura pocos minutos y no duele.',
      'Recibes las imágenes en formato digital y el informe del especialista radiólogo.',
    ],
    nota: 'Antes de la toma retira objetos metálicos de la cabeza y el cuello; si estás embarazada, avísanos.',
    ...IMG('tomografia'),
  },
  {
    id: 'carpal',
    titulo: 'Radiografía carpal',
    categoria: 'Edad ósea',
    descripcion: 'Radiografía de la mano y la muñeca que ayuda a estimar la edad ósea, útil en ortodoncia y crecimiento.',
    introduccion: 'La radiografía carpal permite estimar la madurez ósea a partir de los huesos de la mano y la muñeca.',
    datos: [
      'Se usa para saber en qué etapa de crecimiento está un niño o un adolescente.',
      'Ayuda a elegir el momento más adecuado para algunos tratamientos de ortodoncia.',
      'La toma es rápida y no duele.',
    ],
    nota: 'Tu odontólogo o médico indicará si este estudio es necesario en tu caso.',
    ...IMG('carpal'),
  },
]

export function buscarEstudio(id: string | undefined): EstudioVillamax | undefined {
  return ESTUDIOS_VILLAMAX.find((e) => e.id === id)
}
