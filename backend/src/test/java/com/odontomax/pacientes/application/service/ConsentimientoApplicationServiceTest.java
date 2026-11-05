package com.odontomax.pacientes.application.service;

import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO;
import com.odontomax.pacientes.application.dto.response.ConsentimientoResponseDTO;
import com.odontomax.pacientes.application.dto.response.PlantillaConsentimientoResponseDTO;
import com.odontomax.pacientes.application.port.out.ConsentimientoPdfPort;
import com.odontomax.pacientes.application.port.out.ConsentimientoRepositoryPort;
import com.odontomax.pacientes.application.port.out.PlantillaConsentimientoRepositoryPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.Consentimiento;
import com.odontomax.pacientes.domain.model.PlantillaConsentimiento;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentimientoApplicationServiceTest {

    @Mock
    private ConsentimientoRepositoryPort consentimientoRepository;

    @Mock
    private PlantillaConsentimientoRepositoryPort plantillaRepository;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private StoragePort storagePort;

    @Mock
    private ConsentimientoPdfPort pdfPort;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @Mock
    private com.odontomax.pacientes.application.port.out.HistorialCitasPort historialCitas;

    @InjectMocks
    private ConsentimientoApplicationService consentimientoService;

    private UUID pacienteId;
    private PacienteEntity mockPaciente;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        mockPaciente = PacienteEntity.builder()
                .id(pacienteId)
                .nombres("Carlos")
                .apellidos("Jimenez")
                .dni("71092834")
                .sedeId("sede-chilca-1")
                .build();

        ClinicaUserPrincipal user = new ClinicaUserPrincipal(
                UUID.randomUUID(),
                "admin@odontomax.pe",
                "Admin General",
                "ADMIN_GENERAL",
                null
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("listarPlantillas retorna plantillas activas")
    void testListarPlantillas() {
        when(plantillaRepository.findByActivoTrueOrderByTituloAsc()).thenReturn(List.of(
                new PlantillaConsentimiento(
                        UUID.randomUUID(),
                        "CONS-DATOS-V1",
                        "Datos",
                        "TRATAMIENTO_DATOS",
                        "Contenido datos",
                        1,
                        true
                )
        ));

        List<PlantillaConsentimientoResponseDTO> lista = consentimientoService.listarPlantillas();
        assertEquals(1, lista.size());
        assertEquals("CONS-DATOS-V1", lista.get(0).getCodigo());
    }

    @Test
    @DisplayName("registrarConsentimiento decodifica firma válida PNG, genera PDF con OpenPDF y persiste")
    void testRegistrarConsentimientoExitoso() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(mockPaciente));

        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        String validBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(validPngBytes);

        ConsentimientoRequestDTO dto = ConsentimientoRequestDTO.builder()
                .pacienteId(pacienteId)
                .tipo("TRATAMIENTO_DATOS")
                .titulo("Consentimiento Datos")
                .contenidoAceptado("Acepto tratamiento de datos personales")
                .firmaBase64(validBase64)
                .nombreFirmante("Carlos Jimenez")
                .documentoFirmante("71092834")
                .build();

        byte[] fakePdfBytes = new byte[]{37, 80, 68, 70};
        when(pdfPort.generarDocumentoPdf(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(fakePdfBytes);

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(storagePort.generatePresignedUrl(anyString(), eq(15)))
                .thenReturn("https://r2.privado/presigned.png");

        when(consentimientoRepository.save(any(Consentimiento.class)))
                .thenAnswer(inv -> {
                    Consentimiento c = inv.getArgument(0);
                    c.setId(UUID.randomUUID());
                    return c;
                });

        ConsentimientoResponseDTO response = consentimientoService.registrarConsentimiento(dto);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("TRATAMIENTO_DATOS", response.getTipo());
        assertEquals("Carlos Jimenez", response.getNombreFirmante());
        assertNotNull(response.getFirmaUrl());

        // Debe haber subido 2 archivos a R2 (firma PNG y documento PDF)
        verify(storagePort, times(2)).uploadFile(anyString(), any(InputStream.class), anyLong(), anyString());
        verify(consentimientoRepository).save(any(Consentimiento.class));
        verify(pacienteRepository).save(mockPaciente);
        verify(registroAuditoria).registrarEventoClinico(any(), eq("sede-chilca-1"), eq("REGISTRAR_CONSENTIMIENTO"), eq("consentimientos"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Rechaza firma manuscrita con cabecera mágica no PNG")
    void testRechazarFirmaInvalida_NoEsPng() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(mockPaciente));

        byte[] fakeBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        String invalidBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(fakeBytes);

        ConsentimientoRequestDTO dto = ConsentimientoRequestDTO.builder()
                .pacienteId(pacienteId)
                .tipo("TRATAMIENTO_DATOS")
                .firmaBase64(invalidBase64)
                .nombreFirmante("Carlos Jimenez")
                .documentoFirmante("71092834")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentimientoService.registrarConsentimiento(dto));
        assertTrue(ex.getMessage().contains("PNG válida"));
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("Rechaza firma manuscrita que supera el límite de 500 KB")
    void testRechazarFirmaExcesiva_MayorA500KB() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(mockPaciente));

        byte[] hugeBytes = new byte[510 * 1024];
        hugeBytes[0] = (byte) 0x89;
        hugeBytes[1] = 0x50;
        hugeBytes[2] = 0x4E;
        hugeBytes[3] = 0x47;
        hugeBytes[4] = 0x0D;
        hugeBytes[5] = 0x0A;
        hugeBytes[6] = 0x1A;
        hugeBytes[7] = 0x0A;

        String hugeBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(hugeBytes);

        ConsentimientoRequestDTO dto = ConsentimientoRequestDTO.builder()
                .pacienteId(pacienteId)
                .tipo("TRATAMIENTO_DATOS")
                .firmaBase64(hugeBase64)
                .nombreFirmante("Carlos Jimenez")
                .documentoFirmante("71092834")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentimientoService.registrarConsentimiento(dto));
        assertTrue(ex.getMessage().contains("500 KB"));
        verifyNoInteractions(storagePort);
    }
    @Test
    @DisplayName("listarPorPaciente: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testListarPorPaciente_OdontologoSinCita_Denegado() {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitas).exigirAccesoDeOdontologo(pacienteId);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> consentimientoService.listarPorPaciente(pacienteId));
    }

    @Test
    @DisplayName("registrarConsentimiento: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void testRegistrarConsentimiento_OdontologoSinCita_Denegado() {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitas).exigirAccesoDeOdontologo(pacienteId);

        com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO dto = com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO.builder()
                .pacienteId(pacienteId)
                .tipo("TRATAMIENTO_DATOS")
                .firmaBase64("data:image/png;base64,iVBORw0KGgo=")
                .nombreFirmante("Carlos Jimenez")
                .documentoFirmante("71092834")
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> consentimientoService.registrarConsentimiento(dto));
    }
}
