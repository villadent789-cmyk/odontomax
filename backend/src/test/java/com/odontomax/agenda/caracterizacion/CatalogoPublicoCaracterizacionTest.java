package com.odontomax.agenda.caracterizacion;

import com.odontomax.sedes.service.ServicioService;
import com.odontomax.identidad.testsupport.UsuariosSimuladosWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.*;
import com.odontomax.infrastructure.security.TestSecurityEnvironmentInitializer;
import com.odontomax.agenda.infrastructure.web.CatalogoEspecialistasController;
import com.odontomax.sedes.infrastructure.web.CatalogoPublicoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CatalogoEspecialistasController.class, CatalogoPublicoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, UsuariosSimuladosWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class CatalogoPublicoCaracterizacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @MockBean
    private ServicioService servicioService;

    @Test
    @DisplayName("GET /reservas-publicas/catalogo/especialistas es público, tiene Cache-Control y excluye datos sensibles")
    void catalogoEspecialistas_publico_sinDatosSensibles_conCacheControl() throws Exception {
        UUID doctorId = UUID.randomUUID();
        when(consultarEspecialistaUseCase.listarPublicos()).thenReturn(List.of(
                com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO.builder()
                        .id(doctorId)
                        .nombre("Dr. Fernando Morales")
                        .especialidad("Periodoncia")
                        .fotoUrl("https://r2.odontomax.pe/doctores/dr-morales.webp")
                        .activo(true)
                        .sedes(List.of("sede-chilca-1", "sede-eltambo"))
                        // Los siguientes campos NO deben exponerse en la web pública:
                        .cop(null)
                        .email(null)
                        .telefono(null)
                        .usuarioId(null)
                        .build()
        ));

        mockMvc.perform(get("/reservas-publicas/catalogo/especialistas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=30, public"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(doctorId.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Dr. Fernando Morales"))
                .andExpect(jsonPath("$[0].especialidad").value("Periodoncia"))
                .andExpect(jsonPath("$[0].fotoUrl").value("https://r2.odontomax.pe/doctores/dr-morales.webp"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].sedes[0]").value("sede-chilca-1"))
                .andExpect(jsonPath("$[0].sedes[1]").value("sede-eltambo"))
                .andExpect(jsonPath("$[0].cop").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].telefono").doesNotExist())
                .andExpect(jsonPath("$[0].usuarioId").doesNotExist());
    }
}
