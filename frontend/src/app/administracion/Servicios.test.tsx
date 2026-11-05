import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Esquema } from '@/shared/api'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

type Servicio = Esquema<'ServicioResponseDTO'>

const LIMPIEZA: Servicio = {
  id: 'limpieza-dental',
  nombre: 'Limpieza dental',
  especialidad: 'Odontología general',
  duracionMin: 30,
  precio: 80,
  descripcion: 'Profilaxis con ultrasonido',
  orden: 3,
  activo: true,
}
const BRACKETS: Servicio = { id: 'brackets', nombre: 'Brackets', especialidad: 'Ortodoncia', duracionMin: 45, orden: 7, activo: true }
const VIEJO: Servicio = { id: 'blanqueo', nombre: 'Blanqueo antiguo', especialidad: 'Estética', duracionMin: 60, precio: 250, activo: false }

let servicios: Servicio[] = []
let posts: Record<string, unknown>[] = []
let puts: Record<string, unknown>[] = []
let estados: string[] = []

function api(inicial: Servicio[], opciones: { falla?: 'crear' | 'estado' } = {}) {
  servicios = inicial
  posts = []
  puts = []
  estados = []
  servidor.use(
    http.get(`${API}/usuarios`, () => HttpResponse.json([])),
    http.get(`${API}/especialistas/admin`, () => HttpResponse.json([])),
    http.get(`${API}/servicios/admin`, () => HttpResponse.json(servicios)),
    http.post(`${API}/servicios`, async ({ request }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      posts.push(cuerpo)
      if (opciones.falla === 'crear') return errorBackend(400, 'La duración mínima es 5 minutos')
      const nuevo: Servicio = { ...(cuerpo as Servicio), id: 'endodoncia', activo: true }
      servicios = [...servicios, nuevo]
      return HttpResponse.json(nuevo, { status: 201 })
    }),
    http.put(`${API}/servicios/:id`, async ({ request, params }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      puts.push({ id: params.id, ...cuerpo })
      servicios = servicios.map((s) => (s.id === params.id ? { ...s, ...(cuerpo as Servicio) } : s))
      return HttpResponse.json(servicios.find((s) => s.id === params.id))
    }),
    http.patch(`${API}/servicios/:id/estado`, ({ request, params }) => {
      const activo = new URL(request.url).searchParams.get('activo') === 'true'
      estados.push(`${params.id}:${activo}`)
      if (opciones.falla === 'estado') return errorBackend(400, 'No se puede cambiar el estado del servicio')
      servicios = servicios.map((s) => (s.id === params.id ? { ...s, activo } : s))
      return HttpResponse.json(servicios.find((s) => s.id === params.id))
    }),
  )
}

