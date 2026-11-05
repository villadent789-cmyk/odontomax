import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Esquema } from '@/shared/api'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

type Doctor = Esquema<'EspecialistaResponseDTO'>
type Usuario = Esquema<'UsuarioResponseDTO'>

const ELENA: Doctor = {
  id: 'e-elena',
  usuarioId: 'u-elena',
  nombre: 'Dra. Elena Paucar',
  email: 'elena@villadent.pe',
  especialidad: 'Rehabilitación oral',
  cop: '12345',
  telefono: '987654321',
  sedes: ['sede-chilca'],
  activo: true,
}
const MARIO: Doctor = {
  id: 'e-mario',
  usuarioId: 'u-mario',
  nombre: 'Dr. Mario Rivas',
  especialidad: 'Ortodoncia',
  sedes: ['sede-chilca', 'sede-centro'],
  activo: false,
}
const ANTIGUO: Doctor = { id: 'e-antiguo', nombre: 'Dr. Antiguo', especialidad: 'Endodoncia', sedes: ['sede-centro'], activo: false }

const U_ELENA: Usuario = { id: 'u-elena', nombre: 'Dra. Elena Paucar', email: 'elena@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: true }
const U_MARIO: Usuario = { id: 'u-mario', nombre: 'Dr. Mario Rivas', email: 'mario@villadent.pe', rol: 'ODONTOLOGO', sedeId: 'sede-chilca', activo: false }
const U_ANA: Usuario = { id: 'u-ana', nombre: 'Ana Soto', email: 'ana@villadent.pe', rol: 'RECEPCION', sedeId: 'sede-chilca', activo: true }
const U_DUENO: Usuario = { id: 'u-dueno', nombre: 'Andrea Quispe', email: 'andrea@villadent.pe', rol: 'ADMIN_GENERAL', activo: true }

function sesion(rol: Rol = 'ADMIN_GENERAL') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-admin', nombre: 'Andrea Quispe', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

let usuarios: Usuario[] = []
let doctores: Doctor[] = []

function datos(u: Usuario[], d: Doctor[] = []) {
  usuarios = u
  doctores = d
  servidor.use(
    http.get(`${API}/usuarios`, () => HttpResponse.json(usuarios)),
    http.get(`${API}/especialistas/admin`, () => HttpResponse.json(doctores)),
  )
}

const filaDe = (nombre: RegExp | string) => within(screen.getByRole('rowheader', { name: nombre }).closest('tr') as HTMLElement)

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  sesion()
})
afterEach(() => limpiarSesion())

