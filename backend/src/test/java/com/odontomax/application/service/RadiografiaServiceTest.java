package com.odontomax.application.service;

import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RadiografiaServiceTest {

    @Mock
    private RadiografiaRepositoryPort radiografiaRepository;

    @Mock
    private StoragePort storagePort;

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private com.odontomax.agenda.api.ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Mock
    private com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository auditoriaRepository;

    private RadiografiaService radiografiaService;

    private final UUID pacienteId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID especialistaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        radiografiaService = new RadiografiaService(radiografiaRepository, storagePort, pacienteRepository, consultaEspecialistasApi, auditoriaRepository);

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                doctorId, "admin@odontomax.pe", "Dr. Alex Ríos", "ADMIN_GENERAL", null
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Debe rechazar subida si el archivo es nulo o vacío")
    void testRechazarArchivoVacio() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        MockMultipartFile emptyFile = new MockMultipartFile("archivo", "rx.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, emptyFile)
        );

        verifyNoInteractions(storagePort);
        verifyNoInteractions(radiografiaRepository);
    }

    @Test
    @DisplayName("Debe rechazar archivos que excedan el límite de 15 MB")
    void testRechazarArchivoExcedeTamano() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        byte[] bytesGrande = new byte[100];
        MockMultipartFile bigFile = new MockMultipartFile("archivo", "rx_grande.jpg", "image/jpeg", bytesGrande) {
            @Override
            public long getSize() {
                return 16 * 1024 * 1024L; // 16 MB
            }
        };

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, bigFile)
        );

        assertTrue(ex.getMessage().contains("15 MB"));
        verifyNoInteractions(storagePort);
        verifyNoInteractions(radiografiaRepository);
    }

    @Test
    @DisplayName("Debe rechazar extensiones y tipos MIME no médicos (ej. .exe, .pdf, .php)")
    void testRechazarExtensionInvalida() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        MockMultipartFile exeFile = new MockMultipartFile("archivo", "virus.exe", "application/x-msdownload", "fake content".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, exeFile)
        );

        assertTrue(ex.getMessage().contains("no permitido") || ex.getMessage().contains("no permitida"));
        verifyNoInteractions(storagePort);
        verifyNoInteractions(radiografiaRepository);
    }

    @Test
    @DisplayName("No debe guardar en BD si Cloudflare R2 falla o no tiene credenciales")
    void testFalloR2NoGuardaEnBD() throws IOException {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("archivo", "rx_panoramica.jpg", "image/jpeg", validJpegBytes);

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("Cloudflare R2 no está configurado"));

        assertThrows(IllegalStateException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, validFile)
        );

        // Verifica que NUNCA se guardó el registro en la base de datos si falló R2
        verify(radiografiaRepository, never()).save(any(Radiografia.class));
    }

    @Test
    @DisplayName("Debe subir exitosamente radiografía válida y adjuntar URL prefirmada temporal")
    void testSubidaExitosaConUrlFirmada() throws IOException {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(doctorId)).thenReturn(Optional.of(especialistaId));

        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("archivo", "panoramica_control.png", "image/png", validPngBytes);

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // retorna la storageKey

        when(storagePort.generatePresignedUrl(anyString(), eq(15)))
                .thenReturn("https://radiografias-privadas.r2.cloudflarestorage.com/firmada?X-Amz-Signature=abc123&expira=15m");

        when(radiografiaRepository.save(any(Radiografia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Radiografia resultado = radiografiaService.subirRadiografia(
                pacienteId, doctorId, "PANORAMICA", 46, "CONTROL", "Buen avance óseo", validFile
        );

        assertNotNull(resultado);
        assertEquals("PANORAMICA", resultado.getTipoEstudio());
        assertEquals("panoramica_control.png", resultado.getNombreArchivo());
        assertEquals(especialistaId, resultado.getDoctorId()); // doctorId resuelto a especialistas.id!
        assertTrue(resultado.getArchivoUrl().contains("X-Amz-Signature")); // URL prefirmada temporal
        verify(storagePort).uploadFile(contains("pacientes/" + pacienteId), any(InputStream.class), anyLong(), eq("image/png"));
        verify(radiografiaRepository).save(any(Radiografia.class));
    }

    @Test
    @DisplayName("Si falla el guardado en BD, debe borrar el objeto subido en R2 (compensación)")
    void testCompensacionR2AlFallarBD() throws IOException {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("archivo", "panoramica.png", "image/png", validPngBytes);

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(radiografiaRepository.save(any(Radiografia.class)))
                .thenThrow(new RuntimeException("Error en base de datos"));

        assertThrows(RuntimeException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, validFile)
        );

        verify(storagePort).deleteFile(anyString());
    }

    @Test
    @DisplayName("listarPorPaciente debe generar URLs prefirmadas frescas para cada estudio")
    void testListarPorPacienteGeneraUrlsFirmadas() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        Radiografia rx1 = Radiografia.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .tipoEstudio("PANORAMICA")
                .archivoUrl("pacientes/" + pacienteId + "/rx_1.jpg")
                .fechaToma(LocalDate.now())
                .build();

        when(radiografiaRepository.findByPacienteId(pacienteId)).thenReturn(List.of(rx1));
        when(storagePort.generatePresignedUrl(eq("pacientes/" + pacienteId + "/rx_1.jpg"), eq(15)))
                .thenReturn("https://r2.privado/rx_1.jpg?token=firmado-15min");

        List<Radiografia> lista = radiografiaService.listarPorPaciente(pacienteId);

        assertEquals(1, lista.size());
        assertEquals("https://r2.privado/rx_1.jpg?token=firmado-15min", lista.get(0).getArchivoUrl());
    }

    @Test
    @DisplayName("(e.1) Debe rechazar archivos con firma ejecutable de Windows (MZ) aunque tengan extensión de imagen")
    void testRechazarEjecutableConFirmaMZ() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        // Cabecera 'MZ' (0x4D, 0x5A) camuflada como .jpg
        byte[] payloadMalicioso = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x01, 0x02};
        MockMultipartFile fakeJpg = new MockMultipartFile("archivo", "radiografia_camuflada.jpg", "image/jpeg", payloadMalicioso);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, fakeJpg)
        );

        assertTrue(ex.getMessage().contains("formato ejecutable prohibido"));
        verifyNoInteractions(storagePort);
        verify(radiografiaRepository, never()).save(any(Radiografia.class));
    }

    @Test
    @DisplayName("(e.2) Debe rechazar application/octet-stream si no contiene una firma radiológica válida (DICOM o imagen)")
    void testRechazarOctetStreamSinFirmaValida() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        // 200 bytes aleatorios sin firma DICM ni magic bytes de imagen
        byte[] bytesAleatorios = new byte[200];
        bytesAleatorios[0] = 0x12;
        bytesAleatorios[1] = 0x34;

        MockMultipartFile octetStreamInvalido = new MockMultipartFile(
                "archivo", "estudio_desconocido.dcm", "application/octet-stream", bytesAleatorios
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PERIAPICAL", 36, "INICIAL", null, octetStreamInvalido)
        );

        assertTrue(ex.getMessage().contains("no contiene una firma radiológica válida"));
        verifyNoInteractions(storagePort);
        verify(radiografiaRepository, never()).save(any(Radiografia.class));
    }

    @Test
    @DisplayName("(e.3) Debe aceptar application/octet-stream si cuenta con la firma canónica DICOM ('DICM' en offset 128)")
    void testAceptarOctetStreamConFirmaDicomValida() throws IOException {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        // Estructura oficial DICOM Parte 10: 128 bytes de preámbulo + 'D','I','C','M' (0x44, 0x49, 0x43, 0x4D)
        byte[] dicomValido = new byte[140];
        dicomValido[128] = 0x44; // 'D'
        dicomValido[129] = 0x49; // 'I'
        dicomValido[130] = 0x43; // 'C'
        dicomValido[131] = 0x4D; // 'M'

        MockMultipartFile dicomFile = new MockMultipartFile(
                "archivo", "tomografia.dcm", "application/octet-stream", dicomValido
        );

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), eq("application/octet-stream")))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.generatePresignedUrl(anyString(), eq(15)))
                .thenReturn("https://r2.privado/tomografia.dcm?token=firmado");
        when(radiografiaRepository.save(any(Radiografia.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Radiografia guardada = radiografiaService.subirRadiografia(
                pacienteId, doctorId, "TOMOGRAFIA", null, "INICIAL", "Estudio tomográfico", dicomFile
        );

        assertNotNull(guardada);
        assertEquals("tomografia.dcm", guardada.getNombreArchivo());
        verify(storagePort).uploadFile(contains("pacientes/" + pacienteId), any(InputStream.class), eq(140L), eq("application/octet-stream"));
        verify(radiografiaRepository).save(any(Radiografia.class));
    }

    @Test
    @DisplayName("Debe asignar doctorId null si el usuario autenticado no tiene perfil en tabla especialistas")
    void testDoctorIdNullSiUsuarioNoEsEspecialista() throws IOException {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(doctorId)).thenReturn(Optional.empty());

        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("archivo", "rx.jpg", "image/jpeg", validJpegBytes);

        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.generatePresignedUrl(anyString(), eq(15))).thenReturn("https://r2.privado/rx.jpg");
        when(radiografiaRepository.save(any(Radiografia.class))).thenAnswer(inv -> inv.getArgument(0));

        Radiografia guardada = radiografiaService.subirRadiografia(
                pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, validFile
        );

        assertNotNull(guardada);
        assertNull(guardada.getDoctorId());
    }

    @Test
    @DisplayName("Debe rechazar archivo si la firma binaria (PNG) no coincide con la extensión declarada (.jpg)")
    void testRechazarFirmaNoCoincideConExtension() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        // Bytes reales de PNG pero nombrado como .jpg
        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile mismatchFile = new MockMultipartFile("archivo", "rx_falsa.jpg", "image/jpeg", validPngBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, mismatchFile)
        );

        assertTrue(ex.getMessage().contains("no coincide"));
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("Debe rechazar tipo de estudio inválido o no soportado por el CHECK de BD (como GENERAL o INVALIDO)")
    void testRechazarTipoEstudioInvalido() {
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("archivo", "rx.png", "image/png", validPngBytes);

        // GENERAL ya no es permitido porque viola el CHECK de la BD
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, "GENERAL", null, "INICIAL", null, validFile)
        );
        assertTrue(ex1.getMessage().contains("Tipo de estudio no válido"));

        // Null o vacío tampoco permitido
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                radiografiaService.subirRadiografia(pacienteId, doctorId, null, null, "INICIAL", null, validFile)
        );
        assertTrue(ex2.getMessage().contains("obligatorio"));

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("Debe normalizar a mayúsculas tipos de estudio válidos compatibles con el CHECK de BD")
    void testNormalizarTipoEstudioValido() {
        assertEquals("PANORAMICA", RadiografiaService.normalizarTipoEstudio("panoramica"));
        assertEquals("PERIAPICAL", RadiografiaService.normalizarTipoEstudio("Periapical"));
        assertEquals("ALETA_MORDIDA", RadiografiaService.normalizarTipoEstudio("aleta_mordida"));
        assertEquals("CEFALOMETRICA", RadiografiaService.normalizarTipoEstudio("cefalometrica"));
        assertEquals("TOMOGRAFIA", RadiografiaService.normalizarTipoEstudio("tomografia"));
        assertEquals("FOTO_CLINICA", RadiografiaService.normalizarTipoEstudio("foto_clinica"));
        assertEquals("FOTO_ANTES", RadiografiaService.normalizarTipoEstudio("foto_antes"));
        assertEquals("FOTO_DESPUES", RadiografiaService.normalizarTipoEstudio("foto_despues"));
        assertEquals("INFORME", RadiografiaService.normalizarTipoEstudio("informe"));
    }

    private Radiografia estudioGuardado(UUID id) {
        return Radiografia.builder()
                .id(id)
                .pacienteId(pacienteId)
                .tipoEstudio("PANORAMICA")
                .etapa("INICIAL")
                .archivoUrl("pacientes/" + pacienteId + "/rx_panoramica_1.png")
                .nombreArchivo("panoramica.png")
                .fechaToma(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("Eliminar: borra la fila, audita con el motivo y borra el objeto en R2")
    void eliminarRadiografia_borraBdAuditaYR2() {
        UUID rxId = UUID.randomUUID();
        when(radiografiaRepository.findById(rxId)).thenReturn(Optional.of(estudioGuardado(rxId)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        radiografiaService.eliminarRadiografia(rxId, "Subida al paciente equivocado");

        verify(radiografiaRepository).deleteById(rxId);
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("ELIMINAR_RADIOGRAFIA", captor.getValue().getAccion());
        assertEquals(rxId.toString(), captor.getValue().getRegistroId());
        assertTrue(captor.getValue().getDetalle().contains("Subida al paciente equivocado"));
        verify(storagePort).deleteFile("pacientes/" + pacienteId + "/rx_panoramica_1.png");
    }

    @Test
    @DisplayName("Eliminar: sin motivo suficiente no borra nada")
    void eliminarRadiografia_sinMotivo_noBorra() {
        UUID rxId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> radiografiaService.eliminarRadiografia(rxId, "  "));
        assertThrows(IllegalArgumentException.class, () -> radiografiaService.eliminarRadiografia(rxId, "abc"));

        verify(radiografiaRepository, never()).deleteById(any());
        verify(auditoriaRepository, never()).save(any());
        verify(storagePort, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("Eliminar: ADMIN_SEDE de otra sede recibe acceso denegado y no se borra nada")
    void eliminarRadiografia_otraSede_accesoDenegado() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(UUID.randomUUID(), "admin.tambo@odontomax.pe", "Admin Tambo", "ADMIN_SEDE", "sede-eltambo"),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_SEDE"))));
        UUID rxId = UUID.randomUUID();
        when(radiografiaRepository.findById(rxId)).thenReturn(Optional.of(estudioGuardado(rxId)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> radiografiaService.eliminarRadiografia(rxId, "Motivo de prueba"));

        verify(radiografiaRepository, never()).deleteById(any());
        verify(storagePort, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("Eliminar: si falla la auditoría no se borra el archivo en R2")
    void eliminarRadiografia_fallaAuditoria_noBorraR2() {
        UUID rxId = UUID.randomUUID();
        when(radiografiaRepository.findById(rxId)).thenReturn(Optional.of(estudioGuardado(rxId)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(
                PacienteEntity.builder().id(pacienteId).sedeId("sede-chilca-1").build()
        ));
        when(auditoriaRepository.save(any())).thenThrow(new RuntimeException("BD caída"));

        assertThrows(RuntimeException.class,
                () -> radiografiaService.eliminarRadiografia(rxId, "Motivo de prueba"));

        verify(storagePort, never()).deleteFile(anyString());
    }
    @Test
    @DisplayName("Listar: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void listarPorPaciente_OdontologoSinCita_Denegado() {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class,
                () -> radiografiaService.listarPorPaciente(pacienteId));
    }

    @Test
    @DisplayName("Subir: odontólogo sin cita con el paciente recibe AccessDeniedException")
    void subirRadiografia_OdontologoSinCita_Denegado() throws Exception {
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        org.springframework.mock.web.MockMultipartFile f = new org.springframework.mock.web.MockMultipartFile("archivo", "rx.jpg", "image/jpeg", new byte[10]);
        assertThrows(AccessDeniedException.class,
                () -> radiografiaService.subirRadiografia(pacienteId, doctorId, "PANORAMICA", null, "INICIAL", null, f));
    }
}
