import { Activity, ClipboardList, Layers, Printer, Ruler, ScanLine, Scissors, ShieldCheck, Smile, Sparkles, Stethoscope, Wrench, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/design-system'
import { formatFecha } from '@/shared/format'
import { imprimirDocumento } from '@/shared/imprimir'
import { textoDeError } from '@/shared/http'
import {
  useCerrarFichaMutation,
  useCrearFichaMutation,
  useGuardarFichaMutation,
  usePlantillas,
  useRegistrosPlantilla,
  type DatosFicha,
  type PlantillaTratamiento,
  type RegistroPlantilla,
} from './api-plantillas'
import { CamposPlantilla } from './CamposPlantilla'
import { documentoFicha } from './documentoFicha'

const ICONOS: Record<string, LucideIcon> = {
  CONSULTA: Stethoscope,
  RESTAURACION: Layers,
  EXODONCIA: Scissors,
  CIRUGIA_ORAL: Scissors,
  ENDODONCIA: Activity,
  PREVENCION: ShieldCheck,
  BLANQUEAMIENTO: Sparkles,
  PROTESIS_REMOVIBLE: Wrench,
  PROTESIS_FIJA: Wrench,
  ORTODONCIA: Ruler,
  ORTOPEDIA: Ruler,
  ODONTOPEDIATRIA: Smile,
  ESTUDIO_RADIOLOGICO: ScanLine,
  TOMOGRAFIA_3D: ScanLine,
}

function IconoPlantilla({ codigo }: { codigo: string }) {
  const Icono = ICONOS[codigo] ?? ClipboardList
  return (
    <span aria-hidden className="flex size-9 shrink-0 items-center justify-center rounded-full bg-acento/15 text-primary">
      <Icono className="size-4.5" />
    </span>
  )
}

interface Props {
  pacienteId: string
  negocio: 'VILLADENT' | 'VILLAMAX'
  pacienteNombre?: string
  /** Nombres de los planes en curso del paciente: sirven para sugerir la plantilla que corresponde. */
  planesEnCurso?: { id?: string; nombre?: string }[]
}

interface Edicion {
  plantilla: PlantillaTratamiento
  registro: RegistroPlantilla | null
  corrige?: RegistroPlantilla
  tratamientoId?: string
}

/** Fichas clínicas por tratamiento: cada tratamiento tiene su plantilla (restauración, endodoncia, ortodoncia…). */
export function FichasPorTratamiento({ pacienteId, negocio, pacienteNombre, planesEnCurso = [] }: Props) {
  const plantillas = usePlantillas(negocio)
  const registros = useRegistrosPlantilla(pacienteId)
  const [elegirAbierto, setElegirAbierto] = useState(false)
  const [edicion, setEdicion] = useState<Edicion | null>(null)

  const porCodigo = new Map((plantillas.data ?? []).map((p) => [p.codigo, p]))
  const lista = registros.data ?? []

  return (
    <section aria-label="Fichas por tratamiento" className="flex flex-col gap-4 rounded-card border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-card">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 font-display text-lg font-bold text-on-surface">
            <ClipboardList aria-hidden className="size-5 text-primary" />
            Fichas por tratamiento
          </h2>
          <p className="text-xs text-on-surface-variant">Cada tratamiento se registra con su propia ficha: pieza, técnica, materiales, controles e indicaciones.</p>
        </div>
        <Button variant="accion" size="sm" onClick={() => setElegirAbierto(true)} disabled={plantillas.isPending}>
          + Nueva ficha
        </Button>
      </div>

      {registros.isPending ? (
        <p className="text-sm text-on-surface-variant">Cargando fichas…</p>
      ) : registros.isError ? (
        <p role="alert" className="text-sm text-error">
          {textoDeError(registros.error, 'No se pudieron cargar las fichas.')}
        </p>
      ) : lista.length === 0 ? (
        <p className="rounded-panel bg-surface-container-low p-4 text-sm text-on-surface-variant">Aún no hay fichas de tratamiento para este paciente.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {lista.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-outline-variant/50 bg-surface px-5 py-4">
              <div className="flex min-w-0 items-center gap-3">
              <IconoPlantilla codigo={r.plantillaCodigo} />
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-sm font-bold text-on-surface">
                  {r.plantillaNombre}
                  <Badge tono={r.estado === 'CERRADO' ? 'primario' : 'neutro'}>{r.estado === 'CERRADO' ? 'Cerrada' : 'Borrador'}</Badge>
                  {r.corrigeId && <Badge tono="neutro">Corrección</Badge>}
                </p>
                <p className="text-xs text-on-surface-variant">
                  {formatFecha(r.creadoEn)}
                  {r.creadoPorNombre ? ` · ${r.creadoPorNombre}` : ''}
                </p>
              </div>
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="secundario"
                  onClick={() => {
                    const p = porCodigo.get(r.plantillaCodigo)
                    if (p) setEdicion({ plantilla: p, registro: r })
                  }}
                >
                  {r.estado === 'CERRADO' ? 'Ver' : 'Continuar'}
                </Button>
                {r.estado === 'CERRADO' && (
                  <Button
                    size="sm"
                    variant="secundario"
                    onClick={() => {
                      const p = porCodigo.get(r.plantillaCodigo)
                      if (p) setEdicion({ plantilla: p, registro: null, corrige: r })
                    }}
                  >
                    Corregir
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {elegirAbierto && (
        <ElegirPlantilla
          plantillas={plantillas.data ?? []}
          planesEnCurso={planesEnCurso}
          alElegir={(plantilla, tratamientoId) => {
            setElegirAbierto(false)
            setEdicion({ plantilla, registro: null, tratamientoId })
          }}
          alCerrar={() => setElegirAbierto(false)}
        />
      )}
      {edicion && <EditorFicha key={edicion.registro?.id ?? edicion.corrige?.id ?? edicion.plantilla.codigo} pacienteId={pacienteId} edicion={edicion} alCerrar={() => setEdicion(null)} pacienteNombre={pacienteNombre} />}
    </section>
  )
}

function ElegirPlantilla({
  plantillas,
  planesEnCurso,
  alElegir,
  alCerrar,
}: {
  plantillas: PlantillaTratamiento[]
  planesEnCurso: { id?: string; nombre?: string }[]
  alElegir: (p: PlantillaTratamiento, tratamientoId?: string) => void
  alCerrar: () => void
}) {
  return (
    <Dialog open onOpenChange={(abierto) => !abierto && alCerrar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nueva ficha de tratamiento</DialogTitle>
          <DialogDescription>Elige el tipo de tratamiento que vas a registrar.</DialogDescription>
        </DialogHeader>
        <ul className="flex max-h-[60vh] flex-col gap-2 overflow-y-auto">
          {plantillas.map((p) => {
            const plan = planesEnCurso.find((t) => t.nombre && p.nombre && t.nombre.toLowerCase().includes(p.nombre.toLowerCase().split(' ')[0]))
            return (
              <li key={p.codigo}>
                <button
                  type="button"
                  onClick={() => alElegir(p, plan?.id)}
                  className="flex w-full flex-col gap-0.5 rounded-panel border border-outline-variant bg-surface-container-lowest px-4 py-3 text-left hover:border-acento hover:bg-acento/10"
                >
                  <span className="text-sm font-bold text-primary">{p.nombre}</span>
                  <span className="text-xs text-on-surface-variant">{p.descripcion}</span>
                  {plan && <span className="text-xs font-semibold text-primary">Coincide con el plan en curso: {plan.nombre}</span>}
                </button>
              </li>
            )
          })}
        </ul>
        <DialogFooter>
          <Button variant="secundario" onClick={alCerrar}>
            Cancelar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function EditorFicha({ pacienteId, pacienteNombre, edicion, alCerrar }: { pacienteId: string; pacienteNombre?: string; edicion: Edicion; alCerrar: () => void }) {
  const crear = useCrearFichaMutation()
  const guardar = useGuardarFichaMutation()
  const cerrarFicha = useCerrarFichaMutation()
  const base = edicion.registro?.datos ?? edicion.corrige?.datos ?? {}
  const [datos, setDatos] = useState<DatosFicha>(base)
  const [error, setError] = useState<string | null>(null)
  const soloLectura = edicion.registro?.estado === 'CERRADO'
  const trabajando = crear.isPending || guardar.isPending || cerrarFicha.isPending

  async function persistir(): Promise<RegistroPlantilla> {
    if (edicion.registro) return guardar.mutateAsync({ id: edicion.registro.id, datos })
    return crear.mutateAsync({
      pacienteId,
      plantillaCodigo: edicion.plantilla.codigo,
      tratamientoId: edicion.tratamientoId,
      corrigeId: edicion.corrige?.id,
      datos,
    })
  }

  async function guardarBorrador() {
    setError(null)
    try {
      await persistir()
      alCerrar()
    } catch (e) {
      setError(textoDeError(e, 'No se pudo guardar la ficha.'))
    }
  }

  async function guardarYCerrar() {
    setError(null)
    try {
      const guardada = await persistir()
      await cerrarFicha.mutateAsync(guardada.id)
      alCerrar()
    } catch (e) {
      setError(textoDeError(e, 'No se pudo cerrar la ficha.'))
    }
  }

  return (
    <Dialog open onOpenChange={(abierto) => !abierto && alCerrar()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>
            {edicion.plantilla.nombre}
            {edicion.corrige ? ' · corrección' : ''}
          </DialogTitle>
          <DialogDescription>
            {soloLectura
              ? 'Ficha cerrada: no se puede editar. Usa "Corregir" para registrar un cambio.'
              : 'Completa lo que corresponda. Los campos con * son obligatorios para cerrar la ficha; una ficha cerrada ya no se edita.'}
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-[65vh] overflow-y-auto pr-1">
          <CamposPlantilla plantilla={edicion.plantilla} datos={datos} onCambiar={setDatos} soloLectura={soloLectura} />
        </div>
        {error && (
          <p role="alert" className="text-sm text-error">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button
            variant="secundario"
            onClick={() =>
              void imprimirDocumento(
                documentoFicha({
                  plantilla: edicion.plantilla,
                  datos,
                  pacienteNombre,
                  estado: edicion.registro?.estado,
                  fecha: edicion.registro?.creadoEn,
                  doctor: edicion.registro?.creadoPorNombre,
                }),
              ).catch(() => setError('No se pudo preparar la impresión.'))
            }
          >
            <Printer aria-hidden />
            Imprimir
          </Button>
          <Button variant="secundario" onClick={alCerrar}>
            {soloLectura ? 'Cerrar' : 'Cancelar'}
          </Button>
          {!soloLectura && (
            <>
              <Button variant="secundario" onClick={() => void guardarBorrador()} disabled={trabajando}>
                Guardar borrador
              </Button>
              <Button variant="accion" onClick={() => void guardarYCerrar()} disabled={trabajando}>
                Cerrar ficha
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
