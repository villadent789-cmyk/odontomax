package com.odontomax.pacientes.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.response.DescuentoRecomendacionResponseDTO;
import com.odontomax.pacientes.application.port.in.GestionarDescuentosRecomendacionUseCase;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.DescuentoRecomendacionEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataDescuentoRecomendacionRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DescuentoRecomendacionApplicationService implements GestionarDescuentosRecomendacionUseCase {

    private static final Set<String> ROLES_QUE_DAN_DESCUENTO = Set.of("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL");

    private final SpringDataDescuentoRecomendacionRepository descuentoRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final HistorialCitasPort historialCitas;

    @Override
    @Transactional(readOnly = true)
    public List<DescuentoRecomendacionResponseDTO> listar(UUID pacienteId) {
        exigirAcceso(pacienteId);
        return descuentoRepository.findByPacienteIdOrderByCreadoEnDesc(pacienteId).stream()
                .sorted(Comparator.comparing((DescuentoRecomendacionEntity d) -> !"PENDIENTE".equals(d.getEstado()))
                        .thenComparing(DescuentoRecomendacionEntity::getCreadoEn, Comparator.reverseOrder()))
                .map(this::aDto)
                .toList();
    }

    @Override
    @Transactional
    public DescuentoRecomendacionResponseDTO aplicar(UUID pacienteId, UUID descuentoId, UUID tratamientoId) {
        exigirAcceso(pacienteId);
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user == null || user.roles().stream().noneMatch(ROLES_QUE_DAN_DESCUENTO::contains)) {
            throw new AccessDeniedException("Solo el odontólogo o un administrador da el descuento por recomendación.");
        }
        DescuentoRecomendacionEntity descuento = descuentoRepository.findById(descuentoId)
                .filter(d -> d.getPacienteId().equals(pacienteId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Descuento por recomendación no encontrado"));
        if (!"PENDIENTE".equals(descuento.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este descuento por recomendación ya se aplicó.");
        }
        descuento.setEstado("APLICADO");
        descuento.setAplicadoEn(OffsetDateTime.now());
        descuento.setAplicadoPor(user.nombre());
        descuento.setTratamientoId(tratamientoId);
        return aDto(descuentoRepository.save(descuento));
    }

    @Override
    @Transactional
    public void registrarRecomendacion(UUID pacienteRecomendadorId, UUID pacienteNuevoId) {
        if (pacienteRecomendadorId == null || pacienteRecomendadorId.equals(pacienteNuevoId)
                || descuentoRepository.existsByReferidoId(pacienteNuevoId)
                || !pacienteRepository.existsById(pacienteRecomendadorId)) {
            return;
        }
        descuentoRepository.save(DescuentoRecomendacionEntity.builder()
                .pacienteId(pacienteRecomendadorId)
                .referidoId(pacienteNuevoId)
                .build());
    }

    private PacienteEntity exigirAcceso(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());
        return paciente;
    }

    private DescuentoRecomendacionResponseDTO aDto(DescuentoRecomendacionEntity d) {
        String nombre = pacienteRepository.findById(d.getReferidoId()).map(PacienteEntity::getNombreCompleto).orElse(null);
        return DescuentoRecomendacionResponseDTO.builder()
                .id(d.getId())
                .referidoId(d.getReferidoId())
                .referidoNombre(nombre)
                .estado(d.getEstado())
                .creadoEn(d.getCreadoEn())
                .aplicadoEn(d.getAplicadoEn())
                .aplicadoPor(d.getAplicadoPor())
                .tratamientoId(d.getTratamientoId())
                .build();
    }
}
