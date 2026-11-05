package com.odontomax.pacientes.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.port.in.GestionarFotoPacienteUseCase;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.FotoPacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataFotoPacienteRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FotoPacienteApplicationService implements GestionarFotoPacienteUseCase {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final SpringDataPacienteRepository pacienteRepository;
    private final SpringDataFotoPacienteRepository fotoRepository;
    private final HistorialCitasPort historialCitas;
    private final RegistroAuditoriaPort registroAuditoria;

    @Override
    @Transactional(readOnly = true)
    public Optional<FotoPaciente> obtener(UUID pacienteId) {
        exigirAcceso(pacienteId);
        return fotoRepository.findById(pacienteId)
                .map(f -> new FotoPaciente(f.getContenido(), f.getContentType(), f.getActualizadoEn()));
    }

    @Override
    @Transactional
    public OffsetDateTime guardar(UUID pacienteId, byte[] contenido, String contentType) {
        PacienteEntity paciente = exigirAcceso(pacienteId);
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("Elige una foto para el paciente.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new IllegalArgumentException("La foto no puede superar los 2 MB.");
        }
        String tipoReal = tipoSegunContenido(contenido);
        if (tipoReal == null) {
            throw new IllegalArgumentException("La foto debe ser una imagen JPEG, PNG o WebP.");
        }
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        OffsetDateTime ahora = OffsetDateTime.now();

        FotoPacienteEntity foto = fotoRepository.findById(pacienteId)
                .orElseGet(() -> FotoPacienteEntity.builder().pacienteId(pacienteId).build());
        foto.setContenido(contenido);
        foto.setContentType(tipoReal);
        foto.setActualizadoEn(ahora);
        foto.setActualizadoPor(user != null ? user.nombre() : "Sistema");
        fotoRepository.save(foto);

        paciente.setFotoActualizadaEn(ahora);
        pacienteRepository.save(paciente);
        registroAuditoria.registrarEventoClinico(user, paciente.getSedeId(), "ACTUALIZAR_FOTO_PACIENTE",
                "paciente_fotos", pacienteId.toString(), "Foto de perfil actualizada", SecurityUtils.getClientIp());
        return ahora;
    }

    @Override
    @Transactional
    public void eliminar(UUID pacienteId) {
        PacienteEntity paciente = exigirAcceso(pacienteId);
        fotoRepository.deleteById(pacienteId);
        paciente.setFotoActualizadaEn(null);
        pacienteRepository.save(paciente);
        registroAuditoria.registrarEventoClinico(SecurityUtils.getCurrentUser(), paciente.getSedeId(),
                "ELIMINAR_FOTO_PACIENTE", "paciente_fotos", pacienteId.toString(), "Foto de perfil eliminada",
                SecurityUtils.getClientIp());
    }

    /** El tipo real sale de los primeros bytes: el nombre o el tipo que declara el cliente no son de fiar. */
    static String tipoSegunContenido(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private PacienteEntity exigirAcceso(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());
        return paciente;
    }
}
