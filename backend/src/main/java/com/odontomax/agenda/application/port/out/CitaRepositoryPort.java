package com.odontomax.agenda.application.port.out;

import com.odontomax.agenda.domain.model.Cita;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitaRepositoryPort {

    Cita save(Cita cita);

    Optional<Cita> findById(UUID id);

    /**
     * Carga la cita bloqueando su fila hasta el fin de la transaccion (B-039). Toda operacion que cambie el estado o mueva
     * dinero de una cita la carga con este metodo y valida el estado despues de obtener el bloqueo, de modo que dos
     * operaciones simultaneas sobre la misma cita se ejecutan una detras de otra. Debe llamarse dentro de una transaccion.
     */
    Optional<Cita> findByIdParaActualizar(UUID id);

    List<Cita> findByFechaAndSede(LocalDate fecha, String sedeId);

    /** Serializa hasta el fin de la transaccion las reservas de una sede y fecha, para contar sillones sin carreras. */
    void bloquearCapacidadDeSede(String sedeId, LocalDate fecha);

    List<Cita> findByRangoFechas(LocalDate desde, LocalDate hasta, String sedeId);

    List<Cita> findReservasWebPendientes(String sedeId);

    /** Citas que ocupan al especialista el día indicado y los días contiguos (por si cruzan la medianoche). */
    List<Cita> findOcupantesDoctor(UUID doctorId, LocalDate fecha);

    /** Citas que ocupan el sillón de la sede el día indicado y los días contiguos. */
    List<Cita> findOcupantesSillon(String sedeId, Integer sillonNumero, LocalDate fecha);

    Optional<Cita> findByCodigoTicket(String codigoTicket);

    long countPendientesWebPorTurno(String sedeId, LocalDate fecha, boolean isManana);

    /** Reservas web sin confirmar (de hoy en adelante) de un paciente: frena que alguien llene la agenda con un solo DNI. */
    long countReservasWebActivasDelPaciente(UUID pacienteId, LocalDate desde);

    List<Cita> findByDoctorIdAndFecha(UUID doctorId, LocalDate fecha);

    List<Cita> findCitasPendientesDePago(String sedeId);

    /** Citas ATENDIDAS que pertenecen al plan de tratamiento indicado. */
    List<Cita> findAtendidasByTratamientoId(UUID tratamientoId);

    List<Cita> findCitasVencidasDePago(OffsetDateTime ahora);
}
