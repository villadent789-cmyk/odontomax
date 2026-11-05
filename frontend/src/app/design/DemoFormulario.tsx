import { zodResolver } from '@hookform/resolvers/zod'
import { Mail, Phone } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button, Field, Input, PasswordInput, toast } from '@/design-system'

const esquema = z.object({
  nombre: z.string().trim().min(3, 'Ingresa nombres y apellidos completos.'),
  dni: z.string().regex(/^\d{8}$/, 'DNI inválido: debe contener 8 dígitos numéricos.'),
  correo: z.email('Ingresa un correo válido.'),
  contrasena: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres.'),
})

type Datos = z.infer<typeof esquema>

/** Formulario de demostración (React Hook Form + Zod). No envía nada a la API. */
export function DemoFormulario() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: { dni: '4590' } })

  const enviar = async () => {
    await new Promise((r) => setTimeout(r, 600))
    toast.success('Formulario válido', { description: 'Demostración: no se envió nada al servidor.' })
  }

  return (
    <form noValidate onSubmit={handleSubmit(enviar)} className="grid gap-5 md:grid-cols-2">
      <Field label="Nombres y apellidos" ayuda="Ingresa tus datos completos" error={errors.nombre?.message}>
        <Input placeholder="Ej. Carlos Huamán" autoComplete="name" {...register('nombre')} />
      </Field>
      <Field label="DNI" error={errors.dni?.message}>
        <Input inputMode="numeric" maxLength={8} {...register('dni')} />
      </Field>
      <Field label="Correo corporativo" error={errors.correo?.message}>
        <Input
          type="email"
          icono={<Mail />}
          placeholder="recepcion@villadent.pe"
          autoComplete="email"
          {...register('correo')}
        />
      </Field>
      <Field label="Contraseña" error={errors.contrasena?.message}>
        <PasswordInput {...register('contrasena')} />
      </Field>
      <Field label="Teléfono (deshabilitado)">
        <Input icono={<Phone />} disabled value="964 123 456" readOnly />
      </Field>
      <div className="flex items-end">
        <Button type="submit" variant="accion" cargando={isSubmitting} className="w-full">
          Validar formulario
        </Button>
      </div>
    </form>
  )
}
