/**
 * Hallazgos y tratamientos del odontograma con su color, su símbolo y su sigla.
 *
 * Siguen la Norma Técnica del Odontograma (Minsa): para registrar hallazgos SOLO se usan el rojo y el azul.
 *  - AZUL: tratamientos en buen estado o realizados, y las anomalías (desgaste, ectópico, impactación, etc.).
 *  - ROJO: caries, fractura, remanente radicular, lo temporal y todo tratamiento en mal estado.
 * Las siglas son las de la norma (TC, PC, PP, CC, CF, CMC, CV, CJ, AM, R, IV, IM, IE, IMP, DES, DIS, E, I, SI, MAC, MIC,
 * M1–M3, RR…). Donde la norma usa un dibujo (flechas, triángulo, paréntesis) el sistema muestra la sigla en su lugar.
 * La norma permite sumar nomenclatura propia sin contradecir la oficial (regla 14): sellantes, fosas y fisuras, mancha
 * blanca, etc. quedan como extras.
 */

export type GrupoHerramienta = 'por_hacer' | 'realizado' | 'anomalia' | 'otro'
/** 'cara' se marca por superficie (oclusal, vestibular…); 'pieza' afecta al diente completo. */
export type NivelHerramienta = 'cara' | 'pieza'
export type SimboloHerramienta = 'ninguno' | 'conducto' | 'corona' | 'implante' | 'protesis' | 'aparato' | 'equis' | 'fractura' | 'sigla' | 'visto'

export interface HerramientaConfig {
  id: string
  label: string
  sublabel: string
  colorHex: string
  strokeHex: string
  bgPill: string
  borderPill: string
  textPill: string
  codigoCorto: string
  icon: string
  grupo: GrupoHerramienta
  nivel: NivelHerramienta
  simbolo: SimboloHerramienta
  /** Falso en los hallazgos que solo llevan su sigla y no pintan las caras del diente. */
  pintaCaras: boolean
  /** Falso en los hallazgos antiguos que se siguen leyendo pero ya no se ofrecen para marcar (los reemplaza uno más preciso). */
  enPaleta: boolean
}

/** Los dos únicos colores de la norma. */
const ROJO = { colorHex: '#D81E1E', strokeHex: '#B01515', bgPill: 'bg-red-600 text-white', borderPill: 'border-red-700', textPill: 'text-red-600', icon: '●' }
const AZUL = { colorHex: '#0B4FD6', strokeHex: '#093DA3', bgPill: 'bg-blue-700 text-white', borderPill: 'border-blue-800', textPill: 'text-blue-700', icon: '●' }
/** «Pieza sana» no es un hallazgo de la norma: es la ausencia de hallazgos, y se distingue de los dos colores. */
const SANO = { colorHex: '#0D9488', strokeHex: '#0F766E', bgPill: 'bg-teal-600 text-white', borderPill: 'border-teal-700', textPill: 'text-teal-700', icon: '●' }

type Paleta = typeof ROJO

function herramienta(
  id: string,
  label: string,
  sublabel: string,
  paleta: Paleta,
  codigoCorto: string,
  grupo: GrupoHerramienta,
  nivel: NivelHerramienta,
  simbolo: SimboloHerramienta = 'ninguno',
  enPaleta = true,
): HerramientaConfig {
  // Solo las caries y restauraciones (hallazgos «por cara») pintan caras; los demás se dibujan como símbolo o sigla.
  return { id, label, sublabel, codigoCorto, grupo, nivel, simbolo, pintaCaras: nivel === 'cara' || simbolo === 'visto', enPaleta, ...paleta }
}

