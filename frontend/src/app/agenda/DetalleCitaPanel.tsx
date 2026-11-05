import { Link, useLocation } from '@tanstack/react-router'
import { Phone, ReceiptText, Stethoscope, TriangleAlert, Wallet, X } from 'lucide-react'
import { Dialog as DialogPrimitive } from 'radix-ui'
import { useRef, type ReactNode } from 'react'
import { Alert, Avatar, EstadoBadge, Eyebrow, Skeleton, buttonVariants } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatFecha, formatSoles } from '@/shared/format'
import { minutosEntreInstantes, horaInstanteEnLima } from './fechas'
import { esSaldoAFavor, formatoSaldo } from '@/shared/saldo'
import { AccionesCita } from './AccionesCita'
import { usePaciente, useSedesActivas, type Cita } from './api-agenda'
import { capitalizar, horaCorta, horaFin, nombreDia } from './fechas'

interface Props {
  cita: Cita | null
  onCerrar: () => void
  onCancelar: (cita: Cita) => void
  onReprogramar?: (cita: Cita) => void
  onActualizada: (cita: Cita) => void
}

/**
 * B4 · Detalle de cita (panel lateral). Sin captura: sigue el panel derecho de B3.
 * Datos de la cita (de la lista ya cargada: no existe GET /citas/{id}) + paciente con GET /pacientes/{id}
 * (DNI, edad, alergias, saldo pendiente). Acciones: cambiar estado, reprogramar y cancelar según estado y rol.
 */
export function DetalleCitaPanel({ cita, onCerrar, onCancelar, onReprogramar, onActualizada }: Props) {
  // Se abre sin Dialog.Trigger: se recuerda quién tenía el foco para devolvérselo al cerrar.
  const retorno = useRef<HTMLElement | null>(null)
  return (
    <DialogPrimitive.Root open={!!cita} onOpenChange={(abierto) => !abierto && onCerrar()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-40 bg-on-surface/30" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          onOpenAutoFocus={() => {
            retorno.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
          }}
          onCloseAutoFocus={(e) => {
            if (retorno.current?.isConnected) {
              e.preventDefault()
              retorno.current.focus()
            }
          }}
          className="fixed inset-y-0 right-0 z-40 flex w-full max-w-md flex-col gap-5 overflow-y-auto border-l border-outline-variant/40 bg-surface-container-lowest p-6 text-on-surface shadow-raised focus-visible:outline-none"
        >
          {cita && (
            <Contenido
              cita={cita}
              onCancelar={onCancelar}
              onReprogramar={onReprogramar}
              onActualizada={onActualizada}
            />
          )}
          <DialogPrimitive.Close
            aria-label="Cerrar detalle"
            className="absolute top-4 right-4 inline-flex size-9 items-center justify-center rounded-xl text-on-surface-variant hover:bg-surface-container-high focus-visible:outline-2 focus-visible:outline-focus [&_svg]:size-5"
          >
            <X aria-hidden />
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

function Fila({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8.5rem_1fr] gap-3">
      <dt className="text-sm text-on-surface-variant">{termino}</dt>
      <dd className="font-bold text-on-surface">{children}</dd>
    </div>
  )
}

