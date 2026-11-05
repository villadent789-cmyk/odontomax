package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.infrastructure.web.CitaController;
import com.odontomax.agenda.infrastructure.web.EspecialistaController;
import com.odontomax.application.service.RecetaPdfService;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.presentation.controller.RecetaController;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CitaController.class, EspecialistaController.class, RecetaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class AgendaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AgendarCitaUseCase agendarCitaUseCase;

    @MockBean
    private ReprogramarCitaUseCase reprogramarCitaUseCase;

    @MockBean
    private CancelarCitaUseCase cancelarCitaUseCase;

    @MockBean
    private CambiarEstadoCitaUseCase cambiarEstadoCitaUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.IniciarAtencionCitaUseCase iniciarAtencionCitaUseCase;

    @MockBean
    private ConsultarCitaUseCase consultarCitaUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.RegistrarAdelantoUseCase registrarAdelantoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.RegistrarContactoUseCase registrarContactoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ProcesarExpiracionUseCase procesarExpiracionUseCase;

    @MockBean
    private ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @MockBean
    private GestionEspecialistaUseCase gestionEspecialistaUseCase;

    @MockBean
    private GestionHorariosEspecialistaUseCase gestionHorariosEspecialistaUseCase;

    @MockBean
    private ConsultarDisponibilidadUseCase consultarDisponibilidadUseCase;

    @MockBean
    private RecetaPdfService recetaPdfService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenChilca1;
    private String tokenRecepcion;
    private final UUID citaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UUID docId = UUID.randomUUID();
        String docEmail = "doc.seg@odontomax.pe";
        when(usuarioRepository.findByEmailIgnoreCase(docEmail))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(docId)
                        .email(docEmail)
                        .nombre("Dr. Odontólogo")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()));

        tokenChilca1 = jwtTokenProvider.generateToken(
                docId, docEmail, "Dr. Odontólogo", "ODONTOLOGO", "sede-chilca-1"
        , 0);

        UUID recId = UUID.randomUUID();
        String recEmail = "recepcion@odontomax.pe";
        when(usuarioRepository.findByEmailIgnoreCase(recEmail))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(recId)
                        .email(recEmail)
                        .nombre("Recepcionista")
                        .rol("RECEPCION")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()));

        tokenRecepcion = jwtTokenProvider.generateToken(
                recId, recEmail, "Recepcionista", "RECEPCION", "sede-chilca-1"
        , 0);
    }

    @Test
    @DisplayName("PATCH /citas/{id}/reprogramar sin autenticación: retorna 401 Unauthorized")
    void testReprogramarSinAuth() throws Exception {
        mockMvc.perform(patch("/citas/" + citaId + "/reprogramar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFecha\":\"2026-10-10\",\"nuevoHorario\":\"10:00\",\"motivo\":\"Cambio solicitado\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /citas/{id}/reprogramar entre sedes cruzadas: retorna 403 Forbidden")
    void testReprogramarSedeCruzada() throws Exception {
        // Cita pertenece a sede-eltambo y el token es de sede-chilca-1
        CitaResponseDTO citaEltambo = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-eltambo")
                .estado("PROGRAMADA")
                .build();
        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(citaEltambo);

        mockMvc.perform(patch("/citas/" + citaId + "/reprogramar")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFecha\":\"2026-10-10\",\"nuevoHorario\":\"10:00\",\"motivo\":\"Cambio solicitado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-035: PATCH /citas/{id}/reprogramar entre sedes cruzadas con RECEPCION: retorna 403 y no llama al caso de uso")
    void testReprogramarSedeCruzadaRecepcion() throws Exception {
        CitaResponseDTO citaEltambo = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-eltambo")
                .estado("PROGRAMADA")
                .build();
        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(citaEltambo);

        mockMvc.perform(patch("/citas/" + citaId + "/reprogramar")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFecha\":\"2026-10-10\",\"nuevoHorario\":\"10:00\",\"motivo\":\"Cambio solicitado\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(reprogramarCitaUseCase);
    }

    @Test
    @DisplayName("B-035: PATCH /citas/{id}/cancelar con rol ODONTOLOGO: retorna 403 y no llama al caso de uso")
    void testCancelarOdontologoProhibido() throws Exception {
        mockMvc.perform(patch("/citas/" + citaId + "/cancelar")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cancela el paciente\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(cancelarCitaUseCase);
    }

    @Test
    @DisplayName("PATCH /citas/{id}/cancelar sin autenticación: retorna 401 Unauthorized")
    void testCancelarSinAuth() throws Exception {
        mockMvc.perform(patch("/citas/" + citaId + "/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cancela el paciente\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /especialistas/{id}/disponibilidad sin autenticación: retorna 401 Unauthorized")
    void testDisponibilidadSinAuth() throws Exception {
        mockMvc.perform(get("/especialistas/" + UUID.randomUUID() + "/disponibilidad")
                        .param("fecha", "2026-10-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /recetas/pdf con rol RECEPCION: retorna 403 Forbidden (solo odontólogo o administradores)")
    void testRecetaPdfRolRecepcionForbidden() throws Exception {
        String body = """
                {
                    "pacienteNombre": "Juan Pérez",
                    "doctorNombre": "Dr. Alex Ríos",
                    "medicamentos": [
                        {"medicamento": "Paracetamol 500mg"}
                    ]
                }
                """;

        mockMvc.perform(post("/recetas/pdf")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /recetas/pdf con rol ODONTOLOGO: caso feliz retorna 200 OK y application/pdf")
    void testRecetaPdfRolOdontologoOk() throws Exception {
        when(recetaPdfService.generarRecetaPdf(any())).thenReturn(new byte[]{1, 2, 3, 4, 5});

        String body = """
                {
                    "pacienteNombre": "Juan Pérez",
                    "doctorNombre": "Dr. Alex Ríos",
                    "medicamentos": [
                        {"medicamento": "Paracetamol 500mg"}
                    ]
                }
                """;

        mockMvc.perform(post("/recetas/pdf")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }
}
