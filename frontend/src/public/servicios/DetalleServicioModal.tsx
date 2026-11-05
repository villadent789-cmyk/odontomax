import { Link } from '@tanstack/react-router'
import { Calendar, CheckCircle2, Clock, Sparkles } from 'lucide-react'
import {
  Badge,
  buttonVariants,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/design-system'
import type { ServicioPublico } from '@/public/reserva/api-reserva'

interface Props {
  servicio: ServicioPublico | null
  abierto: boolean
  onCerrar: () => void
}

export function DetalleServicioModal({ servicio, abierto, onCerrar }: Props) {
  if (!servicio) return null

  const duracion = servicio.duracionMin ? `${servicio.duracionMin} minutos` : '30-45 minutos'

  return (
    <Dialog open={abierto} onOpenChange={(ab) => !ab && onCerrar()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tono="primario">{servicio.especialidad ?? 'Especialidad Odontológica'}</Badge>
            <span className="flex items-center gap-1 text-xs font-semibold text-on-surface-variant">
              <Clock className="size-3.5" />
              {duracion}
            </span>
          </div>
          <DialogTitle className="text-2xl font-bold font-display text-primary mt-2">
            {servicio.nombre}
          </DialogTitle>
          <DialogDescription className="text-body-md text-on-surface-variant">
            {servicio.descripcion ||
              'Tratamiento odontológico integral realizado por especialistas certificados con instrumental esterilizado.'}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-3">
          <div className="flex items-center justify-between rounded-panel bg-surface-container-low p-4">
            <div>
              <span className="text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">
                Presupuesto
              </span>
              <p className="font-display text-xl font-bold text-primary">Segun evaluacion clinica</p>
            </div>
            <div className="text-right">
              <span className="text-xs font-semibold text-secondary">Reserva tu horario</span>
              <p className="text-sm font-bold text-on-surface">Confirmacion por recepcion</p>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <h4 className="text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">
              ¿Qué incluye este procedimiento?
            </h4>
            <ul className="flex flex-col gap-2 text-sm text-on-surface">
              <li className="flex items-start gap-2">
                <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" />
                <span>Evaluación clínica inicial y diagnóstico detallado por especialista.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" />
                <span>Uso de materiales certificados de alta biocompatibilidad y estética.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" />
                <span>Asepsia y bioseguridad bajo normas del Ministerio de Salud (MINSA).</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" />
                <span>Orientación post-tratamiento y pautas de higiene personalizadas.</span>
              </li>
            </ul>
          </div>

          <div className="rounded-panel border border-primary/20 bg-primary/5 p-3 text-xs text-primary">
            <p className="flex items-center gap-1.5 font-bold">
              <Sparkles className="size-4" />
              Diagnóstico 3D Villamax disponible
            </p>
            <p className="mt-0.5 text-on-surface-variant">
              Si tu tratamiento requiere radiografía panorámica o tomografía 3D, puedes realizarla en nuestra sede Villamax Chilca sin esperas.
            </p>
          </div>
        </div>

        <DialogFooter className="flex-col sm:flex-row gap-2">
          <button
            type="button"
            onClick={onCerrar}
            className={buttonVariants({ variant: 'secundario', size: 'md' })}
          >
            Cerrar
          </button>
          <Link
            to="/reservar"
            search={{ servicioId: servicio.id }}
            onClick={onCerrar}
            className={buttonVariants({ variant: 'accion', size: 'md', className: 'font-bold' })}
          >
            <Calendar className="mr-2 size-4" />
            Reservar este servicio
          </Link>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
