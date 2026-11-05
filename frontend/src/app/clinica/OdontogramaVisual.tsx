import { useState, useRef, useEffect } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import { useRegistrarPiezaOdontogramaMutation, type OdontogramaRegistroDTO } from './api-odontograma'
import {
  GRUPOS_HERRAMIENTAS,
  HERRAMIENTAS_ODONTOGRAMA,
  HALLAZGOS_FRECUENTES,
  HERRAMIENTAS_EN_PALETA,
  buscarHerramienta,
  colorDeCara,
  esDePiezaCompleta,
  type ColorCara,
} from './herramientasOdontograma'
import {
  ALTO,
  ANCHO,
  CARAS_CORONA,
  CONTORNO,
  decoracionDeNumero,
  desplazamientoCorona,
  primitivasDeHallazgos,
  rutaRaiz,
  vecinosDe,
  type Primitiva,
} from './dienteNorma'
import { lineasEspecificaciones } from './especificaciones'

interface Props {
  pacienteId?: string
  registros: OdontogramaRegistroDTO[]
  alSeleccionarPieza?: (piezaFdi: number) => void
  puedeEditar?: boolean
  denticion?: 'PERMANENTE' | 'DECIDUA'
  onRegistrarNuevaPieza?: () => void
  onCambio?: () => void
}

// Cuadrantes Adultos (Permanentes) - 16 por arcada
const CUADRANTE_1 = [18, 17, 16, 15, 14, 13, 12, 11]
const CUADRANTE_2 = [21, 22, 23, 24, 25, 26, 27, 28]
const CUADRANTE_4 = [48, 47, 46, 45, 44, 43, 42, 41]
const CUADRANTE_3 = [31, 32, 33, 34, 35, 36, 37, 38]

// Cuadrantes Infantiles (Deciduos) - 10 por arcada
const CUADRANTE_5 = [55, 54, 53, 52, 51]
const CUADRANTE_6 = [61, 62, 63, 64, 65]
const CUADRANTE_8 = [85, 84, 83, 82, 81]
const CUADRANTE_7 = [71, 72, 73, 74, 75]

