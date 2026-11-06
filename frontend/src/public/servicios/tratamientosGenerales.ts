/**
 * Tratamientos generales que se muestran en la web pública. Son familias de tratamientos, no cada procedimiento del
 * tarifario, y no llevan precios: el detalle y el costo se explican en la evaluación. Las fotos viven en
 * `public/tratamientos/<id>.webp`; mientras una no exista se muestra su ilustración `<id>.svg`.
 *
 * Cada tratamiento tiene su página de explicación (`/servicios/<id>`): una introducción, unos «datos importantes» y una
 * nota final. Los textos son orientativos: nunca prometen resultados ni precios; eso lo define el odontólogo al evaluar.
 * El orden de la lista es el orden en pantalla, y los que comparten `grupo` se muestran juntos.
 */
export interface TratamientoGeneral {
  id: string
  titulo: string
  /** Sección de la lista en la que aparece. */
  grupo: string
  /** Etiqueta pequeña sobre el título en la página de detalle (la familia a la que pertenece). */
  categoria: string
  /** Texto corto de la tarjeta. */
  descripcion: string
  /** Frase de entrada de la página de detalle. */
  introduccion: string
  /** Puntos del recuadro «Datos importantes». */
  datos: readonly string[]
  /** Aviso al pie del recuadro. */
  nota: string
  /** Foto del tratamiento: se busca `public/tratamientos/<id>.webp`. */
  imagen: string
  /** Ilustración que se muestra mientras no exista la foto. */
  respaldo: string
}

const IMG = (id: string) => ({ imagen: `/tratamientos/${id}.webp`, respaldo: `/tratamientos/${id}.svg` })