describe('Administración · directorio de usuarios', { timeout: 15_000 }, () => {
  it('muestra esqueleto y luego cargo, sedes, perfil profesional y estado de cada usuario', async () => {
    datos([U_ELENA, U_MARIO, U_ANA, U_DUENO], [ELENA, MARIO])
    await renderRuta('/app/configuracion')
    expect(screen.getByRole('heading', { level: 1, name: 'Administración' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Cargando usuarios' })).toBeInTheDocument()

    await screen.findByRole('rowheader', { name: /Dra\. Elena Paucar/ })
    const elena = filaDe(/Dra\. Elena Paucar/)
    expect(elena.getByText('Doctor / Odontólogo')).toBeInTheDocument()
    expect(elena.getByText('Sede Chilca')).toBeInTheDocument()
    expect(elena.getByText('Rehabilitación oral')).toBeInTheDocument()
    expect(elena.getByText('COP 12345')).toBeInTheDocument()
    expect(elena.getByText('Tel. 987654321')).toBeInTheDocument()
    expect(elena.getByText('Activo')).toBeInTheDocument()

    const mario = filaDe(/Dr\. Mario Rivas/)
    expect(mario.getByText('Inactivo')).toBeInTheDocument()
    expect(mario.getByText('Sede Huancayo Centro')).toBeInTheDocument()

    const ana = filaDe(/Ana Soto/)
    expect(ana.getByText('Recepcionista')).toBeInTheDocument()
    expect(ana.getByText('Sede Chilca')).toBeInTheDocument()
    expect(ana.queryByRole('button', { name: /Horarios/ })).not.toBeInTheDocument()
    expect(ana.queryByRole('button', { name: /Editar perfil/ })).not.toBeInTheDocument()

    const dueno = filaDe(/Andrea Quispe/)
    expect(dueno.getByText('Administrador general')).toBeInTheDocument()
    expect(dueno.getByText('Todas las sedes')).toBeInTheDocument()
  })

  it('el botón principal es "Nuevo usuario"', async () => {
    datos([U_ELENA], [ELENA])
    await renderRuta('/app/configuracion')
    await screen.findByRole('rowheader', { name: /Elena/ })
    expect(screen.getByRole('button', { name: 'Nuevo usuario' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nuevo doctor' })).not.toBeInTheDocument()
  })

  it('muestra el estado vacío con la acción de crear el primer usuario', async () => {
    datos([], [])
    await renderRuta('/app/configuracion')
    expect(await screen.findByRole('heading', { name: 'Aún no hay usuarios' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Nuevo usuario' }).length).toBeGreaterThan(1)
  })

  it('muestra el error del backend y permite reintentar', async () => {
    let intentos = 0
    servidor.use(
      http.get(`${API}/usuarios`, () => {
        intentos += 1
        return intentos === 1 ? errorBackend(500, 'Fallo temporal del servidor') : HttpResponse.json([U_ANA])
      }),
      http.get(`${API}/especialistas/admin`, () => HttpResponse.json([])),
    )
    await renderRuta('/app/configuracion')
    expect(await screen.findByText('Fallo temporal del servidor')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Reintentar/ }))
    expect(await screen.findByRole('rowheader', { name: /Ana Soto/ })).toBeInTheDocument()
  })

  it('desactivar pide confirmación y llama PATCH /usuarios/{id}/estado?activo=false', async () => {
    datos([U_ANA])
    const llamadas: string[] = []
    servidor.use(
      http.patch(`${API}/usuarios/:id/estado`, ({ request, params }) => {
        llamadas.push(`${params.id}:${new URL(request.url).searchParams.get('activo')}`)
        return HttpResponse.json({ id: params.id, activo: false })
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar a Ana Soto' }))
    expect(llamadas).toEqual([])

    const dialogo = await screen.findByRole('dialog')
    expect(within(dialogo).getByText(/No podrá iniciar sesión/)).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }))
    await waitFor(() => expect(llamadas).toEqual(['u-ana:false']))
  })

  it('activar no pide confirmación; un doctor sin cuenta usa PATCH /especialistas/{id}/estado', async () => {
    datos([U_MARIO], [MARIO, ANTIGUO])
    const llamadas: string[] = []
    servidor.use(
      http.patch(`${API}/usuarios/:id/estado`, ({ request, params }) => {
        llamadas.push(`usuarios/${params.id}:${new URL(request.url).searchParams.get('activo')}`)
        return HttpResponse.json({ id: params.id, activo: true })
      }),
      http.patch(`${API}/especialistas/:id/estado`, ({ request, params }) => {
        llamadas.push(`especialistas/${params.id}:${new URL(request.url).searchParams.get('activo')}`)
        return HttpResponse.json({ id: params.id, activo: true })
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Activar a Dr. Mario Rivas' }))
    await waitFor(() => expect(llamadas).toEqual(['usuarios/u-mario:true']))
    await userEvent.click(screen.getByRole('button', { name: 'Activar a Dr. Antiguo' }))
    await waitFor(() => expect(llamadas).toEqual(['usuarios/u-mario:true', 'especialistas/e-antiguo:true']))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('un error al cambiar el estado se muestra sin cerrar la pantalla', async () => {
    datos([U_MARIO], [MARIO])
    servidor.use(http.patch(`${API}/usuarios/:id/estado`, () => errorBackend(403, 'No tienes permiso para esta acción')))
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Activar a Dr. Mario Rivas' }))
    expect(await screen.findByText('No se pudo cambiar el estado')).toBeInTheDocument()
  })
})

describe('Administración · nuevo usuario (un solo paso)', { timeout: 20_000 }, () => {
  let posts: Record<string, unknown>[] = []
  let otrasEscrituras: string[] = []

  /** Simula al backend: guarda el usuario y, si es ODONTOLOGO, crea su perfil de especialista. */
  function apiAlta() {
    posts = []
    otrasEscrituras = []
    datos([U_ANA], [])
    servidor.use(
      http.post(`${API}/usuarios`, async ({ request }) => {
        const cuerpo = (await request.json()) as Record<string, unknown>
        posts.push(cuerpo)
        const nuevo: Usuario = { id: 'u-nuevo', nombre: String(cuerpo.nombre), email: String(cuerpo.email), rol: String(cuerpo.rol), sedeId: cuerpo.sedeId as string | undefined, activo: true }
        usuarios = [...usuarios, nuevo]
        if (cuerpo.rol === 'ODONTOLOGO') {
          doctores = [...doctores, { id: 'e-nuevo', usuarioId: 'u-nuevo', nombre: nuevo.nombre, especialidad: 'Odontología general', sedes: [String(cuerpo.sedeId)], activo: true }]
        }
        return HttpResponse.json(nuevo)
      }),
      http.put(`${API}/especialistas/:id`, () => {
        otrasEscrituras.push('PUT /especialistas')
        return HttpResponse.json({})
      }),
      http.put(`${API}/especialistas/:id/horarios`, () => {
        otrasEscrituras.push('PUT horarios')
        return HttpResponse.json([])
      }),
    )
  }

  async function abrirFormulario() {
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo usuario' }))
    return screen.findByRole('dialog')
  }

  it('es un solo formulario: sin pasos, sin COP ni horarios y con "Guardar usuario"', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const dialogo = await abrirFormulario()
    const f = within(dialogo)
    expect(f.getByRole('heading', { name: 'Nuevo usuario' })).toBeInTheDocument()
    for (const campo of ['Nombre completo', 'Correo electrónico', 'Contraseña inicial', 'Cargo / Rol', 'Sede']) {
      expect(f.getByLabelText(campo)).toBeInTheDocument()
    }
    expect(f.getByRole('button', { name: 'Guardar usuario' })).toBeInTheDocument()
    expect(f.queryByRole('button', { name: 'Siguiente' })).not.toBeInTheDocument()
    expect(f.queryByText(/Paso \d/)).not.toBeInTheDocument()
    expect(f.queryByLabelText(/COP/)).not.toBeInTheDocument()
    expect(f.queryByRole('button', { name: 'Agregar turno' })).not.toBeInTheDocument()
    expect(within(f.getByLabelText('Cargo / Rol')).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Doctor / Odontólogo',
      'Recepcionista',
      'Administrador general',
    ])
  })

  it('valida los campos y exige una contraseña de al menos 4 caracteres', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const f = within(await abrirFormulario())
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))
    expect(f.getByText('Escribe el nombre completo.')).toBeInTheDocument()
    expect(f.getByText('Escribe un correo válido.')).toBeInTheDocument()
    expect(f.getByText('La contraseña debe tener al menos 4 caracteres.')).toBeInTheDocument()

    await userEvent.type(f.getByLabelText('Nombre completo'), 'Carlos Gómez')
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'carlos@villadent.pe')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), 'abc')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))
    expect(f.getByText('La contraseña debe tener al menos 4 caracteres.')).toBeInTheDocument()
    expect(posts).toEqual([])
  })

  it('doctor: una sola petición POST /usuarios, cierra el modal y la lista muestra al nuevo doctor con "Horarios"', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const f = within(await abrirFormulario())
    await userEvent.type(f.getByLabelText('Nombre completo'), 'Dra. Patricia Ramos')
    await userEvent.type(f.getByLabelText('Correo electrónico'), '  Patricia.Ramos@Villadent.PE ')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), 'clave-1')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posts).toEqual([
      { nombre: 'Dra. Patricia Ramos', email: 'patricia.ramos@villadent.pe', password: 'clave-1', rol: 'ODONTOLOGO', sedeId: 'sede-chilca' },
    ])
    const fila = filaDe(/Dra\. Patricia Ramos/)
    expect(await fila.findByRole('button', { name: 'Configurar horarios de Dra. Patricia Ramos' })).toBeInTheDocument()
    expect(otrasEscrituras).toEqual([])
  })

  it('doctor: pide la especialidad (libre) y la envía; recepcionista no la pide', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const f = within(await abrirFormulario())
    expect(f.getByLabelText('Especialidad')).toBeInTheDocument()
    await userEvent.type(f.getByLabelText('Nombre completo'), 'Dr. Luis Soto')
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'luis@villadent.pe')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), 'clave-1')
    await userEvent.type(f.getByLabelText('Especialidad'), ' Ortodoncia ')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))
    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toMatchObject({ rol: 'ODONTOLOGO', especialidad: 'Ortodoncia' })

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo usuario' }))
    const g = within(await screen.findByRole('dialog'))
    await userEvent.selectOptions(g.getByLabelText('Cargo / Rol'), 'RECEPCION')
    expect(g.queryByLabelText('Especialidad')).not.toBeInTheDocument()
  })

  it('recepcionista: usa la sede elegida y no ofrece acciones de doctor', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const f = within(await abrirFormulario())
    await userEvent.type(f.getByLabelText('Nombre completo'), 'Carlos Gómez')
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'carlos@villadent.pe')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), '1234')
    await userEvent.selectOptions(f.getByLabelText('Cargo / Rol'), 'RECEPCION')
    await userEvent.selectOptions(f.getByLabelText('Sede'), 'sede-centro')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))

    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toEqual({ nombre: 'Carlos Gómez', email: 'carlos@villadent.pe', password: '1234', rol: 'RECEPCION', sedeId: 'sede-centro' })
    const fila = filaDe(/Carlos Gómez/)
    expect(await fila.findByText('Recepcionista')).toBeInTheDocument()
    expect(fila.queryByRole('button', { name: /Horarios/ })).not.toBeInTheDocument()
  })

  it('administrador general: oculta la sede y no la envía', async () => {
    apiAlta()
    await renderRuta('/app/configuracion')
    const f = within(await abrirFormulario())
    await userEvent.selectOptions(f.getByLabelText('Cargo / Rol'), 'ADMIN_GENERAL')
    expect(f.queryByLabelText('Sede')).not.toBeInTheDocument()
    expect(f.getByText(/acceso a todas las sedes/)).toBeInTheDocument()

    await userEvent.type(f.getByLabelText('Nombre completo'), 'Dueño Dos')
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'dueno2@villadent.pe')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), '9876')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))

    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toEqual({ nombre: 'Dueño Dos', email: 'dueno2@villadent.pe', password: '9876', rol: 'ADMIN_GENERAL' })
    expect(posts[0]).not.toHaveProperty('sedeId')
  })

  it('un error del backend (correo duplicado) se muestra en una alerta sin la contraseña y permite corregir', async () => {
    apiAlta()
    let intentos = 0
    servidor.use(
      http.post(`${API}/usuarios`, async ({ request }) => {
        intentos += 1
        const cuerpo = (await request.json()) as Record<string, unknown>
        if (intentos === 1) return errorBackend(409, 'Ya existe un usuario con el correo ana@villadent.pe.')
        posts.push(cuerpo)
        return HttpResponse.json({ id: 'u-x', nombre: cuerpo.nombre, activo: true })
      }),
    )
    await renderRuta('/app/configuracion')
    const dialogo = await abrirFormulario()
    const f = within(dialogo)
    await userEvent.type(f.getByLabelText('Nombre completo'), 'Otra Ana')
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'ana@villadent.pe')
    await userEvent.type(f.getByLabelText('Contraseña inicial'), 'secreto-77')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))

    const alerta = await f.findByRole('alert')
    expect(alerta).toHaveTextContent('Ya existe un usuario con el correo ana@villadent.pe.')
    expect(alerta).not.toHaveTextContent('secreto-77')
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.clear(f.getByLabelText('Correo electrónico'))
    await userEvent.type(f.getByLabelText('Correo electrónico'), 'otra.ana@villadent.pe')
    await userEvent.click(f.getByRole('button', { name: 'Guardar usuario' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posts).toHaveLength(1)
  })
})