async function abrirServicios() {
  await renderRuta('/app/configuracion')
  await userEvent.click(await screen.findByRole('tab', { name: 'Servicios' }))
  await screen.findByRole('region', { name: 'Catálogo de servicios' })
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
      sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }],
    },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Administración · servicios', { timeout: 20_000 }, () => {
  it('lista todo el catálogo, también los inactivos, con especialidad, duración y precio', async () => {
    api([LIMPIEZA, BRACKETS, VIEJO])
    await abrirServicios()
    const limpieza = fila(/Limpieza dental/)
    expect(limpieza.getByText('Odontología general')).toBeInTheDocument()
    expect(limpieza.getByText('30 min')).toBeInTheDocument()
    expect(limpieza.getByText('S/ 80.00')).toBeInTheDocument()
    expect(limpieza.getByText('Activo')).toBeInTheDocument()
    // Sin precio cargado se muestra un guion, no S/ 0.00.
    expect(fila(/Brackets/).getByText('—')).toBeInTheDocument()
    expect(fila(/Blanqueo antiguo/).getByText('Inactivo')).toBeInTheDocument()
  })

  it('"Insumos" guarda la receta del servicio con los insumos y cantidades elegidos', async () => {
    api([LIMPIEZA])
    let guardada: unknown = null
    servidor.use(
      http.get(`${API}/inventario/productos`, () =>
        HttpResponse.json([
          { id: 'p-resina', codigo: 'R-1', nombre: 'Resina A2', stock_minimo: 5, activo: true, stockPorSede: {}, stockTotal: 9, alertaStockBajo: false },
          { id: 'p-anestesia', codigo: 'A-1', nombre: 'Anestesia', stock_minimo: 5, activo: true, stockPorSede: {}, stockTotal: 9, alertaStockBajo: false },
        ]),
      ),
      http.get(`${API}/inventario/servicios/limpieza-dental/insumos`, () => HttpResponse.json([])),
      http.put(`${API}/inventario/servicios/limpieza-dental/insumos`, async ({ request }) => {
        guardada = await request.json()
        return HttpResponse.json([])
      }),
    )
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Insumos del servicio Limpieza dental' }))
    const d = within(await screen.findByRole('dialog', { name: /Insumos de Limpieza dental/ }))
    await userEvent.click(d.getByRole('button', { name: 'Agregar insumo' }))
    await userEvent.selectOptions(await d.findByLabelText('Insumo 1'), 'p-resina')
    await userEvent.clear(d.getByLabelText('Cantidad 1'))
    await userEvent.type(d.getByLabelText('Cantidad 1'), '3')
    await userEvent.click(d.getByRole('button', { name: 'Guardar receta' }))

    await waitFor(() => expect(guardada).toEqual([{ productoId: 'p-resina', cantidad: 3 }]))
  })

  it('en Villamax los servicios no tienen botón de insumos', async () => {
    api([LIMPIEZA])
    guardarSesion({
      token: 't',
      usuario: {
        id: 'u-admin',
        nombre: 'Andrea Quispe',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-villamax', nombre: 'Villamax Chilca', marca: 'VILLAMAX' }],
      },
      sedeActivaId: 'sede-villamax',
    })
    _reiniciarCacheSesion()
    await abrirServicios()
    expect(screen.queryByRole('button', { name: /Insumos del servicio/ })).not.toBeInTheDocument()
  })

  it('sin servicios muestra el estado vacío con la acción de crear el primero', async () => {
    api([])
    await abrirServicios()
    expect(await screen.findByRole('heading', { name: 'Aún no hay servicios' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Nuevo servicio' }).length).toBeGreaterThan(1)
  })

  it('si falla la carga muestra el error y permite reintentar', async () => {
    let intentos = 0
    api([LIMPIEZA])
    servidor.use(
      http.get(`${API}/servicios/admin`, () => {
        intentos += 1
        return intentos === 1 ? errorBackend(500, 'Fallo temporal del servidor') : HttpResponse.json([LIMPIEZA])
      }),
    )
    await abrirServicios()
    expect(await screen.findByText('Fallo temporal del servidor')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Reintentar/ }))
    expect(await screen.findByRole('rowheader', { name: /Limpieza dental/ })).toBeInTheDocument()
  })

  it('"Nuevo servicio" valida los campos antes de enviar', async () => {
    api([LIMPIEZA])
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo servicio' }))
    const d = within(await screen.findByRole('dialog', { name: 'Nuevo servicio' }))
    await userEvent.clear(d.getByLabelText('Duración (minutos)'))
    await userEvent.type(d.getByLabelText('Duración (minutos)'), '3')
    await userEvent.type(d.getByLabelText('Precio en S/ (opcional)'), '-5')
    await userEvent.click(d.getByRole('button', { name: 'Crear servicio' }))
    expect(d.getByText('Escribe el nombre del servicio.')).toBeInTheDocument()
    expect(d.getByText('Indica la especialidad.')).toBeInTheDocument()
    expect(d.getByText('La duración va de 5 a 480 minutos.')).toBeInTheDocument()
    expect(d.getByText('El precio no puede ser negativo.')).toBeInTheDocument()
    expect(posts).toEqual([])
  })

  it('crea un servicio con el cuerpo esperado y lo agrega a la lista', async () => {
    api([LIMPIEZA, BRACKETS])
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo servicio' }))
    const d = within(await screen.findByRole('dialog', { name: 'Nuevo servicio' }))
    await userEvent.type(d.getByLabelText('Nombre del servicio'), '  Endodoncia ')
    await userEvent.type(d.getByLabelText('Especialidad'), 'Endodoncia')
    await userEvent.clear(d.getByLabelText('Duración (minutos)'))
    await userEvent.type(d.getByLabelText('Duración (minutos)'), '90')
    await userEvent.type(d.getByLabelText('Precio en S/ (opcional)'), '350.5')
    await userEvent.click(d.getByRole('button', { name: 'Crear servicio' }))

    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toEqual({ nombre: 'Endodoncia', especialidad: 'Endodoncia', duracionMin: 90, precio: 350.5, descripcion: '' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('rowheader', { name: /Endodoncia/ })).toBeInTheDocument()
    expect(screen.getByText('Servicio creado: Endodoncia')).toBeInTheDocument()
  })

  it('sugiere las especialidades que ya existen en el catálogo', async () => {
    api([LIMPIEZA, BRACKETS, VIEJO])
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo servicio' }))
    const d = await screen.findByRole('dialog', { name: 'Nuevo servicio' })
    const sugeridas = Array.from(d.querySelectorAll('datalist option')).map((o) => o.getAttribute('value'))
    expect(sugeridas).toEqual(['Estética', 'Odontología general', 'Ortodoncia'])
  })

  it('si el backend rechaza el servicio muestra el motivo y deja el formulario abierto', async () => {
    api([LIMPIEZA], { falla: 'crear' })
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo servicio' }))
    const d = within(await screen.findByRole('dialog'))
    await userEvent.type(d.getByLabelText('Nombre del servicio'), 'Radiografía')
    await userEvent.type(d.getByLabelText('Especialidad'), 'Diagnóstico')
    await userEvent.click(d.getByRole('button', { name: 'Crear servicio' }))
    expect(await d.findByRole('alert')).toHaveTextContent('La duración mínima es 5 minutos')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('editar reenvía TODOS los datos (precio, descripción y orden): así el backend no los deja en blanco', async () => {
    api([LIMPIEZA])
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Editar servicio Limpieza dental' }))
    const d = within(await screen.findByRole('dialog', { name: 'Editar servicio' }))
    expect(d.getByLabelText('Nombre del servicio')).toHaveValue('Limpieza dental')
    expect(d.getByLabelText('Precio en S/ (opcional)')).toHaveValue(80)
    expect(d.getByLabelText('Descripción (opcional)')).toHaveValue('Profilaxis con ultrasonido')

    // Solo se cambia la duración.
    await userEvent.clear(d.getByLabelText('Duración (minutos)'))
    await userEvent.type(d.getByLabelText('Duración (minutos)'), '45')
    await userEvent.click(d.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toEqual({
      id: 'limpieza-dental',
      nombre: 'Limpieza dental',
      especialidad: 'Odontología general',
      duracionMin: 45,
      precio: 80,
      descripcion: 'Profilaxis con ultrasonido',
      orden: 3,
    })
    expect(await screen.findByText('45 min')).toBeInTheDocument()
    expect(screen.getByText('Servicio actualizado: Limpieza dental')).toBeInTheDocument()
  })

  it('no ofrece desactivar: los servicios activos solo tienen Editar; un inactivo se puede volver a activar', async () => {
    api([LIMPIEZA, VIEJO])
    await abrirServicios()
    expect(screen.queryByRole('button', { name: /Desactivar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Activar servicio Limpieza dental' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar servicio Limpieza dental' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Activar servicio Blanqueo antiguo' }))
    await waitFor(() => expect(estados).toEqual(['blanqueo:true']))
    expect(await fila(/Blanqueo antiguo/).findByText('Activo')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('explica que un servicio nuevo queda disponible en tratamientos y en la web de reservas', async () => {
    api([LIMPIEZA])
    await abrirServicios()
    expect(screen.getByText(/disponible de inmediato en los tratamientos clínicos y en la web de reservas/)).toBeInTheDocument()
  })

  it('un error al cambiar el estado se muestra sin cerrar la pantalla', async () => {
    api([VIEJO], { falla: 'estado' })
    await abrirServicios()
    await userEvent.click(screen.getByRole('button', { name: 'Activar servicio Blanqueo antiguo' }))
    expect(await screen.findByText('No se pudo activar el servicio')).toBeInTheDocument()
    expect(screen.getByText('No se puede cambiar el estado del servicio')).toBeInTheDocument()
  })

  it('la comisión para el doctor que deriva solo se pide al trabajar en Villamax y viaja en el cuerpo', async () => {
    guardarSesion({
      token: 't',
      usuario: {
        id: 'u-admin',
        nombre: 'Andrea Quispe',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
          { id: 'sede-villamax', nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
        ],
      },
      sedeActivaId: 'sede-villamax',
      negocioActivo: 'VILLAMAX',
    })
    api([])
    await abrirServicios()
    await userEvent.click(screen.getAllByRole('button', { name: 'Nuevo servicio' })[0])
    const d = within(await screen.findByRole('dialog', { name: 'Nuevo servicio' }))
    await userEvent.type(d.getByLabelText('Nombre del servicio'), 'Tomografía 3D')
    await userEvent.type(d.getByLabelText('Especialidad'), 'Radiología')
    await userEvent.type(d.getByLabelText('Precio en S/ (opcional)'), '250')
    await userEvent.type(d.getByLabelText('Comisión para el doctor que deriva (%)'), '12.5')
    await userEvent.click(d.getByRole('button', { name: 'Crear servicio' }))

    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toMatchObject({ nombre: 'Tomografía 3D', precio: 250, comisionPorcentaje: 12.5 })
  })

  it('con Villadent activo el formulario no muestra la comisión', async () => {
    api([])
    await abrirServicios()
    await userEvent.click(screen.getAllByRole('button', { name: 'Nuevo servicio' })[0])
    const d = within(await screen.findByRole('dialog', { name: 'Nuevo servicio' }))

    expect(d.queryByLabelText('Comisión para el doctor que deriva (%)')).not.toBeInTheDocument()
  })
})
