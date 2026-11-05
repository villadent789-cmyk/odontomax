package com.odontomax.pacientes.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.request.FichaIngresoDTO;
import com.odontomax.pacientes.application.port.in.GestionarFichaIngresoUseCase;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.FichaIngresoEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataFichaIngresoRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FichaIngresoApplicationService implements GestionarFichaIngresoUseCase {

    private final SpringDataPacienteRepository pacienteRepository;
    private final SpringDataFichaIngresoRepository fichaRepository;
    private final HistorialCitasPort historialCitas;
    private final RegistroAuditoriaPort registroAuditoria;

    @Override
    @Transactional(readOnly = true)
    public FichaIngresoDTO obtener(UUID pacienteId) {
        exigirAcceso(pacienteId);
        return fichaRepository.findById(pacienteId)
                .map(FichaIngresoApplicationService::aDto)
                .orElseGet(FichaIngresoDTO::new);
    }

    @Override
    @Transactional
    public FichaIngresoDTO guardar(UUID pacienteId, FichaIngresoDTO ficha) {
        PacienteEntity paciente = exigirAcceso(pacienteId);
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();

        FichaIngresoDTO datos = ficha != null ? ficha : new FichaIngresoDTO();
        // La fecha y el autor los pone el servidor, no el cliente.
        datos.setActualizadoEn(null);
        datos.setActualizadoPorNombre(null);

        FichaIngresoEntity entidad = fichaRepository.findById(pacienteId)
                .orElseGet(() -> FichaIngresoEntity.builder().pacienteId(pacienteId).build());
        entidad.setDatos(datos);
        entidad.setActualizadoEn(OffsetDateTime.now());
        entidad.setActualizadoPor(user != null ? user.id() : null);
        entidad.setActualizadoPorNombre(user != null ? user.nombre() : "Sistema");
        FichaIngresoEntity guardada = fichaRepository.save(entidad);

        registroAuditoria.registrarEventoClinico(user, paciente.getSedeId(), "GUARDAR_FICHA_INGRESO",
                "pacientes_ficha_ingreso", pacienteId.toString(), "Historia clínica general actualizada",
                SecurityUtils.getClientIp());
        return aDto(guardada);
    }

    private PacienteEntity exigirAcceso(UUID pacienteId) {
        historialCitas.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paciente con ID: " + pacienteId));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());
        return paciente;
    }

    private static FichaIngresoDTO aDto(FichaIngresoEntity e) {
        FichaIngresoDTO dto = e.getDatos() != null ? e.getDatos() : new FichaIngresoDTO();
        dto.setActualizadoEn(e.getActualizadoEn());
        dto.setActualizadoPorNombre(e.getActualizadoPorNombre());
        return dto;
    }
}
