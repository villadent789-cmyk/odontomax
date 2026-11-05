package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.application.dto.response.DatabaseSizeResponseDTO;
import com.odontomax.application.dto.response.TablaSizeDTO;
import com.odontomax.application.service.MantenimientoService;
import com.odontomax.presentation.controller.MantenimientoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MantenimientoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class MantenimientoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MantenimientoService mantenimientoService;

    @MockBean
    private com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(username = "admin@odontomax.pe", roles = {"ADMIN_GENERAL"})
    @DisplayName("Mantenimiento: ADMIN_GENERAL consulta tamaño de BD con alertas y recibe 200 OK")
    void testAdminGeneralPuedeConsultarTamanoBd() throws Exception {
        DatabaseSizeResponseDTO dto = DatabaseSizeResponseDTO.builder()
                .tamanoBytes(367001600L) // ~350 MB
                .tamanoMb(350.0)
                .cuotaMaximaMb(500.0)
                .porcentajeUso(70.0)
                .alerta("ADVERTENCIA")
                .mensaje("ADVERTENCIA: La base de datos supera el 70% de la cuota asignada.")
                .topTablas(List.of(new TablaSizeDTO("historia_clinica_accesos", 150000000L, 143.05)))
                .build();

        when(mantenimientoService.obtenerTamanoBaseDatos()).thenReturn(dto);

        mockMvc.perform(get("/admin/mantenimiento/tamano-bd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanoMb").value(350.0))
                .andExpect(jsonPath("$.cuotaMaximaMb").value(500.0))
                .andExpect(jsonPath("$.porcentajeUso").value(70.0))
                .andExpect(jsonPath("$.alerta").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.topTablas[0].tabla").value("historia_clinica_accesos"));
    }

    @Test
    @WithMockUser(username = "recepcion@odontomax.pe", roles = {"RECEPCION"})
    @DisplayName("Mantenimiento: Deniega acceso (403) a rol RECEPCION")
    void testRecepcionNoPuedeConsultarTamanoBd() throws Exception {
        mockMvc.perform(get("/admin/mantenimiento/tamano-bd"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin.sede@odontomax.pe", roles = {"ADMIN_SEDE"})
    @DisplayName("Mantenimiento: Deniega acceso (403) a rol ADMIN_SEDE (solo ADMIN_GENERAL)")
    void testAdminSedeNoPuedeConsultarTamanoBd() throws Exception {
        mockMvc.perform(get("/admin/mantenimiento/tamano-bd"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mantenimiento: Deniega acceso (401/403) a petición anónima")
    void testAnonimoNoPuedeConsultarTamanoBd() throws Exception {
        mockMvc.perform(get("/admin/mantenimiento/tamano-bd"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@odontomax.pe", roles = {"ADMIN_GENERAL"})
    @DisplayName("Mantenimiento: Error al consultar tamaño de BD devuelve 503 Service Unavailable")
    void testErrorAlConsultarTamanoDevuelve503() throws Exception {
        when(mantenimientoService.obtenerTamanoBaseDatos()).thenThrow(
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "El servicio de telemetría de base de datos no está disponible. No se pudo calcular el tamaño del almacenamiento."
                )
        );

        mockMvc.perform(get("/admin/mantenimiento/tamano-bd"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value("El servicio de telemetría de base de datos no está disponible. No se pudo calcular el tamaño del almacenamiento."));
    }
}
