package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.application.dto.request.InventarioMovimientoRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioCreateRequestDTO;
import com.odontomax.inventario.service.InventarioService;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.presentation.controller.InventarioController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventarioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class InventarioSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventarioService inventarioService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenAdminGeneral;
    private String tokenAdminChilca1;
    private String tokenRecepcionChilca1;
    private String tokenOdontologo;

    @BeforeEach
    void setUp() {
        UUID adminId = UUID.randomUUID();
        UsuarioEntity admin = UsuarioEntity.builder()
                .id(adminId)
                .email("admin@odontomax.pe")
                .nombre("Administrador General")
                .rol("ADMIN_GENERAL")
                .sedeId(null)
                .activo(true)
                .build();

        UUID adminSedeId = UUID.randomUUID();
        UsuarioEntity adminSede = UsuarioEntity.builder()
                .id(adminSedeId)
                .email("adminchilca@odontomax.pe")
                .nombre("Admin Chilca 1")
                .rol("ADMIN_SEDE")
                .sedeId("sede-chilca-1")
                .activo(true)
                .build();

        UUID recepcionId = UUID.randomUUID();
        UsuarioEntity recepcion = UsuarioEntity.builder()
                .id(recepcionId)
                .email("recepcion@odontomax.pe")
                .nombre("Recepción Chilca 1")
                .rol("RECEPCION")
                .sedeId("sede-chilca-1")
                .activo(true)
                .build();

        UUID odontoId = UUID.randomUUID();
        UsuarioEntity odontologo = UsuarioEntity.builder()
                .id(odontoId)
                .email("doctor@odontomax.pe")
                .nombre("Dr. Odontólogo")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .activo(true)
                .build();

        when(usuarioRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByEmailIgnoreCase(adminSede.getEmail())).thenReturn(Optional.of(adminSede));
        when(usuarioRepository.findByEmailIgnoreCase(recepcion.getEmail())).thenReturn(Optional.of(recepcion));
        when(usuarioRepository.findByEmailIgnoreCase(odontologo.getEmail())).thenReturn(Optional.of(odontologo));

        tokenAdminGeneral = jwtTokenProvider.generateToken(adminId, admin.getEmail(), admin.getNombre(), admin.getRol(), admin.getSedeId(), 0);
        tokenAdminChilca1 = jwtTokenProvider.generateToken(adminSedeId, adminSede.getEmail(), adminSede.getNombre(), adminSede.getRol(), adminSede.getSedeId(), 0);
        tokenRecepcionChilca1 = jwtTokenProvider.generateToken(recepcionId, recepcion.getEmail(), recepcion.getNombre(), recepcion.getRol(), recepcion.getSedeId(), 0);
        tokenOdontologo = jwtTokenProvider.generateToken(odontoId, odontologo.getEmail(), odontologo.getNombre(), odontologo.getRol(), odontologo.getSedeId(), 0);
    }

    @Test
    @DisplayName("GET /inventario/productos sin token responde 401")
    void listarProductos_sinAutenticar_retorna401() throws Exception {
        mockMvc.perform(get("/inventario/productos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /inventario/productos con rol ODONTOLOGO responde 200")
    void listarProductos_odontologo_retorna200() throws Exception {
        when(inventarioService.listarProductos(any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventario/productos")
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /inventario/productos con rol RECEPCION responde 403 Forbidden")
    void crearProducto_recepcion_retorna403() throws Exception {
        ProductoInventarioCreateRequestDTO req = ProductoInventarioCreateRequestDTO.builder()
                .codigo("ALM-001")
                .nombre("Algodón Odontológico")
                .stockMinimo(5)
                .build();

        mockMvc.perform(post("/inventario/productos")
                        .header("Authorization", "Bearer " + tokenRecepcionChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /inventario/movimientos sede cruzada responde 403 Forbidden")
    void registrarMovimiento_sedeCruzada_retorna403() throws Exception {
        InventarioMovimientoRequestDTO req = InventarioMovimientoRequestDTO.builder()
                .productoId(UUID.randomUUID())
                .sedeId("sede-eltambo") // Usuario tokenAdminChilca1 es de sede-chilca-1
                .tipo("ENTRADA")
                .cantidad(10)
                .build();

        mockMvc.perform(post("/inventario/movimientos")
                        .header("Authorization", "Bearer " + tokenAdminChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /inventario/productos con ADMIN_GENERAL responde 201 Created")
    void crearProducto_adminGeneral_retorna201() throws Exception {
        ProductoInventarioCreateRequestDTO req = ProductoInventarioCreateRequestDTO.builder()
                .codigo("ANE-001")
                .nombre("Anestesia Lidocaína 2%")
                .precioUnitario(new BigDecimal("110.00"))
                .stockMinimo(10)
                .build();

        mockMvc.perform(post("/inventario/productos")
                        .header("Authorization", "Bearer " + tokenAdminGeneral)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /inventario/productos con ADMIN_SEDE responde 403: el catálogo es solo de ADMIN_GENERAL")
    void crearProducto_adminSede_retorna403() throws Exception {
        ProductoInventarioCreateRequestDTO req = ProductoInventarioCreateRequestDTO.builder()
                .codigo("ALG-001")
                .nombre("Alginato Kromopan")
                .stockMinimo(3)
                .build();

        mockMvc.perform(post("/inventario/productos")
                        .header("Authorization", "Bearer " + tokenAdminChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /inventario/productos/{id}: ADMIN_GENERAL 204; ADMIN_SEDE, RECEPCION y ODONTOLOGO 403")
    void eliminarProducto_soloAdminGeneral() throws Exception {
        UUID id = UUID.randomUUID();
        for (String token : new String[]{tokenAdminChilca1, tokenRecepcionChilca1, tokenOdontologo}) {
            mockMvc.perform(delete("/inventario/productos/" + id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verify(inventarioService, org.mockito.Mockito.never()).darDeBajaProducto(any(), any());

        mockMvc.perform(delete("/inventario/productos/" + id)
                        .header("Authorization", "Bearer " + tokenAdminGeneral))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(inventarioService).darDeBajaProducto(org.mockito.ArgumentMatchers.eq(id), any());
    }
}
