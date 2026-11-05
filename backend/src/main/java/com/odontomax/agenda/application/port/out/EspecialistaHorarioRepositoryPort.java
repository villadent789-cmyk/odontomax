package com.odontomax.agenda.application.port.out;

import com.odontomax.agenda.domain.model.HorarioEspecialista;

import java.util.List;
import java.util.UUID;

public interface EspecialistaHorarioRepositoryPort {
    List<HorarioEspecialista> findByEspecialistaId(UUID especialistaId);
    List<HorarioEspecialista> findByEspecialistaIdAndDiaSemanaAndActivoTrue(UUID especialistaId, int diaSemana);
    List<HorarioEspecialista> findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(UUID especialistaId, String sedeId, int diaSemana);
    void saveAll(List<HorarioEspecialista> horarios);
    void eliminarPorEspecialista(UUID especialistaId);
}
