import { useMemo, useRef, useState } from 'react'
import { Alert, Button, Card, Skeleton, toast } from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { formatFechaHora } from '@/shared/format'
import { useFichaIngreso, useGuardarFichaIngreso, type FichaIngreso } from './api-ficha-ingreso'

type Clave = keyof Omit<FichaIngreso, 'actualizadoEn' | 'actualizadoPorNombre'>

type Campo =
  | { clave: Clave; etiqueta: string; tipo: 'texto'; ancho?: 'completo' }
  | { clave: Clave; etiqueta: string; tipo: 'largo' }
  | { clave: Clave; etiqueta: string; tipo: 'numero' }
  | { clave: Clave; etiqueta: string; tipo: 'fecha' }
  | { clave: Clave; etiqueta: string; tipo: 'si-no' }
  | { clave: Clave; etiqueta: string; tipo: 'opciones'; opciones: string[] }

interface Seccion {
  id: string
  titulo: string
  ayuda: string
  campos: Campo[]
  /** Solo para niños: se abre sola si el paciente es menor de 12 años. */
  pediatrica?: boolean
}

const SECCIONES: Seccion[] = [
  {
    id: 'personales',
    titulo: 'Datos personales',
    ayuda: 'Quién es el paciente y dónde ubicarlo.',
    campos: [
      { clave: 'lugarNacimiento', etiqueta: 'Lugar de nacimiento', tipo: 'texto' },
      { clave: 'estadoCivil', etiqueta: 'Estado civil', tipo: 'opciones', opciones: ['Soltero(a)', 'Casado(a)', 'Conviviente', 'Divorciado(a)', 'Viudo(a)'] },
      { clave: 'hijos', etiqueta: 'Hijos', tipo: 'numero' },
      { clave: 'gradoInstruccion', etiqueta: 'Grado de instrucción', tipo: 'opciones', opciones: ['Sin estudios', 'Primaria', 'Secundaria', 'Técnico', 'Superior', 'Posgrado'] },
      { clave: 'ocupacion', etiqueta: 'Ocupación', tipo: 'texto' },
      { clave: 'centroTrabajo', etiqueta: 'Centro de trabajo', tipo: 'texto' },
      { clave: 'centroEstudios', etiqueta: 'Centro de estudios', tipo: 'texto' },
      { clave: 'direccion', etiqueta: 'Dirección', tipo: 'texto', ancho: 'completo' },
      { clave: 'urbanizacion', etiqueta: 'Urbanización', tipo: 'texto' },
      { clave: 'pais', etiqueta: 'País', tipo: 'texto' },
      { clave: 'departamento', etiqueta: 'Departamento', tipo: 'texto' },
      { clave: 'provincia', etiqueta: 'Provincia', tipo: 'texto' },
      { clave: 'distrito', etiqueta: 'Distrito', tipo: 'texto' },
      { clave: 'correoAlternativo', etiqueta: 'Correo alternativo', tipo: 'texto' },
      { clave: 'telefonoFijo', etiqueta: 'Teléfono fijo', tipo: 'texto' },
    ],
  },
  {
    id: 'emergencia',
    titulo: 'En caso de emergencia',
    ayuda: 'A quién llamar y qué parentesco tiene con el paciente.',
    campos: [
      { clave: 'emergenciaNombre', etiqueta: 'Nombre del contacto', tipo: 'texto' },
      { clave: 'emergenciaTelefono', etiqueta: 'Teléfono del contacto', tipo: 'texto' },
      { clave: 'emergenciaParentesco', etiqueta: 'Parentesco', tipo: 'opciones', opciones: ['Madre', 'Padre', 'Esposo(a)', 'Hijo(a)', 'Hermano(a)', 'Abuelo(a)', 'Tío(a)', 'Amigo(a)', 'Otro'] },
    ],
  },
  {
    id: 'apoderado',
    titulo: 'Apoderado (si es menor de edad)',
    ayuda: 'Quién se responsabiliza del paciente: su papá, su mamá u otro adulto.',
    campos: [
      { clave: 'apoderadoNombre', etiqueta: 'Nombres', tipo: 'texto' },
      { clave: 'apoderadoApellido', etiqueta: 'Apellidos', tipo: 'texto' },
      { clave: 'apoderadoDni', etiqueta: 'DNI', tipo: 'texto' },
      { clave: 'apoderadoResponsabilidad', etiqueta: 'Responsabilidad', tipo: 'opciones', opciones: ['Paciente', 'Padre', 'Madre', 'Tutor', 'Otro familiar'] },
    ],
  },
  {
    id: 'seguro',
    titulo: 'Seguro (opcional)',
    ayuda: 'Aún no se trabaja con seguros; queda listo para cuando se necesite.',
    campos: [
      { clave: 'seguroCompania', etiqueta: 'Compañía de seguro', tipo: 'texto' },
      { clave: 'seguroPoliza', etiqueta: 'Póliza', tipo: 'texto' },
    ],
  },
  {
    id: 'atencion',
    titulo: 'Motivo de consulta y visitas anteriores',
    ayuda: 'Por qué viene hoy y qué antecedentes de atención dental tiene.',
    campos: [
      { clave: 'motivoConsulta', etiqueta: 'Motivo de consulta', tipo: 'largo' },
      { clave: 'ultimaVisitaDentista', etiqueta: 'Última visita al dentista', tipo: 'texto' },
      { clave: 'motivoUltimaVisita', etiqueta: 'Motivo de esa visita', tipo: 'texto' },
      { clave: 'fichaAnterior', etiqueta: 'N.º de ficha anterior', tipo: 'texto' },
      { clave: 'pacienteConRiesgo', etiqueta: 'Paciente con riesgo', tipo: 'si-no' },
      { clave: 'observaciones', etiqueta: 'Observaciones', tipo: 'largo' },
    ],
  },
  {
    id: 'examen',
    titulo: 'Examen clínico y estomatológico',
    ayuda: 'Lo que se observa y los hábitos de higiene.',
    campos: [
      { clave: 'sangradoEncias', etiqueta: 'Sangrado de encías', tipo: 'si-no' },
      { clave: 'bruxismo', etiqueta: 'Bruxismo', tipo: 'si-no' },
      { clave: 'mucosaBucalAlterada', etiqueta: 'Mucosa bucal alterada', tipo: 'si-no' },
      { clave: 'dolor', etiqueta: 'Dolor', tipo: 'si-no' },
      { clave: 'movilidadDentaria', etiqueta: 'Movilidad dentaria', tipo: 'si-no' },
      { clave: 'frecuenciaCepillado', etiqueta: 'Frecuencia de cepillado', tipo: 'texto' },
      { clave: 'frecuenciaVisitaDentista', etiqueta: 'Frecuencia de visita al dentista', tipo: 'texto' },
      { clave: 'experienciasTraumaticas', etiqueta: 'Experiencias dentales traumáticas', tipo: 'texto', ancho: 'completo' },
      { clave: 'ultimaProfilaxis', etiqueta: 'Última profilaxis', tipo: 'fecha' },
      { clave: 'siguienteProfilaxis', etiqueta: 'Siguiente profilaxis', tipo: 'fecha' },
    ],
  },
  {
    id: 'enfermedades',
    titulo: 'Historial de enfermedades',
    ayuda: 'Marca las que tiene o tuvo. Las alergias se registran en “Actualizar antecedentes”.',
    campos: [
      { clave: 'diabetes', etiqueta: 'Diabetes', tipo: 'si-no' },
      { clave: 'presionAlta', etiqueta: 'Presión alta', tipo: 'si-no' },
      { clave: 'infartoCardiaco', etiqueta: 'Infarto cardíaco', tipo: 'si-no' },
      { clave: 'anginaPecho', etiqueta: 'Angina de pecho', tipo: 'si-no' },
      { clave: 'hemorragias', etiqueta: 'Hemorragias', tipo: 'si-no' },
      { clave: 'alteracionRespiratoria', etiqueta: 'Alteración respiratoria', tipo: 'si-no' },
      { clave: 'enfermedadRenal', etiqueta: 'Enfermedad renal', tipo: 'si-no' },
      { clave: 'hepatitis', etiqueta: 'Hepatitis', tipo: 'si-no' },
      { clave: 'tbc', etiqueta: 'TBC', tipo: 'si-no' },
      { clave: 'enfVenereas', etiqueta: 'Enfermedades venéreas', tipo: 'si-no' },
      { clave: 'medicamentos', etiqueta: 'Medicamentos que toma', tipo: 'texto' },
      { clave: 'nombreMedico', etiqueta: 'Nombre de su médico', tipo: 'texto' },
    ],
  },
  {
    id: 'pediatria',
    titulo: 'Pediatría',
    ayuda: 'Solo para niños: antecedentes de la familia y del nacimiento.',
    pediatrica: true,
    campos: [
      { clave: 'pedMadre', etiqueta: 'Antecedentes de la madre', tipo: 'texto' },
      { clave: 'pedPadre', etiqueta: 'Antecedentes del padre', tipo: 'texto' },
      { clave: 'pedAbuelosPaternos', etiqueta: 'Abuelos paternos', tipo: 'texto' },
      { clave: 'pedAbuelosMaternos', etiqueta: 'Abuelos maternos', tipo: 'texto' },
      { clave: 'pedTipoParto', etiqueta: 'Tipo de parto', tipo: 'opciones', opciones: ['Parto', 'Cesárea'] },
      { clave: 'pedHorasTrabajoParto', etiqueta: 'Horas de trabajo de parto', tipo: 'texto' },
      { clave: 'pedSemanasGestacion', etiqueta: 'Semanas de gestación', tipo: 'texto' },
      { clave: 'pedLloroAlNacer', etiqueta: 'Lloró al nacer', tipo: 'si-no' },
      { clave: 'pedObservaciones', etiqueta: 'Observaciones', tipo: 'largo' },
    ],
  },
]

