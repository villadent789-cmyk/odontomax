package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.pacientes.application.dto.response.PacienteExportDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;
import com.odontomax.pacientes.domain.model.Paciente;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.pacientes.infrastructure.web.PacienteController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PacienteController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class ExportacionPacienteSecurityTest {

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
    private ExportarExpedienteUseCase exportacionPacienteService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenAdminGeneral;
    private String tokenRecepcion;
    private final UUID pacienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UUID adminId = UUID.randomUUID();
        UsuarioEntity admin = UsuarioEntity.builder()
                .id(adminId)
                .email("admin@odontomax.pe")
                .nombre("Admin General")
                .rol("ADMIN_GENERAL")
                .sedeId(null)
                .activo(true)
                .build();

        UUID recId = UUID.randomUUID();
        UsuarioEntity rec = UsuarioEntity.builder()
                .id(recId)
                .email("recepcion@odontomax.pe")
                .nombre("Recepción")
                .rol("RECEPCION")
                .sedeId("sede-chilca-1")
                .activo(true)
                .build();

        when(usuarioRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByEmailIgnoreCase(rec.getEmail())).thenReturn(Optional.of(rec));

        tokenAdminGeneral = jwtTokenProvider.generateToken(adminId, admin.getEmail(), admin.getNombre(), admin.getRol(), admin.getSedeId(), 0);
        tokenRecepcion = jwtTokenProvider.generateToken(recId, rec.getEmail(), rec.getNombre(), rec.getRol(), rec.getSedeId(), 0);
    }

    @Test
    @DisplayName("GET /pacientes/{id}/exportar sin autenticación: 401")
    void exportar_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/pacientes/" + pacienteId + "/exportar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /pacientes/{id}/exportar con rol RECEPCION: 403 Forbidden")
    void exportar_rolRecepcion_retorna403() throws Exception {
        mockMvc.perform(get("/pacientes/" + pacienteId + "/exportar")
                        .header("Authorization", "Bearer " + tokenRecepcion))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /pacientes/{id}/exportar con rol ADMIN_GENERAL: 200 OK con aviso legal Ley 29733")
    void exportar_adminGeneral_retorna200ConAvisoLegal() throws Exception {
        PacienteExportDTO dto = PacienteExportDTO.builder()
                .datosPersonales(PacienteResponseDTO.builder().id(pacienteId).nombres("Juan").apellidos("Pérez").build())
                .citas(Collections.emptyList())
                .tratamientos(Collections.emptyList())
                .pagos(Collections.emptyList())
                .avisoLegal("Documento emitido conforme a la Ley N° 29733 de Protección de Datos Personales")
                .build();

        when(exportacionPacienteService.exportarExpedienteCompleto(pacienteId)).thenReturn(dto);

        mockMvc.perform(get("/pacientes/" + pacienteId + "/exportar")
                        .header("Authorization", "Bearer " + tokenAdminGeneral))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisoLegal").value("Documento emitido conforme a la Ley N° 29733 de Protección de Datos Personales"));
    }
}