export function OdontogramaVisual({
  pacienteId,
  registros,
  alSeleccionarPieza,
  puedeEditar = true,
  denticion: denticionProp,
  onRegistrarNuevaPieza,
  onCambio,
}: Props) {
  const [tipoDenticionInterna, setTipoDenticionInterna] = useState<'PERMANENTE' | 'DECIDUA'>('PERMANENTE')
  const [herramientaActiva, setHerramientaActiva] = useState<string | null>('caries')
  const [verTodos, setVerTodos] = useState(false)
  const [piezaHover, setPiezaHover] = useState<number | null>(null)
  const [menuFlotantePieza, setMenuFlotantePieza] = useState<number | null>(null)
  const [notificacionRapida, setNotificacionRapida] = useState<string | null>(null)
  // Lista completa de hallazgos de la pieza tras la última acción, mientras llega la respuesta del servidor.
  const [piezasOptimistas, setPiezasOptimistas] = useState<Map<number, OdontogramaRegistroDTO[]>>(new Map())

  const tipoDenticion = denticionProp ?? tipoDenticionInterna
  const setTipoDenticion = setTipoDenticionInterna
  const menuRef = useRef<HTMLDivElement>(null)

  const registrarMutation = useRegistrarPiezaOdontogramaMutation()

  // Sincronizar optimistas cuando lleguen nuevos registros del servidor
  useAlCambiar([registros], () => setPiezasOptimistas(new Map()))

  // Cerrar menú flotante al hacer clic fuera
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuFlotantePieza(null)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Auto ocultar notificación rápida
  useEffect(() => {
    if (!notificacionRapida) return
    const timer = setTimeout(() => setNotificacionRapida(null), 2500)
    return () => clearTimeout(timer)
  }, [notificacionRapida])

  // Hallazgos vigentes de cada pieza (puede haber varios), fusionando lo que acaba de marcarse
  const mapaPiezas = new Map<number, OdontogramaRegistroDTO[]>()
  for (const reg of registros) {
    if (reg.activo === false) continue
    mapaPiezas.set(reg.piezaFdi, [...(mapaPiezas.get(reg.piezaFdi) ?? []), reg])
  }
  for (const [fdi, lista] of piezasOptimistas.entries()) {
    if (lista.length === 0) mapaPiezas.delete(fdi)
    else mapaPiezas.set(fdi, lista)
  }

  // Conteo dinámico para la leyenda
  const todosLosRegistros = Array.from(mapaPiezas.values()).flat().filter((r) => r.estado?.trim().toLowerCase() !== 'sano')
  const hallazgosActivos = todosLosRegistros
  const cuenta = (coincide: (id: string) => boolean) => todosLosRegistros.filter((r) => coincide(r.estado?.trim().toLowerCase() ?? '')).length
  const totalCaries = cuenta((id) => id === 'caries')
  const totalRestauraciones = cuenta((id) => id.startsWith('restauracion') || id === 'resina' || id === 'amalgama')
  const totalPulpares = cuenta((id) => ['endodoncia', 'pulpectomia', 'pulpotomia', 'endodoncia_mal_estado', 'pulpotomia_mal_estado'].includes(id))
  const totalCoronas = cuenta((id) => id.startsWith('corona'))
  const totalImplantes = cuenta((id) => id.startsWith('implante'))
  const totalAusentes = cuenta((id) => ['ausente', 'extraccion', 'exodoncia'].includes(id))
  const especificaciones = lineasEspecificaciones(todosLosRegistros)

  // Aplicar o desmarcar un hallazgo con 1 solo clic. Cada hallazgo de la pieza es independiente: marcar uno nuevo no borra
  // los demás, y quitarlo (o limpiar la pieza con «sano») es una acción aparte.
  function aplicarEstado1Click(fdi: number, estadoId: string, caraKey?: string) {
    if (!puedeEditar) return

    if (!pacienteId) {
      alSeleccionarPieza?.(fdi)
      return
    }

    const hallazgos = (mapaPiezas.get(fdi) ?? []).filter((h) => h.estado?.trim().toLowerCase() !== 'sano')
    const estadoNuevo = estadoId.trim().toLowerCase()
    const labelEstado = buscarHerramienta(estadoNuevo)?.label ?? estadoNuevo
    const ahora = new Date().toISOString()

    const registro = (estado: string, caras: string[]): OdontogramaRegistroDTO => ({
      id: `opt-${fdi}-${estado}`,
      pacienteId,
      piezaFdi: fdi,
      estado,
      caras,
      activo: true,
      registradoEn: ahora,
    })
    const dejar = (lista: OdontogramaRegistroDTO[], aviso: string) => {
      setPiezasOptimistas((prev) => new Map(prev).set(fdi, lista))
      setNotificacionRapida(aviso)
      setMenuFlotantePieza(null)
    }
    const enviar = (dto: { estado: string; caras: string[]; activo?: boolean; observacion?: string }) =>
      registrarMutation.mutate({ pacienteId, dto: { piezaFdi: fdi, ...dto } }, { onSuccess: () => onCambio?.() })

    // «Sano» limpia la pieza entera
    if (estadoNuevo === 'sano') {
      dejar([registro('sano', [])], `✓ Pieza ${fdi}: marcada como sana`)
      enviar({ estado: 'sano', caras: [], observacion: 'Pieza limpiada / sana' })
      return
    }

    const existente = hallazgos.find((h) => h.estado?.trim().toLowerCase() === estadoNuevo)
    const otros = hallazgos.filter((h) => h !== existente)

    const quitar = () => {
      dejar(otros, `✓ Pieza ${fdi}: ${labelEstado} eliminado`)
      enviar({ estado: estadoNuevo, caras: [], activo: false, observacion: 'Hallazgo eliminado por clic' })
    }

    if (existente) {
      const carasActuales = (existente.caras || []).map((c) => c.toUpperCase())
      if (caraKey && !esDePiezaCompleta(estadoNuevo)) {
        const cara = caraKey.toUpperCase()
        const marcada = carasActuales.length === 0 || carasActuales.includes(cara) || carasActuales.includes('TODAS')
        if (marcada) {
          const restantes = carasActuales.filter((c) => c !== cara && c !== 'TODAS')
          if (restantes.length === 0) {
            quitar()
          } else {
            dejar([...otros, registro(estadoNuevo, restantes)], `✓ Pieza ${fdi}: Cara ${cara} desmarcada`)
            enviar({ estado: estadoNuevo, caras: restantes })
          }
        } else {
          const nuevas = Array.from(new Set([...carasActuales, cara]))
          dejar([...otros, registro(estadoNuevo, nuevas)], `✓ Pieza ${fdi}: Cara ${cara} agregada`)
          enviar({ estado: estadoNuevo, caras: nuevas })
        }
        return
      }
      // Clic en el diente completo, o hallazgo de pieza completa: se quita
      quitar()
      return
    }

    // Hallazgo nuevo en la pieza
    const carasAEnviar = esDePiezaCompleta(estadoNuevo) ? [] : [(caraKey ?? 'O').toUpperCase()]
    const detalleCaras = carasAEnviar.length > 0 ? ` (${carasAEnviar.join(', ')})` : ''
    dejar([...otros, registro(estadoNuevo, carasAEnviar)], `✓ Pieza ${fdi}: ${labelEstado}${detalleCaras} aplicado`)
    enviar({ estado: estadoNuevo, caras: carasAEnviar })
  }

  // Clic en la pieza entera o cara (1 solo clic instantáneo)
  /** De entrada solo lo de uso diario (y lo que ya está elegido); «Ver todos» muestra los ~70 hallazgos. */
  const visibles = HERRAMIENTAS_EN_PALETA.filter((t) => verTodos || HALLAZGOS_FRECUENTES.has(t.id) || t.id === herramientaActiva)
  const ocultos = HERRAMIENTAS_EN_PALETA.length - visibles.length

  function manejarClickPieza(fdi: number, caraKey?: string) {
    if (!puedeEditar) return
    const herramienta = herramientaActiva || 'caries'
    aplicarEstado1Click(fdi, herramienta, caraKey)
  }

  /** Color de una cara: manda el hallazgo de esa cara sobre el de la pieza entera, y entre varios, el rojo. */
  function obtenerColorCara(hallazgos: OdontogramaRegistroDTO[], caraKey: string): ColorCara {
    return colorDeCara(hallazgos, caraKey)
  }

  // Qué hallazgos tiene cada pieza, para unir con una sola línea los trazos que abarcan varias piezas (prótesis, aparatos)
  const idsPorPieza = new Map<number, string[]>()
  for (const [fdi, lista] of mapaPiezas) idsPorPieza.set(fdi, lista.map((h) => buscarHerramienta(h.estado)?.id ?? ''))

  function renderizarPiezaDental(fdi: number, esSuperior: boolean) {
    const hallazgosPieza = mapaPiezas.get(fdi) ?? []
    const estados = hallazgosPieza.map((h) => h.estado?.trim().toLowerCase())
    const esAusente = estados.some((e) => ['ausente', 'extraccion', 'exodoncia'].includes(e))
    const esSano = hallazgosPieza.every((h) => h.estado?.trim().toLowerCase() === 'sano')
    const esHover = piezaHover === fdi
    const menuAbierto = menuFlotantePieza === fdi

    // Mapeo de caras
    const caraArriba = esSuperior ? 'V' : 'L'
    const caraAbajo = esSuperior ? 'P' : 'V'
    const caraCentro = 'O'
    const cuadrante = Math.floor(fdi / 10)
    const esLadoDerecho = cuadrante === 1 || cuadrante === 4 || cuadrante === 5 || cuadrante === 8
    const caraIzquierda = esLadoDerecho ? 'D' : 'M'
    const caraDerecha = esLadoDerecho ? 'M' : 'D'

    const colorArriba = obtenerColorCara(hallazgosPieza, caraArriba)
    const colorAbajo = obtenerColorCara(hallazgosPieza, caraAbajo)
    const colorCentro = obtenerColorCara(hallazgosPieza, caraCentro)
    const colorIzquierda = obtenerColorCara(hallazgosPieza, caraIzquierda)
    const colorDerecha = obtenerColorCara(hallazgosPieza, caraDerecha)

    // Herramientas de los hallazgos de la pieza (símbolos, siglas)
    const configs = hallazgosPieza.flatMap((h) => {
      const c = buscarHerramienta(h.estado)
      return c ? [c] : []
    })

    const decoracion = decoracionDeNumero(esAusente ? [] : configs)

    return (
      <div
        key={fdi}
        className="flex flex-col items-center gap-1 group relative select-none"
        onMouseEnter={() => setPiezaHover(fdi)}
        onMouseLeave={() => setPiezaHover(null)}
      >
        {/* Número de Pieza FDI - Clic para marcar pieza completa */}
        <button
          type="button"
          onClick={() => manejarClickPieza(fdi)}
          className={`relative font-mono text-xs font-black px-1 rounded transition-all ${
            esHover
              ? 'text-primary scale-110 bg-primary/10'
              : !esSano
              ? 'text-on-surface font-extrabold'
              : 'text-on-surface-variant/80 hover:text-primary'
          }`}
          title={`Pieza ${fdi} - Clic para marcar completa`}
        >
          {decoracion && (
            <svg aria-hidden viewBox="0 0 40 24" className="pointer-events-none absolute inset-0 m-auto h-6 w-10 overflow-visible" fill="none" stroke={decoracion.color} strokeWidth="1.8">
              {decoracion.tipo === 'triangulo' ? (
                <polygon points="20,0 38,23 2,23" />
              ) : (
                <>
                  <circle cx="14" cy="12" r="10" />
                  <circle cx="26" cy="12" r="10" />
                </>
              )}
            </svg>
          )}
          {fdi}
        </button>

        {/* Diente de la norma: contorno negro, raíz proporcional y 5 caras (solo se pintan las de caries y restauración) */}
        <div
          className={`relative w-8 sm:w-9 md:w-10 aspect-[100/165] rounded-sm transition-all ${
            menuAbierto ? 'ring-2 ring-primary ring-offset-1' : esHover ? 'ring-2 ring-primary/60 ring-offset-1' : ''
          }`}
        >
          <svg viewBox={`0 0 ${ANCHO} ${ALTO}`} className="size-full overflow-visible">
            <path
              d={rutaRaiz(esSuperior)}
              fill="#fff"
              stroke={CONTORNO}
              strokeWidth="3"
              className="cursor-pointer hover:opacity-80 transition-opacity"
              onClick={(e) => {
                e.stopPropagation()
                manejarClickPieza(fdi)
              }}
            />
            <g transform={`translate(0,${desplazamientoCorona(esSuperior)})`}>
              {(
                [
                  ['arriba', CARAS_CORONA.arriba, caraArriba, colorArriba],
                  ['derecha', CARAS_CORONA.derecha, caraDerecha, colorDerecha],
                  ['abajo', CARAS_CORONA.abajo, caraAbajo, colorAbajo],
                  ['izquierda', CARAS_CORONA.izquierda, caraIzquierda, colorIzquierda],
                  ['centro', CARAS_CORONA.centro, caraCentro, colorCentro],
                ] as const
              ).map(([clave, puntos, cara, color]) => (
                <polygon
                  key={clave}
                  points={puntos}
                  fill={color.fill}
                  stroke={color.contorno ?? CONTORNO}
                  strokeWidth={color.contorno ? 7 : 3}
                  className="cursor-pointer hover:opacity-80 transition-opacity"
                  onClick={(e) => {
                    e.stopPropagation()
                    manejarClickPieza(fdi, cara)
                  }}
                />
              ))}
            </g>
            {/* Símbolos de la norma: línea en la raíz, círculo, aspa del diente ausente, prótesis y aparatos en los ápices… */}
            <Primitivas lista={primitivasDeHallazgos(esSuperior, esAusente ? [buscarHerramienta('ausente')!] : configs, vecinosDe(fdi, idsPorPieza))} />
          </svg>
        </div>

        {/* Recuadro de siglas: letras sencillas, azules o rojas, como en la norma */}
        <div
          className="flex h-4 w-8 items-center justify-center overflow-hidden border border-zinc-700 bg-white sm:w-9 md:w-10"
          title={configs.map((c) => c.label).join(' · ')}
        >
          {configs.length > 0 && (
            <span className="flex items-center gap-0.5 font-mono text-[8px] font-bold leading-none sm:text-[9px]">
              {configs.slice(0, 2).map((c) => (
                <span key={c.id} style={{ color: c.colorHex }}>
                  {c.codigoCorto}
                </span>
              ))}
              {configs.length > 2 && <span className="text-zinc-500">+{configs.length - 2}</span>}
            </span>
          )}
        </div>

        {/* Popover Menú Rápido Flotante de 1 Clic (cuando se hace clic sin herramienta fija) */}
        {menuAbierto && (
          <div
            ref={menuRef}
            className="absolute top-12 left-1/2 -translate-x-1/2 z-50 bg-surface-container-lowest border border-outline-variant shadow-elevation-3 rounded-xl p-2.5 w-60 space-y-2 animate-in fade-in zoom-in-95 duration-150"
          >
            <div className="flex items-center justify-between pb-1.5 border-b border-outline-variant/30 text-xs font-bold text-on-surface">
              <span>Pieza {fdi} · Elegir Estado</span>
              <button
                type="button"
                onClick={() => setMenuFlotantePieza(null)}
                className="text-on-surface-variant hover:text-on-surface p-0.5 rounded"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-on-surface-variant">
              Haz 1 clic en el color para aplicar de inmediato:
            </p>

            <div className="grid grid-cols-2 gap-1.5">
              {visibles.map((tool) => (
                <button
                  key={tool.id}
                  type="button"
                  onClick={() => aplicarEstado1Click(fdi, tool.id)}
                  style={estados.includes(tool.id) ? { backgroundColor: tool.colorHex, borderColor: tool.strokeHex, color: '#ffffff' } : undefined}
                  className={`flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-xs font-bold border transition-all text-left ${
                    estados.includes(tool.id)
                      ? 'shadow-sm ring-1 ring-offset-1'
                      : 'bg-surface hover:bg-surface-container-low border-outline-variant/40 text-on-surface'
                  }`}
                >
                  <span className="text-xs" style={{ color: estados.includes(tool.id) ? '#ffffff' : tool.colorHex }}>{tool.icon}</span>
                  <span className="truncate">{tool.label}</span>
                </button>
              ))}
            </div>

            {alSeleccionarPieza && (
              <div className="pt-1 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => {
                    setMenuFlotantePieza(null)
                    alSeleccionarPieza(fdi)
                  }}
                  className="w-full text-center text-xs text-primary hover:underline font-semibold py-0.5"
                >
                  ⚙️ Opciones avanzadas y notas...
                </button>
              </div>
            )}
          </div>
        )}

        {/* Tooltip Hover Informativo */}
        {esHover && !menuAbierto && hallazgosPieza.length > 0 && (
          <div className="absolute -top-16 left-1/2 -translate-x-1/2 z-30 pointer-events-none rounded-lg bg-slate-900 text-white px-2.5 py-1.5 text-xs shadow-lg whitespace-nowrap space-y-0.5 animate-in fade-in duration-100">
            {hallazgosPieza.map((h) => {
              const c = buscarHerramienta(h.estado)
              return (
                <p key={h.id ?? h.estado} className="font-bold flex items-center gap-1">
                  <span style={{ color: c?.colorHex }}>{c?.icon}</span>
                  <span>
                    Pieza {fdi} · {c?.label || h.estado.toUpperCase()}
                    {h.caras && h.caras.length > 0 ? ` (${h.caras.join(', ')})` : ''}
                  </span>
                </p>
              )
            })}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 sm:p-6 shadow-card space-y-5">
      {/* ─────────── 1. Cabecera y Selector de Dentición ─────────── */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-outline-variant/30">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-display text-lg font-bold text-on-surface">
              Odontograma Clínico Universal (FDI)
            </h3>
            <Badge tono="primario">Norma Técnica MINSA</Badge>
            {registrarMutation.isPending && (
              <span className="text-xs text-primary font-medium flex items-center gap-1 animate-pulse">
                <span>●</span> Guardando cambio...
              </span>
            )}
          </div>
          <p className="text-xs text-on-surface-variant mt-0.5">
            Registro con 1 clic: elige una herramienta de color arriba y toca cualquier pieza o cara para diagnosticarla al instante.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Selector de Dentición */}
          <div className="inline-flex rounded-lg border border-outline-variant/40 p-0.5 bg-surface-container-low text-xs">
            <button
              type="button"
              onClick={() => setTipoDenticion('PERMANENTE')}
              className={`px-3 py-1.5 rounded-md font-bold transition-all ${
                tipoDenticion === 'PERMANENTE'
                  ? 'bg-surface-container-lowest text-primary shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              Dentición Adulta (32)
            </button>
            <button
              type="button"
              onClick={() => setTipoDenticion('DECIDUA')}
              className={`px-3 py-1.5 rounded-md font-bold transition-all ${
                tipoDenticion === 'DECIDUA'
                  ? 'bg-surface-container-lowest text-primary shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              Dentición Infantil (20)
            </button>
          </div>

          {puedeEditar && onRegistrarNuevaPieza && (
            <Button
              variant="secundario"
              onClick={onRegistrarNuevaPieza}
              className="text-xs py-1.5 px-3"
            >
              + Registro Manual Avanzado
            </Button>
          )}
        </div>
      </div>

      {/* ─────────── 2. Barra de Herramientas de 1 Clic (Color Palette) ─────────── */}
      {puedeEditar && (
        <section className="p-3.5 rounded-xl border border-primary/20 bg-primary/5 space-y-2.5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-on-surface uppercase tracking-wider flex items-center gap-1.5">
                <span className="size-2 rounded-full bg-primary animate-ping" />
                Herramientas de 1 Clic:
              </span>
              <span className="text-xs text-on-surface-variant hidden md:inline">
                (Elige un color y haz clic directamente en cualquier diente)
              </span>
            </div>

            {notificacionRapida && (
              <span className="text-xs font-bold text-primary bg-primary/10 px-2.5 py-1 rounded-full animate-in fade-in">
                {notificacionRapida}
              </span>
            )}
          </div>

          {/* Botones de Paleta de Colores, agrupados como en la norma: rojo en mal estado o por hacer, azul realizado */}
          <div className="space-y-3">
            {GRUPOS_HERRAMIENTAS.filter((grupo) => visibles.some((t) => t.grupo === grupo.id)).map((grupo) => (
              <div key={grupo.id} className="space-y-1.5">
                <p className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  {grupo.titulo}
                  {grupo.ayuda && <span className="ml-1.5 font-normal normal-case">· {grupo.ayuda}</span>}
                </p>
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
                  {visibles.filter((t) => t.grupo === grupo.id).map((tool) => {
                    const estaActiva = herramientaActiva === tool.id
                    return (
                      <button
                        key={tool.id}
                        type="button"
                        title={tool.sublabel}
                        aria-pressed={estaActiva}
                        onClick={() => setHerramientaActiva(tool.id)}
                        style={
                          estaActiva
                            ? { backgroundColor: tool.colorHex, color: '#ffffff', borderColor: tool.strokeHex }
                            : undefined
                        }
                        className={`group relative flex min-h-11 items-center justify-between gap-1.5 px-3 py-2 rounded-xl text-xs font-bold border transition-all text-left shadow-xs ${
                          estaActiva
                            ? 'ring-2 ring-offset-1 ring-primary shadow-md scale-[1.02]'
                            : 'bg-surface hover:bg-surface-container-low border-outline-variant/60 text-on-surface hover:border-primary/40'
                        }`}
                      >
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span className="text-sm shrink-0" style={{ color: estaActiva ? '#ffffff' : tool.colorHex }}>{tool.icon}</span>
                          <p className="leading-tight truncate font-extrabold">{tool.label}</p>
                        </div>
                        {estaActiva && (
                          <span className="shrink-0 text-xs font-black bg-black/20 text-white rounded-full px-1.5 py-0.5 shadow-xs">✓</span>
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
            {(ocultos > 0 || verTodos) && (
              <button
                type="button"
                onClick={() => setVerTodos((v) => !v)}
                className="text-xs font-bold text-primary underline-offset-4 hover:underline"
              >
                {verTodos ? 'Ver menos hallazgos' : `Ver todos los hallazgos (${ocultos} más)`}
              </button>
            )}
          </div>
        </section>
      )}

      {/* ─────────── 3. Esquema Anatómico Cuadrantes (Ajuste Perfecto Sin Scroll) ─────────── */}
      <div className="w-full py-1">
        <div className="space-y-4">
          {/* MAXILAR SUPERIOR */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between px-2 text-xs font-bold text-on-surface-variant">
              <span>Derecha del Paciente (Vestibular Sup.)</span>
              <span className="text-primary font-black uppercase tracking-widest text-xs flex items-center gap-1">
                ▲ Arcada Superior (Maxilar)
              </span>
              <span>Izquierda del Paciente (Vestibular Sup.)</span>
            </div>

            <div className="p-3 sm:p-4 rounded-xl border border-outline-variant/40 bg-surface-container-low/30 flex items-center justify-between sm:justify-center gap-1 sm:gap-2 md:gap-3 overflow-x-auto">
              {tipoDenticion === 'PERMANENTE' ? (
                <>
                  {/* Cuadrante 1: 18 - 11 */}
                  <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
                    {CUADRANTE_1.map((fdi) => renderizarPiezaDental(fdi, true))}
                  </div>
                  {/* Línea Media Superior */}
                  <div
                    className="w-0.5 h-16 bg-primary/40 rounded-full mx-1 shrink-0"
                    title="Línea Media Facial"
                  />
                  {/* Cuadrante 2: 21 - 28 */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    {CUADRANTE_2.map((fdi) => renderizarPiezaDental(fdi, true))}
                  </div>
                </>
              ) : (
                <>
                  {/* Cuadrante 5: 55 - 51 */}
                  <div className="flex items-center gap-2 shrink-0">
                    {CUADRANTE_5.map((fdi) => renderizarPiezaDental(fdi, true))}
                  </div>
                  <div
                    className="w-0.5 h-16 bg-primary/40 rounded-full mx-2 shrink-0"
                    title="Línea Media Facial"
                  />
                  {/* Cuadrante 6: 61 - 65 */}
                  <div className="flex items-center gap-2 shrink-0">
                    {CUADRANTE_6.map((fdi) => renderizarPiezaDental(fdi, true))}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* LÍNEA DE OCLUSIÓN CENTRAL */}
          <div className="flex items-center gap-3 px-4">
            <div className="h-px bg-outline-variant/40 flex-1" />
            <span className="text-xs font-bold text-on-surface-variant uppercase tracking-widest px-2 py-0.5 rounded-full bg-surface-container-low border border-outline-variant/30">
              Plano de Oclusión Dental
            </span>
            <div className="h-px bg-outline-variant/40 flex-1" />
          </div>

          {/* MANDÍBULA INFERIOR */}
          <div className="space-y-1.5">
            <div className="p-3 sm:p-4 rounded-xl border border-outline-variant/40 bg-surface-container-low/30 flex items-center justify-between sm:justify-center gap-1 sm:gap-2 md:gap-3 overflow-x-auto">
              {tipoDenticion === 'PERMANENTE' ? (
                <>
                  {/* Cuadrante 4: 48 - 41 */}
                  <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
                    {CUADRANTE_4.map((fdi) => renderizarPiezaDental(fdi, false))}
                  </div>
                  {/* Línea Media Inferior */}
                  <div
                    className="w-0.5 h-16 bg-primary/40 rounded-full mx-1 shrink-0"
                    title="Línea Media Facial"
                  />
                  {/* Cuadrante 3: 31 - 38 */}
                  <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
                    {CUADRANTE_3.map((fdi) => renderizarPiezaDental(fdi, false))}
                  </div>
                </>
              ) : (
                <>
                  {/* Cuadrante 8: 85 - 81 */}
                  <div className="flex items-center gap-2 shrink-0">
                    {CUADRANTE_8.map((fdi) => renderizarPiezaDental(fdi, false))}
                  </div>
                  <div
                    className="w-0.5 h-16 bg-primary/40 rounded-full mx-2 shrink-0"
                    title="Línea Media Facial"
                  />
                  {/* Cuadrante 7: 71 - 75 */}
                  <div className="flex items-center gap-2 shrink-0">
                    {CUADRANTE_7.map((fdi) => renderizarPiezaDental(fdi, false))}
                  </div>
                </>
              )}
            </div>

            <div className="flex items-center justify-between px-2 text-xs font-bold text-on-surface-variant">
              <span>Derecha del Paciente (Vestibular Inf.)</span>
              <span className="text-primary font-black uppercase tracking-widest text-xs flex items-center gap-1">
                ▼ Arcada Inferior (Mandíbula)
              </span>
              <span>Izquierda del Paciente (Vestibular Inf.)</span>
            </div>
          </div>
        </div>
      </div>

      {/* ─────────── 4. Leyenda Clínica Rápida con Contadores ─────────── */}
      <div className="pt-3 border-t border-outline-variant/30 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-bold text-on-surface">Resumen de Piezas:</span>

          {[
            { nombre: 'Caries', total: totalCaries, rojo: true },
            { nombre: 'Restauraciones', total: totalRestauraciones, rojo: false },
            { nombre: 'Tratamientos pulpares', total: totalPulpares, rojo: false },
            { nombre: 'Coronas', total: totalCoronas, rojo: false },
            { nombre: 'Implantes', total: totalImplantes, rojo: false },
            { nombre: 'Ausentes', total: totalAusentes, rojo: false },
          ].map((c) => (
            <span
              key={c.nombre}
              className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md border font-semibold ${
                c.rojo ? 'bg-red-50 text-red-700 border-red-200' : 'bg-blue-50 text-blue-700 border-blue-200'
              }`}
            >
              <span className={`size-2.5 rounded-full ${c.rojo ? 'bg-red-600' : 'bg-blue-700'}`} />
              {c.nombre}: <strong>{c.total}</strong>
            </span>
          ))}
        </div>

        <p className="text-xs text-on-surface-variant italic">
          💡 Toca una cara o número para diagnosticar. <strong>Vuelve a tocar la misma parte para desmarcarla y borrarla al instante (Toggle rápido)</strong>.
        </p>
      </div>

      {/* Cómo leer los colores (Norma Técnica: solo rojo y azul) */}
      <p className="text-xs text-on-surface-variant">
        <strong className="text-red-700">Rojo</strong>: caries, fractura, remanente radicular, temporales y tratamientos en mal estado.{' '}
        <strong className="text-blue-700">Azul</strong>: tratamientos en buen estado o realizados, diente ausente y anomalías. Las siglas van en el recuadro de cada pieza.
      </p>

      {/* Especificaciones: explica con claridad cada hallazgo (reglas 9 y 10 de la norma) */}
      <section aria-label="Especificaciones" className="rounded-sm border border-zinc-700 bg-white p-3">
        <h4 className="text-xs font-bold uppercase tracking-wider text-on-surface">Especificaciones</h4>
        {especificaciones.length === 0 ? (
          <p className="mt-1 text-sm italic text-on-surface-variant">Sin hallazgos registrados.</p>
        ) : (
          <p className="mt-1 text-sm leading-relaxed text-on-surface">
            {especificaciones.map((l) => (
              <span key={l.piezaFdi} className="mr-3 inline-block">
                <strong>{l.piezaFdi}:</strong> {l.texto}.
              </span>
            ))}
          </p>
        )}
        <p className="mt-2 text-xs text-on-surface-variant">
          El odontograma inicial no se altera: cada cambio queda registrado con su fecha y su responsable.
        </p>
      </section>

      {/* ─────────── 5. Resumen Clínico Inmediato "Lo que tiene el paciente" ─────────── */}
      {hallazgosActivos.length > 0 && (
        <section className="pt-3 border-t border-outline-variant/30 space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider flex items-center gap-1.5">
              <span>📋</span> Hallazgos Clínicos del Paciente ({hallazgosActivos.length})
            </h4>
            <span className="text-xs text-on-surface-variant">
              Toca ✕ para limpiar o corregir cualquier pieza en 1 clic
            </span>
          </div>

          <div className="flex flex-wrap gap-2">
            {hallazgosActivos.map((reg) => {
              const tool = HERRAMIENTAS_ODONTOGRAMA.find((h) => h.id === reg.estado?.toLowerCase())
              return (
                <div
                  key={reg.id || `${reg.piezaFdi}-${reg.estado}`}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border border-outline-variant/50 bg-surface shadow-xs text-xs font-bold"
                >
                  <span
                    className={`size-2 rounded-full ${tool?.colorHex ? '' : 'bg-slate-400'}`}
                    style={{ backgroundColor: tool?.colorHex }}
                  />
                  <span className="font-mono text-primary">Pieza #{reg.piezaFdi}</span>
                  <span
                    className="text-xs font-extrabold px-2 py-0.5 rounded text-white shadow-xs"
                    style={{ backgroundColor: tool?.colorHex || '#64748B' }}
                  >
                    {tool?.label || reg.estado}
                  </span>
                  {reg.caras && reg.caras.length > 0 && (
                    <span className="text-xs text-on-surface-variant font-mono">
                      ({reg.caras.join(', ')})
                    </span>
                  )}

                  {puedeEditar && (
                    <button
                      type="button"
                      onClick={() => aplicarEstado1Click(reg.piezaFdi, reg.estado)}
                      className="ml-1 text-on-surface-variant hover:text-error rounded px-1 hover:bg-error/10 text-xs"
                      title={`Quitar ${tool?.label ?? reg.estado} de la pieza ${reg.piezaFdi}`}
                    >
                      ✕
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        </section>
      )}
    </div>
  )
}

/** Dibuja los símbolos de la norma (las mismas primitivas que usa la hoja impresa). */
function Primitivas({ lista }: { lista: Primitiva[] }) {
  return (
    <>
      {lista.map((p, i) => {
        switch (p.t) {
          case 'linea':
            return <line key={i} x1={p.x1} y1={p.y1} x2={p.x2} y2={p.y2} stroke={p.color} strokeWidth={p.ancho} strokeLinecap="round" />
          case 'circulo':
            return <circle key={i} cx={p.cx} cy={p.cy} r={p.r} fill="none" stroke={p.color} strokeWidth={p.ancho} />
          case 'rect':
            return <rect key={i} x={p.x} y={p.y} width={p.w} height={p.h} fill="#fff" stroke={p.color} strokeWidth={p.ancho} />
          case 'poli':
            return <polyline key={i} points={p.puntos} fill="none" stroke={p.color} strokeWidth={p.ancho} strokeLinecap="round" strokeLinejoin="round" />
          case 'camino':
            return <path key={i} d={p.d} fill="none" stroke={p.color} strokeWidth={p.ancho} strokeLinecap="round" />
          case 'relleno':
            return <polygon key={i} points={p.puntos} fill={p.color} />
          case 'texto':
            return (
              <text key={i} x={p.x} y={p.y} fontSize={p.tam} fontWeight={800} fill={p.color} textAnchor="middle">
                {p.texto}
              </text>
            )
        }
      })}
    </>
  )
}
