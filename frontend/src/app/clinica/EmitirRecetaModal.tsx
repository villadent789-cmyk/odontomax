import { MessageCircle, Plus, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  Input,
  toast,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { useSesion } from '@/shared/auth'
import { descargarArchivo, nombreParaArchivo } from '@/shared/descargas'
import { hoyEnLima } from '@/shared/format'
import { mensajeError } from '@/shared/errores'
import { useEspecialistas } from '../agenda/api-agenda'
import { useGenerarRecetaPdf, type MedicamentoReceta } from './api-recetas'

export interface PacienteReceta {
  id: string
  nombreCompleto: string
  dni?: string
  edad?: number
  telefono?: string
}

interface Props {
  abierto: boolean
  alCerrar: () => void
  paciente: PacienteReceta
}

interface FilaMedicamento {
  clave: number
  medicamento: string
  dosis: string
  frecuencia: string
  duracion: string
  indicaciones: string
}

interface PlantillaFarmaco {
  etiqueta: string
  medicamento: string
  dosis: string
  frecuencia: string
  duracion: string
  indicaciones: string
}

const FARMACOS_FRECUENTES: PlantillaFarmaco[] = [
  {
    etiqueta: '+ Amoxicilina 500mg',
    medicamento: 'Amoxicilina 500 mg',
    dosis: '1 cápsula',
    frecuencia: 'Cada 8 horas',
    duracion: 'Por 7 días',
    indicaciones: 'Tomar con abundante agua después de las comidas',
  },
  {
    etiqueta: '+ Ibuprofeno 400mg',
    medicamento: 'Ibuprofeno 400 mg',
    dosis: '1 tableta',
    frecuencia: 'Cada 8 horas',
    duracion: 'Por 3 días',
    indicaciones: 'Tomar con alimentos si hay dolor o inflamación',
  },
  {
    etiqueta: '+ Ketorolaco 10mg SL',
    medicamento: 'Ketorolaco 10 mg (Sublingual)',
    dosis: '1 tableta sublingual',
    frecuencia: 'Cada 8 horas',
    duracion: 'Máx 3 días',
    indicaciones: 'Disolver debajo de la lengua ante dolor agudo intenso',
  },
  {
    etiqueta: '+ Paracetamol 500mg',
    medicamento: 'Paracetamol 500 mg',
    dosis: '1 tableta',
    frecuencia: 'Cada 8 horas',
    duracion: 'Por 3 días',
    indicaciones: 'Tomar para molestias leves o febrícula',
  },
  {
    etiqueta: '+ Clorhexidina 0.12%',
    medicamento: 'Clorhexidina 0.12% Colutorio',
    dosis: '15 ml sin diluir',
    frecuencia: 'Cada 12 horas',
    duracion: 'Por 7 días',
    indicaciones: 'Enjuagar durante 30 segundos tras el cepillado. No ingerir',
  },
]

let contador = 0
const filaVacia = (): FilaMedicamento => ({
  clave: ++contador,
  medicamento: '',
  dosis: '',
  frecuencia: '',
  duracion: '',
  indicaciones: '',
})

const tieneContenido = (f: FilaMedicamento) => [f.medicamento, f.dosis, f.frecuencia, f.duracion, f.indicaciones].some((t) => t.trim())

export function EmitirRecetaModal({ abierto, alCerrar, paciente }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-3xl">
        <Formulario alCerrar={alCerrar} paciente={paciente} />
      </DialogContent>
    </Dialog>
  )
}

