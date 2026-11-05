import { Link } from '@tanstack/react-router'
import { AlertTriangle, CheckCircle2, Circle, ClipboardPlus, FileText, Stethoscope } from 'lucide-react'
import { useState } from 'react'
import { Alert, Badge, Button, Card } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatSoles } from '@/shared/format'
import { BuscadorPaciente } from '../agenda/BuscadorPaciente'
import { nombrePaciente, usePaciente, type Paciente } from '../agenda/api-agenda'
import { useOdontogramaActual } from '../clinica/api-odontograma'
import { Miniatura } from '../clinica/EpisodiosOdontograma'
import { buscarHerramienta } from '../clinica/herramientasOdontograma'
import { useFichaIngreso } from '../pacientes/api-ficha-ingreso'
import { FotoPaciente } from '../pacientes/FotoPaciente'
import { AtajosNuevoTratamiento } from './AtajosNuevoTratamiento'
import { NuevoPresupuestoModal } from './NuevoPresupuestoModal'
import { NuevoTratamientoModal } from './NuevoTratamientoModal'
import type { TratamientoDTO } from './api-tratamientos'

function Paso({ hecho, titulo, detalle, accion }: { hecho: boolean; titulo: string; detalle: string; accion?: React.ReactNode }) {
  return (
    <li className="flex items-start gap-3 rounded-panel border border-outline-variant/50 p-3">
      {hecho ? <CheckCircle2 aria-hidden className="mt-0.5 size-5 shrink-0 text-emerald-600" /> : <Circle aria-hidden className="mt-0.5 size-5 shrink-0 text-on-surface-variant" />}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-on-surface">{titulo}</p>
        <p className="text-xs text-on-surface-variant">{detalle}</p>
      </div>
      {accion}
    </li>
  )
}

/**
 * Nuevo tratamiento: el camino corto para el doctor. Elige al paciente, revisa de un vistazo que tenga su historia y su
 * odontograma, y crea el plan o el presupuesto sin pasar por la agenda ni por la ficha.
 */