const claseControl =
  'w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-focus'

function tieneValor(v: unknown) {
  return v !== undefined && v !== null && v !== ''
}

/**
 * Historia clínica general del paciente: se llena una sola vez al ingresar y se puede ampliar después. Los odontogramas,
 * en cambio, se registran en cada episodio (inicial y final) y por eso viven en su propia pestaña.
 */
export function FichaIngresoPanel({ pacienteId, edad, puedeEditar = true, alGuardadoSeguir }: { pacienteId: string; edad?: number; puedeEditar?: boolean; alGuardadoSeguir?: () => void }) {
  const consulta = useFichaIngreso(pacienteId)
  if (consulta.isPending) return <Skeleton className="h-64 w-full" />
  if (consulta.isError) return <Alert tono="error" titulo="No se pudo cargar la historia clínica general">{mensajeError(consulta.error)}</Alert>
  // La clave reinicia el formulario cuando llega una versión nueva guardada.
  return <FormularioFicha key={consulta.data.actualizadoEn ?? 'nueva'} pacienteId={pacienteId} inicial={consulta.data} edad={edad} puedeEditar={puedeEditar} alGuardadoSeguir={alGuardadoSeguir} />
}

function FormularioFicha({ pacienteId, inicial, edad, puedeEditar, alGuardadoSeguir }: { pacienteId: string; inicial: FichaIngreso; edad?: number; puedeEditar: boolean; alGuardadoSeguir?: () => void }) {
  const guardar = useGuardarFichaIngreso(pacienteId)
  const [borrador, setBorrador] = useState<FichaIngreso>(inicial)
  const [sucio, setSucio] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const seguirAlGuardar = useRef(false)

  const secciones = useMemo(() => SECCIONES.filter((s) => !s.pediatrica || edad == null || edad < 18), [edad])
  const todos = secciones.flatMap((s) => s.campos)
  const llenos = todos.filter((c) => tieneValor(borrador[c.clave])).length
  const porcentaje = todos.length ? Math.round((llenos / todos.length) * 100) : 0

  const poner = (clave: Clave, valor: unknown) => {
    setBorrador((b) => ({ ...b, [clave]: valor === '' ? undefined : valor }))
    setSucio(true)
  }

  const enviar = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    seguirAlGuardar.current = (e.nativeEvent as SubmitEvent).submitter?.getAttribute('data-seguir') === 'true'
    try {
      await guardar.mutateAsync(borrador)
      setSucio(false)
      toast('Historia clínica general guardada')
      if (seguirAlGuardar.current) alGuardadoSeguir?.()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <form onSubmit={enviar} autoComplete="off" className="flex flex-col gap-4" aria-label="Historia clínica general">
      <Card className="flex flex-col gap-2 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-semibold text-on-surface">Historia clínica general · {porcentaje} % completa</p>
          {inicial.actualizadoEn && (
            <p className="text-xs text-on-surface-variant">
              Última actualización {formatFechaHora(inicial.actualizadoEn)}
              {inicial.actualizadoPorNombre ? ` por ${inicial.actualizadoPorNombre}` : ''}
            </p>
          )}
        </div>
        <div role="progressbar" aria-label="Avance de la historia clínica general" aria-valuenow={porcentaje} aria-valuemin={0} aria-valuemax={100} className="h-2 w-full overflow-hidden rounded-full bg-surface-container">
          <div className="h-full rounded-full bg-primary" style={{ width: `${porcentaje}%` }} />
        </div>
        <p className="text-xs text-on-surface-variant">Se llena una sola vez, al ingresar. Puedes dejar campos en blanco y completarlos después.</p>
      </Card>

      {secciones.map((s) => (
        <details key={s.id} open={s.id === 'personales' || s.id === 'atencion'} className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-4 shadow-card">
          <summary className="cursor-pointer select-none font-display text-base font-bold text-primary">{s.titulo}</summary>
          <p className="mb-3 mt-1 text-xs text-on-surface-variant">{s.ayuda}</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {s.campos.map((c) => {
              const id = `ficha-${c.clave}`
              const valor = borrador[c.clave]
              const ancho = c.tipo === 'largo' || (c.tipo === 'texto' && c.ancho === 'completo') ? 'sm:col-span-2 lg:col-span-3' : ''
              if (c.tipo === 'si-no') {
                return (
                  <label key={c.clave} htmlFor={id} className="flex min-h-11 items-center gap-2 rounded-control border border-outline-variant/40 px-3 text-sm text-on-surface">
                    <input id={id} type="checkbox" className="size-4" disabled={!puedeEditar} checked={Boolean(valor)} onChange={(e) => poner(c.clave, e.target.checked ? true : undefined)} />
                    {c.etiqueta}
                  </label>
                )
              }
              return (
                <div key={c.clave} className={ancho}>
                  <label htmlFor={id} className="mb-1 block text-xs font-bold text-on-surface">{c.etiqueta}</label>
                  {c.tipo === 'largo' ? (
                    <textarea id={id} rows={3} className={claseControl} disabled={!puedeEditar} value={(valor as string) ?? ''} onChange={(e) => poner(c.clave, e.target.value)} />
                  ) : c.tipo === 'opciones' ? (
                    <select id={id} className={claseControl} disabled={!puedeEditar} value={(valor as string) ?? ''} onChange={(e) => poner(c.clave, e.target.value)}>
                      <option value="">—</option>
                      {c.opciones.map((o) => (
                        <option key={o} value={o}>{o}</option>
                      ))}
                    </select>
                  ) : c.tipo === 'numero' ? (
                    <input id={id} type="number" min={0} max={30} inputMode="numeric" className={claseControl} disabled={!puedeEditar} value={(valor as number | undefined) ?? ''} onChange={(e) => poner(c.clave, e.target.value === '' ? undefined : Number(e.target.value))} />
                  ) : c.tipo === 'fecha' ? (
                    <input id={id} type="date" className={claseControl} disabled={!puedeEditar} value={(valor as string) ?? ''} onChange={(e) => poner(c.clave, e.target.value)} />
                  ) : (
                    <input id={id} type="text" autoComplete="off" className={claseControl} disabled={!puedeEditar} value={(valor as string) ?? ''} onChange={(e) => poner(c.clave, e.target.value)} />
                  )}
                </div>
              )
            })}
          </div>
        </details>
      ))}

      {error && <Alert tono="error" titulo="No se pudo guardar">{error}</Alert>}
      {puedeEditar && (
        <div className="sticky bottom-2 flex flex-wrap justify-end gap-2">
          {alGuardadoSeguir && (
            <Button type="submit" data-seguir="true" cargando={guardar.isPending}>
              Guardar y seguir con el odontograma
            </Button>
          )}
          <Button type="submit" variant={alGuardadoSeguir ? 'secundario' : 'primario'} cargando={guardar.isPending} disabled={!sucio}>
            Guardar historia clínica general
          </Button>
        </div>
      )}
    </form>
  )
}