export const TRATAMIENTOS_GENERALES: readonly TratamientoGeneral[] = [
  // ─── Diagnóstico y prevención ───
  {
    id: 'consulta',
    titulo: 'Consulta y diagnóstico',
    grupo: 'Diagnóstico y prevención',
    categoria: 'Atención inicial',
    descripcion: 'Revisamos tu salud bucal, resolvemos tus dudas y te explicamos el tratamiento que necesitas, con un plan claro.',
    introduccion: 'Todo buen tratamiento empieza con una evaluación completa de tu salud bucal.',
    datos: [
      'Revisamos tus dientes, encías y mordida, y escuchamos lo que te preocupa.',
      'Si hace falta, indicamos radiografías para ver lo que no se aprecia a simple vista.',
      'Sales con un plan claro: qué tratar, en qué orden y cuánto tiempo puede tomar.',
    ],
    nota: 'El costo de cada tratamiento se define en la evaluación, cuando el odontólogo revisa tu caso.',
    ...IMG('consulta'),
  },
  {
    id: 'profilaxis',
    titulo: 'Profilaxis (limpieza dental)',
    grupo: 'Diagnóstico y prevención',
    categoria: 'Prevención',
    descripcion: 'Limpieza profesional que elimina la placa y el sarro y deja tus dientes lisos y tus encías más sanas.',
    introduccion: 'La profilaxis es la limpieza profesional que complementa tu cepillado diario.',
    datos: [
      'Eliminamos placa y sarro, incluso en las zonas donde el cepillo no llega.',
      'Deja la superficie de los dientes lisa, lo que ayuda a que se ensucien menos.',
      'Tu odontólogo te indica cada cuánto conviene repetirla según tu caso.',
    ],
    nota: 'Si tienes mucho sarro o las encías inflamadas, puede hacer falta una limpieza más profunda; lo define el odontólogo al evaluarte.',
    ...IMG('profilaxis'),
  },
  {
    id: 'fluorizacion',
    titulo: 'Fluorización',
    grupo: 'Diagnóstico y prevención',
    categoria: 'Prevención',
    descripcion: 'Aplicamos flúor y sellantes para fortalecer el esmalte y proteger tus dientes de las caries, en niños y adultos.',
    introduccion: 'El flúor fortalece el esmalte y ayuda a prevenir las caries.',
    datos: [
      'Se aplica de forma rápida y sin dolor, en la misma consulta.',
      'Es especialmente útil en niños y en personas con tendencia a las caries.',
      'Los sellantes protegen las muelas, donde más se acumulan los restos de comida.',
    ],
    nota: 'La frecuencia y el tipo de aplicación los indica el odontólogo según la edad y la salud bucal de cada paciente.',
    ...IMG('fluorizacion'),
  },

  // ─── Restauración, endodoncia y cirugía ───
  {
    id: 'restauraciones',
    titulo: 'Restauraciones',
    grupo: 'Restauración, endodoncia y cirugía',
    categoria: 'Odontología restauradora',
    descripcion: 'Reparamos dientes con caries o fracturados con resina estética del color de tu diente, devolviéndoles forma y función.',
    introduccion: 'Las restauraciones devuelven la forma y la función de un diente dañado por una caries o una fractura.',
    datos: [
      'Usamos resina del color de tu diente, para que el arreglo pase desapercibido.',
      'En la mayoría de los casos se realiza en una sola cita.',
      'Con buena higiene y controles periódicos, pueden durar muchos años.',
    ],
    nota: 'Mientras antes se trata una caries, más pequeña y sencilla es la restauración.',
    ...IMG('restauraciones'),
  },
  {
    id: 'endodoncia',
    titulo: 'Endodoncia',
    grupo: 'Restauración, endodoncia y cirugía',
    categoria: 'Tratamiento de conducto',
    descripcion: 'Tratamiento de conducto para salvar un diente con dolor o infección profunda, sin necesidad de extraerlo.',
    introduccion: 'La endodoncia permite conservar un diente que, de otro modo, se perdería.',
    datos: [
      'Se realiza con anestesia local para que el procedimiento sea cómodo.',
      'Elimina la infección y el dolor que provienen del nervio dañado.',
      'Después, el diente suele necesitar una restauración o corona para quedar protegido.',
    ],
    nota: 'Antes de empezar, el odontólogo revisa una radiografía de tu diente; si hace falta, te indicamos la que necesitas.',
    ...IMG('endodoncia'),
  },
  {
    id: 'endodoncia-especializada',
    titulo: 'Endodoncia especializada',
    grupo: 'Restauración, endodoncia y cirugía',
    categoria: 'Casos complejos',
    descripcion: 'Tratamiento de conducto en casos complejos: dientes con varias raíces o que necesitan ser tratados nuevamente.',
    introduccion: 'Algunos dientes necesitan una atención más cuidadosa: tienen varios conductos, conductos muy finos o ya fueron tratados antes.',
    datos: [
      'Atendemos dientes de varias raíces (como las muelas), que requieren más tiempo y precisión.',
      'También evaluamos el retratamiento de conductos cuando un tratamiento anterior no tuvo el resultado esperado.',
      'Se planifica con radiografías para conocer la forma de las raíces antes de empezar.',
    ],
    nota: 'Si tu diente puede conservarse o no lo define el odontólogo al evaluar tu caso con la radiografía.',
    ...IMG('endodoncia-especializada'),
  },
  {
    id: 'cirugia',
    titulo: 'Cirugía y extracciones',
    grupo: 'Restauración, endodoncia y cirugía',
    categoria: 'Cirugía oral',
    descripcion: 'Extracciones simples y de muelas del juicio, y cirugía oral menor, con anestesia local y cuidado en cada paso.',
    introduccion: 'Realizamos extracciones y cirugías orales menores con técnica cuidadosa y anestesia local.',
    datos: [
      'Atendemos extracciones simples y de muelas del juicio.',
      'Antes de operar se evalúa con radiografía para planificar con seguridad.',
      'Te damos indicaciones claras para cuidarte en casa y recuperarte mejor.',
    ],
    nota: 'Cada caso es distinto: la forma y el tiempo de la cirugía se definen en la evaluación.',
    ...IMG('cirugia'),
  },

  // ─── Prótesis y rehabilitación ───
  {
    id: 'coronas',
    titulo: 'Coronas y puentes',
    grupo: 'Prótesis y rehabilitación',
    categoria: 'Prótesis fija',
    descripcion: 'Cubrimos y reconstruimos dientes dañados con coronas, y reponemos dientes ausentes con puentes fijos.',
    introduccion: 'Una corona protege y reconstruye un diente muy deteriorado; un puente repone los dientes que faltan apoyándose en los vecinos.',
    datos: [
      'Se fabrican a medida, con materiales como porcelana o zirconio, según tu caso.',
      'Quedan fijas: no se retiran para dormir ni para limpiarlas como una prótesis removible.',
      'Se adaptan al color y la forma de tus dientes para que se vean naturales.',
    ],
    nota: 'El tipo de material y el número de piezas se deciden en la evaluación, revisando tu boca y tus radiografías.',
    ...IMG('coronas'),
  },
  {
    id: 'protesis',
    titulo: 'Prótesis removibles y totales',
    grupo: 'Prótesis y rehabilitación',
    categoria: 'Prótesis removible',
    descripcion: 'Reponemos varios dientes o toda la dentadura con prótesis removibles que se colocan y retiran con facilidad.',
    introduccion: 'Las prótesis removibles devuelven la masticación y la sonrisa cuando faltan varios dientes o todos.',
    datos: [
      'La prótesis parcial repone algunos dientes y se sostiene en los que conservas.',
      'La prótesis total reemplaza todos los dientes de una arcada.',
      'Se retiran para limpiarlas, y te enseñamos cómo cuidarlas.',
    ],
    nota: 'Después de colocarla pueden ser necesarios controles y ajustes hasta que te sientas cómodo; es parte normal del tratamiento.',
    ...IMG('protesis'),
  },
  {
    id: 'implante',
    titulo: 'Implante dental',
    grupo: 'Prótesis y rehabilitación',
    categoria: 'Rehabilitación oral',
    descripcion: 'Reemplazamos un diente perdido con un implante que funciona como una raíz nueva y sostiene una corona.',
    introduccion: 'Un implante dental es un pequeño tornillo que se coloca en el hueso y sostiene un diente nuevo.',
    datos: [
      'Permite reponer un diente sin desgastar los dientes vecinos.',
      'Antes de decidir se evalúan tu hueso y tu salud general con radiografías.',
      'El tratamiento se realiza en etapas y requiere buena higiene y controles periódicos.',
    ],
    nota: 'No todos los pacientes pueden recibir implantes: el odontólogo evalúa tu caso y te explica las alternativas.',
    ...IMG('implante'),
  },

  // ─── Ortodoncia y estética ───
  {
    id: 'ortodoncia',
    titulo: 'Ortodoncia',
    grupo: 'Ortodoncia y estética',
    categoria: 'Alineación dental',
    descripcion: 'Alineamos tus dientes y corregimos la mordida con brackets, para adultos, adolescentes y niños.',
    introduccion: 'La ortodoncia alinea los dientes y corrige la mordida, para una sonrisa armoniosa y una mejor salud bucal.',
    datos: [
      'Atendemos a niños, adolescentes y adultos.',
      'Los controles periódicos son parte del tratamiento y se agendan con anticipación.',
      'La higiene durante el tratamiento es clave: te enseñamos a cuidar tus brackets y tus dientes.',
    ],
    nota: 'La duración depende de cada caso y se explica después de la evaluación.',
    ...IMG('ortodoncia'),
  },
  {
    id: 'blanqueamiento',
    titulo: 'Blanqueamiento dental',
    grupo: 'Ortodoncia y estética',
    categoria: 'Estética dental',
    descripcion: 'Aclaramos el color de tus dientes de forma controlada, para una sonrisa más luminosa.',
    introduccion: 'El blanqueamiento aclara el color de tus dientes sin desgastarlos.',
    datos: [
      'Antes de empezar revisamos que tus dientes y encías estén sanos.',
      'Puede causar sensibilidad pasajera; te indicamos cómo cuidarte.',
      'El tono final depende del color de partida de cada diente.',
    ],
    nota: 'El blanqueamiento no cambia el color de coronas ni de restauraciones; el odontólogo te explica qué esperar en tu caso.',
    ...IMG('blanqueamiento'),
  },
  {
    id: 'estetica',
    titulo: 'Carillas y estética dental',
    grupo: 'Ortodoncia y estética',
    categoria: 'Estética dental',
    descripcion: 'Mejoramos la forma, el tamaño y el color de tus dientes con carillas, para una sonrisa armoniosa y natural.',
    introduccion: 'Las carillas son finas láminas que cubren la parte visible del diente para mejorar su forma y su color.',
    datos: [
      'Se elaboran en resina o porcelana, según lo que convenga a tu caso.',
      'Antes se revisa que tus dientes y encías estén sanos.',
      'Elegimos el color y la forma junto contigo, para un resultado natural.',
    ],
    nota: 'El resultado final depende de cada caso; el odontólogo evalúa si las carillas son la mejor opción para ti.',
    ...IMG('estetica'),
  },
]

export function buscarTratamiento(id: string | undefined): TratamientoGeneral | undefined {
  return TRATAMIENTOS_GENERALES.find((t) => t.id === id)
}

/** Los tratamientos agrupados por sección, respetando el orden de la lista. */
export function tratamientosPorGrupo(): { grupo: string; tratamientos: TratamientoGeneral[] }[] {
  const grupos: { grupo: string; tratamientos: TratamientoGeneral[] }[] = []
  for (const t of TRATAMIENTOS_GENERALES) {
    const existente = grupos.find((g) => g.grupo === t.grupo)
    if (existente) existente.tratamientos.push(t)
    else grupos.push({ grupo: t.grupo, tratamientos: [t] })
  }
  return grupos
}
