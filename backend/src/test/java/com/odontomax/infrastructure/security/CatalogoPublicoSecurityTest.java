package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.sedes.dto.ServicioResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.sedes.service.ServicioService;
import com.odontomax.agenda.infrastructure.web.CatalogoEspecialistasController;
import com.odontomax.sedes.infrastructure.web.CatalogoPublicoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CatalogoEspecialistasController.class, CatalogoPublicoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class CatalogoPublicoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @MockBean
    private ServicioService servicioService;

    @MockBean
    private com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Catálogo público: especialistas accesibles sin sesión y sin datos de contacto")
    void especialistas_sinSesion_sinDatosSensibles() throws Exception {
        when(consultarEspecialistaUseCase.listarPublicos()).thenReturn(List.of(
                EspecialistaResponseDTO.builder()
                        .id(UUID.randomUUID())
                        .nombre("Dr. Alex Ríos")
                        .especialidad("Ortodoncia")
                        .activo(true)
                        .sedes(List.of("sede-chilca-1"))
                        .build()
        ));

        mockMvc.perform(get("/reservas-publicas/catalogo/especialistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Dr. Alex Ríos"))
                .andExpect(jsonPath("$[0].sedes[0]").value("sede-chilca-1"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].telefono").doesNotExist())
                .andExpect(jsonPath("$[0].cop").doesNotExist())
                .andExpect(jsonPath("$[0].usuarioId").doesNotExist());
    }

    @Test
    @DisplayName("Catálogo público: servicios accesibles sin sesión")
    void servicios_sinSesion() throws Exception {
        when(servicioService.listarServiciosPublicos(null)).thenReturn(List.of(
                ServicioResponseDTO.builder().id("ortodoncia").nombre("Ortodoncia").especialidad("Ortodoncia")
                        .duracionMin(45).precio(new BigDecimal("150.00")).activo(true).build()
        ));

        mockMvc.perform(get("/reservas-publicas/catalogo/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ortodoncia"));
    }
}
