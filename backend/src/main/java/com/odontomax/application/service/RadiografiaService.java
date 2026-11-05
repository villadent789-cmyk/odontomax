package com.odontomax.application.service;

import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RadiografiaService {

    public static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L; // 15 MB

    public static final int MAX_PRESIGNED_MINUTOS = 15;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "application/dicom",
            "image/dicom-rle",
            "application/octet-stream"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "dcm", "dicom"
    );

    public static final Set<String> TIPOS_ESTUDIO_PERMITIDOS = Set.of(
            "PANORAMICA",
            "PERIAPICAL",
            "ALETA_MORDIDA",
            "CEFALOMETRICA",
            "TOMOGRAFIA",
            "FOTO_CLINICA",
            "FOTO_ANTES",
            "FOTO_DESPUES",
            "INFORME"
    );

    public static String normalizarTipoEstudio(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo de estudio radiográfico es obligatorio.");
        }
        String tipoNorm = tipo.trim().toUpperCase();
        if (!TIPOS_ESTUDIO_PERMITIDOS.contains(tipoNorm)) {
            throw new IllegalArgumentException(
                    "Tipo de estudio no válido: '" + tipo + "'. Debe coincidir con los tipos permitidos: " + TIPOS_ESTUDIO_PERMITIDOS
            );
        }
        return tipoNorm;
    }

    private final RadiografiaRepositoryPort radiografiaRepository;
    private final StoragePort storagePort;
    private final SpringDataPacienteRepository pacienteRepository;
    private final com.odontomax.agenda.api.ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private final com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository auditoriaRepository;

    public Radiografia subirRadiografia(
            UUID pacienteId,
            String tipoEstudio,
            Integer piezaFdi,
            String etapa,
            String diagnostico,
            MultipartFile archivo
    ) throws IOException {
        return subirRadiografia(pacienteId, null, tipoEstudio, piezaFdi, etapa, diagnostico, archivo);
    }

    /**
     * Caso de uso: Subir radiografía dental a bucket privado en Cloudflare R2 con validación de tipo y tamaño
     */
    @Transactional
    public Radiografia subirRadiografia(
            UUID pacienteId,
            UUID doctorIdParam,
            String tipoEstudio,
            Integer piezaFdi,
            String etapa,
            String diagnostico,
            MultipartFile archivo
    ) throws IOException {

        // 1. Cargar el paciente y verificar que exista
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        // 3. radiografias_estudios.doctor_id referencia especialistas(id), no usuarios(id).
        // Se resuelve el especialista por especialistas.usuario_id (o se deja null si no existe)
        com.odontomax.identidad.api.ClinicaUserPrincipal principal =
                com.odontomax.identidad.api.SecurityUtils.getCurrentUser();
        UUID doctorId = null;
        if (principal != null && principal.id() != null) {
            // Resolver id especialista por cuenta de usuario
            doctorId = consultaEspecialistasApi.findEspecialistaIdByUsuarioId(principal.id())
                    .orElse(null);
        } else if (doctorIdParam != null) {
            if (consultaEspecialistasApi.existsById(doctorIdParam)) {
                doctorId = doctorIdParam;
            } else {
                // Resolver id especialista por id de usuario alternativo
                doctorId = consultaEspecialistasApi.findEspecialistaIdByUsuarioId(doctorIdParam)
                        .orElse(null);
            }
        }

        // 4. Validar tipo de estudio según CHECK de BD (nunca null ni GENERAL)
        String tipoNormalizado = normalizarTipoEstudio(tipoEstudio);

        // 5. Validar presencia de archivo
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo radiográfico válido y no vacío.");
        }

        // 6. Control estricto de tamaño de archivo (Máximo 15 MB)
        if (archivo.getSize() > MAX_FILE_SIZE_BYTES) {
            double mb = archivo.getSize() / (1024.0 * 1024.0);
            throw new IllegalArgumentException(String.format("El archivo excede el tamaño máximo permitido de 15 MB (recibido: %.2f MB).", mb));
        }

        // 7. Control estricto de tipo MIME y extensión
        String rawFilename = archivo.getOriginalFilename();
        String extension = validarYExtraerExtension(rawFilename, archivo.getContentType());

        // 8. Verificación del contenido real (Magic Bytes) exigiendo siempre firma válida y coincidencia
        validarContenidoReal(archivo, extension, archivo.getContentType());

        // 9. Clave canónica de almacenamiento en Cloudflare R2 (Bucket Privado)
        String tipoSanitizado = tipoNormalizado.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        String storageKey = String.format("pacientes/%s/rx_%s_%d.%s",
                pacienteId.toString(),
                tipoSanitizado,
                System.currentTimeMillis(),
                extension
        );

        // 10. Sube a Cloudflare R2 SOLO después de validar
        storagePort.uploadFile(
                storageKey,
                archivo.getInputStream(),
                archivo.getSize(),
                archivo.getContentType()
        );

        // 11. Registro de metadatos en la base de datos PostgreSQL con compensación si falla
        try {
            Radiografia radiografia = Radiografia.builder()
                    .id(UUID.randomUUID())
                    .pacienteId(pacienteId)
                    .doctorId(doctorId)
                    .tipoEstudio(tipoNormalizado)
                    .piezaFdi(piezaFdi)
                    .etapa(etapa != null ? etapa : "INICIAL")
                    .archivoUrl(storageKey) // Se almacena la clave canónica privada
                    .nombreArchivo(sanitizarNombreArchivo(rawFilename))
                    .tamanoBytes(archivo.getSize())
                    .diagnosticoRadiografico(diagnostico)
                    .fechaToma(LocalDate.now())
                    .build();

            Radiografia guardada = radiografiaRepository.save(radiografia);

            // 11. Retornar con URL prefirmada temporal de máximo 15 minutos para visualización
            String presignedUrl = storagePort.generatePresignedUrl(storageKey, MAX_PRESIGNED_MINUTOS);
            guardada.setArchivoUrl(presignedUrl);

            return guardada;
        } catch (Exception ex) {
            // Si falla el guardado en BD, borrar el objeto en R2 para mantener la consistencia
            try {
                storagePort.deleteFile(storageKey);
            } catch (Exception deleteEx) {
                org.slf4j.LoggerFactory.getLogger(RadiografiaService.class)
                        .error("Error al compensar/borrar archivo en R2 tras fallo en BD: {}", deleteEx.getMessage());
            }
            throw ex;
        }
    }

    /**
     * Lista los estudios radiográficos de un paciente generando URLs prefirmadas temporales
     */
    public List<Radiografia> listarPorPaciente(UUID pacienteId) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        // Cargar paciente y validar acceso a su sede
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        com.odontomax.identidad.api.SecurityUtils.validarAccesoSede(paciente.getSedeId());

        List<Radiografia> lista = radiografiaRepository.findByPacienteId(pacienteId);
        for (Radiografia rx : lista) {
            if (rx.getArchivoUrl() != null && !rx.getArchivoUrl().isBlank()) {
                try {
                    // Genera una URL temporal firmada válida por máximo 15 minutos
                    String urlFirmada = storagePort.generatePresignedUrl(rx.getArchivoUrl(), MAX_PRESIGNED_MINUTOS);
                    rx.setArchivoUrl(urlFirmada);
                } catch (Exception ignored) {
                }
            }
        }
        return lista;
    }

    /**
     * Genera una URL prefirmada fresca para un estudio específico limitada a máximo 15 minutos
     */
    public String obtenerUrlFirmada(UUID radiografiaId, int minutos) {
        Radiografia rx = radiografiaRepository.findById(radiografiaId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el estudio radiográfico solicitado con ID: " + radiografiaId));

        PacienteEntity paciente = pacienteRepository.findById(rx.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente asociado al estudio radiográfico"));
        com.odontomax.identidad.api.SecurityUtils.validarAccesoSede(paciente.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(rx.getPacienteId());

        // Limita minutos a máximo 15 para el visor
        int minutosValidados = Math.max(1, Math.min(minutos > 0 ? minutos : MAX_PRESIGNED_MINUTOS, MAX_PRESIGNED_MINUTOS));
        return storagePort.generatePresignedUrl(rx.getArchivoUrl(), minutosValidados);
    }

    public record ArchivoRadiografia(byte[] contenido, String nombreArchivo, String contentType) {}

    /**
     * Obtiene el contenido binario real del archivo para visualización directa o descarga
     */
    public ArchivoRadiografia obtenerContenidoArchivo(UUID radiografiaId) {
        Radiografia rx = radiografiaRepository.findById(radiografiaId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el estudio radiográfico solicitado con ID: " + radiografiaId));

        PacienteEntity paciente = pacienteRepository.findById(rx.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente asociado al estudio radiográfico"));
        com.odontomax.identidad.api.SecurityUtils.validarAccesoSede(paciente.getSedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(paciente.getId());

        byte[] contenido = storagePort.downloadFile(rx.getArchivoUrl());
        String ext = "";
        if (rx.getNombreArchivo() != null && rx.getNombreArchivo().contains(".")) {
            ext = rx.getNombreArchivo().substring(rx.getNombreArchivo().lastIndexOf(".") + 1).toLowerCase();
        }
        String contentType = switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "dcm", "dicom" -> "application/dicom";
            default -> "image/png";
        };

        return new ArchivoRadiografia(contenido, rx.getNombreArchivo(), contentType);
    }

    /**
     * Elimina físicamente un estudio: fila en BD y objeto en R2.
     * El borrado queda registrado en auditoria_log (con motivo) dentro de la misma transacción:
     * si la auditoría falla, no se borra nada. El objeto en R2 se borra solo tras el commit.
     */
    @Transactional
    public void eliminarRadiografia(UUID radiografiaId, String motivo) {
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Debe indicar el motivo de la eliminación (mínimo 5 caracteres).");
        }
        String motivoLimpio = motivo.trim();
        if (motivoLimpio.length() > 500) {
            motivoLimpio = motivoLimpio.substring(0, 500);
        }

        Radiografia rx = radiografiaRepository.findById(radiografiaId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el estudio radiográfico solicitado con ID: " + radiografiaId));
        PacienteEntity paciente = pacienteRepository.findById(rx.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente asociado al estudio radiográfico"));
        com.odontomax.identidad.api.SecurityUtils.validarAccesoSede(paciente.getSedeId());

        String storageKey = rx.getArchivoUrl();
        radiografiaRepository.deleteById(radiografiaId);

        com.odontomax.identidad.api.ClinicaUserPrincipal principal =
                com.odontomax.identidad.api.SecurityUtils.getCurrentUser();
        auditoriaRepository.save(com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity.builder()
                .usuarioId(principal != null ? principal.id() : null)
                .usuarioNombre(principal != null ? principal.nombre() : "SISTEMA")
                .rol(principal != null ? principal.rol() : "SISTEMA")
                .sedeId(paciente.getSedeId())
                .modulo("IMAGENES_CLINICAS")
                .accion("ELIMINAR_RADIOGRAFIA")
                .tabla("radiografias_estudios")
                .registroId(radiografiaId.toString())
                .detalle(String.format("Eliminado físicamente estudio %s (%s) del paciente %s, tomado el %s, archivo '%s'. Motivo: %s",
                        rx.getTipoEstudio(), rx.getEtapa(), rx.getPacienteId(), rx.getFechaToma(),
                        rx.getNombreArchivo(), motivoLimpio))
                .ip(com.odontomax.identidad.api.SecurityUtils.getClientIp())
                .build());

        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Runnable borrarArchivo = () -> {
            try {
                storagePort.deleteFile(storageKey);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(RadiografiaService.class)
                        .error("Estudio {} eliminado de la BD pero falló el borrado en R2 (clave {}): {}",
                                radiografiaId, storageKey, e.getMessage());
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            borrarArchivo.run();
                        }
                    });
        } else {
            borrarArchivo.run();
        }
    }

    public static void validarContenidoReal(MultipartFile archivo, String extension, String contentType) throws IOException {
        byte[] header = new byte[132];
        int read;
        try (InputStream is = archivo.getInputStream()) {
            read = is.read(header);
        }

        if (read < 4) {
            throw new IllegalArgumentException("El archivo radiográfico está corrupto o es demasiado pequeño.");
        }

        // 1. Prohibir ejecutables y scripts peligrosos
        if (header[0] == 0x4D && header[1] == 0x5A) { // 'MZ' Windows executable / DLL
            throw new IllegalArgumentException("El archivo contiene un formato ejecutable prohibido.");
        }
        if (header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) { // 'ELF'
            throw new IllegalArgumentException("El archivo contiene un formato ejecutable prohibido.");
        }
        if (header[0] == 0x23 && header[1] == 0x21) { // '#!' script shebang
            throw new IllegalArgumentException("El archivo contiene un script no permitido.");
        }

        // 2. Exigir siempre firma radiológica válida (JPEG, PNG, WEBP o DICOM)
        boolean isJpeg = (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
        boolean isPng = (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47);
        boolean isWebp = (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46 &&
                header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50);
        boolean isDicom = (read >= 132 && header[128] == 0x44 && header[129] == 0x49 && header[130] == 0x43 && header[131] == 0x4D);

        if (!isJpeg && !isPng && !isWebp && !isDicom) {
            throw new IllegalArgumentException("El archivo no contiene una firma radiológica válida (JPEG, PNG, WEBP o DICOM).");
        }

        // 3. Verificar que la firma coincida con la extensión y el Content-Type declarados
        String extNorm = extension != null ? extension.toLowerCase().trim() : "";
        String ctNorm = contentType != null ? contentType.toLowerCase().trim() : "";

        if (isJpeg) {
            if (!extNorm.equals("jpg") && !extNorm.equals("jpeg")) {
                throw new IllegalArgumentException("La firma binaria del archivo (JPEG) no coincide con la extensión declarada (." + extNorm + ").");
            }
            if (!ctNorm.equals("image/jpeg") && !ctNorm.equals("image/jpg") && !ctNorm.equals("application/octet-stream")) {
                throw new IllegalArgumentException("La firma binaria del archivo (JPEG) no coincide con el Content-Type declarado (" + ctNorm + ").");
            }
        } else if (isPng) {
            if (!extNorm.equals("png")) {
                throw new IllegalArgumentException("La firma binaria del archivo (PNG) no coincide con la extensión declarada (." + extNorm + ").");
            }
            if (!ctNorm.equals("image/png") && !ctNorm.equals("application/octet-stream")) {
                throw new IllegalArgumentException("La firma binaria del archivo (PNG) no coincide con el Content-Type declarado (" + ctNorm + ").");
            }
        } else if (isWebp) {
            if (!extNorm.equals("webp")) {
                throw new IllegalArgumentException("La firma binaria del archivo (WEBP) no coincide con la extensión declarada (." + extNorm + ").");
            }
            if (!ctNorm.equals("image/webp") && !ctNorm.equals("application/octet-stream")) {
                throw new IllegalArgumentException("La firma binaria del archivo (WEBP) no coincide con el Content-Type declarado (" + ctNorm + ").");
            }
        } else if (isDicom) {
            if (!extNorm.equals("dcm") && !extNorm.equals("dicom")) {
                throw new IllegalArgumentException("La firma binaria del archivo (DICOM) no coincide con la extensión declarada (." + extNorm + ").");
            }
            if (!ctNorm.equals("application/dicom") && !ctNorm.equals("image/dicom-rle") && !ctNorm.equals("application/octet-stream")) {
                throw new IllegalArgumentException("La firma binaria del archivo (DICOM) no coincide con el Content-Type declarado (" + ctNorm + ").");
            }
        }
    }

    public static String validarYExtraerExtension(String filename, String contentType) {
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Formato no permitido: '" + contentType + "'. Solo se aceptan imágenes radiográficas (JPEG, PNG, WEBP) o archivos DICOM.");
        }

        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("El archivo debe contener una extensión válida (.jpg, .png, .webp, .dcm).");
        }

        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase().trim();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Extensión de archivo '" + ext + "' no permitida. Solo se admiten archivos .jpg, .jpeg, .png, .webp, .dcm");
        }

        return ext;
    }

    public static String sanitizarNombreArchivo(String filename) {
        if (filename == null) return "estudio.jpg";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
