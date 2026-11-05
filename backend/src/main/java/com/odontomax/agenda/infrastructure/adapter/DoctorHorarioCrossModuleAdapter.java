package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.DoctorHorarioConsultaPort;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaHorarioEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaHorarioRepository;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// TODO: En Bloque B este adaptador se convertirá en llamadas directas a los repositorios de especialistas del módulo agenda.
@Component
@RequiredArgsConstructor
public class DoctorHorarioCrossModuleAdapter implements DoctorHorarioConsultaPort {

    private final SpringDataEspecialistaRepository especialistaRepository;
    private final SpringDataEspecialistaHorarioRepository especialistaHorarioRepository;

    @Override
    public boolean esDoctorActivo(UUID doctorId) {
        if (doctorId == null) return true;
        return especialistaRepository.findById(doctorId)
                .map(EspecialistaEntity::isActivo)
                .orElse(true);
    }

    @Override
    public String obtenerNombreDoctor(UUID doctorId) {
        if (doctorId == null) return null;
        return especialistaRepository.findById(doctorId)
                .map(EspecialistaEntity::getNombre)
                .orElse(null);
    }

    @Override
    public boolean atiendeEnHorarioYSede(UUID doctorId, String sedeId, LocalDate fecha, LocalTime hora, int duracionMinutos) {
        if (doctorId == null) return true;
        List<EspecialistaHorarioEntity> horariosDoc = especialistaHorarioRepository.findByEspecialistaIdAndActivoTrue(doctorId);
        if (horariosDoc.isEmpty()) {
            return true;
        }
        int diaSemana = fecha.getDayOfWeek().getValue();
        LocalTime finEstimado = hora.plusMinutes(duracionMinutos > 0 ? duracionMinutos : 30);
        return horariosDoc.stream().anyMatch(h ->
                h.getDiaSemana() == diaSemana &&
                (sedeId == null || h.getSedeId().equalsIgnoreCase(sedeId)) &&
                !hora.isBefore(h.getHoraInicio()) &&
                !finEstimado.isAfter(h.getHoraFin())
        );
    }
}
