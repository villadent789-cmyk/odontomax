package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.sedes.dto.SedeResponseDTO;
import com.odontomax.sedes.service.SedeService;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.sedes.infrastructure.web.SedeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SedeController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class SedeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SedeService sedeService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private static final String SEDE_JSON = """
            {"nombre":"Sede Pilcomayo","direccion":"Av. Principal 100","ciudad":"Huancayo","sillones":2}
            """;

    private static SedeResponseDTO sede(String id, boolean activa) {
        return SedeResponseDTO.builder().id(id).nombre("Sede Pilcomayo").direccion("Av. Principal 100")
                .ciudad("Huancayo").sillones(2).fotos(List.of()).activa(activa).build();
    }

    @Test
    @DisplayName("GET /sedes es público: responde 200 sin sesión con las sedes activas")
    void listarActivas_publico() throws Exception {
        when(sedeService.listarActivas()).thenReturn(List.of(sede("sede-pilcomayo", true)));

        mockMvc.perform(get("/sedes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sede-pilcomayo"))
                .andExpect(jsonPath("$[0].fotos").isArray())
                .andExpect(jsonPath("$[0].activa").value(true));
    }

    @Test
    @DisplayName("Sin sesión, las rutas de administración y las mutaciones responden 401")
    void sinSesion_mutaciones401() throws Exception {
        mockMvc.perform(get("/sedes/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/sedes/sede-pilcomayo").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/sedes/sede-pilcomayo/estado").param("activa", "false"))
                .andExpect(status().isUnauthorized());
        verify(sedeService, never()).crear(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN_SEDE")
    @DisplayName("ADMIN_SEDE no administra sedes (403)")
    void adminSede_403() throws Exception {
        mockMvc.perform(get("/sedes/admin")).andExpect(status().isForbidden());
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/sedes/sede-pilcomayo").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/sedes/sede-pilcomayo/estado").param("activa", "false"))
                .andExpect(status().isForbidden());
        verify(sedeService, never()).crear(any());
        verify(sedeService, never()).actualizar(any(), any());
        verify(sedeService, never()).cambiarEstado(any(), eq(false));
    }

    @Test
    @WithMockUser(roles = {"RECEPCION"})
    @DisplayName("RECEPCION puede leer las sedes activas pero no el listado de administración (403)")
    void recepcion_soloLectura() throws Exception {
        when(sedeService.listarActivas()).thenReturn(List.of());
        mockMvc.perform(get("/sedes")).andExpect(status().isOk());
        mockMvc.perform(get("/sedes/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("ADMIN_GENERAL lista todas, crea (201), edita (200) y cambia el estado (200)")
    void adminGeneral_gestiona() throws Exception {
        when(sedeService.listarTodas()).thenReturn(List.of(sede("sede-a", true), sede("sede-b", false)));
        when(sedeService.crear(any())).thenReturn(sede("sede-pilcomayo", true));
        when(sedeService.actualizar(eq("sede-pilcomayo"), any())).thenReturn(sede("sede-pilcomayo", true));
        when(sedeService.cambiarEstado("sede-pilcomayo", false)).thenReturn(sede("sede-pilcomayo", false));

        mockMvc.perform(get("/sedes/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].activa").value(false));
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sede-pilcomayo"));
        mockMvc.perform(put("/sedes/sede-pilcomayo").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/sedes/sede-pilcomayo/estado").param("activa", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("Validación: nombre, dirección y sillones (>=1) son obligatorios (400)")
    void validacion_400() throws Exception {
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"direccion\":\"Av. 1\",\"sillones\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sede X\",\"sillones\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/sedes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sede X\",\"direccion\":\"Av. 1\",\"sillones\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/sedes/sede-x").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sede X\",\"direccion\":\"Av. 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
        verify(sedeService, never()).crear(any());
        verify(sedeService, never()).actualizar(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN_GENERAL")
    @DisplayName("Sede inexistente: PUT y PATCH responden 404")
    void noExiste_404() throws Exception {
        when(sedeService.actualizar(eq("sede-nada"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada: sede-nada"));
        when(sedeService.cambiarEstado("sede-nada", true))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada: sede-nada"));

        mockMvc.perform(put("/sedes/sede-nada").contentType(MediaType.APPLICATION_JSON).content(SEDE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Sede no encontrada: sede-nada"));
        mockMvc.perform(patch("/sedes/sede-nada/estado").param("activa", "true"))
                .andExpect(status().isNotFound());
    }
}
