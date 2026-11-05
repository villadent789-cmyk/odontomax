package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.application.port.in.ConsultarUsuarioUseCase;
import com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.identidad.infrastructure.web.UsuarioController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UsuarioController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class UsuarioManagementSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarUsuarioUseCase consultarUsuarioUseCase;

    @MockBean
    private GestionarUsuarioUseCase usuarioService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(username = "odontologo@odontomax.pe", roles = {"ODONTOLOGO"})
    @DisplayName("Gestión Usuarios: Deniega acceso (403) a roles que no sean ADMIN_GENERAL")
    void testOdontologoNoPuedeGestionarUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isForbidden());

        String validPayload = """
                {
                    "nombre": "Test Intrusion",
                    "email": "test.intrusion@odontomax.pe",
                    "password": "Password123!",
                    "rol": "ODONTOLOGO",
                    "sedeId": "sede-chilca-1"
                }
                """;

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@odontomax.pe", roles = {"ADMIN_GENERAL"})
    @DisplayName("Gestión Usuarios: ADMIN_GENERAL lista todos los usuarios exitosamente")
    void testAdminGeneralPuedeListarUsuarios() throws Exception {
        UsuarioResponseDTO u1 = UsuarioResponseDTO.builder()
                .id(UUID.randomUUID())
                .nombre("Dr. Alex Ríos")
                .email("alex@odontomax.pe")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .sedeNombre("Chilca I (Calle Real)")
                .activo(true)
                .creadoEn(OffsetDateTime.now())
                .build();

        when(consultarUsuarioUseCase.listarTodos()).thenReturn(List.of(u1));

        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Dr. Alex Ríos"))
                .andExpect(jsonPath("$[0].email").value("alex@odontomax.pe"))
                .andExpect(jsonPath("$[0].rol").value("ODONTOLOGO"));
    }

    @Test
    @WithMockUser(username = "admin@odontomax.pe", roles = {"ADMIN_GENERAL"})
    @DisplayName("Gestión Usuarios: ADMIN_GENERAL crea nuevo usuario exitosamente con rol y sede válidos")
    void testAdminGeneralCreaUsuario() throws Exception {
        UsuarioResponseDTO responseDTO = UsuarioResponseDTO.builder()
                .id(UUID.randomUUID())
                .nombre("Dra. María Lopez")
                .email("maria@odontomax.pe")
                .rol("ADMIN_SEDE")
                .sedeId("sede-villamax-chilca")
                .sedeNombre("Villamax Chilca")
                .activo(true)
                .creadoEn(OffsetDateTime.now())
                .build();

        when(usuarioService.crearUsuario(any(), any(), any())).thenReturn(responseDTO);

        String payload = """
                {
                    "nombre": "Dra. María Lopez",
                    "email": "maria@odontomax.pe",
                    "password": "PasswordSeguro123!",
                    "rol": "ADMIN_SEDE",
                    "sedeId": "sede-villamax-chilca"
                }
                """;

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("maria@odontomax.pe"))
                .andExpect(jsonPath("$.rol").value("ADMIN_SEDE"))
                .andExpect(jsonPath("$.sedeId").value("sede-villamax-chilca"));
    }

    @Test
    @WithMockUser(username = "admin@odontomax.pe", roles = {"ADMIN_GENERAL"})
    @DisplayName("Gestión Usuarios: ADMIN_GENERAL elimina cuenta de usuario en la base de datos")
    void testAdminGeneralEliminaUsuario() throws Exception {
        UUID userId = UUID.randomUUID();
        when(usuarioService.eliminarUsuario(any(), any(), any())).thenReturn(Map.of(
                "status", 200,
                "tipo", "FISICO",
                "mensaje", "Usuario eliminado exitosamente de la base de datos."
        ));

        mockMvc.perform(delete("/usuarios/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FISICO"))
                .andExpect(jsonPath("$.mensaje").value("Usuario eliminado exitosamente de la base de datos."));
    }
}
