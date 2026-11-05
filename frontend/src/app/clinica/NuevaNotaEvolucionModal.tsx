import { useState } from 'react'
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { Alert } from '@/design-system/components/Alert'
import { useCrearNotaEvolucionMutation, type NotaEvolucionDTO } from './api-historia'
import { textoDeError } from '@/shared/http'
import { Sparkles, Check } from 'lucide-react'

export interface PlantillaClinica {
  id: string
  nombre: string
  especialidad: string
  subjetivo: string
  objetivo: string
  analisis: string
  plan: string
  libre: string
}

export const PLANTILLAS_CLINICAS: PlantillaClinica[] = [
  {
    id: 'profilaxis',
    nombre: 'Profilaxis / Destartraje',
    especialidad: 'Prevención',
    subjetivo: 'Paciente acude para control periódico y profilaxis dental preventiva. Refiere sin sintomatología dolorosa.',
    objetivo: 'Presencia de cálculo supragingival en sector anteroinferior lingual. Encías eritematosas marginales sin bolsas activas.',
    analisis: 'Gingivitis marginal inducida por placa bacteriana (K05.0).',
    plan: 'Se realiza tartrectomía ultrasónica con cavitron, raspaje coronal, pulido con pasta profiláctica y topicación de flúor en gel neutro. Indicaciones de higiene oral.',
    libre: 'Procedimiento de Profilaxis y Destartraje: Tartrectomía ultrasónica con cavitron, raspaje coronal supragingival, pulido con pasta profiláctica y aplicación tópica de flúor en gel neutro. Paciente tolera bien el procedimiento. Indicaciones de técnica de cepillado y uso de hilo dental.',
  },
  {
    id: 'curacion',
    nombre: 'Curación / Resina',
    especialidad: 'Operatoria',
    subjetivo: 'Paciente refiere ligero empaquetamiento de alimentos y molestia transitoria con dulces o frío.',
    objetivo: 'Lesión cariosa en esmalte y dentina superficial. Sin dolor a la percusión horizontal ni vertical. Pruebas de vitalidad pulpar positivas (+).',
    analisis: 'Caries de la dentina (K02.1).',
    plan: 'Aislamiento de campo, remoción de tejido cariado, grabado ácido 37% por 15s, adhesivo universal fotocurado 20s, restauración con resina compuesta por capas incrementales, fotopolimerización, control oclusal y pulido final.',
    libre: 'Restauración con Resina Compuesta: Aislamiento de campo operatorio, eliminación completa de caries, grabado ácido 15s, aplicación de sistema adhesivo fotocurado, estratificación con resina compuesta fotopolimerizada, control de puntos de contacto y oclusión, pulido con discos y pastas abrasivas.',
  },
  {
    id: 'endodoncia',
    nombre: 'Endodoncia (Sesión)',
    especialidad: 'Endodoncia',
    subjetivo: 'Paciente refiere dolor espontáneo nocturno pulsátil que aumenta con cambios térmicos.',
    objetivo: 'Lesión cariosa profunda próxima a cámara pulpar. Percusión vertical (+). Vitalidad pulpar exacerbada con dolor persistente.',
    analisis: 'Pulpitis irreversible aguda sintomática (K04.0).',
    plan: 'Anestesia infiltrativa/troncular con vasoconstrictor, aislamiento absoluto, apertura cameral, localización de conductos, conductometría, instrumentación rotatoria, irrigación con NaOCl 2.5%, medicación intraconducto y obturación provisional.',
    libre: 'Tratamiento de Endodoncia: Anestesia local infiltrativa/troncular, aislamiento absoluto con dique de goma, apertura cameral, pulpectomía, localización de conductos, instrumentación biomecánica rotatoria, irrigación profusa con NaOCl 2.5%, secado con conos de papel, medicación intraconducto y selle coronal provisional con ionómero de vidrio.',
  },
  {
    id: 'exodoncia',
    nombre: 'Exodoncia Simple',
    especialidad: 'Cirugía',
    subjetivo: 'Paciente acude para extracción indicada por destrucción coronaria no restaurable o fractura.',
    objetivo: 'Resto radicular o pieza con pérdida estructural severa. Sin movilidad patológica aguda ni flemón activo.',
    analisis: 'Resto radicular no conservable (K08.3).',
    plan: 'Anestesia local infiltrativa con vasoconstrictor, sindesmotomía, luxación con elevador recto y avulsión con fórceps. Curetaje alveolar, hemostasia por compresión, indicaciones postoperatorias verbales y escritas.',
    libre: 'Exodoncia Dental: Antisepsia del campo, anestesia local infiltrativa con vasoconstrictor, sindesmotomía circunferencial, luxación progresiva con elevador y extracción atraumática. Curetaje del lecho alveolar, lavado con solución salina, compresión de tablas óseas y gasa hemostática estéril. Se entregan indicaciones postoperatorias escritas y receta de analgésico.',
  },
  {
    id: 'ortodoncia',
    nombre: 'Control Ortodoncia',
    especialidad: 'Ortodoncia',
    subjetivo: 'Paciente acude a cita mensual de control de aparatología ortodóncica fija.',
    objetivo: 'Aparatología fija en boca completa y en buen estado. Higiene oral adecuada. Evolución de alineación y nivelación favorable.',
    analisis: 'Maloclusión dentaria en tratamiento de ortodoncia activa.',
    plan: 'Retiro de módulos elásticos, cambio/activación de arcos, recolocación de ligaduras elastoméricas, verificación de tubos y brackets. Próxima cita en 4 semanas.',
    libre: 'Control Mensual de Ortodoncia: Revisión de aparatología fija, retiro de módulos elásticos, limpieza superficial, activación y cambio de arcos, colocación de nuevas ligaduras elásticas. Se instruye en higiene interdental. Cita programada en 4 semanas.',
  },
  {
    id: 'blanqueamiento',
    nombre: 'Blanqueamiento',
    especialidad: 'Estética',
    subjetivo: 'Paciente solicita aclaramiento dental estético por pigmentaciones extrínsecas.',
    objetivo: 'Esmalte íntegro, sin caries activas en sector anterior. Índice gingival favorable.',
    analisis: 'Discromía dental adquirida.',
    plan: 'Profilaxis previa, colocación de barrera gingival fotocurable, aplicación de peróxido de hidrógeno al 35% por 3 sesiones de 15 min. Lavado, retiro de barrera, flúor desensibilizante e indicaciones de dieta blanca.',
    libre: 'Sesión de Blanqueamiento Dental en Consultorio: Aislamiento con retractor labial, aplicación y fotocurado de barrera gingival protectora, 3 aplicaciones de gel de peróxido de hidrógeno al 35% de 15 minutos cada una, enjuague y aspiración cuidadosa. Aplicación final de gel desensibilizante. Se entrega guía de cuidados y dieta blanca.',
  },
]

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre?: string
  /** Si se pasa, el modal abre en modo ADENDA de esa nota */
  notaReferenciaId?: string
  alGuardar?: (nota: NotaEvolucionDTO) => void
}

export function NuevaNotaEvolucionModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  notaReferenciaId,
  alGuardar,
}: Props) {
  const esAdenda = Boolean(notaReferenciaId)
  const [tipoNota, setTipoNota] = useState<'SOAP' | 'TEXTO_LIBRE'>('SOAP')
  const [subjetivo, setSubjetivo] = useState('')
  const [objetivo, setObjetivo] = useState('')
  const [analisis, setAnalisis] = useState('')
  const [plan, setPlan] = useState('')
  const [contenidoLibre, setContenidoLibre] = useState('')
  const [motivoCorreccion, setMotivoCorreccion] = useState('')
  const [plantillaActivaId, setPlantillaActivaId] = useState<string | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const crearMutation = useCrearNotaEvolucionMutation()

  function aplicarPlantilla(p: PlantillaClinica) {
    setPlantillaActivaId(p.id)
    if (tipoNota === 'SOAP') {
      setSubjetivo(p.subjetivo)
      setObjetivo(p.objetivo)
      setAnalisis(p.analisis)
      setPlan(p.plan)
    } else {
      setContenidoLibre(p.libre)
    }
  }

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (esAdenda && !motivoCorreccion.trim()) {
      setErrorLocal('El motivo de la corrección o ampliación es obligatorio para una adenda.')
      return
    }

    if (tipoNota === 'SOAP') {
      if (!subjetivo.trim() && !objetivo.trim() && !analisis.trim() && !plan.trim()) {
        setErrorLocal('Debe completar al menos uno de los campos clínicos SOAP (S, O, A o P).')
        return
      }
    } else {
      if (!contenidoLibre.trim()) {
        setErrorLocal('El contenido de la nota de evolución es obligatorio.')
        return
      }
    }

    crearMutation.mutate(
      {
        pacienteId,
        dto: {
          tipoNota,
          subjetivo: tipoNota === 'SOAP' ? subjetivo.trim() || undefined : undefined,
          objetivo: tipoNota === 'SOAP' ? objetivo.trim() || undefined : undefined,
          analisis: tipoNota === 'SOAP' ? analisis.trim() || undefined : undefined,
          plan: tipoNota === 'SOAP' ? plan.trim() || undefined : undefined,
          contenido: tipoNota === 'TEXTO_LIBRE' ? contenidoLibre.trim() : undefined,
          notaReferenciaId: notaReferenciaId ?? undefined,
          motivoCorreccion: esAdenda ? motivoCorreccion.trim() : undefined,
        },
      },
      {
        onSuccess: (guardada) => {
          alGuardar?.(guardada)
          setSubjetivo('')
          setObjetivo('')
          setAnalisis('')
          setPlan('')
          setContenidoLibre('')
          setMotivoCorreccion('')
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al guardar la nota de evolución'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>
            {esAdenda ? 'Nueva Adenda a Nota de Evolución' : 'Nueva Nota de Evolución Odontológica'}
          </DialogTitle>
          <DialogDescription>
            {esAdenda
              ? `Adenda legal a nota existente de ${pacienteNombre ?? 'el paciente'}. Las notas son inmutables; esta adenda queda vinculada a la nota original.`
              : `Registro clínico oficial en la historia de ${pacienteNombre ?? 'el paciente'}. Quedará firmado digitalmente con su usuario y matrícula profesional.`}
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          {/* Motivo de corrección — solo en modo ADENDA */}
          {esAdenda && (
            <Field
              label="Motivo de la corrección o ampliación *"
              ayuda="Indique por qué se emite esta adenda (error tipográfico, información omitida, aclaración clínica, etc.)"
            >
              <Input
                id="motivoCorreccionInput"
                value={motivoCorreccion}
                onChange={(e) => setMotivoCorreccion(e.target.value)}
                placeholder="Ej. Se omitió el registro de la medicación prescrita"
                ref={enfocarAlMontar}
                required
              />
            </Field>
          )}

          {/* Selector de Formato: SOAP vs Libre */}
          <Field label="Formato de Registro Clínico">
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setTipoNota('SOAP')}
                className={`py-2 px-3 rounded-lg border text-sm font-semibold transition-all ${
                  tipoNota === 'SOAP'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-outline bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                Evolución Estándar (SOAP)
              </button>
              <button
                type="button"
                onClick={() => setTipoNota('TEXTO_LIBRE')}
                className={`py-2 px-3 rounded-lg border text-sm font-semibold transition-all ${
                  tipoNota === 'TEXTO_LIBRE'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-outline bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                Nota Libre / Informe
              </button>
            </div>
          </Field>

          {/* Plantillas Rápidas de Procedimiento Odontológico */}
          <div className="rounded-lg border border-primary/20 bg-primary/5 p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-1.5 text-xs font-bold text-primary">
                <Sparkles className="size-3.5" />
                Plantillas Rápidas en 1 Clic:
              </span>
              <span className="text-xs text-on-surface-variant">
                Selecciona para autocompletar
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {PLANTILLAS_CLINICAS.map((p) => {
                const activa = plantillaActivaId === p.id
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => aplicarPlantilla(p)}
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold transition-all cursor-pointer border ${
                      activa
                        ? 'bg-acento text-on-acento border-primary shadow-xs'
                        : 'bg-surface-container-lowest text-on-surface hover:border-primary/50 border-outline-variant'
                    }`}
                  >
                    {activa && <Check className="size-3" />}
                    {p.nombre}
                  </button>
                )
              })}
            </div>
          </div>

          {tipoNota === 'SOAP' ? (
            <div className="space-y-3">
              <Field
                label="S — Subjetivo (Lo que refiere el paciente)"
                ayuda="Motivo de consulta, localización de molestia, dolor (escala EVA)"
              >
                <Input
                  id="subjetivoInput"
                  value={subjetivo}
                  onChange={(e) => setSubjetivo(e.target.value)}
                  placeholder="Ej. Paciente refiere dolor punzante en molar inferior derecho al masticar"
                  ref={enfocarAlMontar}
                />
              </Field>

              <Field
                label="O — Objetivo (Examen clínico y radiográfico)"
                ayuda="Hallazgos visuales, sondaje periodontal, percusión, movilidad"
              >
                <Input
                  id="objetivoInput"
                  value={objetivo}
                  onChange={(e) => setObjetivo(e.target.value)}
                  placeholder="Ej. Cavitación en pieza 46 oclusal, percusión vertical (+), sin tumefacción"
                />
              </Field>

              <Field
                label="A — Análisis (Diagnóstico clínico presuntivo)"
                ayuda="Diagnóstico nosológico (Pulpitis, Gingivitis, Caries, etc.)"
              >
                <Input
                  id="analisisInput"
                  value={analisis}
                  onChange={(e) => setAnalisis(e.target.value)}
                  placeholder="Ej. K04.0 Pulpitis irreversible sintomática pieza 46"
                />
              </Field>

              <Field
                label="P — Plan (Tratamiento ejecutado y conducta)"
                ayuda="Procedimiento realizado hoy, medicación recetada y próxima cita"
              >
                <Input
                  id="planInput"
                  value={plan}
                  onChange={(e) => setPlan(e.target.value)}
                  placeholder="Ej. Apertura cameral, pulpectomía cameral, cura provisional. Cita en 5 días."
                />
              </Field>
            </div>
          ) : (
            <Field label="Contenido de la Nota Clínica *">
              <textarea
                id="contenidoLibreInput"
                rows={5}
                value={contenidoLibre}
                onChange={(e) => setContenidoLibre(e.target.value)}
                placeholder="Describa detalladamente el procedimiento clínico, medicamentos aplicados y evolución..."
                className="w-full rounded-md border border-outline bg-surface-container-lowest p-3 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                required
                ref={enfocarAlMontar}
              />
            </Field>
          )}

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={crearMutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={crearMutation.isPending}>
              {crearMutation.isPending
                ? 'Firmando y Guardando...'
                : esAdenda
                ? 'Firmar y Guardar Adenda'
                : 'Firmar y Guardar Nota'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
