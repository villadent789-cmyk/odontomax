import { Toaster as Sonner } from 'sonner'

/**
 * Contenedor de notificaciones emergentes (sonner, el que usa shadcn/ui).
 * Se monta una sola vez en la raíz. Uso: `toast.success('Paciente registrado')`.
 */
export function Toaster() {
  return (
    <Sonner
      position="top-right"
      closeButton
      containerAriaLabel="Notificaciones"
      toastOptions={{
        classNames: {
          toast:
            'font-body !rounded-panel !border-0 !bg-surface-container-lowest !text-on-surface !shadow-raised',
          title: '!font-bold',
          description: '!text-on-surface-variant',
          error: '!bg-error-container !text-on-error-container',
          success: '!bg-primary-fixed !text-on-primary-fixed',
          closeButton: '!bg-surface-container-lowest !text-on-surface-variant',
        },
      }}
    />
  )
}