describe('Administración · horarios de un doctor existente', { timeout: 20_000 }, () => {
  it('carga el horario, lo modifica y, si usa una sede nueva, la agrega al perfil antes de guardar', async () => {
    datos([U_ELENA], [ELENA])
    const orden: string[] = []
    let perfil: Record<string, unknown> | undefined
    let enviados: Record<string, unknown>[] = []
    servidor.use(
      http.get(`${API}/especialistas/:id/horarios`, () =>
        HttpResponse.json([{ id: 'h1', sedeId: 'sede-chilca', diaSemana: 2, horaInicio: '09:00:00', horaFin: '13:00:00' }]),
      ),
      http.put(`${API}/especialistas/:id`, async ({ request }) => {
        orden.push('perfil')
        perfil = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(ELENA)
      }),
      http.put(`${API}/especialistas/:id/horarios`, async ({ request }) => {
        orden.push('horarios')
        enviados = (await request.json()) as Record<string, unknown>[]
        return HttpResponse.json([])
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Configurar horarios de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')

    expect(await within(dialogo).findByLabelText('Hora de fin del turno 1')).toHaveValue('13:00')
    fireEvent.change(within(dialogo).getByLabelText('Hora de fin del turno 1'), { target: { value: '14:00' } })

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Agregar turno' }))
    await userEvent.selectOptions(within(dialogo).getByLabelText('Sede del turno 2'), 'sede-centro')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() => expect(orden).toEqual(['perfil', 'horarios']))
    expect(perfil).toMatchObject({ usuarioId: 'u-elena', email: 'elena@villadent.pe', sedes: ['sede-chilca', 'sede-centro'] })
    expect(enviados).toEqual([
      { sedeId: 'sede-chilca', diaSemana: 2, horaInicio: '09:00', horaFin: '14:00' },
      // Un turno nuevo empieza con el horario de Villadent: 09:00 a 20:00.
      { sedeId: 'sede-centro', diaSemana: 1, horaInicio: '09:00', horaFin: '20:00' },
    ])
  })

  it('no envía si un turno termina antes de empezar', async () => {
    datos([U_ELENA], [ELENA])
    let puts = 0
    servidor.use(
      http.get(`${API}/especialistas/:id/horarios`, () =>
        HttpResponse.json([{ id: 'h1', sedeId: 'sede-chilca', diaSemana: 2, horaInicio: '09:00:00', horaFin: '13:00:00' }]),
      ),
      http.put(`${API}/especialistas/:id/horarios`, () => {
        puts += 1
        return HttpResponse.json([])
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Configurar horarios de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')
    fireEvent.change(await within(dialogo).findByLabelText('Hora de fin del turno 1'), { target: { value: '08:00' } })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar horarios' }))
    expect(await within(dialogo).findByText(/termina antes de empezar/)).toBeInTheDocument()
    expect(puts).toBe(0)
  })
})

describe('Administración · editar perfil', { timeout: 15_000 }, () => {
  it('reenvía el perfil completo conservando la cuenta vinculada y el correo', async () => {
    datos([U_ELENA], [ELENA])
    let cuerpo: Record<string, unknown> | undefined
    servidor.use(
      http.put(`${API}/especialistas/:id`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(ELENA)
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')
    const tel = within(dialogo).getByLabelText(/Teléfono/)
    await userEvent.clear(tel)
    await userEvent.type(tel, '911222333')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(cuerpo).toBeDefined())
    expect(cuerpo).toMatchObject({
      nombre: 'Dra. Elena Paucar',
      email: 'elena@villadent.pe',
      usuarioId: 'u-elena',
      cop: '12345',
      telefono: '911222333',
      sedes: ['sede-chilca'],
    })
  })

  it('exige al menos una sede de atención', async () => {
    datos([U_ELENA], [ELENA])
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.click(within(dialogo).getByRole('checkbox', { name: 'Sede Chilca' }))
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))
    expect(await within(dialogo).findByText('Elige al menos una sede de atención.')).toBeInTheDocument()
  })

  it('guarda el sillón fijo por sede con PATCH específico', async () => {
    datos([U_ELENA], [ELENA])
    let cuerpoSillones: unknown
    servidor.use(
      http.put(`${API}/especialistas/:id`, () => HttpResponse.json(ELENA)),
      http.patch(`${API}/especialistas/:id/sillones`, async ({ request }) => {
        cuerpoSillones = await request.json()
        return HttpResponse.json({ ...ELENA, sillonesPorSede: { 'sede-chilca': 2 } })
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.selectOptions(within(dialogo).getByLabelText('Sillón en Sede Chilca'), '2')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() =>
      expect(cuerpoSillones).toEqual({ sillonesPorSede: { 'sede-chilca': 2 }, sedesSinSillon: [] }),
    )
  })

  it('muestra la meta guardada y la cambia con PATCH /especialistas/{id}/meta', async () => {
    datos([U_ELENA], [ELENA])
    let cuerpoMeta: unknown
    servidor.use(
      http.get(`${API}/especialistas/:id/meta`, () =>
        HttpResponse.json({ especialistaId: ELENA.id, metaMensual: 20000, comisionExcedentePorcentaje: 10 }),
      ),
      http.put(`${API}/especialistas/:id`, () => HttpResponse.json(ELENA)),
      http.patch(`${API}/especialistas/:id/meta`, async ({ request }) => {
        cuerpoMeta = await request.json()
        return HttpResponse.json({ especialistaId: ELENA.id, metaMensual: 25000, comisionExcedentePorcentaje: 10 })
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')

    const meta = within(dialogo).getByLabelText('Meta mensual (S/)')
    await waitFor(() => expect(meta).toHaveValue('20000'))
    expect(within(dialogo).getByLabelText('Comisión sobre el excedente (%)')).toHaveValue('10')

    await userEvent.clear(meta)
    await userEvent.type(meta, '25000')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(cuerpoMeta).toEqual({ metaMensual: 25000, comisionExcedentePorcentaje: 10 }))
  })

  it('rechaza una comisión mayor a 100 sin llamar al servidor', async () => {
    datos([U_ELENA], [ELENA])
    let llamadas = 0
    servidor.use(
      http.get(`${API}/especialistas/:id/meta`, () => HttpResponse.json({ especialistaId: ELENA.id })),
      http.patch(`${API}/especialistas/:id/meta`, () => {
        llamadas += 1
        return HttpResponse.json({ especialistaId: ELENA.id })
      }),
    )
    await renderRuta('/app/configuracion')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil de Dra. Elena Paucar' }))
    const dialogo = await screen.findByRole('dialog')

    await userEvent.type(within(dialogo).getByLabelText('Comisión sobre el excedente (%)'), '150')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    expect(await within(dialogo).findByText('La comisión debe estar entre 0 y 100.')).toBeInTheDocument()
    expect(llamadas).toBe(0)
  })
})
