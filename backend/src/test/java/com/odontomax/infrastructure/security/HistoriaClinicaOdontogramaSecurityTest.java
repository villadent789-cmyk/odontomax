package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.application.dto.request.NotaEvolucionRequestDTO;
import com.odontomax.application.dto.request.OdontogramaPiezaRequestDTO;
import com.odontomax.pacientes.application.dto.response.AntecedenteHistorialResponseDTO;
import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.application.service.*;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.presentation.controller.*;
import com.odontomax.pacientes.infrastructure.web.AntecedentesController;
import com.odontomax.pacientes.infrastructure.web.ConsentimientoController;
import com.odontomax.pacientes.infrastructure.web.HistoriaClinicaAccesoController;
import com.odontomax.pacientes.application.port.in.GestionarAntecedentesUseCase;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.odontomax.clinica.service.OdontogramaPdfService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        OdontogramaController.class,
        NotaEvolucionController.class,
        AntecedentesController.class,
        ConsentimientoController.class,
        HistoriaClinicaAccesoController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class HistoriaClinicaOdontogramaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OdontogramaService odontogramaService;

    @MockBean
    private OdontogramaPdfService odontogramaPdfService;

    @MockBean
    private NotaEvolucionService notaEvolucionService;

    @MockBean
    private GestionarAntecedentesUseCase antecedentesService;

    @MockBean
    private GestionarConsentimientosUseCase consentimientoService;

    @MockBean
    private GestionarAccesoHistoriaUseCase historiaClinicaAccesoService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private UUID odontologoId;
    private UUID recepcionId;
    private UUID adminGeneralId;
    private String tokenOdontologo;
    private String tokenRecepcion;
    private String tokenAdminGeneral;

    @BeforeEach
    void setUp() {
        odontologoId = UUID.randomUUID();
        recepcionId = UUID.randomUUID();
        adminGeneralId = UUID.randomUUID();

        when(usuarioRepository.findByEmailIgnoreCase("doctor@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(odontologoId)
                        .email("doctor@odontomax.pe")
                        .nombre("Dr. Carlos Odontologo")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()
        ));

        when(usuarioRepository.findByEmailIgnoreCase("recepcion@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(recepcionId)
                        .email("recepcion@odontomax.pe")
                        .nombre("Ana Recepcion")
                        .rol("RECEPCION")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()
        ));

        when(usuarioRepository.findByEmailIgnoreCase("admin@odontomax.pe")).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(adminGeneralId)
                        .email("admin@odontomax.pe")
                        .nombre("Admin General")
                        .rol("ADMIN_GENERAL")
                        .activo(true)
                        .build()
        ));

        tokenOdontologo = jwtTokenProvider.generateToken(
                odontologoId, "doctor@odontomax.pe", "Dr. Carlos Odontologo", "ODONTOLOGO", "sede-chilca-1"
        , 0);
        tokenRecepcion = jwtTokenProvider.generateToken(
                recepcionId, "recepcion@odontomax.pe", "Ana Recepcion", "RECEPCION", "sede-chilca-1"
        , 0);
        tokenAdminGeneral = jwtTokenProvider.generateToken(
                adminGeneralId, "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null
        , 0);
    }

    @Test
    @DisplayName("GET /odontograma/pacientes/{id}: Deniega con 401 si no hay token")
    void testOdontogramaSinToken() throws Exception {
        UUID pacId = UUID.randomUUID();
        mockMvc.perform(get("/odontograma/pacientes/" + pacId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /odontograma/pacientes/{id}: Sede cruzada deniega con 403 Forbidden")
    void testOdontogramaSedeCruzada() throws Exception {
        UUID pacId = UUID.randomUUID();
        when(odontogramaService.obtenerEstadoActual(pacId))
                .thenThrow(new AccessDeniedException("Acceso denegado: Paciente en sede distinta."));

        mockMvc.perform(get("/odontograma/pacientes/" + pacId)
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /odontograma/pacientes/{id}/pieza: RECEPCION sí puede escribir (201 Created)")
    void testOdontogramaEscrituraRecepcionPermitida() throws Exception {
        UUID pacId = UUID.randomUUID();
        OdontogramaPiezaRequestDTO dto = OdontogramaPiezaRequestDTO.builder()
                .piezaFdi(16)
                .estado("caries")
                .caras(List.of("oclusal"))
                .build();

        mockMvc.perform(post("/odontograma/pacientes/" + pacId + "/pieza")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /odontograma/pacientes/{id}/pieza: Caso feliz con ODONTOLOGO retorna 201 Created")
    void testOdontogramaEscrituraOdontologoExitoso() throws Exception {
        UUID pacId = UUID.randomUUID();
        OdontogramaPiezaRequestDTO dto = OdontogramaPiezaRequestDTO.builder()
                .piezaFdi(16)
                .estado("caries")
                .caras(List.of("oclusal"))
                .build();

        OdontogramaRegistroResponseDTO response = OdontogramaRegistroResponseDTO.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacId)
                .piezaFdi(16)
                .estado("caries")
                .caras(List.of("oclusal"))
                .build();

        when(odontogramaService.registrarPieza(eq(pacId), any())).thenReturn(response);

        mockMvc.perform(post("/odontograma/pacientes/" + pacId + "/pieza")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.piezaFdi").value(16))
                .andExpect(jsonPath("$.estado").value("caries"));
    }

    @Test
    @DisplayName("POST /evoluciones/pacientes/{id}: RECEPCION sí puede registrar notas clínicas (201 Created)")
    void testEvolucionRecepcionPermitida() throws Exception {
        UUID pacId = UUID.randomUUID();
        NotaEvolucionRequestDTO dto = NotaEvolucionRequestDTO.builder()
                .subjetivo("Paciente refiere dolor")
                .objetivo("Caries profunda")
                .analisis("Pulpitis reversible")
                .plan("Restauracion resina")
                .build();

        mockMvc.perform(post("/evoluciones/pacientes/" + pacId)
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /evoluciones/pacientes/{id}: Caso feliz con ODONTOLOGO retorna 201 Created")
    void testEvolucionOdontologoExitoso() throws Exception {
        UUID pacId = UUID.randomUUID();
        NotaEvolucionRequestDTO dto = NotaEvolucionRequestDTO.builder()
                .tipoNota("SOAP")
                .subjetivo("Paciente acude por control")
                .objetivo("Encías rosadas, sin sangrado")
                .analisis("Salud gingival")
                .plan("Profilaxis y flúor")
                .build();

        NotaEvolucionResponseDTO response = NotaEvolucionResponseDTO.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacId)
                .autorNombre("Dr. Carlos Odontologo")
                .tipoNota("SOAP")
                .subjetivo(dto.getSubjetivo())
                .firmado(true)
                .build();

        when(notaEvolucionService.crearNota(eq(pacId), any())).thenReturn(response);

        mockMvc.perform(post("/evoluciones/pacientes/" + pacId)
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firmado").value(true))
                .andExpect(jsonPath("$.subjetivo").value("Paciente acude por control"));
    }

    @Test
    @DisplayName("PUT /antecedentes/pacientes/{id}: RECEPCION sí puede editar antecedentes médicos (200 OK)")
    void testAntecedentesRecepcionPermitida() throws Exception {
        UUID pacId = UUID.randomUUID();
        AntecedentesUpdateRequestDTO dto = AntecedentesUpdateRequestDTO.builder()
                .alergias("Penicilina")
                .motivoCambio("Reporte de nueva alergia")
                .build();

        mockMvc.perform(put("/antecedentes/pacientes/" + pacId)
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /antecedentes/pacientes/{id}: Caso feliz con ODONTOLOGO retorna 200 OK")
    void testAntecedentesOdontologoExitoso() throws Exception {
        UUID pacId = UUID.randomUUID();
        AntecedentesUpdateRequestDTO dto = AntecedentesUpdateRequestDTO.builder()
                .alergias("Penicilina, AINES")
                .antecedentesMedicos("Hipertensión controlada")
                .gestante(false)
                .motivoCambio("Actualización en primera consulta clínica")
                .build();

        PacienteResponseDTO response = PacienteResponseDTO.builder()
                .id(pacId)
                .nombres("Juan")
                .apellidos("Perez")
                .alergias(List.of("Penicilina", "AINES"))
                .antecedentesMedicos(dto.getAntecedentesMedicos())
                .build();

        when(antecedentesService.actualizarAntecedentes(eq(pacId), any())).thenReturn(response);

        mockMvc.perform(put("/antecedentes/pacientes/" + pacId)
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergias[0]").value("Penicilina"));
    }

    @Test
    @DisplayName("GET /historia-clinica/accesos: Solo ADMIN_GENERAL puede auditar lecturas de historias clínicas")
    void testHistoriaClinicaAccesosRolAdminGeneral() throws Exception {
        // ODONTOLOGO no puede ver el log global de lecturas (403)
        mockMvc.perform(get("/historia-clinica/accesos")
                        .header("Authorization", "Bearer " + tokenOdontologo)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // ADMIN_GENERAL puede acceder (200)
        when(historiaClinicaAccesoService.listarAccesos(null)).thenReturn(List.of());

        mockMvc.perform(get("/historia-clinica/accesos")
                        .header("Authorization", "Bearer " + tokenAdminGeneral)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
