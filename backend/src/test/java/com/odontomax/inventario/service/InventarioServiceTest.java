package com.odontomax.inventario.service;

import com.odontomax.application.dto.request.InventarioMovimientoRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioCreateRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioUpdateRequestDTO;
import com.odontomax.application.dto.request.TransferenciaCreateRequestDTO;
import com.odontomax.application.dto.response.InventarioMovimientoResponseDTO;
import com.odontomax.application.dto.response.ProductoInventarioResponseDTO;
import com.odontomax.application.dto.response.TransferenciaResponseDTO;
import com.odontomax.infrastructure.persistence.entity.InventarioMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.InventarioStockEntity;
import com.odontomax.infrastructure.persistence.entity.ProductoInventarioEntity;
import com.odontomax.infrastructure.persistence.entity.TransferenciaInventarioEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataInventarioMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataInventarioStockRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataProductoInventarioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTransferenciaInventarioRepository;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock
    private SpringDataProductoInventarioRepository productoRepository;

    @Mock
    private SpringDataInventarioStockRepository stockRepository;

    @Mock
    private SpringDataInventarioMovimientoRepository movimientoRepository;

    @Mock
    private SpringDataTransferenciaInventarioRepository transferenciaRepository;

    @Mock
    private ConsultaUsuarioModuloApi consultaUsuarioModuloApi;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @InjectMocks
    private InventarioService inventarioService;

    private UUID prodId;
    private ProductoInventarioEntity productoEntity;
    private ClinicaUserPrincipal userAdminGeneral;
    private ClinicaUserPrincipal userSedeChilca1;

    @BeforeEach
    void setUp() {
        prodId = UUID.randomUUID();
        productoEntity = ProductoInventarioEntity.builder()
                .id(prodId)
                .codigo("RES-001")
                .nombre("Resina Compuesta A2")
                .categoria("Restauración")
                .unidad("Jeringa 4g")
                .precioUnitario(new BigDecimal("85.00"))
                .stockMinimo(5)
                .activo(true)
                .creadoEn(OffsetDateTime.now())
                .actualizadoEn(OffsetDateTime.now())
                .build();

        userAdminGeneral = new ClinicaUserPrincipal(
                UUID.randomUUID(), "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null
        );

        userSedeChilca1 = new ClinicaUserPrincipal(
                UUID.randomUUID(), "recepcion@odontomax.pe", "Recepción Chilca 1", "RECEPCION", "sede-chilca-1"
        );
    }

    @Test
    @DisplayName("listarProductos: calcula stock total y detecta alerta de stock bajo")
    void listarProductos_detectaStockBajo() {
        when(productoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(productoEntity));
        when(stockRepository.findAll()).thenReturn(List.of(
                new InventarioStockEntity(prodId, "sede-chilca-1", 3), // menor al mínimo 5
                new InventarioStockEntity(prodId, "sede-villamax-chilca", 10)
        ));

        List<ProductoInventarioResponseDTO> list = inventarioService.listarProductos(null, null);

        assertThat(list).hasSize(1);
        ProductoInventarioResponseDTO dto = list.get(0);
        assertThat(dto.getStockTotal()).isEqualTo(13);
        assertThat(dto.isAlertaStockBajo()).isTrue();
    }

    @Test
    @DisplayName("listarProductos: completa próximo vencimiento y última entrega con una sola consulta agregada; sin entradas con fecha quedan en null")
    void listarProductos_completaFechasConUnaSolaConsulta() {
        ProductoInventarioEntity sinEntradas = ProductoInventarioEntity.builder()
                .id(UUID.randomUUID()).codigo("ANE-001").nombre("Anestesia").stockMinimo(2).build();
        LocalDate vence = LocalDate.of(2027, 3, 15);
        LocalDate entrega = LocalDate.of(2026, 10, 1);
        when(productoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(productoEntity, sinEntradas));
        when(stockRepository.findAll()).thenReturn(List.of());
        when(movimientoRepository.resumenFechasPorProducto(any(), isNull()))
                .thenReturn(Collections.singletonList(new Object[] {prodId, vence, entrega}));

        List<ProductoInventarioResponseDTO> lista = inventarioService.listarProductos(null, null);

        assertThat(lista.get(0).getProximoVencimiento()).isEqualTo(vence);
        assertThat(lista.get(0).getUltimaEntrega()).isEqualTo(entrega);
        assertThat(lista.get(1).getProximoVencimiento()).isNull();
        assertThat(lista.get(1).getUltimaEntrega()).isNull();
        verify(movimientoRepository, times(1)).resumenFechasPorProducto(any(), any());
    }

    @Test
    @DisplayName("listarProductos: con sede filtra las fechas por esa sede; una sede en blanco cuenta como todas")
    void listarProductos_fechasRespetanElFiltroDeSede() {
        when(productoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(productoEntity));
        when(stockRepository.findAll()).thenReturn(List.of());

        inventarioService.listarProductos("sede-chilca-1", null);
        inventarioService.listarProductos("  ", null);

        verify(movimientoRepository).resumenFechasPorProducto(any(), eq("sede-chilca-1"));
        verify(movimientoRepository).resumenFechasPorProducto(any(), isNull());
    }

    @Test
    @DisplayName("crearProducto: sin código autogenera el siguiente MAT-### libre")
    void crearProducto_sinCodigo_autogenera() {
        ProductoInventarioCreateRequestDTO req = ProductoInventarioCreateRequestDTO.builder()
                .nombre("Agujas Carpule 30G")
                .stockMinimo(2)
                .build();
        when(productoRepository.count()).thenReturn(5L);
        when(productoRepository.existsByCodigoIgnoreCase("MAT-006")).thenReturn(true);
        when(productoRepository.existsByCodigoIgnoreCase("MAT-007")).thenReturn(false);
        when(productoRepository.save(any())).thenAnswer(inv -> {
            ProductoInventarioEntity e = inv.getArgument(0);
            e.setId(prodId);
            return e;
        });
        when(productoRepository.findById(prodId)).thenAnswer(inv -> Optional.of(productoEntity));

        inventarioService.crearProducto(req, userAdminGeneral);

        verify(productoRepository).save(argThat(e -> "MAT-007".equals(e.getCodigo())));
    }

    @Test
    @DisplayName("darDeBajaProducto: baja lógica (activo=false), no borra la fila")
    void darDeBajaProducto_bajaLogica() {
        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));

        inventarioService.darDeBajaProducto(prodId, userAdminGeneral);

        assertThat(productoEntity.isActivo()).isFalse();
        verify(productoRepository).save(productoEntity);
        verify(productoRepository, never()).delete(any());
        verify(productoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("registrarMovimiento: un insumo dado de baja no admite movimientos (409)")
    void registrarMovimiento_productoDadoDeBaja_lanza409() {
        productoEntity.setActivo(false);
        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(prodId).sedeId("sede-chilca-1").tipo("ENTRADA").cantidad(5).build();

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dado de baja");
    }

    @Test
    @DisplayName("crearProducto: rechaza código duplicado con 409 Conflict")
    void crearProducto_codigoDuplicado_lanza409() {
        ProductoInventarioCreateRequestDTO req = ProductoInventarioCreateRequestDTO.builder()
                .codigo("RES-001")
                .nombre("Resina Compuesta A2")
                .stockMinimo(5)
                .build();

        when(productoRepository.existsByCodigoIgnoreCase("RES-001")).thenReturn(true);

        assertThatThrownBy(() -> inventarioService.crearProducto(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe un insumo con el código");
    }

    @Test
    @DisplayName("registrarMovimiento: previene stock negativo en SALIDA con 409 Conflict")
    void registrarMovimiento_stockInsuficiente_lanza409() {
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(prodId)
                .sedeId("sede-chilca-1")
                .tipo("SALIDA")
                .cantidad(10)
                .build();

        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        when(stockRepository.findByProductoIdAndSedeId(prodId, "sede-chilca-1"))
                .thenReturn(Optional.of(new InventarioStockEntity(prodId, "sede-chilca-1", 4))); // 4 < 10

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarMovimiento: usuario de sede no puede registrar en otra sede (403)")
    void registrarMovimiento_sedeCruzada_lanza403() {
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(prodId)
                .sedeId("sede-eltambo") // Usuario pertenece a sede-chilca-1
                .tipo("ENTRADA")
                .cantidad(5)
                .build();

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(req, userSedeChilca1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos para registrar movimientos en una sede distinta");
    }

    @Test
    @DisplayName("solicitarTransferencia: valida stock suficiente en sede de origen (409)")
    void solicitarTransferencia_stockInsuficienteOrigen_lanza409() {
        TransferenciaCreateRequestDTO req = TransferenciaCreateRequestDTO.builder()
                .productoId(prodId)
                .cantidad(15)
                .sedeOrigenId("sede-chilca-1")
                .sedeDestinoId("sede-villamax-chilca")
                .build();

        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        when(stockRepository.findByProductoIdAndSedeId(prodId, "sede-chilca-1"))
                .thenReturn(Optional.of(new InventarioStockEntity(prodId, "sede-chilca-1", 5))); // 5 < 15

        assertThatThrownBy(() -> inventarioService.solicitarTransferencia(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Stock insuficiente en sede de origen");

        verify(transferenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitarTransferencia: caso feliz pone estado EN_TRANSITO")
    void solicitarTransferencia_casoFeliz_creaEnTransito() {
        TransferenciaCreateRequestDTO req = TransferenciaCreateRequestDTO.builder()
                .productoId(prodId)
                .cantidad(3)
                .sedeOrigenId("sede-chilca-1")
                .sedeDestinoId("sede-villamax-chilca")
                .motivo("Reposición")
                .build();

        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        when(stockRepository.findByProductoIdAndSedeId(prodId, "sede-chilca-1"))
                .thenReturn(Optional.of(new InventarioStockEntity(prodId, "sede-chilca-1", 10)));
        when(transferenciaRepository.save(any(TransferenciaInventarioEntity.class))).thenAnswer(inv -> {
            TransferenciaInventarioEntity t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TransferenciaResponseDTO result = inventarioService.solicitarTransferencia(req, userAdminGeneral);

        assertThat(result).isNotNull();
        assertThat(result.getEstado()).isEqualTo("EN_TRANSITO");
        assertThat(result.getCantidad()).isEqualTo(3);
    }

    @Test
    @DisplayName("recibirTransferencia: solo destino o admin general puede confirmar recepción")
    void recibirTransferencia_sedeDistintaADestino_lanza403() {
        UUID transId = UUID.randomUUID();
        TransferenciaInventarioEntity transfer = TransferenciaInventarioEntity.builder()
                .id(transId)
                .productoId(prodId)
                .cantidad(2)
                .sedeOrigenId("sede-chilca-1")
                .sedeDestinoId("sede-eltambo")
                .estado("EN_TRANSITO")
                .build();

        when(transferenciaRepository.findById(transId)).thenReturn(Optional.of(transfer));

        // userSedeChilca1 es de sede-chilca-1, pero el destino es sede-eltambo!
        assertThatThrownBy(() -> inventarioService.recibirTransferencia(transId, userSedeChilca1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Solo el personal de la sede de destino");
    }

    @Test
    @DisplayName("recibirTransferencia: caso feliz actualiza a RECIBIDO")
    void recibirTransferencia_casoFeliz_actualizaEstado() {
        UUID transId = UUID.randomUUID();
        TransferenciaInventarioEntity transfer = TransferenciaInventarioEntity.builder()
                .id(transId)
                .productoId(prodId)
                .cantidad(2)
                .sedeOrigenId("sede-chilca-1")
                .sedeDestinoId("sede-villamax-chilca")
                .estado("EN_TRANSITO")
                .build();

        when(transferenciaRepository.findById(transId)).thenReturn(Optional.of(transfer));
        when(transferenciaRepository.save(any())).thenReturn(transfer);
        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));

        TransferenciaResponseDTO result = inventarioService.recibirTransferencia(transId, userAdminGeneral);

        assertThat(result.getEstado()).isEqualTo("RECIBIDO");
        assertThat(transfer.getRecibidoEn()).isNotNull();
    }

    @Test
    @DisplayName("cancelarTransferencia: rechaza cancelar si ya fue recibida (409)")
    void cancelarTransferencia_yaRecibida_lanza409() {
        UUID transId = UUID.randomUUID();
        TransferenciaInventarioEntity transfer = TransferenciaInventarioEntity.builder()
                .id(transId)
                .productoId(prodId)
                .cantidad(2)
                .sedeOrigenId("sede-chilca-1")
                .sedeDestinoId("sede-villamax-chilca")
                .estado("RECIBIDO")
                .build();

        when(transferenciaRepository.findById(transId)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> inventarioService.cancelarTransferencia(transId, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se puede cancelar una transferencia que ya fue recibida");
    }

    @Test
    @DisplayName("actualizarProducto: actualiza datos correctamente")
    void actualizarProducto_exitoso() {
        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        when(productoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockRepository.findByProductoId(prodId)).thenReturn(java.util.Collections.emptyList());

        ProductoInventarioUpdateRequestDTO req = ProductoInventarioUpdateRequestDTO.builder()
                .nombre("Guantes de Látex Premium")
                .categoria("Bioseguridad")
                .unidad("Caja x 100")
                .precioUnitario(new BigDecimal("35.00"))
                .stockMinimo(12)
                .activo(true)
                .build();

        ProductoInventarioResponseDTO res = inventarioService.actualizarProducto(prodId, req, userAdminGeneral);

        assertThat(res.getNombre()).isEqualTo("Guantes de Látex Premium");
        assertThat(res.getCategoria()).isEqualTo("Bioseguridad");
        assertThat(res.getUnidad()).isEqualTo("Caja x 100");
        assertThat(res.getStockMinimo()).isEqualTo(12);
        assertThat(res.getPrecioUnitario()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("registrarMovimiento: AJUSTE sin motivo obligatorio lanza 400 Bad Request")
    void registrarMovimiento_ajusteSinMotivo_lanza400() {
        when(productoRepository.findById(prodId)).thenReturn(Optional.of(productoEntity));
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(prodId)
                .sedeId("sede-chilca-1")
                .tipo("AJUSTE")
                .cantidad(2)
                .motivo("   ")
                .build();

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("El motivo es obligatorio para ajustes");
    }

    @Test
    @DisplayName("registrarMovimiento: cantidad cero o negativa en ENTRADA lanza 400 Bad Request")
    void registrarMovimiento_cantidadInvalida_lanza400() {
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(prodId)
                .sedeId("sede-chilca-1")
                .tipo("ENTRADA")
                .cantidad(0)
                .build();

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(req, userAdminGeneral))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("La cantidad debe ser diferente de cero");
    }
}
