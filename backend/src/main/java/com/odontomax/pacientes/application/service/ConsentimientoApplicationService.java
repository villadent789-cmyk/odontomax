package com.odontomax.pacientes.application.service;

import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO;
import com.odontomax.pacientes.application.dto.response.ConsentimientoResponseDTO;
import com.odontomax.pacientes.application.dto.response.PlantillaConsentimientoResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.out.ConsentimientoPdfPort;
import com.odontomax.pacientes.application.port.out.ConsentimientoRepositoryPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.PlantillaConsentimientoRepositoryPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.Consentimiento;
import com.odontomax.pacientes.domain.model.FirmaManuscrita;
import com.odontomax.pacientes.domain.model.PlantillaConsentimiento;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentimientoApplicationService implements GestionarConsentimientosUseCase {

    private final ConsentimientoRepositoryPort consentimientoRepository;
    private final PlantillaConsentimientoRepositoryPort plantillaRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final StoragePort storagePort;
    private final ConsentimientoPdfPort pdfPort;
    private final RegistroAuditoriaPort registroAuditoria;
    private final HistorialCitasPort historialCitas;

    @Override
    @Transactional(readOnly = true)
    public List<PlantillaConsentimientoResponseDTO> listarPlantillas() {
        return plantillaRepository.findByActivoTrueOrderByTituloAsc()
                .stream()
                .map(ConsentimientoApplicationService::toPlantillaDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentimientoResponseDTO> listarPorPaciente(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return consentimientoRepository.findByPacienteIdOrderByFirmadoEnDesc(pacienteId)
                .stream()
                .map(c -> {
                    String presignedFirma = null;
                    if (c.getFirmaImagenKey() != null) {
                        try {
                            presignedFirma = storagePort.generatePresignedUrl(c.getFirmaImagenKey(), 15);
                        } catch (Exception ignored) {
                        }
                    }
                    String presignedPdf = null;
                    if (c.getPdfKey() != null) {
                        try {
                            presignedPdf = storagePort.generatePresignedUrl(c.getPdfKey(), 15);
                        } catch (Exception ignored) {
                        }
                    }
                    return toDto(c, presignedFirma, presignedPdf);
                })
                .toList();
    }

    @Override
    @Transactional
    public ConsentimientoResponseDTO registrarConsentimiento(ConsentimientoRequestDTO dto) {
        historialCitas.exigirAccesoDeOdontologo(dto.getPacienteId());
        PacienteEntity paciente = pacienteRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + dto.getPacienteId()));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        long timestamp = System.currentTimeMillis();

        // 1. Extraer y subir firma manuscrita a R2 Privado (validado en dominio)
        FirmaManuscrita firma = FirmaManuscrita.desdeBase64(dto.getFirmaBase64());
        byte[] firmaBytes = firma.getBytes();
        String firmaKey = String.format("pacientes/%s/consentimientos/firma_%d.png",
                paciente.getId(), timestamp);

        storagePort.uploadFile(
                firmaKey,
                new ByteArrayInputStream(firmaBytes),
                firmaBytes.length,
                "image/png"
        );

        // 2. Generar documento PDF firmado y subir a R2 Privado
        byte[] pdfBytes = pdfPort.generarDocumentoPdf(
                dto.getTitulo() != null ? dto.getTitulo() : "Consentimiento Informado",
                paciente.getNombreCompleto(),
                dto.getTipo(),
                dto.getContenidoAceptado(),
                dto.getNombreFirmante(),
                dto.getDocumentoFirmante(),
                OffsetDateTime.now()
        );

        String pdfKey = String.format("pacientes/%s/consentimientos/consentimiento_%d.pdf",
                paciente.getId(), timestamp);

        storagePort.uploadFile(
                pdfKey,
                new ByteArrayInputStream(pdfBytes),
                pdfBytes.length,
                "application/pdf"
        );

        // 3. Crear modelo de dominio y persistir
        Consentimiento domain = Consentimiento.firmar(
                paciente.getId(),
                dto.getTratamientoId(),
                dto.getPlantillaId(),
                dto.getTipo(),
                dto.getTitulo(),
                dto.getContenidoAceptado(),
                firmaKey,
                pdfKey,
                dto.getNombreFirmante(),
                dto.getDocumentoFirmante(),
                dto.getParentescoFirmante(),
                SecurityUtils.getClientIp(),
                user != null ? user.id() : null,
                OffsetDateTime.now()
        );

        Consentimiento guardado = consentimientoRepository.save(domain);

        // 4. Si es consentimiento de tratamiento de datos, actualizar fecha en paciente
        if (guardado.esTratamientoDatos()) {
            OffsetDateTime ahora = OffsetDateTime.now();
            paciente.registrarConsentimientoDatos(ahora);
            pacienteRepository.save(paciente);
        }

        // 5. Auditoría
        registroAuditoria.registrarEventoClinico(
                user,
                paciente.getSedeId(),
                "REGISTRAR_CONSENTIMIENTO",
                "consentimientos",
                guardado.getId().toString(),
                String.format("Consentimiento '%s' firmado por %s (DNI: %s)",
                        guardado.getTipo(), guardado.getNombreFirmante(), guardado.getDocumentoFirmante()),
                SecurityUtils.getClientIp());

        String presignedFirma = storagePort.generatePresignedUrl(firmaKey, 15);
        String presignedPdf = storagePort.generatePresignedUrl(pdfKey, 15);

        return toDto(guardado, presignedFirma, presignedPdf);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] obtenerFirmaImagen(UUID consentimientoId) {
        Consentimiento c = consentimientoRepository.findById(consentimientoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el consentimiento con ID: " + consentimientoId));
        PacienteEntity paciente = pacienteRepository.findById(c.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente asociado al consentimiento"));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());
        historialCitas.exigirAccesoDeOdontologo(paciente.getId());

        if (c.getFirmaImagenKey() == null || c.getFirmaImagenKey().isBlank()) {
            return new byte[0];
        }
        return storagePort.downloadFile(c.getFirmaImagenKey());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] obtenerPdf(UUID consentimientoId) {
        Consentimiento c = consentimientoRepository.findById(consentimientoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el consentimiento con ID: " + consentimientoId));
        PacienteEntity paciente = pacienteRepository.findById(c.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente asociado al consentimiento"));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());
        historialCitas.exigirAccesoDeOdontologo(paciente.getId());

        if (c.getPdfKey() == null || c.getPdfKey().isBlank()) {
            return new byte[0];
        }
        return storagePort.downloadFile(c.getPdfKey());
    }

    private static PlantillaConsentimientoResponseDTO toPlantillaDto(PlantillaConsentimiento domain) {
        if (domain == null) return null;
        return PlantillaConsentimientoResponseDTO.builder()
                .id(domain.id())
                .codigo(domain.codigo())
                .titulo(domain.titulo())
                .tipo(domain.tipo())
                .contenido(domain.contenido())
                .version(domain.version())
                .activo(domain.activo())
                .build();
    }

    private static ConsentimientoResponseDTO toDto(Consentimiento domain, String presignedFirmaUrl, String presignedPdfUrl) {
        if (domain == null) return null;
        return ConsentimientoResponseDTO.builder()
                .id(domain.getId())
                .pacienteId(domain.getPacienteId())
                .tratamientoId(domain.getTratamientoId())
                .tipo(domain.getTipo())
                .plantillaId(domain.getPlantillaId())
                .titulo(domain.getTitulo())
                .contenidoAceptado(domain.getContenidoAceptado())
                .firmaUrl(presignedFirmaUrl)
                .pdfUrl(presignedPdfUrl)
                .nombreFirmante(domain.getNombreFirmante())
                .documentoFirmante(domain.getDocumentoFirmante())
                .parentescoFirmante(domain.getParentescoFirmante())
                .firmadoEn(domain.getFirmadoEn())
                .build();
    }
}