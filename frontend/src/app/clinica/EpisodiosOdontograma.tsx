import { ArrowRight, ClipboardCheck, Eye, GitCompare, History, Printer } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Alert, Button, Card, Dialog, DialogContent, DialogDescription, DialogTitle, Input, Skeleton, toast } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { mensajeError } from '@/shared/errores'
import { formatFecha } from '@/shared/format'
import { imprimirDocumento } from '@/shared/imprimir'
import {
  useAbrirEpisodio,
  useCerrarEpisodio,
  useEpisodiosOdontograma,
  type EpisodioOdontograma,
  type PiezaInstantanea,
} from './api-episodios-odontograma'
import { dienteSvg, documentoOdontograma, type CapturaImpresa } from './documentoOdontograma'
import { buscarHerramienta } from './herramientasOdontograma'

const SUPERIOR = [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28]
const INFERIOR = [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38]

interface Captura {
  clave: string
  etiqueta: string
  fecha: string
  piezas: PiezaInstantanea[]
}

/** Todas las capturas en orden cronológico: inicial y final de cada episodio. */
function capturasDe(episodios: EpisodioOdontograma[]): Captura[] {
  const lista: Captura[] = []
  for (const e of [...episodios].sort((a, b) => a.numero - b.numero)) {
    lista.push({ clave: `${e.id}:inicial`, etiqueta: `Episodio ${e.numero} · inicial`, fecha: e.inicialEn, piezas: e.inicial })
    if (e.finalEn && e.finalPiezas) {
      lista.push({ clave: `${e.id}:final`, etiqueta: `Episodio ${e.numero} · final`, fecha: e.finalEn, piezas: e.finalPiezas })
    }
  }
  return lista
}

function nombreEstado(estado?: string) {
  return buscarHerramienta(estado)?.label ?? estado ?? 'Sin hallazgo'
}

/** El odontograma de una fecha con los dientes dibujados (caras y símbolos), igual que en pantalla y en la impresión. */
export function Miniatura({ piezas, titulo }: { piezas: PiezaInstantanea[]; titulo: string }) {
  const fila = (fdis: number[]) => (
    <div className="flex justify-center gap-0.5">
      {fdis.map((fdi) => {
        const hs = piezas.filter((p) => p.piezaFdi === fdi)
        return (
          <span key={fdi} title={`Pieza ${fdi}: ${hs.length ? hs.map((h) => nombreEstado(h.estado)).join(', ') : 'sin hallazgo'}`} className="flex flex-col items-center">
            <span className="font-mono text-[8px] font-bold text-on-surface-variant">{fdi}</span>
            {/* El SVG lo arma dienteSvg solo con constantes del sistema; la sigla va escapada. */}
            <span className="block size-5 sm:size-7 [&>svg]:size-full" dangerouslySetInnerHTML={{ __html: dienteSvg(fdi, hs) }} />
          </span>
        )
      })}
    </div>
  )
  return (
    <figure className="flex flex-col gap-1.5" aria-label={titulo}>
      <figcaption className="text-xs font-bold text-on-surface">{titulo}</figcaption>
      {fila(SUPERIOR)}
      {fila(INFERIOR)}
    </figure>
  )
}

