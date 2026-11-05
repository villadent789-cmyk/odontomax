import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { formatFecha, hoyEnLima } from '@/shared/format'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type {
  ProductoInventarioDTO,
  InventarioMovimientoDTO,
  TransferenciaResponseDTO,
} from './api-inventario'

const API = 'http://localhost:3000/api/v1'

/** Fecha "aaaa-mm-dd" a `dias` días de hoy (Lima), para que los avisos de vencimiento no dependan del día en que corra la prueba. */
function fechaEn(dias: number): string {
  const d = new Date(`${hoyEnLima()}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + dias)
  return d.toISOString().slice(0, 10)
}

const PRODUCTO_ANESTESIA: ProductoInventarioDTO = {
  id: 'a0000000-0000-4000-8000-000000000001',
  codigo: 'MAT-001',
  nombre: 'Anestesia Lidocaína 2% c/Epinefrina 1:100000',
  categoria: 'ANESTESICOS',
  unidad: 'CAJA_50',
  precio_unitario: 110.0,
  stock_minimo: 50,
  activo: true,
  stockPorSede: {
    'sede-chilca-1': 50,
    'sede-villamax-chilca': 25,
    'sede-eltambo': 10,
  },
  stockTotal: 85,
  alertaStockBajo: false,
  proximoVencimiento: fechaEn(90),
  ultimaEntrega: '2026-10-10',
}

const PRODUCTO_RESINA: ProductoInventarioDTO = {
  id: 'a0000000-0000-4000-8000-000000000002',
  codigo: 'MAT-002',
  nombre: 'Resina Compuesta Filtek Z350 XT 3M',
  categoria: 'RESTAURADORES',
  unidad: 'JERINGA',
  precio_unitario: 85.0,
  stock_minimo: 20,
  activo: true,
  stockPorSede: {
    'sede-chilca-1': 5,
    'sede-villamax-chilca': 10,
    'sede-eltambo': 0,
  },
  stockTotal: 15,
  alertaStockBajo: true,
  proximoVencimiento: fechaEn(12),
  ultimaEntrega: '2026-09-20',
}

const PRODUCTO_RADIOLOGIA: ProductoInventarioDTO = {
  id: 'a0000000-0000-4000-8000-000000000003',
  codigo: 'MAT-003',
  nombre: 'Película Radiológica Periapical Carestream Ultra-speed',
  categoria: 'RADIOLOGIA',
  unidad: 'CAJA_150',
  precio_unitario: 220.0,
  stock_minimo: 30,
  activo: true,
  stockPorSede: {
    'sede-chilca-1': 0,
    'sede-villamax-chilca': 0,
    'sede-eltambo': 0,
  },
  stockTotal: 0,
  alertaStockBajo: true,
}

const MOVIMIENTO_ENTRADA: InventarioMovimientoDTO = {
  id: 101,
  producto_id: PRODUCTO_ANESTESIA.id,
  producto_nombre: PRODUCTO_ANESTESIA.nombre,
  sede_id: 'sede-chilca-1',
  tipo: 'ENTRADA',
  cantidad: 20,
  lote: 'LOT-2026-X1',
  fecha_vencimiento: '2027-12-31',
  proveedor: 'Distribuidora Dental Sur SAC',
  motivo: 'Compra mensual programada',
  registrado_por: 'Recepcionista Turno Mañana',
  creado_en: '2026-10-10T10:00:00Z',
}

const TRANSFERENCIA_TRANSITO: TransferenciaResponseDTO = {
  id: 't0000000-0000-4000-8000-000000000001',
  producto_id: PRODUCTO_RESINA.id,
  producto_nombre: PRODUCTO_RESINA.nombre,
  cantidad: 3,
  sede_origen_id: 'sede-villamax-chilca',
  sede_origen_nombre: 'Villamax Chilca',
  sede_destino_id: 'sede-chilca-1',
  sede_destino_nombre: 'Sede Chilca 1 - Calle Real',
  estado: 'EN_TRANSITO',
  motivo: 'Reposición urgente para operatoria dental',
  solicitado_por: 'Katherin Mendoza',
  creado_en: '2026-10-10T11:00:00Z',
}

function mockApiInventario() {
  servidor.use(
    http.get(`${API}/sedes`, () => {
      return HttpResponse.json([
        { id: 'sede-chilca-1', nombre: 'Sede Chilca 1 - Calle Real', activa: true, unidadDeNegocio: 'VILLADENT' },
        { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', activa: true, unidadDeNegocio: 'VILLAMAX' },
        { id: 'sede-eltambo', nombre: 'Sede El Tambo - Mariátegui', activa: true, unidadDeNegocio: 'VILLADENT' },
      ])
    }),
    http.get(`${API}/inventario/productos`, () => {
      return HttpResponse.json([
        PRODUCTO_ANESTESIA,
        PRODUCTO_RESINA,
        PRODUCTO_RADIOLOGIA,
      ])
    }),
    http.get(`${API}/inventario/productos/:id`, ({ params }) => {
      if (params.id === PRODUCTO_ANESTESIA.id) return HttpResponse.json(PRODUCTO_ANESTESIA)
      if (params.id === PRODUCTO_RESINA.id) return HttpResponse.json(PRODUCTO_RESINA)
      return HttpResponse.json(PRODUCTO_RADIOLOGIA)
    }),
    http.get(`${API}/inventario/movimientos`, () => {
      return HttpResponse.json([MOVIMIENTO_ENTRADA])
    }),
    http.get(`${API}/inventario/transferencias`, () => {
      return HttpResponse.json([TRANSFERENCIA_TRANSITO])
    }),
    http.post(`${API}/inventario/movimientos`, async ({ request }) => {
      const cuerpo = (await request.json()) as {
        producto_id: string
        sede_id: string
        tipo: string
        cantidad: number
        motivo?: string
      }
      return HttpResponse.json({
        id: 102,
        producto_id: cuerpo.producto_id,
        producto_nombre: 'Producto registrado',
        sede_id: cuerpo.sede_id,
        tipo: cuerpo.tipo,
        cantidad: cuerpo.cantidad,
        motivo: cuerpo.motivo,
        creado_en: new Date().toISOString(),
      })
    }),
    http.patch(`${API}/inventario/transferencias/:id/recibir`, ({ params }) => {
      return HttpResponse.json({
        ...TRANSFERENCIA_TRANSITO,
        id: params.id,
        estado: 'RECIBIDO',
        recibido_en: new Date().toISOString(),
      })
    })
  )
}

describe('Fase 6 · Módulo de Inventario y Almacén Odontológico', { timeout: 30_000 }, () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-admin-general',
      usuario: {
        id: 'u-admin',
        nombre: 'Dra. Andrea Quispe',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' },
          { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
          { id: 'sede-eltambo', nombre: 'Sede El Tambo', marca: 'VILLADENT' },
        ],
      },
      sedeActivaId: 'sede-chilca-1',
    })
    mockApiInventario()
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('renderiza el dashboard general con KPIs clínicos y estado de insumos', async () => {
    await renderRuta('/app/inventario')

    // Título y badges
    expect(await screen.findByRole('heading', { name: /Inventario & Almacén Odontológico/i })).toBeInTheDocument()
    expect(screen.getByText(/Control multisede de insumos clínicos/i)).toBeInTheDocument()

    // 4 Stat Cards
    expect(screen.getByText(/Total de Insumos/i)).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument() // 3 productos en catálogo
    expect(screen.getAllByText(/Stock Bajo/i).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/Insumos Agotados/i)).toBeInTheDocument()
    expect(screen.getByText(/Traslados Activos/i)).toBeInTheDocument()

    // Alerta de reposición clínica en el feed del dashboard
    expect(screen.getByText(/Alertas de Reabastecimiento/i)).toBeInTheDocument()
    expect(screen.getByText(/Película Radiológica Periapical Carestream Ultra-speed/i)).toBeInTheDocument()
  })

  it('avisa de los insumos que se agotaron al atender y permite marcar la alerta como resuelta', async () => {
    const user = userEvent.setup()
    let resuelta = false
    servidor.use(
      http.get(`${API}/inventario/alertas-consumo`, () =>
        HttpResponse.json(
          resuelta
            ? []
            : [{ id: 'a-1', citaId: 'c-1', fechaCita: '2026-10-20', motivoCita: 'Restauración', sedeId: 'sede-chilca-1', productoId: 'p-1', productoNombre: 'Anestesia', cantidadRequerida: 5, cantidadDescontada: 2, faltante: 3 }],
        ),
      ),
      http.post(`${API}/inventario/alertas-consumo/a-1/resolver`, () => {
        resuelta = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderRuta('/app/inventario')

    expect(await screen.findByText('Insumos agotados al atender')).toBeInTheDocument()
    expect(screen.getByText(/faltaron 3 de 5/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Marcar resuelta' }))
    await waitFor(() => expect(screen.queryByText('Insumos agotados al atender')).not.toBeInTheDocument())
  })

  it('lista los productos, filtra por búsqueda y muestra badges clínicos de stock', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    // Cambiar a la pestaña de Insumos & Stock
    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    // Verificar presencia de los 3 productos con sus códigos
    expect(await screen.findByText('MAT-001')).toBeInTheDocument()
    expect(screen.getByText('MAT-002')).toBeInTheDocument()
    expect(screen.getByText('MAT-003')).toBeInTheDocument()

    // Badges de estado clínico
    expect(screen.getByText('Stock óptimo')).toBeInTheDocument()
    expect(screen.getByText('Stock bajo')).toBeInTheDocument()
    expect(screen.getByText('Agotado')).toBeInTheDocument()

    // Filtrar por buscador
    const inputBusqueda = screen.getByPlaceholderText(/Buscar por código, nombre o categoría/i)
    await user.type(inputBusqueda, 'Lidocaína')

    expect(screen.getByText('MAT-001')).toBeInTheDocument()
    expect(screen.queryByText('MAT-002')).not.toBeInTheDocument()
    expect(screen.queryByText('MAT-003')).not.toBeInTheDocument()

    // Limpiar búsqueda
    await user.clear(inputBusqueda)
    expect(screen.getByText('MAT-002')).toBeInTheDocument()
  })

  it('muestra próximo vencimiento y última entrega por producto, con aviso si vence en 30 días o menos', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    await user.click(await screen.findByRole('tab', { name: /Insumos & Stock/i }))
    expect(await screen.findByRole('columnheader', { name: 'Próximo vencimiento' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Última entrega' })).toBeInTheDocument()

    const filaAnestesia = screen.getByText('MAT-001').closest('tr')!
    expect(within(filaAnestesia).getByText(formatFecha(fechaEn(90)))).toBeInTheDocument()
    expect(within(filaAnestesia).getByText(formatFecha('2026-10-10'))).toBeInTheDocument()
    expect(within(filaAnestesia).queryByText(/en \d+ d/)).not.toBeInTheDocument()

    const filaResina = screen.getByText('MAT-002').closest('tr')!
    expect(within(filaResina).getByText(`${formatFecha(fechaEn(12))} · en 12 d`)).toBeInTheDocument()

    const filaRadiologia = screen.getByText('MAT-003').closest('tr')!
    expect(within(filaRadiologia).getAllByText('—')).toHaveLength(2)
  })

  it('permite abrir el modal de movimiento, valida campos obligatorios y registra una entrada', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    // Abrir modal de Entrada desde cabecera
    const botonEntrada = screen.getByRole('button', { name: /Entrada/i })
    await user.click(botonEntrada)

    expect(await screen.findByRole('heading', { name: /Registrar Movimiento de Inventario/i })).toBeInTheDocument()

    // Cambiar a Ajuste para verificar validación de motivo obligatorio
    const tabAjuste = screen.getByRole('button', { name: /Ajuste/i })
    await user.click(tabAjuste)

    // Intentar enviar sin motivo
    const botonConfirmar = screen.getByRole('button', { name: /Confirmar Movimiento/i })
    await user.click(botonConfirmar)

    expect(await screen.findByText(/El motivo o justificación es obligatorio para los ajustes de stock/i)).toBeInTheDocument()

    // Volver a pestaña Entrada
    const tabEntrada = screen.getByRole('button', { name: /^Entrada$/i })
    await user.click(tabEntrada)

    // Completar cantidad y motivo
    const inputCantidad = screen.getByRole('spinbutton')
    await user.clear(inputCantidad)
    await user.type(inputCantidad, '15')

    const inputMotivo = screen.getByPlaceholderText(/Recepción de factura/i)
    await user.type(inputMotivo, 'Ingreso de lote nuevo de anestesia')

    // Enviar formulario
    await user.click(botonConfirmar)

    // El modal se cierra tras mutación exitosa
    expect(screen.queryByRole('heading', { name: /Registrar Movimiento de Inventario/i })).not.toBeInTheDocument()
  })

  it('permite visualizar y gestionar traslados entre sedes', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    // Cambiar a la pestaña Traslados entre Sedes
    const tabTraslados = await screen.findByRole('tab', { name: /Traslados entre Sedes/i })
    await user.click(tabTraslados)

    // Verificar item en tránsito
    expect(await screen.findByText(/Resina Compuesta Filtek Z350 XT 3M/i)).toBeInTheDocument()
    expect(screen.getAllByText(/Villamax Chilca/i).length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText(/Sede Chilca 1 - Calle Real/i).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/3 uds/i)).toBeInTheDocument()

    // Botón de recepción disponible para el destino
    const botonRecibir = screen.getByRole('button', { name: /Recibir/i })
    expect(botonRecibir).toBeInTheDocument()
    await user.click(botonRecibir)

    // Comprobar que se ejecutó la acción sin error
    expect(screen.queryByText(/Error al recibir transferencia/i)).not.toBeInTheDocument()
  })

  it('permite abrir el modal de detalle de un producto y ver el stock por sede', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    // Ir a pestaña de Insumos
    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    // Buscar botón "Ver ficha y movimientos" del primer producto (MAT-001)
    const filaAnestesia = (await screen.findByText('MAT-001')).closest('tr')!
    const botonDetalle = within(filaAnestesia).getByTitle(/Ver ficha y movimientos/i)
    await user.click(botonDetalle)

    // Verificar modal de detalle (con nombre del producto como título)
    expect(await screen.findByRole('heading', { name: PRODUCTO_ANESTESIA.nombre })).toBeInTheDocument()
    expect(screen.getAllByText('MAT-001').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/Disponibilidad por Sede Clínica/i)).toBeInTheDocument()
    const detalle = within(screen.getByRole('dialog'))
    expect(detalle.getByText('Próximo vencimiento')).toBeInTheDocument()
    expect(detalle.getByText('Última entrega')).toBeInTheDocument()
    expect(detalle.getByText(formatFecha(fechaEn(90)))).toBeInTheDocument()
    expect(screen.getAllByText(/Villadent Chilca/i).length).toBeGreaterThanOrEqual(1)
  })

  it('muestra 3 elementos de acción por fila en la vista de insumos para el administrador', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    const filaAnestesia = (await screen.findByText('MAT-001')).closest('tr')!
    const acciones = within(filaAnestesia).getByTestId('acciones-insumo')
    const botones = within(acciones).getAllByRole('button')

    // 3 elementos por fila: Ver, Movimiento, ⋯
    expect(botones).toHaveLength(3)
    expect(botones[0]).toHaveTextContent('Ver')
    expect(botones[1]).toHaveTextContent('Movimiento')
    expect(botones[2]).toHaveTextContent('⋯')
  })

  it('el menú desplegable Movimiento lista las 4 opciones y cada una abre su modal respectivo', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    const filaAnestesia = (await screen.findByText('MAT-001')).closest('tr')!
    const acciones = within(filaAnestesia).getByTestId('acciones-insumo')

    // 1. Abrir menú Movimiento y verificar las 4 opciones
    const botonMovimiento = within(acciones).getByRole('button', { name: /Movimiento para/i })
    await user.click(botonMovimiento)

    expect(await screen.findByRole('menuitem', { name: /Entrada/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /Salida/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /Ajuste/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /Transferir/i })).toBeInTheDocument()

    // Entrada abre Registrar Movimiento modal
    await user.click(screen.getByRole('menuitem', { name: /Entrada/i }))
    expect(await screen.findByRole('heading', { name: /Registrar Movimiento de Inventario/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cancelar/i }))

    // 2. Probar opción Salida
    await user.click(botonMovimiento)
    await user.click(await screen.findByRole('menuitem', { name: /Salida/i }))
    expect(await screen.findByRole('heading', { name: /Registrar Movimiento de Inventario/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cancelar/i }))

    // 3. Probar opción Ajuste
    await user.click(botonMovimiento)
    await user.click(await screen.findByRole('menuitem', { name: /Ajuste/i }))
    expect(await screen.findByRole('heading', { name: /Registrar Movimiento de Inventario/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cancelar/i }))

    // 4. Probar opción Transferir (abre Solicitar Traslado entre Sedes)
    await user.click(botonMovimiento)
    await user.click(await screen.findByRole('menuitem', { name: /Transferir/i }))
    expect(await screen.findByRole('heading', { name: /Solicitar Traslado entre Sedes/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cancelar/i }))
  })

  it('el menú ⋯ ofrece Editar y Eliminar, y Eliminar solicita confirmación', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    let eliminadoId: string | null = null

    servidor.use(
      http.delete(`${API}/inventario/productos/:id`, ({ params }) => {
        eliminadoId = String(params.id)
        return new HttpResponse(null, { status: 204 })
      })
    )

    await renderRuta('/app/inventario')

    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    const filaAnestesia = (await screen.findByText('MAT-001')).closest('tr')!
    const acciones = within(filaAnestesia).getByTestId('acciones-insumo')

    // Abrir menú ⋯
    const botonOpciones = within(acciones).getByRole('button', { name: /Más opciones para/i })
    await user.click(botonOpciones)

    expect(await screen.findByRole('menuitem', { name: /Editar/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /Eliminar/i })).toBeInTheDocument()

    // Clic en Editar abre el modal de edición
    await user.click(screen.getByRole('menuitem', { name: /Editar/i }))
    expect(await screen.findByRole('heading', { name: /Editar Insumo Odontológico/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cancelar/i }))

    // Abrir menú ⋯ y probar Eliminar
    await user.click(botonOpciones)
    await user.click(await screen.findByRole('menuitem', { name: /Eliminar/i }))

    expect(confirmSpy).toHaveBeenCalled()
    expect(eliminadoId).toBe(PRODUCTO_ANESTESIA.id)

    confirmSpy.mockRestore()
  })

  it('un usuario con rol sin privilegios (RECEPCION) no tiene acceso a Editar ni Eliminar en la fila', async () => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-recepcion',
      usuario: {
        id: 'u-recep',
        nombre: 'Katherin Mendoza',
        rol: 'RECEPCION',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })

    const user = userEvent.setup()
    await renderRuta('/app/inventario')

    const tabInsumos = await screen.findByRole('tab', { name: /Insumos & Stock/i })
    await user.click(tabInsumos)

    const filaAnestesia = (await screen.findByText('MAT-001')).closest('tr')!
    const acciones = within(filaAnestesia).getByTestId('acciones-insumo')
    const botones = within(acciones).getAllByRole('button')

    // Solo 2 botones: Ver y Movimiento (el menú ⋯ con Editar y Eliminar no se ofrece)
    expect(botones).toHaveLength(2)
    expect(botones[0]).toHaveTextContent('Ver')
    expect(botones[1]).toHaveTextContent('Movimiento')
    expect(within(acciones).queryByRole('button', { name: /Más opciones para/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Editar/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Eliminar/i })).not.toBeInTheDocument()
  })
})