export const HERRAMIENTAS_ODONTOGRAMA: HerramientaConfig[] = [
  // ── Rojo: caries, fractura, remanente radicular, temporales y tratamientos en mal estado ──
  herramienta('caries', 'Caries dental', 'Se pinta de rojo siguiendo su forma', ROJO, 'CARIES', 'por_hacer', 'cara'),
  herramienta('fractura', 'Fractura', 'Línea roja en el sentido de la fractura', ROJO, 'FRACT', 'por_hacer', 'pieza', 'fractura'),
  herramienta('remanente_radicular', 'Remanente radicular', '«RR» roja sobre la raíz', ROJO, 'RR', 'por_hacer', 'pieza', 'sigla'),
  herramienta('restauracion_temporal', 'Restauración temporal', 'Contorno rojo de la restauración', ROJO, 'RT', 'por_hacer', 'cara'),
  herramienta('corona_temporal', 'Corona temporal', 'Círculo rojo sobre la corona', ROJO, 'CT', 'por_hacer', 'pieza', 'corona'),
  herramienta('restauracion_mal_estado', 'Restauración en mal estado', 'Filtrada o fracturada: rehacer', ROJO, 'R', 'por_hacer', 'cara'),
  herramienta('corona_mal_estado', 'Corona definitiva en mal estado', 'Desadaptada o fracturada: rehacer', ROJO, 'CD', 'por_hacer', 'pieza', 'corona'),
  herramienta('endodoncia_mal_estado', 'Tratamiento de conducto en mal estado', 'Fallido: retratar (TC)', ROJO, 'TC', 'por_hacer', 'pieza', 'conducto'),
  herramienta('pulpotomia_mal_estado', 'Pulpotomía en mal estado', 'Tratamiento pulpar fallido (PP)', ROJO, 'PP', 'por_hacer', 'pieza', 'conducto'),
  herramienta('implante_mal_estado', 'Implante en mal estado', 'Perdido o con falla (IMP)', ROJO, 'IMP', 'por_hacer', 'pieza', 'implante'),
  herramienta('protesis_mal_estado', 'Prótesis en mal estado', 'Desadaptada o fracturada', ROJO, 'PRO', 'por_hacer', 'pieza', 'protesis'),
  herramienta('aparato_mal_estado', 'Aparato ortodóntico en mal estado', 'Brackets sueltos o dañados', ROJO, 'AO', 'por_hacer', 'pieza', 'aparato'),
  // Extras (no son de la norma): también son lesiones por tratar.
  herramienta('fosas_fisuras', 'Fosas y fisuras profundas', 'Superficie en riesgo (FFP)', ROJO, 'FFP', 'por_hacer', 'cara'),
  herramienta('sellante_mal_estado', 'Sellante en mal estado', 'Desgastado o perdido', ROJO, 'SELL', 'por_hacer', 'cara'),
  herramienta('mancha_blanca', 'Mancha blanca', 'Desmineralización inicial (MB)', ROJO, 'MB', 'anomalia', 'cara'),
  herramienta('lesion_periapical', 'Lesión periapical', 'Lesión en la raíz (LPA)', ROJO, 'LPA', 'anomalia', 'pieza', 'sigla'),

  // ── Azul: restauraciones (sigla del material) ──
  herramienta('restauracion', 'Restauración de resina', 'Se pinta de azul · sigla R', AZUL, 'R', 'realizado', 'cara'),
  herramienta('restauracion_am', 'Restauración de amalgama', 'Se pinta de azul · sigla AM', AZUL, 'AM', 'realizado', 'cara'),
  herramienta('restauracion_iv', 'Restauración de ionómero de vidrio', 'Se pinta de azul · sigla IV', AZUL, 'IV', 'realizado', 'cara'),
  herramienta('restauracion_im', 'Incrustación metálica', 'Se pinta de azul · sigla IM', AZUL, 'IM', 'realizado', 'cara'),
  herramienta('restauracion_ie', 'Incrustación estética', 'Se pinta de azul · sigla IE', AZUL, 'IE', 'realizado', 'cara'),
  // ── Azul: corona definitiva (círculo azul y la sigla del tipo) ──
  herramienta('corona_cc', 'Corona completa metálica', 'CC · solo coronas metálicas', AZUL, 'CC', 'realizado', 'pieza', 'corona'),
  herramienta('corona_cf', 'Corona fenestrada', 'CF · metálica con ventana vestibular', AZUL, 'CF', 'realizado', 'pieza', 'corona'),
  herramienta('corona_cmc', 'Corona metal cerámica', 'CMC · metálica revestida con material estético', AZUL, 'CMC', 'realizado', 'pieza', 'corona'),
  herramienta('corona_parcial', 'Corona parcial', '3/4, 4/5 o 7/8 · solo metálica', AZUL, '3/4', 'realizado', 'pieza', 'corona'),
  herramienta('corona_cv', 'Corona veneer', 'CV · metálica con frente estético', AZUL, 'CV', 'realizado', 'pieza', 'corona'),
  herramienta('corona_cj', 'Corona jacket', 'CJ · estética libre de metal', AZUL, 'CJ', 'realizado', 'pieza', 'corona'),
  herramienta('corona', 'Corona definitiva (sin tipo)', 'Registro antiguo: elige el tipo de corona', AZUL, 'CD', 'realizado', 'pieza', 'corona', false),
  // ── Azul: tratamiento pulpar, implante, prótesis y aparatos ──
  herramienta('endodoncia', 'Tratamiento de conductos', 'Línea azul en la raíz · TC', AZUL, 'TC', 'realizado', 'pieza', 'conducto'),
  herramienta('pulpectomia', 'Pulpectomía', 'Línea azul en la raíz · PC', AZUL, 'PC', 'realizado', 'pieza', 'conducto'),
  herramienta('pulpotomia', 'Pulpotomía', 'Línea azul en la raíz · PP', AZUL, 'PP', 'realizado', 'pieza', 'conducto'),
  herramienta('implante', 'Implante', 'Sigla IMP en la pieza reemplazada', AZUL, 'IMP', 'realizado', 'pieza', 'implante'),
  herramienta('protesis_fija', 'Prótesis fija', 'Línea azul con rayas en los pilares', AZUL, 'PF', 'realizado', 'pieza', 'protesis'),
  herramienta('protesis_removible', 'Prótesis removible', 'Dos líneas azules paralelas en los ápices', AZUL, 'PR', 'realizado', 'pieza', 'protesis'),
  herramienta('protesis_total', 'Prótesis total', 'Dos líneas azules sobre las coronas', AZUL, 'PT', 'realizado', 'pieza', 'protesis'),
  herramienta('aparato_ortodontico', 'Aparato ortodóntico fijo', 'Cuadrados con cruz en los ápices', AZUL, 'AOF', 'realizado', 'pieza', 'aparato'),
  herramienta('aparato_removible', 'Aparato ortodóntico removible', 'Zig-zag azul en los ápices', AZUL, 'AOR', 'realizado', 'pieza', 'aparato'),
  herramienta('sellante', 'Sellante', 'Sellante de fosas y fisuras (extra)', AZUL, 'SELL', 'realizado', 'cara'),

  // ── Azul: anomalías (todas azules en la norma) ──
  herramienta('movilidad_1', 'Movilidad grado 1', 'M1', AZUL, 'M1', 'anomalia', 'pieza', 'sigla'),
  herramienta('movilidad_2', 'Movilidad grado 2', 'M2', AZUL, 'M2', 'anomalia', 'pieza', 'sigla'),
  herramienta('movilidad_3', 'Movilidad grado 3', 'M3', AZUL, 'M3', 'anomalia', 'pieza', 'sigla'),
  herramienta('movilidad', 'Movilidad (sin grado)', 'Registro antiguo: elige el grado', AZUL, 'M', 'anomalia', 'pieza', 'sigla', false),
  herramienta('desgaste', 'Desgaste oclusal / incisal', 'DES', AZUL, 'DES', 'anomalia', 'pieza', 'sigla'),
  herramienta('discromico', 'Diente discrómico', 'DIS · cambio de color', AZUL, 'DIS', 'anomalia', 'pieza', 'sigla'),
  herramienta('ectopico', 'Diente ectópico', 'E', AZUL, 'E', 'anomalia', 'pieza', 'sigla'),
  herramienta('impactacion', 'Impactación', 'I', AZUL, 'I', 'anomalia', 'pieza', 'sigla'),
  herramienta('semi_impactacion', 'Semi-impactación', 'SI', AZUL, 'SI', 'anomalia', 'pieza', 'sigla'),
  herramienta('macrodoncia', 'Macrodoncia', 'MAC', AZUL, 'MAC', 'anomalia', 'pieza', 'sigla'),
  herramienta('microdoncia', 'Microdoncia', 'MIC', AZUL, 'MIC', 'anomalia', 'pieza', 'sigla'),
  herramienta('supernumerario', 'Supernumerario', 'S', AZUL, 'S', 'anomalia', 'pieza', 'sigla'),
  herramienta('diente_clavija', 'Diente en clavija', 'Triángulo azul en la norma', AZUL, 'CLAV', 'anomalia', 'pieza', 'sigla'),
  herramienta('extruido', 'Diente extruido', 'Flecha hacia el plano oclusal en la norma', AZUL, 'EXT', 'anomalia', 'pieza', 'sigla'),
  herramienta('intruido', 'Diente intruido', 'Flecha hacia el ápice en la norma', AZUL, 'INT', 'anomalia', 'pieza', 'sigla'),
  herramienta('migracion', 'Migración', 'Flecha horizontal en la norma', AZUL, 'MIG', 'anomalia', 'pieza', 'sigla'),
  herramienta('giroversion', 'Giroversión', 'Flecha curva en la norma', AZUL, 'GIR', 'anomalia', 'pieza', 'sigla'),
  herramienta('transposicion', 'Transposición', 'Dos flechas curvas entrecruzadas en la norma', AZUL, 'TRP', 'anomalia', 'pieza', 'sigla'),
  herramienta('diastema', 'Diastema', 'Paréntesis invertido entre las piezas en la norma', AZUL, 'DIA', 'anomalia', 'pieza', 'sigla'),
  herramienta('geminacion', 'Geminación', 'Dos circunferencias interceptadas en la norma', AZUL, 'GEM', 'anomalia', 'pieza', 'sigla'),
  herramienta('fusion', 'Fusión', 'Dos circunferencias interceptadas en la norma', AZUL, 'FUS', 'anomalia', 'pieza', 'sigla'),
  herramienta('edentulo_total', 'Edéntulo total', 'Línea azul sobre las coronas del maxilar', AZUL, 'ET', 'anomalia', 'pieza', 'sigla'),
  // Extras (no son de la norma)
  herramienta('dde', 'Defecto del esmalte', 'Defecto de desarrollo del esmalte (DDE)', AZUL, 'DDE', 'anomalia', 'cara'),
  herramienta('posicion_anormal', 'Posición anormal', 'Mal posicionado (PA)', AZUL, 'PA', 'anomalia', 'pieza', 'sigla'),
  herramienta('en_erupcion', 'En erupción', 'Pieza saliendo (ERU)', AZUL, 'ERU', 'anomalia', 'pieza', 'sigla'),

  // ── Diente ausente (aspa azul) y pieza sana ──
  herramienta('ausente', 'Diente ausente', 'Aspa azul sobre la pieza', AZUL, 'AUSENTE', 'otro', 'pieza', 'equis'),
  herramienta('sano', 'Pieza sana', 'Sin hallazgos: limpia la pieza y la deja marcada como sana', SANO, 'SANO', 'otro', 'pieza', 'visto'),
]

