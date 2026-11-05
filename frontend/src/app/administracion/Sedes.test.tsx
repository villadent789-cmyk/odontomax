import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Esquema } from '@/shared/api'
import { guardarSesion, limpiarSesion, obtenerSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

type Sede = Esquema<'SedeResponseDTO'>

const CHILCA: Sede = {
  id: 'sede-chilca',
  nombre: 'Villadent Chilca',
  direccion: 'Calle Real 716',
  sillones: 4,
  telefono: '943672062',
  whatsapp: '51943672062',
  email: 'chilca@villadent.pe',
  horarioTexto: 'Lun - Sáb: 9:00 am - 8:00 pm',
  descripcion: 'Clínica principal',
  unidadDeNegocio: 'VILLADENT',
  activa: true,
}
const CENTRO: Sede = { id: 'sede-centro', nombre: 'Huancayo Centro', direccion: 'Calle Real 450', sillones: 3, unidadDeNegocio: 'VILLADENT', activa: true }
const CERRADA: Sede = { id: 'sede-vieja', nombre: 'Sede Vieja', direccion: 'Jr. Antiguo 1', sillones: 1, unidadDeNegocio: 'VILLAMAX', activa: false }

let sedes: Sede[] = []
let posts: Record<string, unknown>[] = []
let puts: Record<string, unknown>[] = []
let estados: string[] = []

function api(inicial: Sede[], opciones: { falla?: 'crear' | 'estado' } = {}) {
  sedes = inicial
  posts = []
  puts = []
  estados = []
  servidor.use(
    http.get(`${API}/usuarios`, () => HttpResponse.json([])),
    http.get(`${API}/especialistas/admin`, () => HttpResponse.json([])),
    http.get(`${API}/sedes/admin`, () => HttpResponse.json(sedes)),
    // La lista pública (la que arma el selector de sedes de la sesión) solo trae las activas.
    http.get(`${API}/sedes`, () => HttpResponse.json(sedes.filter((s) => s.activa))),
    http.post(`${API}/sedes`, async ({ request }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      posts.push(cuerpo)
      if (opciones.falla === 'crear') return errorBackend(400, 'Ya existe una sede con ese nombre')
      const nueva: Sede = { ...(cuerpo as Sede), id: 'sede-norte', activa: true }
      sedes = [...sedes, nueva]
      return HttpResponse.json(nueva)
    }),
    http.put(`${API}/sedes/:id`, async ({ request, params }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      puts.push({ id: params.id, ...cuerpo })
      sedes = sedes.map((s) => (s.id === params.id ? { ...s, ...(cuerpo as Sede) } : s))
      return HttpResponse.json(sedes.find((s) => s.id === params.id))
    }),
    http.patch(`${API}/sedes/:id/estado`, ({ request, params }) => {
      const activa = new URL(request.url).searchParams.get('activa') === 'true'
      estados.push(`${params.id}:${activa}`)
      if (opciones.falla === 'estado') return errorBackend(400, 'No se puede cambiar el estado de la sede')
      sedes = sedes.map((s) => (s.id === params.id ? { ...s, activa } : s))
      return HttpResponse.json(sedes.find((s) => s.id === params.id))
    }),
  )
}

async function abrirSedes() {
  await renderRuta('/app/configuracion')
  await userEvent.click(await screen.findByRole('tab', { name: 'Sedes' }))
  await screen.findByRole('region', { name: 'Directorio de sedes' })
}

const fila = (nombre: RegExp | string) => within(screen.getByRole('rowheader', { name: nombre }).closest('tr') as HTMLElement)

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: {
      id: 'u-admin',
      nombre: 'Andrea Quispe',
      rol: 'ADMIN_GENERAL',
      sedes: [
        { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
        { id: 'sede-centro', nombre: 'Huancayo Centro', marca: 'VILLADENT' },
      ],
    },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Administración · pestañas', { timeout: 20_000 }, () => {
  it('tiene las pestañas Usuarios, Sedes y Servicios, y abre en Usuarios', async () => {
    api([CHILCA])
    await renderRuta('/app/configuracion')
    const pestanas = within(await screen.findByRole('tablist')).getAllByRole('tab')
    expect(pestanas.map((t) => t.textContent)).toEqual(['Usuarios', 'Sedes', 'Servicios'])
    expect(screen.getByRole('tab', { name: 'Usuarios' })).toHaveAttribute('aria-selected', 'true')
    expect((await screen.findAllByRole('button', { name: 'Nuevo usuario' })).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Nueva sede' })).not.toBeInTheDocument()
  })
})

describe('Administración · sedes', { timeout: 20_000 }, () => {
  it('lista todas las sedes, también las inactivas, con su unidad, dirección, sillones y contacto', async () => {
    api([CHILCA, CENTRO, CERRADA])
    await abrirSedes()
    const chilca = fila(/Villadent Chilca/)
    expect(chilca.getByText('Villadent (odontología)')).toBeInTheDocument()
    expect(chilca.getByText('Calle Real 716')).toBeInTheDocument()
    expect(chilca.getByText('4')).toBeInTheDocument()
    expect(chilca.getByText('943672062')).toBeInTheDocument()
    expect(chilca.getByText('chilca@villadent.pe')).toBeInTheDocument()
    expect(chilca.getByText('Activa')).toBeInTheDocument()
    const vieja = fila(/Sede Vieja/)
    expect(vieja.getByText('Inactiva')).toBeInTheDocument()
    expect(vieja.getByText('Villamax (radiología)')).toBeInTheDocument()
  })

  it('sin sedes muestra el estado vacío con la acción de crear la primera', async () => {
    api([])
    await abrirSedes()
    expect(await screen.findByRole('heading', { name: 'Aún no hay sedes' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Nueva sede' }).length).toBeGreaterThan(1)
  })

  it('si falla la carga muestra el error y permite reintentar', async () => {
    let intentos = 0
    api([CHILCA])
    servidor.use(
      http.get(`${API}/sedes/admin`, () => {
        intentos += 1
        return intentos === 1 ? errorBackend(500, 'Fallo temporal del servidor') : HttpResponse.json([CHILCA])
      }),
    )
    await abrirSedes()
    expect(await screen.findByText('Fallo temporal del servidor')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Reintentar/ }))
    expect(await screen.findByRole('rowheader', { name: /Villadent Chilca/ })).toBeInTheDocument()
  })

  it('"Nueva sede" valida los campos antes de enviar', async () => {
    api([CHILCA])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Nueva sede' }))
    const d = within(await screen.findByRole('dialog', { name: 'Nueva sede' }))
    await userEvent.clear(d.getByLabelText('Sillones'))
    await userEvent.type(d.getByLabelText('Sillones'), '0')
    await userEvent.type(d.getByLabelText('Correo (opcional)'), 'no-es-correo')
    await userEvent.click(d.getByRole('button', { name: 'Crear sede' }))
    expect(d.getByText('Escribe el nombre de la sede.')).toBeInTheDocument()
    expect(d.getByText('Escribe la dirección.')).toBeInTheDocument()
    expect(d.getByText('Indica cuántos sillones tiene (al menos 1).')).toBeInTheDocument()
    expect(d.getByText('Escribe un correo válido.')).toBeInTheDocument()
    expect(posts).toEqual([])
  })

  it('crea una sede con el cuerpo esperado, la agrega a la lista y al selector de sedes de la sesión', async () => {
    api([CHILCA, CENTRO])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Nueva sede' }))
    const d = within(await screen.findByRole('dialog', { name: 'Nueva sede' }))
    await userEvent.type(d.getByLabelText('Nombre de la sede'), '  Sede Norte ')
    await userEvent.type(d.getByLabelText('Dirección'), 'Av. Norte 123')
    await userEvent.clear(d.getByLabelText('Sillones'))
    await userEvent.type(d.getByLabelText('Sillones'), '2')
    await userEvent.type(d.getByLabelText('Teléfono (opcional)'), '999888777')
    await userEvent.click(d.getByRole('button', { name: 'Crear sede' }))

    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toEqual({
      nombre: 'Sede Norte',
      direccion: 'Av. Norte 123',
      sillones: 2,
      telefono: '999888777',
      whatsapp: '',
      email: '',
      horarioTexto: '',
      descripcion: '',
      unidadDeNegocio: 'VILLADENT',
    })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('rowheader', { name: /Sede Norte/ })).toBeInTheDocument()
    expect(screen.getByText('Sede creada: Sede Norte')).toBeInTheDocument()
    // El selector de sedes de la cabecera sale de la sesión: ya incluye la nueva sin volver a iniciar sesión.
    await waitFor(() => expect(obtenerSesion()?.usuario.sedes.map((s) => s.id)).toEqual(['sede-chilca', 'sede-centro', 'sede-norte']))
  })

  it('si el backend rechaza la sede muestra el motivo y deja el formulario abierto', async () => {
    api([CHILCA], { falla: 'crear' })
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Nueva sede' }))
    const d = within(await screen.findByRole('dialog'))
    await userEvent.type(d.getByLabelText('Nombre de la sede'), 'Villadent Chilca')
    await userEvent.type(d.getByLabelText('Dirección'), 'Calle Real 716')
    await userEvent.click(d.getByRole('button', { name: 'Crear sede' }))
    expect(await d.findByRole('alert')).toHaveTextContent('Ya existe una sede con ese nombre')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('editar reenvía TODOS los datos de contacto: así el backend no los deja en blanco', async () => {
    api([CHILCA])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Editar sede Villadent Chilca' }))
    const d = within(await screen.findByRole('dialog', { name: 'Editar sede' }))
    expect(d.getByLabelText('Nombre de la sede')).toHaveValue('Villadent Chilca')
    expect(d.getByLabelText('Sillones')).toHaveValue(4)
    expect(d.getByLabelText('WhatsApp (opcional)')).toHaveValue('51943672062')
    expect(d.getByLabelText('Descripción (opcional)')).toHaveValue('Clínica principal')

    // Solo se cambia la dirección.
    await userEvent.clear(d.getByLabelText('Dirección'))
    await userEvent.type(d.getByLabelText('Dirección'), 'Calle Real 800')
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toEqual({
      id: 'sede-chilca',
      nombre: 'Villadent Chilca',
      direccion: 'Calle Real 800',
      sillones: 4,
      telefono: '943672062',
      whatsapp: '51943672062',
      email: 'chilca@villadent.pe',
      horarioTexto: 'Lun - Sáb: 9:00 am - 8:00 pm',
      descripcion: 'Clínica principal',
      unidadDeNegocio: 'VILLADENT',
    })
    expect(await screen.findByText('Calle Real 800')).toBeInTheDocument()
    expect(screen.getByText('Sede actualizada: Villadent Chilca')).toBeInTheDocument()
  })

  it('vaciar un campo opcional lo envía en blanco (así se borra a propósito)', async () => {
    api([CHILCA])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Editar sede Villadent Chilca' }))
    const d = within(await screen.findByRole('dialog'))
    await userEvent.clear(d.getByLabelText('Teléfono (opcional)'))
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toMatchObject({ telefono: '', whatsapp: '51943672062' })
  })

  it('desactivar pide confirmación; activar no; y la sede inactiva sale del selector de la sesión', async () => {
    api([CHILCA, CENTRO, CERRADA])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Desactivar sede Villadent Chilca' }))
    const dialogo = await screen.findByRole('dialog', { name: '¿Desactivar la sede Villadent Chilca?' })
    expect(within(dialogo).getByText(/Dejará de mostrarse en la web pública/)).toBeInTheDocument()
    expect(estados).toEqual([])
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar sede' }))

    await waitFor(() => expect(estados).toEqual(['sede-chilca:false']))
    expect(await fila(/Villadent Chilca/).findByText('Inactiva')).toBeInTheDocument()
    // La sede activa de la cabecera pasa a otra disponible: no queda apuntando a una sede desactivada.
    await waitFor(() => expect(obtenerSesion()?.usuario.sedes.map((s) => s.id)).toEqual(['sede-centro']))
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-centro')

    // Reactivar una inactiva no pide confirmación.
    await userEvent.click(screen.getByRole('button', { name: 'Activar sede Sede Vieja' }))
    await waitFor(() => expect(estados).toEqual(['sede-chilca:false', 'sede-vieja:true']))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('las sedes físicas oficiales no se pueden desactivar: muestran "Sede fija" en lugar del botón', async () => {
    const fijas: Sede[] = [
      { ...CHILCA, id: 'sede-chilca-1' },
      { ...CENTRO, id: 'sede-eltambo', nombre: 'Villadent Huancayo Centro' },
      { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', direccion: 'Jr. Ricardo Palma 411', sillones: 1, unidadDeNegocio: 'VILLAMAX', activa: true },
    ]
    api([...fijas, { ...CENTRO, id: 'sede-norte', nombre: 'Sede Norte' }])
    await abrirSedes()
    for (const nombre of ['Villadent Chilca', 'Villadent Huancayo Centro', 'Villamax Chilca']) {
      expect(fila(new RegExp(nombre)).getByText('Sede fija')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: `Desactivar sede ${nombre}` })).not.toBeInTheDocument()
      // Editar sigue disponible.
      expect(screen.getByRole('button', { name: `Editar sede ${nombre}` })).toBeInTheDocument()
    }
    // Una sede que no es de las oficiales sí se puede desactivar.
    expect(screen.getByRole('button', { name: 'Desactivar sede Sede Norte' })).toBeInTheDocument()
  })

  it('una sede fija que ya estuviera inactiva se puede volver a activar', async () => {
    api([{ ...CERRADA, id: 'sede-eltambo', nombre: 'Villadent Huancayo Centro' }])
    await abrirSedes()
    expect(screen.getByRole('button', { name: 'Activar sede Villadent Huancayo Centro' })).toBeInTheDocument()
  })

  it('cancelar la desactivación no llama al backend', async () => {
    api([CHILCA])
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Desactivar sede Villadent Chilca' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(estados).toEqual([])
  })

  it('un error al cambiar el estado se muestra sin cerrar la pantalla', async () => {
    api([CERRADA], { falla: 'estado' })
    await abrirSedes()
    await userEvent.click(screen.getByRole('button', { name: 'Activar sede Sede Vieja' }))
    expect(await screen.findByText('No se pudo cambiar el estado de la sede')).toBeInTheDocument()
    expect(screen.getByText('No se puede cambiar el estado de la sede')).toBeInTheDocument()
  })
})
