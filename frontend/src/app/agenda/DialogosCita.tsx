import { CancelarCitaDialog } from './CancelarCitaDialog'
import { DetalleCitaPanel } from './DetalleCitaPanel'
import { NuevaCitaDialog } from './NuevaCitaDialog'
import { ReprogramarDialog } from './ReprogramarDialog'
import type { EstadoDialogosCita } from './useDialogosCita'

/** Diálogos compartidos por la bandeja (B2) y la agenda (B3): detalle (B4), cancelar, crear y reprogramar (B5). */
export function DialogosCita({ dialogos }: { dialogos: EstadoDialogosCita }) {
  return (
    <>
      <DetalleCitaPanel
        cita={dialogos.detalle}
        onCerrar={dialogos.cerrarDetalle}
        onCancelar={dialogos.cancelar}
        onReprogramar={dialogos.reprogramar}
        onActualizada={dialogos.actualizada}
      />
      <CancelarCitaDialog cita={dialogos.cancelando} onCerrar={dialogos.cerrarCancelar} onCancelada={dialogos.actualizada} />
      <ReprogramarDialog cita={dialogos.reprogramando} onCerrar={dialogos.cerrarReprogramar} onReprogramada={dialogos.actualizada} />
      <NuevaCitaDialog prefijo={dialogos.nueva} onCerrar={dialogos.cerrarNueva} />
    </>
  )
}
