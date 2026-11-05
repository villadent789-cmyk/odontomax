package com.odontomax.agenda.infrastructure.persistence.adapter;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.domain.exception.CitaEnUsoException;
import org.springframework.dao.DataIntegrityViolationException;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.infrastructure.persistence.entity.CitaEntity;
import com.odontomax.agenda.infrastructure.persistence.mapper.CitaMapper;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataCitaRepository;
import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary
@RequiredArgsConstructor
public class CitaJpaAdapter implements CitaRepositoryPort, ConsultaCitasModuloApi {

    private final SpringDataCitaRepository repository;
    private final CitaMapper mapper;
    private final PacienteReservaPort pacienteReservaPort;
    private final SpringDataEspecialistaRepository especialistaRepository;
    private final EntityManager entityManager;

    /** Espera maxima por el bloqueo de la fila de una cita antes de responder 409 (R2). */
    static final int ESPERA_MAXIMA_BLOQUEO_MS = 5000;

    private Cita completarNombres(Cita c) {
        if ((c.getPacienteNombre() == null || c.getPacienteNombre().isBlank()) && c.getPacienteId() != null) {
            pacienteReservaPort.findById(c.getPacienteId()).ifPresent(p -> {
                c.setPacienteNombre(p.nombreCompleto().trim());
                if (c.getPacienteTelefono() == null || c.getPacienteTelefono().isBlank()) {
                    c.setPacienteTelefono(p.telefono());
                }
            });
        }
        if ((c.getDoctorNombre() == null || c.getDoctorNombre().isBlank()) && c.getDoctorId() != null) {
            especialistaRepository.findById(c.getDoctorId()).ifPresent(e -> c.setDoctorNombre(e.getNombre()));
        }
        return c;
    }

    @Override
    public Cita save(Cita cita) {
        CitaEntity entity = mapper.toEntity(completarNombres(cita));
        try {
            // saveAndFlush: la restricción EXCLUDE salta aquí y no al confirmar la transacción
            CitaEntity saved = repository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            String detalle = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (detalle.contains("citas_sin_choque_doctor")) {
                throw new CitaColisionException("El especialista ya tiene una cita agendada que se cruza con ese horario.");
            }
            if (detalle.contains("citas_sin_choque_sillon")) {
                throw new CitaColisionException("El sillón ya está ocupado en ese horario.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Cita> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Cita> findByIdParaActualizar(UUID id) {
        // El controller carga la cita antes (para validar la sede) y, con open-in-view, esa copia queda en el contexto de
        // persistencia de la peticion: un SELECT ... FOR UPDATE normal devolveria esa copia vieja aunque otra transaccion
        // ya haya cambiado la fila. refresh con el bloqueo toma el candado Y relee el estado vigente (B-039).
        CitaEntity entidad = entityManager.find(CitaEntity.class, id);
        if (entidad == null) {
            return Optional.empty();
        }
        // PostgreSQL no tiene espera maxima en FOR UPDATE: se fija lock_timeout para esta transaccion (SET LOCAL)
        entityManager.createNativeQuery("SELECT set_config('lock_timeout', :ms, true)")
                .setParameter("ms", ESPERA_MAXIMA_BLOQUEO_MS + "ms").getSingleResult();
        try {
            entityManager.refresh(entidad, LockModeType.PESSIMISTIC_WRITE);
        } catch (PessimisticLockException | LockTimeoutException ex) {
            throw new CitaEnUsoException();
        }
        return Optional.of(mapper.toDomain(entidad));
    }

    @Override
    public void bloquearCapacidadDeSede(String sedeId, LocalDate fecha) {
        // shortcut: el candado asesor no tiene tiempo maximo de espera; vale mientras la transaccion de reserva sea corta
        entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:clave))) AS t")
                .setParameter("clave", "sillones:" + sedeId + ":" + fecha)
                .getSingleResult();
    }