function Contenido({ cita, onCancelar, onReprogramar, onActualizada }: Omit<Props, 'cita' | 'onCerrar'> & { cita: Cita }) {
  const sesion = useSesion()
  const origen = useLocation({ select: (l) => (l.pathname.endsWith('/bandeja') ? 'bandeja' : 'agenda') as 'agenda' | 'bandeja' })
  const paciente = usePaciente(cita.pacienteId)
  const sedes = useSedesActivas()
  const nombre = cita.pacienteNombre || paciente.data?.nombreCompleto || 'Paciente sin nombre'
  const sede =
    sedes.data?.find((s) => s.id === cita.sedeId)?.nombre ??
    sesion?.usuario.sedes.find((s) => s.id === cita.sedeId)?.nombre ??
    cita.sedeId
  const p = paciente.data
  const telefono = cita.pacienteTelefono || p?.telefono
  const notas = (cita.notas ?? '').split('\n').map((n) => n.trim()).filter(Boolean)

  return (
    <>
      <header className="flex items-start gap-4 pr-10">
        <Avatar nombre={nombre} tamano="lg" />
        <div className="flex min-w-0 flex-col gap-1">
          <DialogPrimitive.Title className="font-display text-headline-md font-bold text-primary">{nombre}</DialogPrimitive.Title>
          <EstadoBadge estado={cita.estado ?? ''} />
          {(cita.pacienteInasistencias ?? 0) >= 2 && (
            <p role="status" className="text-xs font-semibold text-error">
              Aviso: {cita.pacienteInasistencias} inasistencias previas
            </p>
          )}
          {paciente.isPending ? (
            <Skeleton className="h-5 w-40" />
          ) : p ? (
            <p className="text-sm text-on-surface-variant">
              DNI: <strong className="text-on-surface">{p.dni ?? '—'}</strong>
              {p.edad != null && <> · {p.edad} años</>}
            </p>
          ) : null}
        </div>
      </header>

      {telefono && (
        <a href={`tel:+51${telefono.replace(/\D/g, '').slice(-9)}`} className={buttonVariants({ variant: 'villamax' })}>
          <Phone aria-hidden />
          Llamar al paciente ({telefono})
        </a>
      )}

      {p && (p.alergias?.length || p.tieneDeuda || p.gestante || p.antecedentesMedicos) ? (
        <div className="flex flex-col gap-2">
          {p.alergias && p.alergias.length > 0 && (
            <Alert tono="error" titulo={`Alergias registradas: ${p.alergias.join(', ')}`} />
          )}
          {p.antecedentesMedicos && (
            <Alert tono="aviso" titulo={`Antecedentes médicos: ${p.antecedentesMedicos}`} />
          )}
          {p.gestante && (
            <Alert tono="info" titulo={`Paciente gestante${p.semanasGestacion ? ` (${p.semanasGestacion} semanas)` : ''}`} />
          )}
          {p.tieneDeuda && (
            <div role="status" className="flex items-center gap-3 rounded-panel bg-secondary-fixed p-4 text-sm text-on-secondary-fixed">
              <Wallet aria-hidden className="size-5 shrink-0" />
              <p className={`font-bold ${esSaldoAFavor(p.saldoPendiente) ? 'text-emerald-700' : ''}`}>
                {esSaldoAFavor(p.saldoPendiente) ? formatoSaldo(p.saldoPendiente) : `Saldo pendiente: ${formatSoles(p.saldoPendiente)}`}
              </p>
            </div>
          )}
        </div>
      ) : null}
      {paciente.isError && (
        <p className="flex items-center gap-2 text-sm text-on-surface-variant">
          <TriangleAlert aria-hidden className="size-4" />
          No se pudieron cargar los datos del paciente.
        </p>
      )}

      <dl className="flex flex-col gap-3 rounded-panel bg-surface-container-low p-4">
        <Fila termino="Sede y sillón">
          {sede}
          {cita.sillonNumero != null ? ` · Sillón ${cita.sillonNumero}` : ' · Sin sillón asignado'}
        </Fila>
        <Fila termino="Especialista">{cita.doctorNombre || 'Sin asignar'}</Fila>
        <Fila termino="Procedimiento">{cita.motivoConsulta || '—'}</Fila>
        <Fila termino="Horario">
          {capitalizar(nombreDia(cita.fecha ?? ''))} {formatFecha(cita.fecha)} · {horaCorta(cita.hora)} a{' '}
          {horaFin(cita.hora, cita.duracionMinutos)} ({cita.duracionMinutos ?? 30} min)
        </Fila>
        {cita.codigoTicket && (
          <Fila termino="Reserva web">
            <span className="font-mono">{cita.codigoTicket}</span>
          </Fila>
        )}
        {cita.adelantoRequerido && (
          <Fila termino="Adelanto">
            <span className={cita.adelantoEstado === 'PAGADO' ? 'text-emerald-700' : 'text-amber-800'}>
              S/ {(cita.adelantoMonto ?? 30).toFixed(2)} · {cita.adelantoEstado ?? 'PENDIENTE'}
              {cita.adelantoMedioPago ? ` (${cita.adelantoMedioPago})` : ''}
              {cita.adelantoNumeroOperacion ? ` · Op: ${cita.adelantoNumeroOperacion}` : ''}
            </span>
          </Fila>
        )}
        {cita.llegadaEn && cita.inicioAtencionEn && (
          <Fila termino="Tiempo de espera">
            {minutosEntreInstantes(cita.llegadaEn, cita.inicioAtencionEn)} min
            <span className="ml-1 text-xs font-normal text-on-surface-variant">
              (llegó {horaInstanteEnLima(cita.llegadaEn)} · pasó {horaInstanteEnLima(cita.inicioAtencionEn)})
            </span>
          </Fila>
        )}
        {cita.llegadaEn && !cita.inicioAtencionEn && cita.estado === 'EN_SALA' && (
          <Fila termino="Tiempo en espera">
            {minutosEntreInstantes(cita.llegadaEn)} min
            <span className="ml-1 text-xs font-normal text-on-surface-variant">
              (llegó {horaInstanteEnLima(cita.llegadaEn)})
            </span>
          </Fila>
        )}
        {cita.inicioAtencionEn && cita.finAtencionEn && (
          <Fila termino="Tiempo de atención">
            {minutosEntreInstantes(cita.inicioAtencionEn, cita.finAtencionEn)} min
            <span className="ml-1 text-xs font-normal text-on-surface-variant">
              ({horaInstanteEnLima(cita.inicioAtencionEn)} a {horaInstanteEnLima(cita.finAtencionEn)})
            </span>
          </Fila>
        )}
        {cita.inicioAtencionEn && !cita.finAtencionEn && cita.estado === 'EN_ATENCION' && (
          <Fila termino="Tiempo en sillón">
            {minutosEntreInstantes(cita.inicioAtencionEn)} min
            <span className="ml-1 text-xs font-normal text-on-surface-variant">
              (inició {horaInstanteEnLima(cita.inicioAtencionEn)})
            </span>
          </Fila>
        )}
        {cita.motivoReprogramacion && (
          <Fila termino="Motivo de reprogramación">
            {cita.motivoReprogramacion}
            {cita.motivoReprogramacionPor && (
              <span className="block text-xs font-normal text-on-surface-variant">
                Registrado por: {cita.motivoReprogramacionPor}
              </span>
            )}
          </Fila>
        )}
        {cita.estado === 'CANCELADA' && cita.motivoCancelacion && (
          <Fila termino="Motivo de cancelación">
            {cita.motivoCancelacion}
            {cita.motivoCancelacionPor && (
              <span className="block text-xs font-normal text-on-surface-variant">
                Registrado por: {cita.motivoCancelacionPor}
              </span>
            )}
          </Fila>
        )}
      </dl>

      {notas.length > 0 && (
        <section aria-labelledby="historial-cita" className="flex flex-col gap-2">
          <Eyebrow id="historial-cita">Historial de la cita</Eyebrow>
          <ul className="flex flex-col gap-2 text-sm">
            {notas.map((n, i) => (
              <li key={i} className="rounded-control bg-surface-container-low px-3 py-2">
                {n}
              </li>
            ))}
          </ul>
        </section>
      )}

      {cita.pacienteId && (
        <div className="flex flex-col gap-2">
          {cita.estado === 'ATENDIDA' && (
            <>
              {p && p.tieneDeuda && Number(p.saldoPendiente) > 0 && (
                <Link
                  to="/app/caja"
                  className={buttonVariants({
                    variant: 'primario',
                    size: 'lg',
                    className: 'w-full gap-2',
                  })}
                >
                  <Wallet aria-hidden />
                  Cobrar saldo ({formatSoles(p.saldoPendiente)})
                </Link>
              )}
              <Link
                to="/app/pacientes/$id"
                params={{ id: cita.pacienteId }}
                search={{ tab: 'comprobantes', desde: origen }}
                className={buttonVariants({
                  variant: 'secundario',
                  size: 'lg',
                  className: 'w-full gap-2',
                })}
              >
                <ReceiptText aria-hidden />
                Ver comprobante
              </Link>
              <Link
                to="/app/pacientes/$id"
                params={{ id: cita.pacienteId }}
                search={{ tab: 'resumen', desde: origen }}
                className={buttonVariants({
                  variant: 'secundario',
                  size: 'lg',
                  className: 'w-full gap-2',
                })}
              >
                <Stethoscope aria-hidden />
                Ver ficha
              </Link>
            </>
          )}

          {cita.estado === 'EN_ATENCION' && (
            <Link
              to="/app/pacientes/$id"
              params={{ id: cita.pacienteId }}
              search={{ tab: 'odontograma', desde: origen }}
              className={buttonVariants({
                variant: 'accion',
                size: 'lg',
                className: 'w-full gap-2',
              })}
            >
              <Stethoscope aria-hidden />
              Atender Paciente / Ver Ficha
            </Link>
          )}

          {cita.estado !== 'ATENDIDA' && cita.estado !== 'EN_ATENCION' && (
            <Link
              to="/app/pacientes/$id"
              params={{ id: cita.pacienteId }}
              search={{ tab: 'odontograma', desde: origen }}
              className={buttonVariants({
                variant: 'secundario',
                size: 'lg',
                className: 'w-full gap-2',
              })}
            >
              <Stethoscope aria-hidden />
              Ver Historia Clínica / Odontograma
            </Link>
          )}
        </div>
      )}

      <AccionesCita
        cita={cita}
        tamano="md"
        onCancelar={onCancelar}
        onReprogramar={onReprogramar}
        onActualizada={onActualizada}
        className="mt-auto"
      />
    </>
  )
}