export const GRUPOS_HERRAMIENTAS: { id: GrupoHerramienta; titulo: string; ayuda: string }[] = [
  { id: 'por_hacer', titulo: 'Caries, fractura, temporales y mal estado', ayuda: 'Rojo' },
  { id: 'realizado', titulo: 'Tratamientos realizados o en buen estado', ayuda: 'Azul' },
  { id: 'anomalia', titulo: 'Anomalías y otros hallazgos', ayuda: 'Azul · llevan su sigla en la pieza' },
  { id: 'otro', titulo: 'Otros', ayuda: '' },
]

export function buscarHerramienta(estado?: string | null): HerramientaConfig | undefined {
  const clave = estado?.trim().toLowerCase()
  return HERRAMIENTAS_ODONTOGRAMA.find((h) => h.id === clave)
}

/** Los hallazgos que la norma pinta de rojo (caries, fractura, temporales, mal estado…). */
export const esHallazgoRojo = (h: HerramientaConfig) => h.colorHex === ROJO.colorHex

/** Lo que el doctor usa a diario: se ofrece de entrada; el resto está detrás de «Ver todos los hallazgos». */
export const HALLAZGOS_FRECUENTES = new Set([
  'caries', 'fractura', 'remanente_radicular', 'restauracion_temporal', 'corona_temporal',
  'restauracion_mal_estado', 'corona_mal_estado', 'endodoncia_mal_estado',
  'restauracion', 'restauracion_am', 'corona_cc', 'corona_cmc', 'corona_cj', 'endodoncia', 'pulpectomia', 'pulpotomia',
  'implante', 'protesis_fija', 'protesis_removible', 'protesis_total', 'aparato_ortodontico',
  'movilidad_1', 'movilidad_2', 'movilidad_3', 'desgaste', 'ausente', 'sano',
])

