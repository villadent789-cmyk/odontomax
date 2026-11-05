package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.application.port.in.CambiarPasswordUseCase;
import com.odontomax.identidad.application.port.in.ConsultarPerfilUseCase;
import com.odontomax.identidad.application.port.in.GestionarTotpUseCase;
import com.odontomax.identidad.application.port.in.LoginUseCase;
import com.odontomax.identidad.infrastructure.web.AuthController;
import com.odontomax.pacientes.infrastructure.web.PacienteController;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.infrastructure.adapter.InMemoryRateLimitAdapter;
import com.odontomax.agenda.infrastructure.web.ReservaPublicaController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PacienteController.class, AuthController.class, ReservaPublicaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, InMemoryRateLimitAdapter.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class SecurityEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase solicitudesCorreccionUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.GestionarSolicitudesEliminacionUseCase solicitudesEliminacionUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase eliminarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase consultarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.RegistrarPacienteUseCase registrarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ActualizarPacienteUseCase actualizarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ArchivarPacienteUseCase archivarPacienteUseCase;

    @MockBean
    private com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase exportacionPacienteService;

    @MockBean
    private LoginUseCase loginUseCase;

    @MockBean
    private ConsultarPerfilUseCase consultarPerfilUseCase;

    @MockBean
    private CambiarPasswordUseCase cambiarPasswordUseCase;

    @MockBean
    private GestionarTotpUseCase gestionarTotpUseCase;

    @MockBean
    private RegistrarReservaPublicaUseCase registrarReservaPublicaUseCase;

    @MockBean
    private ConsultarReservaPublicaUseCase consultarReservaPublicaUseCase;

    @MockBean
    private com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Endpoint clínico protegido (/pacientes): Debe rechazar con 401 si no hay token Bearer")
    void testAccesoDenegadoSinToken() throws Exception {
        mockMvc.perform(get("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Endpoint clínico protegido (/pacientes): Debe permitir acceso con token Bearer válido")
    void testAccesoPermitidoConTokenValido() throws Exception {
        org.mockito.Mockito.when(usuarioRepository.findByEmailIgnoreCase("doctor.rios@odontomax.pe"))
                .thenReturn(java.util.Optional.of(com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity.builder()
                        .id(UUID.randomUUID())
                        .email("doctor.rios@odontomax.pe")
                        .nombre("Dr. Alex Ríos")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()));

        String token = jwtTokenProvider.generateToken(
                UUID.randomUUID(),
                "doctor.rios@odontomax.pe",
                "Dr. Alex Ríos",
                "ODONTOLOGO",
                "sede-chilca-1"
        , 0);

        mockMvc.perform(get("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint público (/auth/login): Debe permitir acceso sin token")
    void testAccesoPublicoAuthLogin() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@odontomax.pe\",\"password\":\"RandomTestPass99!\"}"))
                .andExpect(status().isOk());
    }
}
