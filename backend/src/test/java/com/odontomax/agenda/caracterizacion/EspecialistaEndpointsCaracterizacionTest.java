package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadSlotDTO;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.agenda.application.dto.response.HorarioEspecialistaResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.identidad.testsupport.UsuariosSimuladosWebMvcConfig;
import com.odontomax.identidad.testsupport.UsuarioTestFixtures;
import org.springframework.context.ApplicationContext;
import com.odontomax.identidad.infrastructure.security.*;
import com.odontomax.infrastructure.security.TestSecurityEnvironmentInitializer;
import com.odontomax.agenda.infrastructure.web.EspecialistaController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EspecialistaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, UsuariosSimuladosWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class EspecialistaEndpointsCaracterizacionTest {

    private static final String MENSAJE_409 = "No se puede eliminar el especialista porque tiene historial clínico registrado. "
            + "Desactívelo en su lugar para mantener la auditoría médica.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private ApplicationContext context;

    private String adminToken;
    private final UUID adminId = UUID.randomUUID();
    private final UUID especialistaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UsuarioTestFixtures.simularUsuarioActivo(context, adminId, "admin@odontomax.pe", "Admin General",
                "ADMIN_GENERAL", null);

        adminToken = jwtTokenProvider.generateToken(adminId, "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null, 0);
    }

    private EspecialistaResponseDTO sampleDto(boolean activo) {
        return EspecialistaResponseDTO.builder()
                .id(especialistaId)
                .nombre("Dra. Beatriz Vega")
                .especialidad("Endodoncia")
                .cop("COP-12345")
                .email("bvega@odontomax.pe")
                .telefono("987111222")
                .activo(activo)
                .sedes(List.of("sede-chilca-1"))
                .build();
    }

    @Test
    @DisplayName("GET /especialistas retorna 200 y lista de activos filtrados")
    void listarEspecialistas_200() throws Exception {
        when(consultarEspecialistaUseCase.listarEspecialistas("sede-chilca-1")).thenReturn(List.of(sampleDto(true)));

        mockMvc.perform(get("/especialistas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("sedeId", "sede-chilca-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(especialistaId.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Dra. Beatriz Vega"))
                .andExpect(jsonPath("$[0].activo").value(true));
    }

    @Test
    @DisplayName("GET /especialistas/admin retorna 200 con activos e inactivos (solo ADMIN_GENERAL)")
    void listarTodosAdmin_200() throws Exception {
        when(consultarEspecialistaUseCase.listarTodos()).thenReturn(List.of(sampleDto(true), sampleDto(false)));

        mockMvc.perform(get("/especialistas/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST /especialistas con payload válido retorna 201 Created")
    void crearEspecialista_201() throws Exception {
        EspecialistaRequestDTO request = EspecialistaRequestDTO.builder()
                .nombre("Dra. Beatriz Vega")
                .especialidad("Endodoncia")
                .cop("COP-12345")
                .sedes(List.of("sede-chilca-1"))
                .build();

        when(gestionEspecialistaUseCase.crear(any())).thenReturn(sampleDto(true));

        mockMvc.perform(post("/especialistas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(especialistaId.toString()))
                .andExpect(jsonPath("$.cop").value("COP-12345"));
    }

    @Test
    @DisplayName("PUT /especialistas/{id} con datos válidos retorna 200")
    void actualizarEspecialista_200() throws Exception {
        EspecialistaRequestDTO request = EspecialistaRequestDTO.builder()
                .nombre("Dra. Beatriz Vega Actualizada")
                .especialidad("Endodoncia y Estética")
                .sedes(List.of("sede-chilca-1"))
                .build();

        EspecialistaResponseDTO updated = sampleDto(true);
        updated.setNombre("Dra. Beatriz Vega Actualizada");

        when(gestionEspecialistaUseCase.actualizar(eq(especialistaId), any())).thenReturn(updated);

        mockMvc.perform(put("/especialistas/" + especialistaId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Dra. Beatriz Vega Actualizada"));
    }

    @Test
    @DisplayName("PATCH /especialistas/{id}/estado con activo=false retorna 200")
    void cambiarEstado_200() throws Exception {
        when(gestionEspecialistaUseCase.cambiarEstado(especialistaId, false)).thenReturn(sampleDto(false));

        mockMvc.perform(patch("/especialistas/" + especialistaId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("DELETE /especialistas/{id} sin historial retorna 204 No Content")
    void eliminar_sinHistorial_204() throws Exception {
        mockMvc.perform(delete("/especialistas/" + especialistaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(gestionEspecialistaUseCase).eliminar(especialistaId);
    }

    @Test
    @DisplayName("DELETE /especialistas/{id} con historial retorna 409 Conflict y cuerpo estándar")
    void eliminar_conHistorial_409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, MENSAJE_409))
                .when(gestionEspecialistaUseCase).eliminar(especialistaId);

        mockMvc.perform(delete("/especialistas/" + especialistaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(MENSAJE_409));
    }

    @Test
    @DisplayName("GET /especialistas/{id}/horarios retorna 200 y lista de bloques")
    void obtenerHorarios_200() throws Exception {
        HorarioEspecialistaResponseDTO bloque = HorarioEspecialistaResponseDTO.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .diaSemana(1)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(13, 0))
                .build();

        when(gestionHorariosEspecialistaUseCase.obtenerHorarios(especialistaId)).thenReturn(List.of(bloque));

        mockMvc.perform(get("/especialistas/" + especialistaId + "/horarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diaSemana").value(1))
                .andExpect(jsonPath("$[0].horaInicio").value("09:00:00"))
                .andExpect(jsonPath("$[0].horaFin").value("13:00:00"));
    }

    @Test
    @DisplayName("PUT /especialistas/{id}/horarios reemplaza horarios y retorna 200")
    void reemplazarHorarios_200() throws Exception {
        HorarioEspecialistaRequestDTO request = HorarioEspecialistaRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .diaSemana(2)
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(18, 0))
                .build();

        HorarioEspecialistaResponseDTO response = HorarioEspecialistaResponseDTO.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .diaSemana(2)
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(18, 0))
                .build();

        when(gestionHorariosEspecialistaUseCase.reemplazarHorarios(eq(especialistaId), any())).thenReturn(List.of(response));

        mockMvc.perform(put("/especialistas/" + especialistaId + "/horarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diaSemana").value(2));
    }

    @Test
    @DisplayName("GET /especialistas/{id}/disponibilidad en día con turnos retorna atiendeEseDia=true y slots de 30 minutos")
    void obtenerDisponibilidad_conTurnos_200() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 10, 19); // Lunes
        DisponibilidadResponseDTO dto = DisponibilidadResponseDTO.builder()
                .especialistaId(especialistaId)
                .especialistaNombre("Dra. Beatriz Vega")
                .fecha(fecha)
                .sedeId("sede-chilca-1")
                .atiendeEseDia(true)
                .mensaje("Turnos disponibles calculados con éxito.")
                .slots(List.of(
                        DisponibilidadSlotDTO.builder().hora(LocalTime.of(9, 0)).disponible(true).build(),
                        DisponibilidadSlotDTO.builder().hora(LocalTime.of(9, 30)).disponible(false).motivoNoDisponible("OCUPADO").build()
                ))
                .build();

        when(consultarDisponibilidadUseCase.obtenerDisponibilidad(especialistaId, fecha, "sede-chilca-1")).thenReturn(dto);

        mockMvc.perform(get("/especialistas/" + especialistaId + "/disponibilidad")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("fecha", "2026-10-19")
                        .param("sedeId", "sede-chilca-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atiendeEseDia").value(true))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].hora").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].disponible").value(true))
                .andExpect(jsonPath("$.slots[1].disponible").value(false))
                .andExpect(jsonPath("$.slots[1].motivoNoDisponible").value("OCUPADO"));
    }

    @Test
    @DisplayName("GET /especialistas/{id}/disponibilidad en día sin turnos retorna atiendeEseDia=false y slots vacíos")
    void obtenerDisponibilidad_sinTurnos_200() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 10, 18); // Domingo
        DisponibilidadResponseDTO dto = DisponibilidadResponseDTO.builder()
                .especialistaId(especialistaId)
                .especialistaNombre("Dra. Beatriz Vega")
                .fecha(fecha)
                .sedeId("sede-chilca-1")
                .atiendeEseDia(false)
                .mensaje("El especialista no tiene turnos programados para el día seleccionado.")
                .slots(List.of())
                .build();

        when(consultarDisponibilidadUseCase.obtenerDisponibilidad(especialistaId, fecha, "sede-chilca-1")).thenReturn(dto);

        mockMvc.perform(get("/especialistas/" + especialistaId + "/disponibilidad")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("fecha", "2026-10-18")
                        .param("sedeId", "sede-chilca-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atiendeEseDia").value(false))
                .andExpect(jsonPath("$.slots").isEmpty());
    }
}
