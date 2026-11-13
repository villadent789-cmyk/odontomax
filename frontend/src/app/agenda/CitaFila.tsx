import { Armchair, MessageCircle, Phone, Stethoscope, Ticket } from 'lucide-react'
import { NombrePaciente } from './NombrePaciente'
import type { ReactNode } from 'react'
import { Button, EstadoBadge, buttonVariants } from '@/design-system'
import { formatFecha } from '@/shared/format'
import type { Cita } from './api-agenda'
import { horaCorta, horaFin, minutosEntreInstantes } from './fechas'

interface Props {
  cita: Cita
  /** Muestra la fecha además de la hora (reservas web de otros días). */
  conFecha?: boolean
  acciones?: ReactNode
  onVerDetalle?: (cita: Cita) => void
}

/** Fila de cita de la bandeja (B2): hora, paciente, especialista, motivo, sillón, estado y acciones. */
export function CitaFila({ cita, conFecha = false, acciones, onVerDetalle }: Props) {
  const paciente = cita.pacienteNombre || 'Paciente sin nombre'
  const esperaMinutos =
    cita.llegadaEn && cita.estado === 'EN_SALA' ? minutosEntreInstantes(cita.llegadaEn) : null
  const atencionMinutos =
    cita.inicioAtencionEn && cita.finAtencionEn
      ? minutosEntreInstantes(cita.inicioAtencionEn, cita.finAtencionEn)
      : null
  return (
    <li className="flex flex-col gap-3 rounded-panel bg-surface-container-low p-4" data-cita={cita.id}>
      <div className="flex flex-wrap items-start gap-x-4 gap-y-2">
        <div className="flex min-w-24 flex-col rounded-control bg-surface-container-lowest px-3 py-2 text-center shadow-card">
          {conFecha && <span className="text-xs font-bold text-on-surface-variant">{formatFecha(cita.fecha)}</span>}
          <span className="font-display text-lg font-bold text-primary">{horaCorta(cita.hora)}</span>
          <span className="text-xs text-on-surface-variant">hasta {horaFin(cita.hora, cita.duracionMinutos)}</span>
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-bold text-on-surface">
              <NombrePaciente cita={cita} />
            </h3>
            <EstadoBadge estado={cita.estado ?? ''} />
          </div>
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-on-surface-variant">
            {esperaMinutos !== null && <li>Espera {esperaMinutos} min</li>}
            {atencionMinutos !== null && (
              <li>Atendida en {atencionMinutos} min · prevista {cita.duracionMinutos ?? 30}</li>
            )}
            {cita.doctorNombre && (
              <li className="flex items-center gap-1.5">
                <Stethoscope aria-hidden className="size-4" />
                {cita.doctorNombre}
              </li>
            )}
            {cita.motivoConsulta && <li>{cita.motivoConsulta}</li>}
            {cita.sillonNumero != null && (
              <li className="flex items-center gap-1.5">
                <Armchair aria-hidden className="size-4" />
                Sillón {cita.sillonNumero}
              </li>
            )}
            {cita.codigoTicket && (
              <li className="flex items-center gap-1.5">
                <Ticket aria-hidden className="size-4" />
                <span className="font-mono">{cita.codigoTicket}</span>
              </li>
            )}
          </ul>
        </div>
        <div className="flex flex-wrap gap-2">
          {cita.pacienteTelefono && (
            <>
              {(() => {
                const esAtendida = cita.estado === 'ATENDIDA'
                const mensajeWhatsapp = esAtendida
                  ? encodeURIComponent(
                      `🦷 *INDICACIONES POST-ATENCIÓN - VILLADENT ODONTOLOGÍA*\n` +
                      `Estimado(a) ${cita.pacienteNombre?.split(/\s+/)[0] ?? 'paciente'}, para una pronta y segura recuperación tras su procedimiento de hoy:\n\n` +
                      `1. ❄️ *Hielo local:* Aplique compresas frías sobre la zona de forma intermitente (15 min sí, 15 min no).\n` +
                      `2. 🚫 *No escupir ni enjuagarse con fuerza* hoy para cuidar la cicatrización.\n` +
                      `3. 🍵 *Dieta blanda y tibia/fría:* Evite alimentos duros, calientes o irritantes.\n` +
                      `4. 🚭 *Evite fumar y actividad física intensa* durante las próximas 24 a 48 horas.\n` +
                      `5. 💊 *Medicación:* Tome sus fármacos indicados según la receta del doctor.\n\n` +
                      `Ante cualquier duda o molestia, comuníquese directamente por este canal. ¡Pronta recuperación!`
                    )
                  : encodeURIComponent(
                      `Hola ${cita.pacienteNombre?.split(/\s+/)[0] ?? 'estimado(a) paciente'}, le recordamos su cita${conFecha ? ` del ${formatFecha(cita.fecha)}` : ' de hoy'} a las ${horaCorta(cita.hora)}${cita.doctorNombre ? ` con ${cita.doctorNombre}` : ''} en Villadent Odontología. Por favor confírmenos su asistencia.`
                    )

                return (
                  <a
                    href={`https://wa.me/51${cita.pacienteTelefono.replace(/\D/g, '').slice(-9)}?text=${mensajeWhatsapp}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={buttonVariants({
                      variant: 'secundario',
                      size: 'sm',
                      className: esAtendida
                        ? 'text-sky-600 hover:text-sky-700 dark:text-sky-400'
                        : 'text-emerald-600 hover:text-emerald-700 dark:text-emerald-400',
                    })}
                    aria-label={esAtendida ? `Enviar cuidados post-atención a ${paciente}` : `Enviar WhatsApp a ${paciente}`}
                    title={esAtendida ? 'Enviar cuidados post-atención por WhatsApp' : 'Recordatorio por WhatsApp'}
                  >
                    <MessageCircle aria-hidden />
                    {esAtendida ? 'Cuidados Post-Op' : 'WhatsApp'}
                  </a>
                )
              })()}
              <a
                href={`tel:+51${cita.pacienteTelefono.replace(/\D/g, '').slice(-9)}`}
                className={buttonVariants({ variant: 'secundario', size: 'sm' })}
                aria-label={`Llamar a ${paciente} (${cita.pacienteTelefono})`}
              >
                <Phone aria-hidden />
                {cita.pacienteTelefono}
              </a>
            </>
          )}
          {onVerDetalle && (
            <Button size="sm" variant="fantasma" onClick={() => onVerDetalle(cita)} aria-label={`Ver detalle de la cita de ${paciente}`}>
              Ver detalle
            </Button>
          )}
        </div>
      </div>
      {acciones}
    </li>
  )
}