export function NuevoTratamientoPage() {
  const sesion = useSesion()
  const esVillamax = sesion?.negocioActivo === 'VILLAMAX'
  const sedeId = sesion?.sedeActivaId ?? ''
  const [elegido, setPaciente] = useState<Paciente | null>(null)
  // Paciente elegido desde un atajo (solo se conoce su id): se carga completo.
  const [idDeAtajo, setIdDeAtajo] = useState<string | undefined>()
  const deAtajo = usePaciente(idDeAtajo)
  const paciente: Paciente | null = elegido ?? (idDeAtajo && deAtajo.data ? (deAtajo.data as Paciente) : null)
  const [plan, setPlan] = useState(false)
  const [presupuesto, setPresupuesto] = useState(false)
  const [creado, setCreado] = useState<TratamientoDTO | null>(null)

  const id = paciente?.id
  const ficha = useFichaIngreso(id)
  const odontograma = useOdontogramaActual(esVillamax ? undefined : id)
  const hallazgos = (odontograma.data ?? []).filter((h) => h.estado?.trim().toLowerCase() !== 'sano')
  const fichaLista = Boolean(ficha.data?.actualizadoEn)
  const porHacer = hallazgos.filter((h) => buscarHerramienta(h.estado)?.grupo === 'por_hacer').length
  const realizados = hallazgos.filter((h) => buscarHerramienta(h.estado)?.grupo === 'realizado').length
  const alergias = paciente?.alergias ?? []
  const nombre = paciente ? nombrePaciente(paciente) : ''

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 p-4 md:p-8">
      <header className="flex items-center gap-4 rounded-panel bg-gradient-to-r from-primary to-primary/70 p-5 text-on-primary shadow-md">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-white/20">
          <Stethoscope aria-hidden className="size-6" />
        </span>
        <div>
          <h1 className="font-display text-2xl font-bold text-white">Nuevo tratamiento</h1>
          <p className="mt-0.5 text-sm text-white/90">
            {paciente ? 'Revisa su historia y su odontograma, y arma el plan o el presupuesto.' : 'Paso 1: busca al paciente por nombre, DNI o teléfono.'}
          </p>
        </div>
      </header>

      {!paciente ? (
        <>
          <Card className="p-4">
            <BuscadorPaciente sedeId={sedeId || null} seleccionado={null} onSeleccionar={setPaciente} />
          </Card>
          <AtajosNuevoTratamiento sedeId={sedeId || null} alElegirPaciente={setIdDeAtajo} />
        </>
      ) : (
        <>
          <Card className="flex flex-wrap items-center gap-4 p-4">
            <FotoPaciente pacienteId={paciente.id!} nombres={paciente.nombres} apellidos={paciente.apellidos} fotoActualizadaEn={paciente.fotoActualizadaEn} tamano="lg" />
            <div className="min-w-0 flex-1 space-y-1">
              <h2 className="font-display text-xl font-bold text-on-surface">{nombre}</h2>
              <p className="text-xs text-on-surface-variant">
                DNI {paciente.dni ?? '—'}
                {paciente.edad ? ` · ${paciente.edad} años` : ''}
                {paciente.telefono ? ` · ${paciente.telefono}` : ''}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {alergias.length > 0 && (
                  <Badge tono="peligro">
                    <AlertTriangle aria-hidden className="size-3" />
                    Alergias: {alergias.join(', ')}
                  </Badge>
                )}
                {paciente.gestante && <Badge tono="peligro">Gestante</Badge>}
                {(paciente.saldoPendiente ?? 0) > 0 && <Badge tono="accion">Debe {formatSoles(paciente.saldoPendiente ?? 0)}</Badge>}
              </div>
            </div>
            <Button variant="secundario" size="sm" onClick={() => { setPaciente(null); setIdDeAtajo(undefined); setCreado(null) }}>
              Cambiar paciente
            </Button>
          </Card>

          <ul className="flex flex-col gap-2" aria-label="Antes de empezar">
            <Paso hecho titulo="Datos del paciente" detalle="Ya está registrado." />
            <Paso
              hecho={fichaLista}
              titulo="Historia clínica general"
              detalle={fichaLista ? 'La ficha de ingreso ya se llenó.' : 'Falta llenar la ficha de ingreso (emergencia, enfermedades, hábitos).'}
              accion={
                !fichaLista && (
                  <Link to="/app/pacientes/$id" params={{ id: paciente.id! }} search={{ tab: 'ficha' }} className="text-xs font-semibold text-primary hover:underline">
                    Llenar
                  </Link>
                )
              }
            />
            {!esVillamax && (
              <Paso
                hecho={hallazgos.length > 0}
                titulo="Odontograma"
                detalle={hallazgos.length > 0 ? `${hallazgos.length} hallazgo(s) marcados.` : 'Todavía no hay hallazgos: márcalos antes de armar el plan.'}
                accion={
                  <Link to="/app/pacientes/$id" params={{ id: paciente.id! }} search={{ tab: 'odontograma' }} className="text-xs font-semibold text-primary hover:underline">
                    {hallazgos.length > 0 ? 'Ver / editar' : 'Marcar'}
                  </Link>
                }
              />
            )}
          </ul>

          {!esVillamax && (
            <div className="grid grid-cols-3 gap-3">
              <div className="rounded-panel border border-red-200 bg-red-50 p-3 text-center">
                <p className="font-display text-2xl font-bold text-red-700">{porHacer}</p>
                <p className="text-xs font-semibold text-red-800">Por hacer</p>
              </div>
              <div className="rounded-panel border border-blue-200 bg-blue-50 p-3 text-center">
                <p className="font-display text-2xl font-bold text-blue-700">{realizados}</p>
                <p className="text-xs font-semibold text-blue-800">Realizados</p>
              </div>
              <div className="rounded-panel border border-amber-200 bg-amber-50 p-3 text-center">
                <p className="font-display text-2xl font-bold text-amber-700">{formatSoles(paciente.saldoPendiente ?? 0)}</p>
                <p className="text-xs font-semibold text-amber-800">Saldo pendiente</p>
              </div>
            </div>
          )}

          {!esVillamax && hallazgos.length > 0 && (
            <Card className="p-4">
              <h3 className="mb-2 text-sm font-bold text-on-surface">Lo que hay en el odontograma</h3>
              <div className="mb-3 overflow-x-auto pb-1">
                <Miniatura piezas={hallazgos.map((h) => ({ piezaFdi: h.piezaFdi, estado: h.estado, caras: h.caras ?? undefined }))} titulo="Odontograma actual" />
              </div>
              <ul className="flex flex-wrap gap-1.5">
                {hallazgos.map((h) => {
                  const t = buscarHerramienta(h.estado)
                  return (
                    <li key={h.id ?? `${h.piezaFdi}-${h.estado}`} className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant/50 px-2 py-1 text-xs">
                      <span className="size-2 rounded-full" style={{ backgroundColor: t?.colorHex ?? '#64748B' }} />
                      <span className="font-mono font-bold text-primary">{h.piezaFdi}</span>
                      <span>{t?.label ?? h.estado}</span>
                    </li>
                  )
                })}
              </ul>
            </Card>
          )}

          {creado && (
            <Alert tono="exito" titulo={`Plan creado: ${creado.nombre ?? 'tratamiento'}`}>
              <Link to="/app/pacientes/$id" params={{ id: paciente.id! }} search={{ tab: 'tratamientos' }} className="font-semibold underline">
                Ver el plan en la ficha del paciente
              </Link>
            </Alert>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <button type="button" aria-label="Crear plan de tratamiento" onClick={() => setPlan(true)} className="flex flex-col items-start gap-2 rounded-panel border-2 border-primary bg-primary-fixed/40 p-4 text-left transition hover:bg-primary-fixed/70">
              <ClipboardPlus aria-hidden className="size-7 text-primary" />
              <span className="font-display text-lg font-bold text-on-surface">Crear plan de tratamiento</span>
              <span className="text-xs text-on-surface-variant">Piezas, costo por pieza, sesiones y descuento. Queda en la ficha del paciente.</span>
            </button>
            <button type="button" aria-label="Hacer un presupuesto" onClick={() => setPresupuesto(true)} className="flex flex-col items-start gap-2 rounded-panel border-2 border-outline-variant bg-surface-container-lowest p-4 text-left transition hover:border-primary/60 hover:bg-surface-container-low">
              <FileText aria-hidden className="size-7 text-primary" />
              <span className="font-display text-lg font-bold text-on-surface">Hacer un presupuesto</span>
              <span className="text-xs text-on-surface-variant">Cotiza sin comprometer el plan; se puede imprimir y entregar al paciente.</span>
            </button>
          </div>

          <NuevoTratamientoModal
            abierto={plan}
            alCerrar={() => setPlan(false)}
            pacienteId={paciente.id!}
            pacienteNombre={nombre}
            sedeId={paciente.sedeId ?? sedeId}
            alGuardar={(t) => setCreado(t)}
          />
          <NuevoPresupuestoModal abierto={presupuesto} alCerrar={() => setPresupuesto(false)} pacienteId={paciente.id!} pacienteNombre={nombre} sedeId={paciente.sedeId ?? sedeId} />
        </>
      )}
    </div>
  )
}