function Comparador({ capturas, abierto, onCerrar, inicial, imprimir }: { capturas: Captura[]; abierto: boolean; onCerrar: () => void; inicial: [string, string]; imprimir: (c: CapturaImpresa[]) => void }) {
  const [antes, setAntes] = useState(inicial[0])
  const [despues, setDespues] = useState(inicial[1])
  const a = capturas.find((c) => c.clave === antes)
  const d = capturas.find((c) => c.clave === despues)

  const diferencias = useMemo(() => {
    if (!a || !d) return []
    const pa = new Map(a.piezas.map((p) => [p.piezaFdi, p]))
    const pd = new Map(d.piezas.map((p) => [p.piezaFdi, p]))
    const todas = [...new Set([...pa.keys(), ...pd.keys()])].sort((x, y) => x - y)
    return todas
      .map((fdi) => ({ fdi, antes: pa.get(fdi)?.estado, despues: pd.get(fdi)?.estado }))
      .filter((f) => f.antes !== f.despues)
  }, [a, d])

  const selector = (id: string, valor: string, alCambiar: (v: string) => void, etiqueta: string) => (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-bold text-on-surface">{etiqueta}</label>
      <select id={id} value={valor} onChange={(e) => alCambiar(e.target.value)} className="rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm">
        {capturas.map((c) => (
          <option key={c.clave} value={c.clave}>{`${c.etiqueta} · ${formatFecha(c.fecha)}`}</option>
        ))}
      </select>
    </div>
  )

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-3xl">
        <DialogTitle className="font-display text-lg font-bold text-primary">Comparar odontogramas</DialogTitle>
        <DialogDescription>Elige dos fechas para ver cómo estaba el paciente y cómo está ahora. Rojo: caries, fractura, temporales y mal estado. Azul: lo demás.</DialogDescription>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {selector('comp-antes', antes, setAntes, 'Antes')}
          {selector('comp-despues', despues, setDespues, 'Después')}
        </div>
        {a && d && (
          <>
            <Button size="sm" variant="secundario" className="self-end" onClick={() => imprimir([{ titulo: 'Antes', fecha: a.fecha, piezas: a.piezas }, { titulo: 'Después', fecha: d.fecha, piezas: d.piezas }])}>
              <Printer aria-hidden />
              Vista previa e imprimir
            </Button>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Miniatura piezas={a.piezas} titulo={`Antes · ${formatFecha(a.fecha)}`} />
              <Miniatura piezas={d.piezas} titulo={`Después · ${formatFecha(d.fecha)}`} />
            </div>
            <div className="max-h-64 overflow-y-auto rounded-panel border border-outline-variant/50">
              {diferencias.length === 0 ? (
                <p className="p-3 text-sm text-on-surface-variant">No hay diferencias entre estas dos fechas.</p>
              ) : (
                <table className="sin-apilar w-full text-left text-xs">
                  <thead className="bg-surface-container-low text-on-surface-variant">
                    <tr>
                      <th className="p-2">Pieza</th>
                      <th className="p-2">Antes</th>
                      <th className="p-2" aria-hidden />
                      <th className="p-2">Después</th>
                    </tr>
                  </thead>
                  <tbody>
                    {diferencias.map((f) => (
                      <tr key={f.fdi} className="border-t border-outline-variant/40">
                        <th scope="row" className="p-2 font-mono">{f.fdi}</th>
                        <td className="p-2">{f.antes ? nombreEstado(f.antes) : 'Sin hallazgo'}</td>
                        <td className="p-2"><ArrowRight aria-hidden className="size-3.5 text-on-surface-variant" /></td>
                        <td className="p-2 font-semibold">{f.despues ? nombreEstado(f.despues) : 'Sin hallazgo'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

/**
 * Episodios del odontograma. Cada vez que el paciente llega (dolor, tratamiento, control a los 3 o 4 meses) se registra
 * su odontograma inicial; al terminar (alta) se guarda el final. Así se compara cómo llegó con cómo salió, y cómo volvió.
 */
export function EpisodiosOdontograma({
  pacienteId,
  puedeEditar = true,
  pacienteNombre = '',
  pacienteDni,
  sedeNombre = '',
}: {
  pacienteId: string
  puedeEditar?: boolean
  pacienteNombre?: string
  pacienteDni?: string
  sedeNombre?: string
}) {
  const consulta = useEpisodiosOdontograma(pacienteId)
  const abrir = useAbrirEpisodio(pacienteId)
  const cerrar = useCerrarEpisodio(pacienteId)
  const [motivo, setMotivo] = useState('')
  const [nota, setNota] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [comparando, setComparando] = useState(false)
  const [vistaPrevia, setVistaPrevia] = useState<{ capturas: CapturaImpresa[]; motivo?: string } | null>(null)
  const [abiertos, setAbiertos] = useState<Set<string>>(new Set())
  const sesion = useSesion()
  const imprimir = (capturas: CapturaImpresa[], motivo?: string) =>
    void imprimirDocumento(
      documentoOdontograma({ pacienteNombre, pacienteDni, sedeNombre, negocio: sesion?.negocioActivo === 'VILLAMAX' ? 'VILLAMAX' : 'VILLADENT', motivo, capturas }),
    ).catch(() => window.print())

  const htmlDocumento = (capturas: CapturaImpresa[], motivo?: string) =>
    documentoOdontograma({ pacienteNombre, pacienteDni, sedeNombre, negocio: sesion?.negocioActivo === 'VILLAMAX' ? 'VILLAMAX' : 'VILLADENT', motivo, capturas })

  if (consulta.isPending) return <Skeleton className="h-24 w-full" />
  if (consulta.isError) return <Alert tono="error" titulo="No se pudieron cargar los episodios del odontograma">{mensajeError(consulta.error)}</Alert>

  const episodios = consulta.data
  const abierto = episodios.find((e) => e.estado === 'ABIERTO')
  const capturas = capturasDe(episodios)
  const puedeComparar = capturas.length >= 2

  const registrarLlegada = async () => {
    setError(null)
    try {
      const nuevo = await abrir.mutateAsync(motivo.trim() || undefined)
      setMotivo('')
      toast(`Episodio ${nuevo.numero} abierto: odontograma inicial guardado`)
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  const darAlta = async () => {
    if (!abierto) return
    setError(null)
    try {
      await cerrar.mutateAsync({ episodioId: abierto.id, nota: nota.trim() || undefined })
      setNota('')
      toast(`Episodio ${abierto.numero} cerrado: odontograma final guardado`)
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  return (
    <Card className="flex flex-col gap-3 p-4" aria-label="Episodios del odontograma">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 font-display text-base font-bold text-on-surface">
          <History aria-hidden className="size-4 text-primary" />
          Odontograma inicial y final
        </h3>
        {puedeComparar && (
          <Button size="sm" variant="secundario" onClick={() => setComparando(true)}>
            <GitCompare aria-hidden />
            Comparar fechas
          </Button>
        )}
      </div>

      {abierto ? (
        <div className="flex flex-col gap-2 rounded-panel bg-primary-fixed/40 p-3">
          <p className="text-sm text-on-surface">
            <strong>Episodio {abierto.numero} en curso</strong> desde el {formatFecha(abierto.inicialEn)}
            {abierto.motivo ? ` · ${abierto.motivo}` : ''}. El odontograma inicial ya está guardado.
          </p>
          {puedeEditar && (
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <div className="flex-1">
                <label htmlFor="nota-alta" className="mb-1 block text-xs font-bold text-on-surface">Evolución al finalizar (opcional)</label>
                <Input id="nota-alta" value={nota} onChange={(e) => setNota(e.target.value)} placeholder="Ej. Sin dolor, tratamiento completo" />
              </div>
              <Button onClick={() => void darAlta()} cargando={cerrar.isPending}>
                <ClipboardCheck aria-hidden />
                Dar de alta (odontograma final)
              </Button>
            </div>
          )}
        </div>
      ) : (
        puedeEditar && (
          <div className="flex flex-col gap-2 rounded-panel bg-surface-container-low p-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label htmlFor="motivo-episodio" className="mb-1 block text-xs font-bold text-on-surface">Motivo de esta llegada</label>
              <Input id="motivo-episodio" value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Dolor de muela, tratamiento, control a los 3 meses" />
            </div>
            <Button onClick={() => void registrarLlegada()} cargando={abrir.isPending}>
              Registrar llegada (odontograma inicial)
            </Button>
          </div>
        )
      )}
      {!abierto && puedeEditar && (
        <p className="text-xs text-on-surface-variant">
          Marca primero el odontograma tal como llega el paciente y luego registra la llegada: queda guardado con la fecha de hoy.
        </p>
      )}

      {error && <Alert tono="error" titulo="No se pudo guardar">{error}</Alert>}

      {episodios.length > 0 && (
        <ul className="flex flex-col gap-2">
          {episodios.map((e) => {
            const capturasEpisodio: CapturaImpresa[] = [
              { titulo: `Episodio ${e.numero} · inicial`, fecha: e.inicialEn, piezas: e.inicial },
              ...(e.finalEn && e.finalPiezas ? [{ titulo: `Episodio ${e.numero} · final`, fecha: e.finalEn, piezas: e.finalPiezas, notas: e.notaFinal }] : []),
            ]
            // El episodio en curso (o el último) se ve de entrada; los demás se abren con "Ver".
            const visible = abiertos.has(e.id) || e.id === (abierto ?? episodios[episodios.length - 1]).id
            return (
            <li key={e.id} className="flex flex-wrap items-center justify-between gap-2 rounded-panel border border-outline-variant/50 px-3 py-2 text-sm">
              <span className="font-semibold text-on-surface">Episodio {e.numero}{e.motivo ? ` · ${e.motivo}` : ''}</span>
              <span className="text-xs text-on-surface-variant">
                Inicial {formatFecha(e.inicialEn)} ({e.inicial.filter((p) => p.estado !== 'sano').length} hallazgos)
                {e.finalEn ? ` → Final ${formatFecha(e.finalEn)} (${(e.finalPiezas ?? []).filter((p) => p.estado !== 'sano').length} hallazgos)` : ' → en curso'}
              </span>
              <span className="flex flex-wrap gap-1">
                <button
                  type="button"
                  onClick={() => setAbiertos((s) => { const n = new Set(s); if (n.has(e.id)) n.delete(e.id); else n.add(e.id); return n })}
                  className="inline-flex min-h-9 items-center gap-1 rounded-control px-2 text-xs font-semibold text-primary hover:bg-surface-container"
                  aria-expanded={visible}
                >
                  <Eye aria-hidden className="size-3.5" />
                  {visible ? 'Ocultar' : 'Ver'}
                </button>
                <button
                  type="button"
                  onClick={() => setVistaPrevia({ capturas: capturasEpisodio, motivo: e.motivo })}
                  className="inline-flex min-h-9 items-center gap-1 rounded-control px-2 text-xs font-semibold text-primary hover:bg-surface-container"
                >
                  <Printer aria-hidden className="size-3.5" />
                  Vista previa e imprimir
                </button>
              </span>
              {visible && (
                <div className="grid basis-full grid-cols-1 gap-4 pt-1 lg:grid-cols-2">
                  {capturasEpisodio.map((c) => (
                    <Miniatura key={c.titulo} piezas={c.piezas} titulo={`${c.titulo} · ${formatFecha(c.fecha)}`} />
                  ))}
                  {!e.finalEn && <p className="self-center text-xs italic text-on-surface-variant">El odontograma final se guarda al dar de alta.</p>}
                </div>
              )}
              {e.notaFinal && <span className="basis-full text-xs italic text-on-surface-variant">{e.notaFinal}</span>}
            </li>
            )
          })}
        </ul>
      )}

      {puedeComparar && comparando && (
        <Comparador
          capturas={capturas}
          abierto
          onCerrar={() => setComparando(false)}
          inicial={[capturas[Math.max(0, capturas.length - 2)].clave, capturas[capturas.length - 1].clave]}
          imprimir={(c) => setVistaPrevia({ capturas: c })}
        />
      )}

      <Dialog open={vistaPrevia !== null} onOpenChange={(v) => !v && setVistaPrevia(null)}>
        <DialogContent className="max-w-5xl">
          <DialogTitle className="font-display text-lg font-bold text-primary">Vista previa del odontograma</DialogTitle>
          <DialogDescription>Así saldrá en el papel. Revísalo y pulsa Imprimir.</DialogDescription>
          {vistaPrevia && (
            <>
              <iframe title="Vista previa del odontograma" srcDoc={htmlDocumento(vistaPrevia.capturas, vistaPrevia.motivo)} className="h-[65vh] w-full rounded-panel border border-outline-variant/50 bg-white" />
              <div className="flex justify-end gap-2">
                <Button variant="secundario" onClick={() => setVistaPrevia(null)}>Cerrar</Button>
                <Button onClick={() => imprimir(vistaPrevia.capturas, vistaPrevia.motivo)}>
                  <Printer aria-hidden />
                  Imprimir
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </Card>
  )
}