/** El estado vive dentro del contenido del diálogo: la receta se reinicia cada vez que se abre. */
function Formulario({ alCerrar, paciente }: Omit<Props, 'abierto'>) {
  const sesion = useSesion()
  const generar = useGenerarRecetaPdf()
  const sedeActivaId = sesion?.sedeActivaId ?? null
  const sedeNombre = sesion?.usuario.sedes.find((s) => s.id === sedeActivaId)?.nombre

  const [doctorNombre, setDoctorNombre] = useState(sesion?.usuario.nombre ?? '')
  const [cop, setCop] = useState('')
  const copEditado = useRef(false)
  const [diagnostico, setDiagnostico] = useState('')
  const [filas, setFilas] = useState<FilaMedicamento[]>(() => [filaVacia()])
  const [indicacionesGenerales, setIndicacionesGenerales] = useState('')
  const [errorDoctor, setErrorDoctor] = useState<string | null>(null)
  const [errorFilas, setErrorFilas] = useState<Record<number, string>>({})
  const [errorLista, setErrorLista] = useState<string | null>(null)
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  // El COP se toma del perfil de especialista del usuario, si existe; si no, queda vacío y editable.
  const especialistas = useEspecialistas(sedeActivaId)
  const copPerfil = especialistas.data?.find((e) => e.usuarioId === sesion?.usuario.id)?.cop
  useEffect(() => {
    if (copPerfil && !copEditado.current) setCop(copPerfil)
  }, [copPerfil])

  function cambiarFila(clave: number, campo: keyof Omit<FilaMedicamento, 'clave'>, valor: string) {
    setFilas((actual) => actual.map((f) => (f.clave === clave ? { ...f, [campo]: valor } : f)))
  }

  async function descargar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)

    const conContenido = filas.filter(tieneContenido)
    const faltantes: Record<number, string> = {}
    for (const f of conContenido) {
      if (!f.medicamento.trim()) faltantes[f.clave] = 'Indica el fármaco o presentación.'
    }
    setErrorDoctor(doctorNombre.trim() ? null : 'Escribe el nombre del profesional tratante.')
    setErrorFilas(faltantes)
    const sinMedicamentos = conContenido.length === 0
    setErrorLista(sinMedicamentos ? 'Agrega al menos un medicamento.' : null)
    if (!doctorNombre.trim() || sinMedicamentos || Object.keys(faltantes).length > 0) return

    const medicamentos: MedicamentoReceta[] = conContenido.map((f) => ({
      medicamento: f.medicamento.trim(),
      dosis: f.dosis.trim() || undefined,
      frecuencia: f.frecuencia.trim() || undefined,
      duracion: f.duracion.trim() || undefined,
      indicaciones: f.indicaciones.trim() || undefined,
    }))

    try {
      const pdf = await generar.mutateAsync({
        pacienteId: paciente.id,
        pacienteNombre: paciente.nombreCompleto,
        pacienteDni: paciente.dni || undefined,
        pacienteEdad: paciente.edad ?? undefined,
        doctorNombre: doctorNombre.trim(),
        doctorCop: cop.trim() || undefined,
        sedeNombre,
        fecha: hoyEnLima(),
        diagnostico: diagnostico.trim() || undefined,
        medicamentos,
        indicacionesGenerales: indicacionesGenerales.trim() || undefined,
      })
      descargarArchivo(pdf, `receta-${nombreParaArchivo(paciente.nombreCompleto)}.pdf`)
      toast('Receta descargada con éxito')
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  function aplicarPlantillaFarmaco(p: PlantillaFarmaco) {
    setFilas((actual) => {
      const ultima = actual[actual.length - 1]
      if (ultima && !tieneContenido(ultima)) {
        return actual.map((f, i) =>
          i === actual.length - 1
            ? {
                ...f,
                medicamento: p.medicamento,
                dosis: p.dosis,
                frecuencia: p.frecuencia,
                duracion: p.duracion,
                indicaciones: p.indicaciones,
              }
            : f
        )
      }
      return [
        ...actual,
        {
          clave: ++contador,
          medicamento: p.medicamento,
          dosis: p.dosis,
          frecuencia: p.frecuencia,
          duracion: p.duracion,
          indicaciones: p.indicaciones,
        },
      ]
    })
  }

  function enviarPorWhatsapp() {
    const conContenido = filas.filter(tieneContenido)
    if (conContenido.length === 0) {
      setErrorLista('Agrega al menos un medicamento para enviar la receta.')
      return
    }
    const telefono = paciente.telefono?.replace(/\D/g, '').slice(-9)
    const encabezado = `*📋 RECETA MÉDICA - ${sedeNombre ?? 'VILLADENT ODONTOLOGÍA'}*\n*Paciente:* ${paciente.nombreCompleto}\n*Dr(a):* ${doctorNombre.trim() || 'Odontólogo Tratante'}${cop.trim() ? ` (COP: ${cop.trim()})` : ''}\n*Fecha:* ${hoyEnLima()}${diagnostico.trim() ? `\n*Diagnóstico:* ${diagnostico.trim()}` : ''}\n\n*💊 Medicamentos recetados:*`

    const cuerpoMed = conContenido
      .map(
        (m, idx) =>
          `\n${idx + 1}. *${m.medicamento.trim()}*\n   • Dosis: ${m.dosis.trim() || 'Según indicación'}\n   • Frecuencia: ${m.frecuencia.trim() || '—'}\n   • Duración: ${m.duracion.trim() || '—'}${m.indicaciones.trim() ? `\n   • Nota: ${m.indicaciones.trim()}` : ''}`
      )
      .join('\n')

    const pie = indicacionesGenerales.trim()
      ? `\n\n*ℹ️ Indicaciones generales:*\n${indicacionesGenerales.trim()}\n\n_Presente este mensaje en la farmacia de su preferencia._`
      : `\n\n_Presente este mensaje en la farmacia de su preferencia._`

    const texto = encodeURIComponent(encabezado + cuerpoMed + pie)
    const url = telefono ? `https://wa.me/51${telefono}?text=${texto}` : `https://wa.me/?text=${texto}`

    window.open(url, '_blank', 'noopener,noreferrer')
  }

  return (
    <form onSubmit={descargar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Emitir receta médica</DialogTitle>
        <DialogDescription>
          Se genera el PDF oficial para entregar al paciente. La receta no se guarda en el sistema: descárgala al emitirla.
        </DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo="No se pudo generar la receta">
          {errorEnvio}
        </Alert>
      )}

      <section aria-label="Datos de la receta" className="grid gap-3 rounded-panel bg-surface-container-low p-4 sm:grid-cols-2">
        <p className="text-sm">
          <span className="block text-xs font-bold text-on-surface-variant">Paciente</span>
          <strong>{paciente.nombreCompleto}</strong>
          {paciente.dni && <span className="block text-on-surface-variant">DNI {paciente.dni}</span>}
          {paciente.edad != null && <span className="block text-on-surface-variant">{paciente.edad} años</span>}
        </p>
        <p className="text-sm">
          <span className="block text-xs font-bold text-on-surface-variant">Sede</span>
          <strong>{sedeNombre ?? '—'}</strong>
        </p>
        <Field label="Profesional tratante" error={errorDoctor ?? undefined}>
          <Input value={doctorNombre} onChange={(e) => setDoctorNombre(e.target.value)} autoComplete="off" />
        </Field>
        <Field label="COP (opcional)">
          <Input
            value={cop}
            inputMode="numeric"
            onChange={(e) => {
              copEditado.current = true
              setCop(e.target.value)
            }}
          />
        </Field>
      </section>

      <Field label="Diagnóstico (opcional)">
        <Input value={diagnostico} onChange={(e) => setDiagnostico(e.target.value)} placeholder="Ej. Pulpitis irreversible pieza 16" />
      </Field>

      {/* Botones Rápidos de Fármacos Dentales Habituales */}
      <div className="space-y-1.5 rounded-lg border border-primary/20 bg-primary-fixed/10 p-3">
        <span className="text-xs font-semibold text-primary block">
          ⚡ Fármacos Frecuentes en Odontología (1 clic para autocompletar):
        </span>
        <div className="flex flex-wrap gap-1.5">
          {FARMACOS_FRECUENTES.map((f) => (
            <button
              key={f.etiqueta}
              type="button"
              onClick={() => aplicarPlantillaFarmaco(f)}
              className="rounded-pill border border-primary/30 bg-surface-container-lowest px-2.5 py-1 text-xs font-medium text-primary hover:bg-primary-fixed/30 transition-colors"
            >
              {f.etiqueta}
            </button>
          ))}
        </div>
      </div>

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-sm font-bold text-on-surface">Medicamentos</legend>
        {errorLista && <Alert tono="error" titulo={errorLista} />}
        <ul className="flex flex-col gap-3">
          {filas.map((f, i) => (
            <li key={f.clave} className="flex flex-col gap-3 rounded-panel border border-outline-variant/50 p-3">
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <Fila etiqueta="Fármaco / Presentación" error={errorFilas[f.clave]}>
                    <Input
                      aria-label={`Fármaco / Presentación ${i + 1}`}
                      value={f.medicamento}
                      onChange={(e) => cambiarFila(f.clave, 'medicamento', e.target.value)}
                      placeholder="Ej. Amoxicilina 500mg"
                      aria-invalid={errorFilas[f.clave] ? true : undefined}
                    />
                  </Fila>
                </div>
                {filas.length > 1 && (
                  <Button
                    type="button"
                    variant="secundario"
                    size="icono"
                    aria-label={`Quitar medicamento ${i + 1}`}
                    onClick={() => setFilas((actual) => actual.filter((x) => x.clave !== f.clave))}
                  >
                    <Trash2 aria-hidden />
                  </Button>
                )}
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <Fila etiqueta="Dosis">
                  <Input aria-label={`Dosis ${i + 1}`} value={f.dosis} onChange={(e) => cambiarFila(f.clave, 'dosis', e.target.value)} placeholder="1 cápsula" />
                </Fila>
                <Fila etiqueta="Frecuencia">
                  <Input aria-label={`Frecuencia ${i + 1}`} value={f.frecuencia} onChange={(e) => cambiarFila(f.clave, 'frecuencia', e.target.value)} placeholder="Cada 8 horas" />
                </Fila>
                <Fila etiqueta="Duración">
                  <Input aria-label={`Duración ${i + 1}`} value={f.duracion} onChange={(e) => cambiarFila(f.clave, 'duracion', e.target.value)} placeholder="Por 7 días" />
                </Fila>
              </div>
              <Fila etiqueta="Indicaciones específicas">
                <Input
                  aria-label={`Indicaciones ${i + 1}`}
                  value={f.indicaciones}
                  onChange={(e) => cambiarFila(f.clave, 'indicaciones', e.target.value)}
                  placeholder="Ej. Tomar con abundante agua después de las comidas"
                />
              </Fila>
            </li>
          ))}
        </ul>
        <div>
          <Button type="button" variant="secundario" size="sm" onClick={() => setFilas((actual) => [...actual, filaVacia()])}>
            <Plus aria-hidden />
            Agregar medicamento
          </Button>
        </div>
      </fieldset>

      <Field label="Indicaciones generales (opcional)">
        <textarea
          className={`${inputClases} h-auto min-h-24 py-2`}
          value={indicacionesGenerales}
          onChange={(e) => setIndicacionesGenerales(e.target.value)}
          placeholder="Ej. Reposo relativo por 24 horas, dieta blanda"
        />
      </Field>

      <DialogFooter className="flex-wrap gap-2">
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={generar.isPending}>
          Cancelar
        </Button>
        <Button
          type="button"
          variant="secundario"
          onClick={enviarPorWhatsapp}
          className="text-emerald-700 border-emerald-300 hover:bg-emerald-50 dark:text-emerald-400"
          title="Enviar receta detallada al WhatsApp del paciente"
        >
          <MessageCircle className="size-4" aria-hidden />
          Enviar a WhatsApp
        </Button>
        <Button type="submit" cargando={generar.isPending}>
          Descargar Receta PDF
        </Button>
      </DialogFooter>
    </form>
  )
}

function Fila({ etiqueta, error, children }: { etiqueta: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-bold text-on-surface-variant">{etiqueta}</span>
      {children}
      {error && <p className="text-sm font-bold text-error">{error}</p>}
    </div>
  )
}