    @Override
    public List<Cita> findByFechaAndSede(LocalDate fecha, String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return repository.findByFechaOrderByHoraAsc(fecha)
                    .stream().filter(c -> SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                    .map(mapper::toDomain).collect(Collectors.toList());
        }
        return repository.findByFechaAndSedeIdOrderByHoraAsc(fecha, sedeId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Cita> findByRangoFechas(LocalDate desde, LocalDate hasta, String sedeId) {
        return repository.findByFechaBetweenAndSedeId(desde, hasta, (sedeId != null && !sedeId.isBlank()) ? sedeId : null)
                .stream()
                .filter(c -> (sedeId != null && !sedeId.isBlank()) || SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                .sorted(Comparator.comparing(CitaEntity::getFecha)
                        .thenComparing(CitaEntity::getHora, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Cita> findReservasWebPendientes(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return repository.findByEstadoOrderByFechaAscHoraAsc("PENDIENTE_WEB")
                    .stream().map(mapper::toDomain).collect(Collectors.toList());
        }
        return repository.findBySedeIdAndEstadoOrderByFechaAscHoraAsc(sedeId, "PENDIENTE_WEB")
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Cita> findOcupantesDoctor(UUID doctorId, LocalDate fecha) {
        return repository.findOcupantesDoctor(doctorId, fecha.minusDays(1), fecha.plusDays(1))
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Cita> findOcupantesSillon(String sedeId, Integer sillonNumero, LocalDate fecha) {
        return repository.findOcupantesSillon(sedeId, sillonNumero, fecha.minusDays(1), fecha.plusDays(1))
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Cita> findByCodigoTicket(String codigoTicket) {
        return repository.findByCodigoTicket(codigoTicket).map(mapper::toDomain);
    }

    @Override
    public long countPendientesWebPorTurno(String sedeId, LocalDate fecha, boolean isManana) {
        return repository.countPendientesWebPorTurno(sedeId, fecha, isManana, LocalTime.of(13, 0));
    }

    @Override
    public long countReservasWebActivasDelPaciente(UUID pacienteId, LocalDate desde) {
        return repository.countReservasWebActivasDelPaciente(pacienteId, desde);
    }

    // --- ConsultaCitasModuloApi (API pública de solo lectura para otros módulos) ---

    @Override
    public Map<UUID, LocalDate> findUltimaAtencionPorPaciente(Collection<UUID> pacienteIds) {
        if (pacienteIds == null || pacienteIds.isEmpty()) return Map.of();
        Map<UUID, LocalDate> resultado = new HashMap<>();
        for (Object[] fila : repository.findUltimaAtencionPorPaciente(pacienteIds)) {
            resultado.put((UUID) fila[0], (LocalDate) fila[1]);
        }
        return resultado;
    }

    @Override
    public List<Cita> findByDoctorIdAndFecha(UUID doctorId, LocalDate fecha) {
        return repository.findByDoctorIdAndFechaAndEstadoNot(doctorId, fecha, "CANCELADA")
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Cita> findCitasPendientesDePago(String sedeId) {
        String sede = (sedeId == null || sedeId.isBlank()) ? null : sedeId.trim();
        return repository.findCitasPendientesDePago(sede)
                .stream()
                .filter(c -> sede != null || SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Cita> findAtendidasByTratamientoId(UUID tratamientoId) {
        return repository.findByTratamientoIdAndEstado(tratamientoId, "ATENDIDA")
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Cita> findCitasVencidasDePago(java.time.OffsetDateTime ahora) {
        return repository.findCitasVencidasDePago(ahora)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<CitaResponseDTO> findByPacienteIdOrderByFechaDesc(UUID pacienteId) {
        return repository.findByPacienteIdOrderByFechaDesc(pacienteId).stream()
                .map(c -> CitaResponseDTO.builder()
                        .id(c.getId())
                        .pacienteId(c.getPacienteId())
                        .pacienteNombre(c.getPacienteNombre())
                        .pacienteTelefono(c.getPacienteTelefono())
                        .doctorId(c.getDoctorId())
                        .doctorNombre(c.getDoctorNombre())
                        .sedeId(c.getSedeId())
                        .sillonNumero(c.getSillonNumero())
                        .motivoConsulta(c.getMotivoConsulta())
                        .fecha(c.getFecha())
                        .hora(c.getHora())
                        .duracionMinutos(c.getDuracionMinutos() != null ? c.getDuracionMinutos() : 30)
                        .estado(c.getEstado())
                        .llegadaEn(c.getLlegadaEn())
                        .inicioAtencionEn(c.getInicioAtencionEn())
                        .finAtencionEn(c.getFinAtencionEn())
                        .motivoCancelacion(c.getMotivoCancelacion())
                        .motivoReprogramacion(c.getMotivoReprogramacion())
                        .notas(c.getNotas())
                        .codigoTicket(c.getCodigoTicket())
                        .build())
                .toList();
    }

    @Override
    public List<CitaResponseDTO> findByRangoFechasYSede(LocalDate desde, LocalDate hasta, String sedeId) {
        String sede = (sedeId == null || sedeId.isBlank()) ? null : sedeId;
        return repository.findByFechaBetweenAndSedeId(desde, hasta, sede).stream()
                .filter(c -> sede != null || SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                .map(c -> CitaResponseDTO.builder()
                        .id(c.getId())
                        .pacienteId(c.getPacienteId())
                        .pacienteNombre(c.getPacienteNombre())
                        .pacienteTelefono(c.getPacienteTelefono())
                        .doctorId(c.getDoctorId())
                        .doctorNombre(c.getDoctorNombre())
                        .sedeId(c.getSedeId())
                        .sillonNumero(c.getSillonNumero())
                        .motivoConsulta(c.getMotivoConsulta())
                        .fecha(c.getFecha())
                        .hora(c.getHora())
                        .duracionMinutos(c.getDuracionMinutos() != null ? c.getDuracionMinutos() : 30)
                        .estado(c.getEstado())
                        .llegadaEn(c.getLlegadaEn())
                        .inicioAtencionEn(c.getInicioAtencionEn())
                        .finAtencionEn(c.getFinAtencionEn())
                        .motivoCancelacion(c.getMotivoCancelacion())
                        .motivoReprogramacion(c.getMotivoReprogramacion())
                        .notas(c.getNotas())
                        .codigoTicket(c.getCodigoTicket())
                        .build())
                .toList();
    }
}