/** Lo que se ofrece para marcar: los hallazgos antiguos se siguen leyendo pero ya no se ofrecen. */
export const HERRAMIENTAS_EN_PALETA = HERRAMIENTAS_ODONTOGRAMA.filter((h) => h.enPaleta)

/** Estados que se marcan en el diente completo y no por superficie. */
export function esDePiezaCompleta(estado?: string | null): boolean {
  return buscarHerramienta(estado)?.nivel === 'pieza'
}

/** `contorno`: si existe, la cara se dibuja con ese borde grueso en lugar del negro (restauración temporal). */
export interface ColorCara {
  fill: string
  stroke: string
  contorno?: string
}

/** Relleno y borde con que se pinta un estado; un estado desconocido se ve en gris claro. */
export function colorDeEstado(estado?: string | null): ColorCara {
  const clave = estado?.trim().toLowerCase()
  if (clave === 'extraccion' || clave === 'exodoncia') return { fill: '#F1F5F9', stroke: '#94A3B8' }
  if (clave === 'ausente') return { fill: '#F1F5F9', stroke: AZUL.strokeHex }
  if (clave === 'resina' || clave === 'amalgama') return colorDeEstado('restauracion')
  // Restauración temporal: solo el contorno de la cara va en rojo, sin relleno.
  if (clave === 'restauracion_temporal') return { fill: '#FFFFFF', stroke: ROJO.strokeHex, contorno: ROJO.colorHex }
  const h = buscarHerramienta(clave)
  if (h?.id === 'sano') return { fill: '#CCFBF1', stroke: '#0D9488' }
  if (!h) return { fill: '#E2E8F0', stroke: '#94A3B8' }
  return { fill: h.colorHex, stroke: h.strokeHex }
}

/** Color de una cara: manda el hallazgo de esa cara sobre el de la pieza entera, y entre varios, el que está por hacer. */
export function colorDeCara(hallazgos: { estado: string; caras?: string[] | null }[], caraKey: string): ColorCara {
  const candidatos = hallazgos.filter((h) => {
    if (buscarHerramienta(h.estado)?.pintaCaras === false) return false
    const caras = (h.caras ?? []).map((c) => c.toUpperCase())
    return caras.length === 0 || caras.includes(caraKey) || caras.includes('TODAS')
  })
  if (candidatos.length === 0) return { fill: '#FFFFFF', stroke: hallazgos.length === 0 ? '#CBD5E1' : '#E2E8F0' }
  const propios = candidatos.filter((h) => (h.caras ?? []).length > 0)
  const lista = propios.length > 0 ? propios : candidatos
  const rojo = lista.find((h) => buscarHerramienta(h.estado)?.grupo === 'por_hacer')
  return colorDeEstado((rojo ?? lista[0]).estado)
}
