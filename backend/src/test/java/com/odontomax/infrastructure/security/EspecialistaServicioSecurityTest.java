package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.sedes.dto.ServicioResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.sedes.service.ServicioService;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.agenda.infrastructure.web.EspecialistaController;
import com.odontomax.sedes.infrastructure.web.ServicioController;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EspecialistaController.class, ServicioController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class EspecialistaServicioSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private GestionEspecialistaUseCase gestionEspecialistaUseCase;

    @MockBean
    private ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @MockBean
    private GestionHorariosEspecialistaUseCase gestionHorariosEspecialistaUseCase;

    @MockBean
    private ConsultarDisponibilidadUseCase consultarDisponibilidadUseCase;

    @MockBean
    private ServicioService servicioService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenChilca1;
    private final UUID doctorId = UUID.randomUUID();
    private final String emailDoctor = "doctor.chilca1@odontomax.pe";

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findByEmailIgnoreCase(emailDoctor))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(doctorId)
                        .email(emailDoctor)
                        .nombre("Dr. Carlos Chilca 1")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()));

        tokenChilca1 = jwtTokenProvider.generateToken(
                doctorId,
                emailDoctor,
                "Dr. Carlos Chilca 1",
                "ODONTOLOGO",
                "sede-chilca-1"
        , 0);
    }

    @Test
    @DisplayName("GET /especialistas sin autenticación retorna 401")
    void testGetEspecialistasSinAuth() throws Exception {
        mockMvc.perform(get("/especialistas")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /especialistas con token de sede-chilca-1 filtra por su sede y retorna 200")
    void testGetEspecialistasConAuth() throws Exception {
        UUID espId = UUID.randomUUID();
        when(consultarEspecialistaUseCase.listarEspecialistas("sede-chilca-1"))
                .thenReturn(List.of(EspecialistaResponseDTO.builder()
                        .id(espId)
                        .nombre("Dr. Alex Ríos")
                        .especialidad("Ortodoncia")
                        .activo(true)
                        .sedes(List.of("sede-chilca-1"))
                        .build()));

        mockMvc.perform(get("/especialistas")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(espId.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Dr. Alex Ríos"));
    }

    @Test
    @DisplayName("GET /especialistas?sedeId=sede-eltambo para usuario de sede-chilca-1 retorna 403 Forbidden")
    void testGetEspecialistasSedeAjena() throws Exception {
        mockMvc.perform(get("/especialistas")
                        .param("sedeId", "sede-eltambo")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /servicios sin autenticación retorna 401")
    void testGetServiciosSinAuth() throws Exception {
        mockMvc.perform(get("/servicios")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /servicios con token retorna 200 y catálogo activo")
    void testGetServiciosConAuth() throws Exception {
        when(servicioService.listarServicios())
                .thenReturn(List.of(ServicioResponseDTO.builder()
                        .id("srv-1")
                        .nombre("Evaluación Integral")
                        .especialidad("Odontología General")
                        .duracionMin(40)
                        .precio(new BigDecimal("70.00"))
                        .activo(true)
                        .build()));

        mockMvc.perform(get("/servicios")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("srv-1"))
                .andExpect(jsonPath("$[0].nombre").value("Evaluación Integral"));
    }

    @Test
    @DisplayName("B-021: GET /servicios?sedeId=sede-eltambo para usuario de sede-chilca-1 retorna 200 (catálogo global, sedeId ignorado)")
    void testGetServiciosSedeAjena() throws Exception {
        when(servicioService.listarServicios()).thenReturn(List.of());
        mockMvc.perform(get("/servicios")
                        .param("sedeId", "sede-eltambo")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
