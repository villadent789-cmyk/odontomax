package com.odontomax.domain.port.out;

import com.odontomax.domain.model.Cita;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitaRepositoryPort {
    Cita save(Cita cita);
    Optional<Cita> findById(UUID id);
    List<Cita> findByFechaAndSede(LocalDate fecha, String sedeId);
    /** Citas entre dos fechas (inclusive), ordenadas por fecha y hora; sedeId null = todas. */
    List<Cita> findByRangoFechas(LocalDate desde, LocalDate hasta, String sedeId);
    List<Cita> findReservasWebPendientes(String sedeId);
    boolean existsColisionDoctor(UUID doctorId, LocalDate fecha, LocalTime hora, UUID excludeCitaId);
    boolean existsColisionSillon(Integer sillonNumero, String sedeId, LocalDate fecha, LocalTime hora, UUID excludeCitaId);
    Optional<Cita> findByCodigoTicket(String codigoTicket);
    long countPendientesWebPorTurno(String sedeId, LocalDate fecha, boolean isManana);
    List<Cita> findByDoctorIdAndFecha(UUID doctorId, LocalDate fecha);
}
