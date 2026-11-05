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
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.agenda.infrastructure.web.EspecialistaController;
import com.odontomax.sedes.infrastructure.web.ServicioController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EspecialistaController.class, ServicioController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class EspecialistaServicioGestionSecurityTest {

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
    private ServicioService servicioService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private static final String ESPECIALISTA_JSON = """
            {"nombre":"Dra. Elena Gómez","especialidad":"Endodoncia","telefono":"987654321","sedes":["sede-chilca-1"]}
            """;
    private static final String SERVICIO_JSON = """
            {"nombre":"Limpieza dental","especialidad":"General","duracionMin":30,"precio":80.00}
            """;

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("ADMIN_GENERAL crea especialista (201)")
    void adminGeneral_creaEspecialista() throws Exception {
        when(gestionEspecialistaUseCase.crear(any())).thenReturn(EspecialistaResponseDTO.builder()
                .id(UUID.randomUUID()).nombre("Dra. Elena Gómez").especialidad("Endodoncia")
                .activo(true).sedes(List.of("sede-chilca-1")).build());

        mockMvc.perform(post("/especialistas").contentType(MediaType.APPLICATION_JSON).content(ESPECIALISTA_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Dra. Elena Gómez"));
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("Validación: sin sedes o con celular inválido responde 400")
    void validacion_400() throws Exception {
        mockMvc.perform(post("/especialistas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Dr. X\",\"especialidad\":\"General\",\"sedes\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/especialistas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Dr. X\",\"especialidad\":\"General\",\"telefono\":\"12345\",\"sedes\":[\"sede-chilca-1\"]}"))
                .andExpect(status().isBadRequest());
        verify(gestionEspecialistaUseCase, never()).crear(any());
    }

    @Test
    @WithMockUser(roles = "ODONTOLOGO")
    @DisplayName("ODONTOLOGO no puede crear, editar, desactivar ni cambiar horarios (403)")
    void odontologo_noGestiona() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/especialistas").contentType(MediaType.APPLICATION_JSON).content(ESPECIALISTA_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/especialistas/" + id).contentType(MediaType.APPLICATION_JSON).content(ESPECIALISTA_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/especialistas/" + id + "/estado").param("activo", "false"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/especialistas/" + id + "/horarios").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        verify(gestionEspecialistaUseCase, never()).crear(any());
        verify(gestionHorariosEspecialistaUseCase, never()).reemplazarHorarios(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN_SEDE")
    @DisplayName("ADMIN_SEDE no gestiona el catálogo global de servicios ni ve el listado de administración (403)")
    void adminSede_noGestionaServicios() throws Exception {
        mockMvc.perform(post("/servicios").contentType(MediaType.APPLICATION_JSON).content(SERVICIO_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/servicios/limpieza-dental/estado").param("activo", "false"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/especialistas/admin"))
                .andExpect(status().isForbidden());
        verify(servicioService, never()).crear(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("ADMIN_GENERAL crea y desactiva servicios")
    void adminGeneral_gestionaServicios() throws Exception {
        when(servicioService.crear(any())).thenReturn(ServicioResponseDTO.builder()
                .id("limpieza-dental").nombre("Limpieza dental").especialidad("General").duracionMin(30).activo(true).build());
        when(servicioService.cambiarEstado(eq("limpieza-dental"), eq(false))).thenReturn(ServicioResponseDTO.builder()
                .id("limpieza-dental").nombre("Limpieza dental").especialidad("General").duracionMin(30).activo(false).build());

        mockMvc.perform(post("/servicios").contentType(MediaType.APPLICATION_JSON).content(SERVICIO_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("limpieza-dental"));
        mockMvc.perform(patch("/servicios/limpieza-dental/estado").param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }
}
