package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.agenda.infrastructure.web.EspecialistaController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EspecialistaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class EspecialistaEliminarSecurityTest {

    private static final String MENSAJE_409 = "No se puede eliminar el especialista porque tiene historial clínico registrado. "
            + "Desactívelo en su lugar para mantener la auditoría médica.";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GestionEspecialistaUseCase gestionEspecialistaUseCase;

    @MockBean
    private ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @MockBean
    private GestionHorariosEspecialistaUseCase gestionHorariosEspecialistaUseCase;

    @MockBean
    private ConsultarDisponibilidadUseCase consultarDisponibilidadUseCase;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("ADMIN_GENERAL elimina un especialista sin historial (204 sin cuerpo)")
    void adminGeneral_204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/especialistas/" + id))
                .andExpect(status().isNoContent());
        verify(gestionEspecialistaUseCase).eliminar(id);
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("Con historial clínico responde 409 con el formato de error estándar y el mensaje exacto")
    void conHistorial_409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, MENSAJE_409)).when(gestionEspecialistaUseCase).eliminar(id);

        mockMvc.perform(delete("/especialistas/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(MENSAJE_409))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_409));
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("Especialista inexistente responde 404")
    void noExiste_404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialista no encontrado con ID: " + id))
                .when(gestionEspecialistaUseCase).eliminar(id);

        mockMvc.perform(delete("/especialistas/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN_SEDE")
    @DisplayName("ADMIN_SEDE no puede eliminar especialistas (403)")
    void adminSede_403() throws Exception {
        mockMvc.perform(delete("/especialistas/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(gestionEspecialistaUseCase, never()).eliminar(any());
    }

    @Test
    @WithMockUser(roles = "ODONTOLOGO")
    @DisplayName("ODONTOLOGO no puede eliminar especialistas (403)")
    void odontologo_403() throws Exception {
        mockMvc.perform(delete("/especialistas/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(gestionEspecialistaUseCase, never()).eliminar(any());
    }

    @Test
    @DisplayName("Sin sesión responde 401")
    void sinSesion_401() throws Exception {
        mockMvc.perform(delete("/especialistas/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verify(gestionEspecialistaUseCase, never()).eliminar(any());
    }
}
