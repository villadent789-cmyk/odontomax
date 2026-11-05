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

const ELENA: Doctor = {
  id: 'e-elena',
  usuarioId: 'u-elena',
  nombre: 'Dra. Elena Paucar',
  especialidad: 'Rehabilitación oral',
  sedes: ['sede-chilca'],
  activo: true,
}
const MARIO: Doctor = { id: 'e-mario', usuarioId: 'u-mario', nombre: 'Dr. Mario Rivas', especialidad: 'Ortodoncia', sedes: ['sede-chilca'], activo: true }
const ANTIGUO: Doctor = { id: 'e-antiguo', nombre: 'Dr. Antiguo', especialidad: 'Endodoncia', sedes: ['sede-centro'], activo: false }

const U_ELENA: Usuario = { id: 'u-elena', nombre: 'Dra. Elena Paucar', email: 'elena@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: true }
const U_MARIO: Usuario = { id: 'u-mario', nombre: 'Dr. Mario Rivas', email: 'mario@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: true }
const U_ANA: Usuario = { id: 'u-ana', nombre: 'Ana Soto', email: 'ana@villadent.pe', rol: 'RECEPCION', sedeId: 'sede-chilca', activo: true }
const U_DUENO: Usuario = { id: 'u-dueno', nombre: 'Lucía Paredes', email: 'lucia@villadent.pe', rol: 'ADMIN_GENERAL', activo: true }

let usuarios: Usuario[] = []
let doctores: Doctor[] = []
let llamadas: string[] = []

function datos(u: Usuario[], d: Doctor[]) {
  usuarios = u
  doctores = d
  servidor.use(
    http.get(`${API}/usuarios`, () => HttpResponse.json(usuarios)),
    http.get(`${API}/especialistas/admin`, () => HttpResponse.json(doctores)),
  )
}

/** Simula al backend: DELETE /especialistas borra el perfil (409 si hay historial); DELETE /usuarios borra o da de baja. */
function apiEliminar(opciones: { historial?: boolean; bajaLogica?: boolean; cuentaFalla?: boolean } = {}) {
  llamadas = []
  servidor.use(
    http.delete(`${API}/especialistas/:id`, ({ params }) => {
      llamadas.push(`DELETE especialistas/${params.id}`)
      if (opciones.historial) {
        return errorBackend(
          409,
          'No se puede eliminar el especialista porque tiene historial clínico registrado. Desactívelo en su lugar para mantener la auditoría médica.',
        )
      }
      doctores = doctores.filter((d) => d.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
    http.delete(`${API}/usuarios/:id`, ({ params }) => {
      llamadas.push(`DELETE usuarios/${params.id}`)
      if (opciones.cuentaFalla) return errorBackend(400, 'No puede eliminar su propia cuenta de administrador en sesión.')
      if (opciones.bajaLogica) {
        usuarios = usuarios.map((u) => (u.id === params.id ? { ...u, activo: false } : u))
        return HttpResponse.json({
          status: 200,
          tipo: 'LOGICO',
          mensaje: 'Tiene registros históricos vinculados: fue dado de baja y desactivado del sistema.',
        })
      }
      usuarios = usuarios.filter((u) => u.id !== params.id)
      return HttpResponse.json({ status: 200, tipo: 'FISICO', mensaje: 'Usuario eliminado exitosamente de la base de datos.' })
    }),
    http.patch(`${API}/usuarios/:id/estado`, ({ request, params }) => {
      llamadas.push(`PATCH usuarios/${params.id}:${new URL(request.url).searchParams.get('activo')}`)
      return HttpResponse.json({ id: params.id, activo: false })
    }),
    http.patch(`${API}/especialistas/:id/estado`, ({ request, params }) => {
      llamadas.push(`PATCH especialistas/${params.id}:${new URL(request.url).searchParams.get('activo')}`)
      return HttpResponse.json({ id: params.id, activo: false })
    }),
  )
}

const filaDe = (nombre: RegExp | string) => within(screen.getByRole('rowheader', { name: nombre }).closest('tr') as HTMLElement)
const botonEliminar = (nombre: string) => screen.findByRole('button', { name: `Eliminar a ${nombre}` })
const confirmar = async () =>
  userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Eliminar definitivamente' }))

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

describe('Administración · eliminar doctores', { timeout: 20_000 }, () => {
  it('permite eliminar doctores y otros usuarios, pero protege la propia cuenta activa', async () => {
    datos([U_ELENA, U_ANA, U_DUENO], [ELENA])
    apiEliminar()
    await renderRuta('/app/configuracion')
    expect(await botonEliminar('Dra. Elena Paucar')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar a Ana Soto' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar a Lucía Paredes' })).toBeInTheDocument()
  })

  it('un usuario sin perfil de doctor se elimina directamente mediante su cuenta', async () => {
    datos([U_ANA], [])
    apiEliminar()
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Ana Soto'))
    await confirmar()
    await waitFor(() => expect(llamadas).toEqual(['DELETE usuarios/u-ana']))
    expect(await screen.findByText('Usuario eliminado: Ana Soto')).toBeInTheDocument()
  })

  it('pide confirmación explicando qué se borra, y cancelar no llama al backend', async () => {
    datos([U_ELENA], [ELENA])
    apiEliminar()
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dra. Elena Paucar'))
    const dialogo = await screen.findByRole('dialog', { name: '¿Eliminar a Dra. Elena Paucar?' })
    expect(within(dialogo).getByText(/perfil de doctor, su horario y su cuenta de acceso/)).toBeInTheDocument()
    expect(within(dialogo).getByText(/no se puede deshacer/)).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(llamadas).toEqual([])
  })

  it('borra primero el perfil y después la cuenta, avisa y la fila desaparece de la lista', async () => {
    datos([U_ELENA, U_ANA], [ELENA])
    apiEliminar()
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dra. Elena Paucar'))
    await confirmar()

    await waitFor(() => expect(llamadas).toEqual(['DELETE especialistas/e-elena', 'DELETE usuarios/u-elena']))
    expect(await screen.findByText('Doctor eliminado: Dra. Elena Paucar')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('rowheader', { name: /Dra\. Elena Paucar/ })).not.toBeInTheDocument())
    expect(screen.getByRole('rowheader', { name: /Ana Soto/ })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('si el backend conserva la cuenta por historial (baja lógica) lo dice y la deja desactivada', async () => {
    datos([U_MARIO], [MARIO])
    apiEliminar({ bajaLogica: true })
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dr. Mario Rivas'))
    await confirmar()

    expect(await screen.findByText(/fue dado de baja y desactivado del sistema/)).toBeInTheDocument()
    const fila = filaDe(/Dr\. Mario Rivas/)
    expect(await fila.findByText('Inactivo')).toBeInTheDocument()
    // Ya no es un doctor con perfil: no quedan acciones exclusivas de doctor (Horarios).
    expect(fila.queryByRole('button', { name: /Horarios/ })).not.toBeInTheDocument()
  })

  it('un doctor con historial clínico (409) se da de baja automáticamente sin bloquear al administrador', async () => {
    datos([U_ELENA], [ELENA])
    apiEliminar({ historial: true })
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dra. Elena Paucar'))
    await confirmar()

    await waitFor(() =>
      expect(llamadas).toEqual([
        'DELETE especialistas/e-elena',
        'PATCH especialistas/e-elena:false',
        'DELETE usuarios/u-elena',
      ]),
    )
    expect(await screen.findByText(/Dra\. Elena Paucar: El doctor cuenta con historial clínico/)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('un doctor antiguo sin cuenta solo necesita borrar su perfil', async () => {
    datos([], [ANTIGUO])
    apiEliminar()
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dr. Antiguo'))
    await confirmar()
    await waitFor(() => expect(llamadas).toEqual(['DELETE especialistas/e-antiguo']))
    expect(await screen.findByText('Doctor eliminado: Dr. Antiguo')).toBeInTheDocument()
  })

  it('si tras borrar el perfil falla la cuenta, avisa que quedó incompleto y qué hacer', async () => {
    datos([U_MARIO], [MARIO])
    apiEliminar({ cuentaFalla: true })
    await renderRuta('/app/configuracion')
    await userEvent.click(await botonEliminar('Dr. Mario Rivas'))
    await confirmar()

    const aviso = (await screen.findByText('La eliminación quedó incompleta')).closest('[role="status"]')
    expect(aviso).toHaveTextContent('Se eliminó el perfil de doctor')
    expect(aviso).toHaveTextContent('No puede eliminar su propia cuenta de administrador en sesión.')
    expect(aviso).toHaveTextContent('Desactívala desde la lista')
    expect(llamadas).toEqual(['DELETE especialistas/e-mario', 'DELETE usuarios/u-mario'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('no permite eliminar la propia cuenta', async () => {
    datos(
      [{ id: 'u-admin', nombre: 'Andrea Quispe', email: 'andrea@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: true }],
      [{ ...ELENA, id: 'e-yo', usuarioId: 'u-admin', nombre: 'Andrea Quispe' }],
    )
    apiEliminar()
    await renderRuta('/app/configuracion')
    expect(await botonEliminar('Andrea Quispe')).toBeDisabled()
  })
})
