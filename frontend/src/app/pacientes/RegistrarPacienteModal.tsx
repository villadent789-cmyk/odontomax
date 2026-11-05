import { useState } from 'react'
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
import { useSesion } from '@/shared/auth'
import { useSedesActivas } from '@/app/agenda/api-agenda'
import { useCrearPacienteMutation, type PacienteDTO, type PacienteRequestDTO } from './api-pacientes'
import { textoDeError } from '@/shared/http'

interface Props {
  abierto: boolean
  alCerrar: () => void
  alGuardar?: (nuevo: PacienteDTO) => void
}

const FUENTES_CAPTACION = [
  { valor: 'FACEBOOK', etiqueta: 'Facebook Ads / Página' },
  { valor: 'INSTAGRAM', etiqueta: 'Instagram' },
  { valor: 'TIKTOK', etiqueta: 'TikTok' },
  { valor: 'GOOGLE', etiqueta: 'Búsqueda en Google' },
  { valor: 'REFERIDO_PACIENTE', etiqueta: 'Referido por paciente (Recomendación)' },
  { valor: 'REFERIDO_DOCTOR', etiqueta: 'Referido por doctor / odontólogo externo' },
  { valor: 'LOCAL', etiqueta: 'Pasaba por el local (Transeúnte)' },
  { valor: 'OTRO', etiqueta: 'Otro medio' },
] as const

