import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Esquema } from '@/shared/api'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

type Doctor = Esquema<'EspecialistaResponseDTO'>
type Usuario = Esquema<'UsuarioResponseDTO'>

const ELENA: Doctor = { id: 'e-elena', usuarioId: 'u-elena', nombre: 'Dra. Elena Paucar', especialidad: 'Rehabilitación oral', sedes: ['sede-chilca'], activo: true }
const ANTIGUO: Doctor = { id: 'e-antiguo', nombre: 'Dr. Antiguo', especialidad: 'Endodoncia', sedes: ['sede-centro'], activo: true }

const U_ELENA: Usuario = { id: 'u-elena', nombre: 'Dra. Elena Paucar', email: 'elena@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: true }
const U_ANA: Usuario = { id: 'u-ana', nombre: 'Ana Soto', email: 'ana@villadent.pe', rol: 'RECEPCION', sedeId: 'sede-chilca', activo: true }
const U_DUENO: Usuario = { id: 'u-dueno', nombre: 'Lucía Paredes', email: 'lucia@villadent.pe', rol: 'ADMIN_GENERAL', activo: true }
const U_YO: Usuario = { id: 'u-admin', nombre: 'Andrea Quispe', email: 'andrea@villadent.pe', rol: 'ADMIN_GENERAL', activo: true }
const U_SEDE: Usuario = { id: 'u-sede', nombre: 'Pedro Sede', email: 'pedro@villadent.pe', rol: 'ADMIN_SEDE', sedeId: 'sede-chilca', activo: true }

let usuarios: Usuario[] = []
let doctores: Doctor[] = []
let puts: Record<string, unknown>[] = []
let llamadas: string[] = []

function datos(u: Usuario[], d: Doctor[] = []) {
  usuarios = u
  doctores = d
  puts = []
  llamadas = []
  servidor.use(
    http.get(`${API}/usuarios`, () => HttpResponse.json(usuarios)),
    http.get(`${API}/especialistas/admin`, () => HttpResponse.json(doctores)),
    http.put(`${API}/usuarios/:id`, async ({ request, params }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      puts.push({ id: params.id, ...cuerpo })
      usuarios = usuarios.map((x) => (x.id === params.id ? { ...x, nombre: String(cuerpo.nombre), rol: String(cuerpo.rol), sedeId: cuerpo.sedeId as string | undefined } : x))
      return HttpResponse.json(usuarios.find((x) => x.id === params.id))
    }),
  )
}

const botonCuenta = (nombre: string) => screen.findByRole('button', { name: `Editar cuenta de ${nombre}` })
const botonSeguridad = (nombre: string) => screen.findByRole('button', { name: `Seguridad de ${nombre}` })

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-admin', nombre: 'Andrea Quispe', rol: 'ADMIN_GENERAL', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Administración · editar cuenta', { timeout: 20_000 }, () => {
  it('todo usuario con cuenta tiene "Cuenta" y "Seguridad"; un doctor antiguo sin cuenta, no', async () => {
    datos([U_ANA, U_ELENA, U_DUENO], [ELENA, ANTIGUO])
    await renderRuta('/app/configuracion')
    for (const n of ['Ana Soto', 'Dra. Elena Paucar', 'Lucía Paredes']) {
      expect(await botonCuenta(n)).toBeInTheDocument()
      expect(await botonSeguridad(n)).toBeInTheDocument()
    }
    expect(screen.queryByRole('button', { name: 'Editar cuenta de Dr. Antiguo' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Seguridad de Dr. Antiguo' })).not.toBeInTheDocument()
  })

  it('abre con los datos actuales y cambia nombre y sede enviando el cuerpo exacto', async () => {
    datos([U_ANA])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Ana Soto'))
    const dialogo = await screen.findByRole('dialog', { name: 'Editar cuenta' })
    const d = within(dialogo)
    expect(d.getByText(/ana@villadent.pe · el correo no se puede cambiar/)).toBeInTheDocument()
    expect(d.getByLabelText('Nombre completo')).toHaveValue('Ana Soto')
    expect(d.getByLabelText('Cargo / Rol')).toHaveValue('RECEPCION')
    expect(d.getByLabelText('Sede')).toHaveValue('sede-chilca')

    await userEvent.clear(d.getByLabelText('Nombre completo'))
    await userEvent.type(d.getByLabelText('Nombre completo'), '  Ana María Soto ')
    await userEvent.selectOptions(d.getByLabelText('Sede'), 'sede-centro')
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(puts).toEqual([{ id: 'u-ana', nombre: 'Ana María Soto', rol: 'RECEPCION', sedeId: 'sede-centro' }]))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('rowheader', { name: /Ana María Soto/ })).toBeInTheDocument()
    expect(screen.getByText('Cuenta actualizada: Ana María Soto')).toBeInTheDocument()
  })

  it('pasar a administrador general oculta la sede y no la envía', async () => {
    datos([U_ANA])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Ana Soto'))
    const d = within(await screen.findByRole('dialog'))
    await userEvent.selectOptions(d.getByLabelText('Cargo / Rol'), 'ADMIN_GENERAL')
    expect(d.queryByLabelText('Sede')).not.toBeInTheDocument()
    expect(d.getByText(/acceso a todas las sedes/)).toBeInTheDocument()
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toEqual({ id: 'u-ana', nombre: 'Ana Soto', rol: 'ADMIN_GENERAL' })
    expect(puts[0]).not.toHaveProperty('sedeId')
  })

  it('pasar a doctor avisa que tendrá perfil en la agenda', async () => {
    datos([U_ANA])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Ana Soto'))
    const d = within(await screen.findByRole('dialog'))
    expect(d.queryByText('Tendrá perfil de doctor en la agenda')).not.toBeInTheDocument()
    await userEvent.selectOptions(d.getByLabelText('Cargo / Rol'), 'ODONTOLOGO')
    expect(d.getByText('Tendrá perfil de doctor en la agenda')).toBeInTheDocument()
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(puts[0]).toMatchObject({ id: 'u-ana', rol: 'ODONTOLOGO', sedeId: 'sede-chilca' }))
  })

  it('un cargo heredado (administrador de sede) se conserva al editar solo el nombre', async () => {
    datos([U_SEDE])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Pedro Sede'))
    const d = within(await screen.findByRole('dialog'))
    expect(d.getByLabelText('Cargo / Rol')).toHaveValue('ADMIN_SEDE')
    await userEvent.type(d.getByLabelText('Nombre completo'), ' Ruiz')
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(puts).toEqual([{ id: 'u-sede', nombre: 'Pedro Sede Ruiz', rol: 'ADMIN_SEDE', sedeId: 'sede-chilca' }]))
  })

  it('una sede que ya no está activa sigue como opción para no cambiarla en silencio', async () => {
    datos([{ ...U_ANA, sedeId: 'sede-cerrada' }])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Ana Soto'))
    const d = within(await screen.findByRole('dialog'))
    expect(d.getByLabelText('Sede')).toHaveValue('sede-cerrada')
    expect(d.getByRole('option', { name: 'sede-cerrada (inactiva)' })).toBeInTheDocument()
  })

  it('valida el nombre antes de enviar', async () => {
    datos([U_ANA])
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Ana Soto'))
    const d = within(await screen.findByRole('dialog'))
    await userEvent.clear(d.getByLabelText('Nombre completo'))
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    expect(d.getByText('Escribe el nombre completo.')).toBeInTheDocument()
    expect(puts).toEqual([])
  })

  it('si el backend lo rechaza (p. ej. quitarse su propio rol) muestra el motivo y deja el modal abierto', async () => {
    datos([U_YO])
    servidor.use(
      http.put(`${API}/usuarios/:id`, () =>
        errorBackend(400, 'No puede quitarse el rol de Administrador General ni desactivar su propia cuenta en sesión.'),
      ),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonCuenta('Andrea Quispe'))
    const dialogo = await screen.findByRole('dialog')
    const d = within(dialogo)
    await userEvent.selectOptions(d.getByLabelText('Cargo / Rol'), 'RECEPCION')
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    expect(await d.findByRole('alert')).toHaveTextContent('No puede quitarse el rol de Administrador General')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('Administración · seguridad de la cuenta', { timeout: 20_000 }, () => {
  function apiSeguridad(opciones: { passwordFalla?: boolean; dosPasosFalla?: boolean } = {}) {
    servidor.use(
      http.post(`${API}/usuarios/:id/reset-password`, async ({ request, params }) => {
        const cuerpo = (await request.json()) as { newPassword: string }
        llamadas.push(`password ${params.id} ${cuerpo.newPassword}`)
        return opciones.passwordFalla
          ? errorBackend(400, 'La nueva contraseña no puede coincidir con ninguna de las últimas 3 contraseñas utilizadas.')
          : HttpResponse.json({ status: 200, mensaje: 'Contraseña restablecida correctamente' })
      }),
      http.post(`${API}/usuarios/:id/2fa/restablecer`, ({ params }) => {
        llamadas.push(`2fa ${params.id}`)
        return opciones.dosPasosFalla
          ? errorBackend(400, 'No puede restablecer su propio 2FA.')
          : HttpResponse.json({ status: 200, mensaje: '2FA restablecido correctamente' })
      }),
    )
  }

  async function abrirSeguridad(nombre: string) {
    await userEvent.click(await botonSeguridad(nombre))
    return within(await screen.findByRole('dialog', { name: `Seguridad de ${nombre}` }))
  }

  it('valida la contraseña (mínimo 4 y que coincidan) antes de enviar', async () => {
    datos([U_ANA])
    apiSeguridad()
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Ana Soto')
    const form = within(d.getByRole('form', { name: 'Nueva contraseña' }))
    await userEvent.type(form.getByLabelText('Nueva contraseña'), 'abc')
    await userEvent.type(form.getByLabelText('Repetir la contraseña'), 'abc')
    await userEvent.click(form.getByRole('button', { name: 'Cambiar contraseña' }))
    expect(form.getByText('La contraseña debe tener al menos 4 caracteres.')).toBeInTheDocument()

    await userEvent.type(form.getByLabelText('Nueva contraseña'), 'd')
    await userEvent.click(form.getByRole('button', { name: 'Cambiar contraseña' }))
    expect(form.getByText('Las contraseñas no coinciden.')).toBeInTheDocument()
    expect(llamadas).toEqual([])
  })

  it('cambia la contraseña, avisa que se cierran sus sesiones y limpia los campos sin mostrarla', async () => {
    datos([U_ANA])
    apiSeguridad()
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Ana Soto')
    const form = within(d.getByRole('form', { name: 'Nueva contraseña' }))
    await userEvent.type(form.getByLabelText('Nueva contraseña'), 'nueva-clave-1')
    await userEvent.type(form.getByLabelText('Repetir la contraseña'), 'nueva-clave-1')
    await userEvent.click(form.getByRole('button', { name: 'Cambiar contraseña' }))

    await waitFor(() => expect(llamadas).toEqual(['password u-ana nueva-clave-1']))
    expect(await d.findByText('Contraseña restablecida')).toBeInTheDocument()
    expect(d.getByText(/sesiones abiertas dejan de valer/)).toBeInTheDocument()
    expect(form.getByLabelText('Nueva contraseña')).toHaveValue('')
    expect(form.getByLabelText('Repetir la contraseña')).toHaveValue('')
    expect(screen.queryByText(/nueva-clave-1/)).not.toBeInTheDocument()
  })

  it('si el backend rechaza la contraseña (repetida) muestra el motivo y no repite la contraseña', async () => {
    datos([U_ANA])
    apiSeguridad({ passwordFalla: true })
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Ana Soto')
    const form = within(d.getByRole('form', { name: 'Nueva contraseña' }))
    await userEvent.type(form.getByLabelText('Nueva contraseña'), 'repetida-77')
    await userEvent.type(form.getByLabelText('Repetir la contraseña'), 'repetida-77')
    await userEvent.click(form.getByRole('button', { name: 'Cambiar contraseña' }))
    const alerta = await d.findByRole('alert')
    expect(alerta).toHaveTextContent('últimas 3 contraseñas')
    expect(alerta).not.toHaveTextContent('repetida-77')
  })

  it('restablecer el 2FA pide una segunda confirmación y recién entonces llama al backend', async () => {
    datos([U_ELENA], [ELENA])
    apiSeguridad()
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Dra. Elena Paucar')
    await userEvent.click(d.getByRole('button', { name: 'Restablecer 2FA' }))
    expect(llamadas).toEqual([])
    // Cancelar vuelve atrás sin llamar.
    await userEvent.click(d.getByRole('button', { name: 'Cancelar' }))
    expect(d.getByRole('button', { name: 'Restablecer 2FA' })).toBeInTheDocument()
    expect(llamadas).toEqual([])

    await userEvent.click(d.getByRole('button', { name: 'Restablecer 2FA' }))
    await userEvent.click(d.getByRole('button', { name: 'Sí, restablecer el 2FA' }))
    await waitFor(() => expect(llamadas).toEqual(['2fa u-elena']))
    expect(await d.findByText('Verificación en dos pasos restablecida')).toBeInTheDocument()
  })

  it('en la propia cuenta no se ofrece restablecer el 2FA (el backend no lo permite), pero sí cambiar la contraseña', async () => {
    datos([U_YO])
    apiSeguridad()
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Andrea Quispe')
    expect(d.queryByRole('button', { name: 'Restablecer 2FA' })).not.toBeInTheDocument()
    expect(d.getByText(/No puedes restablecer tu propio 2FA desde aquí/)).toBeInTheDocument()
    expect(d.getByRole('button', { name: 'Cambiar contraseña' })).toBeInTheDocument()
  })

  it('un error al restablecer el 2FA se muestra en el modal', async () => {
    datos([U_ANA])
    apiSeguridad({ dosPasosFalla: true })
    await renderRuta('/app/configuracion')
    const d = await abrirSeguridad('Ana Soto')
    await userEvent.click(d.getByRole('button', { name: 'Restablecer 2FA' }))
    await userEvent.click(d.getByRole('button', { name: 'Sí, restablecer el 2FA' }))
    expect(await d.findByRole('alert')).toHaveTextContent('No puede restablecer su propio 2FA.')
  })
})