export function RegistrarPacienteModal({ abierto, alCerrar, alGuardar }: Props) {
  const sesion = useSesion()
  const negocioActivo = sesion?.negocioActivo ?? 'VILLADENT'
  const { data: todasSedes = [] } = useSedesActivas()
  const sedes = todasSedes.filter(
    (s) => (s.unidadDeNegocio ?? 'VILLADENT') === negocioActivo && s.id !== 'sede-chilca-2'
  )
  const crearMutation = useCrearPacienteMutation()

  const [dni, setDni] = useState('')
  const [nombres, setNombres] = useState('')
  const [apellidos, setApellidos] = useState('')
  const [fechaNacimiento, setFechaNacimiento] = useState('')
  const [genero, setGenero] = useState<string>('FEMENINO')
  const [telefono, setTelefono] = useState('')
  const [sedeId, setSedeId] = useState(() => {
    if (negocioActivo === 'VILLAMAX') return 'sede-villamax-chilca'
    return sesion?.sedeActivaId ?? 'sede-chilca'
  })

  // Sede efectiva: si la seleccionada no está en el listado del negocio activo, usar la primera del negocio
  const sedeEfectiva = sedes.some((s) => s.id === sedeId) ? sedeId : (sedes[0]?.id ?? sedeId)
  const [fuenteCaptacion, setFuenteCaptacion] = useState<string>('LOCAL')
  const [referidoPorPacienteNombre, setReferidoPorPacienteNombre] = useState('')
  const [doctorReferidor, setDoctorReferidor] = useState('')
  const [alergias, setAlergias] = useState('')
  const [antecedentesMedicos, setAntecedentesMedicos] = useState('')
  const [gestante, setGestante] = useState(false)
  const [semanasGestacion, setSemanasGestacion] = useState<number | undefined>(undefined)

  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  function limpiar() {
    setDni('')
    setNombres('')
    setApellidos('')
    setFechaNacimiento('')
    setGenero('FEMENINO')
    setTelefono('')
    setFuenteCaptacion('LOCAL')
    setReferidoPorPacienteNombre('')
    setDoctorReferidor('')
    setAlergias('')
    setAntecedentesMedicos('')
    setGestante(false)
    setSemanasGestacion(undefined)
    setErrorLocal(null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (!/^\d{8}$/.test(dni.trim())) {
      setErrorLocal('El DNI debe tener exactamente 8 dígitos numéricos.')
      return
    }
    if (!nombres.trim() || !apellidos.trim()) {
      setErrorLocal('Nombres y apellidos son obligatorios.')
      return
    }
    if (telefono.trim() && !/^9\d{8}$/.test(telefono.trim())) {
      setErrorLocal('El celular debe tener 9 dígitos y empezar con 9 (ej. 964123456).')
      return
    }

    const payload: PacienteRequestDTO = {
      dni: dni.trim(),
      nombres: nombres.trim(),
      apellidos: apellidos.trim(),
      fechaNacimiento: fechaNacimiento || undefined,
      genero: genero || undefined,
      telefono: telefono.trim() || undefined,
      sedeId: negocioActivo === "VILLAMAX" ? (sedes[0]?.id ?? "sede-villamax-chilca") : sedeEfectiva,
      fuenteCaptacion,
      referidoPorPacienteNombre:
        fuenteCaptacion === 'REFERIDO_PACIENTE' ? referidoPorPacienteNombre.trim() || undefined : undefined,
      doctorReferidor:
        fuenteCaptacion === 'REFERIDO_DOCTOR' ? doctorReferidor.trim() || undefined : undefined,
      alergias: alergias.trim()
        ? alergias.split(',').map((s) => s.trim()).filter(Boolean)
        : undefined,
      antecedentesMedicos: antecedentesMedicos.trim() || undefined,
      gestante,
      semanasGestacion: gestante && semanasGestacion ? Number(semanasGestacion) : undefined,
    }

    try {
      const creado = await crearMutation.mutateAsync(payload)
      limpiar()
      alGuardar?.(creado)
      alCerrar()
    } catch (err) {
      setErrorLocal(textoDeError(err, 'Error al registrar al paciente en el sistema.'))
    }
  }

  return (
    <Dialog
      open={abierto}
      onOpenChange={(open) => {
        if (!open) {
          limpiar()
          alCerrar()
        }
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Registrar Nuevo Paciente</DialogTitle>
          <DialogDescription>
            Apertura de historia clínica digital para {negocioActivo === 'VILLAMAX' ? 'Villamax Radiología' : 'Villadent Odontología'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          {errorLocal && <Alert tono="error" titulo="Error de validación">{errorLocal}</Alert>}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field label="DNI (8 dígitos) *">
              <Input
                id="dni"
                value={dni}
                onChange={(e) => setDni(e.target.value.replace(/\D/g, '').slice(0, 8))}
                placeholder="45908123"
                inputMode="numeric"
                required
              />
            </Field>
            <Field label="Nombres *">
              <Input
                id="nombres"
                value={nombres}
                onChange={(e) => setNombres(e.target.value)}
                placeholder="Carlos"
                required
              />
            </Field>
            <Field label="Apellidos *">
              <Input
                id="apellidos"
                value={apellidos}
                onChange={(e) => setApellidos(e.target.value)}
                placeholder="Huamán Baldeón"
                required
              />
            </Field>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field label="Fecha de Nacimiento">
              <Input
                id="fechaNacimiento"
                type="date"
                value={fechaNacimiento}
                onChange={(e) => setFechaNacimiento(e.target.value)}
              />
            </Field>
            <Field label="Género">
              <select
                id="genero"
                value={genero}
                onChange={(e) => setGenero(e.target.value)}
                className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none"
              >
                <option value="FEMENINO">Femenino</option>
                <option value="MASCULINO">Masculino</option>
                <option value="OTRO">Otro</option>
              </select>
            </Field>
            <Field label="Sede de Registro *">
              {negocioActivo === 'VILLAMAX' ? (
                <Input
                  id="sedeId-fija"
                  value={sedes[0]?.nombre ?? 'Villamax Chilca'}
                  disabled
                  readOnly
                  className="w-full rounded-md border border-outline bg-surface-container-low px-3 py-2 text-sm text-on-surface font-medium cursor-not-allowed"
                />
              ) : (
                <select
                  id="sedeId"
                  value={sedeEfectiva}
                  onChange={(e) => setSedeId(e.target.value)}
                  className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none"
                  required
                >
                  {sedes.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombre}
                    </option>
                  ))}
                </select>
              )}
            </Field>
          </div>

          <Field label="Teléfono WhatsApp (9 dígitos)">
            <Input
              id="telefono"
              value={telefono}
              onChange={(e) => setTelefono(e.target.value.replace(/\D/g, '').slice(0, 9))}
              placeholder="964123456"
              inputMode="numeric"
            />
          </Field>

          {/* Sección Marketing y Captación (Sección 3 / 10) */}
          <div className="rounded-lg border border-outline bg-surface-container-low p-3.5 space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-primary">
              Marketing y Fuente de Captación (Inteligencia de Negocio)
            </p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label="¿Cómo nos conoció? *">
                <select
                  id="fuenteCaptacion"
                  value={fuenteCaptacion}
                  onChange={(e) => setFuenteCaptacion(e.target.value)}
                  className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none"
                >
                  {FUENTES_CAPTACION.map((f) => (
                    <option key={f.valor} value={f.valor}>
                      {f.etiqueta}
                    </option>
                  ))}
                </select>
              </Field>

              {fuenteCaptacion === 'REFERIDO_PACIENTE' && (
                <Field label="Nombre del paciente que lo refirió">
                  <Input
                    id="referidoPorPaciente"
                    value={referidoPorPacienteNombre}
                    onChange={(e) => setReferidoPorPacienteNombre(e.target.value)}
                    placeholder="Ej: Luciana Meza Cárdenas"
                  />
                </Field>
              )}

              {fuenteCaptacion === 'REFERIDO_DOCTOR' && (
                <Field label="Nombre del odontólogo / médico referidor">
                  <Input
                    id="doctorReferidor"
                    value={doctorReferidor}
                    onChange={(e) => setDoctorReferidor(e.target.value)}
                    placeholder="Ej: Dr. Roberto Alanya (Externo)"
                  />
                </Field>
              )}
            </div>
          </div>

          {/* Antecedentes clínicos y alergias (solo en Villadent) */}
          {negocioActivo !== 'VILLAMAX' && (
            <>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Field label="Alergias conocidas (separadas por coma)">
                  <Input
                    id="alergias"
                    value={alergias}
                    onChange={(e) => setAlergias(e.target.value)}
                    placeholder="Penicilina, AINES, Látex"
                  />
                </Field>
                <Field label="Antecedentes médicos relevantes">
                  <Input
                    id="antecedentes"
                    value={antecedentesMedicos}
                    onChange={(e) => setAntecedentesMedicos(e.target.value)}
                    placeholder="Hipertensión, Diabetes..."
                  />
                </Field>
              </div>

              <div className="flex items-center justify-between pt-2 border-t border-outline">
                <label className="flex items-center gap-2 text-sm text-on-surface cursor-pointer">
                  <input
                    type="checkbox"
                    checked={gestante}
                    onChange={(e) => setGestante(e.target.checked)}
                    className="size-4 rounded border-outline text-primary focus:ring-primary"
                  />
                  <span>¿Es paciente gestante?</span>
                </label>

                {gestante && (
                  <div className="w-40">
                    <Input
                      type="number"
                      min={1}
                      max={42}
                      value={semanasGestacion ?? ''}
                      onChange={(e) => setSemanasGestacion(e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="Semanas gestación"
                    />
                  </div>
                )}
              </div>
            </>
          )}

          <div className="flex justify-end gap-2.5 pt-3">
            <Button
              type="button"
              variant="secundario"
              onClick={() => {
                limpiar()
                alCerrar()
              }}
            >
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={crearMutation.isPending}>
              {crearMutation.isPending ? 'Guardando…' : 'Registrar Paciente'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
