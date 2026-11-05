package com.odontomax.agenda.api;

import com.odontomax.identidad.api.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ConsultaEspecialistasModuloApi {
    Optional<UUID> findEspecialistaIdByUsuarioId(UUID usuarioId);
    boolean existsById(UUID especialistaId);
    Optional<String> findNombreById(UUID especialistaId);

    /** Meta mensual de cobranza y comisión sobre el excedente de un doctor; vacío si el doctor no existe. */
    Optional<MetaDoctor> obtenerMeta(UUID especialistaId);

    /** Ids de los doctores activos asignados a la sede. */
    Set<UUID> idsActivosEnSede(String sedeId);

    /** Meta y porcentaje opcionales; la comisión es nula si falta alguno de los dos. */
    record MetaDoctor(BigDecimal metaMensual, BigDecimal comisionExcedentePorcentaje) {

        /** max(0, cobrado − meta) × porcentaje / 100, o null si no hay meta o no hay porcentaje. */
        public BigDecimal comisionEstimada(BigDecimal cobrado) {
            if (metaMensual == null || comisionExcedentePorcentaje == null) {
                return null;
            }
            BigDecimal excedente = cobrado.subtract(metaMensual).max(BigDecimal.ZERO);
            return excedente.multiply(comisionExcedentePorcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }

    /** Indica si el usuario odontólogo tiene al menos una cita con el paciente (la cita guarda al especialista vinculado a su usuario). */
    boolean odontologoAtiendeAlPaciente(UUID usuarioId, UUID pacienteId);

    /** Devuelve el conjunto de IDs de pacientes con los que el odontólogo tiene al menos una cita. */
    Set<UUID> pacientesDelOdontologo(UUID usuarioId);

    /** Un odontólogo solo accede a pacientes con los que tiene cita; los demás roles pasan (su alcance es por sede). */
    default void exigirAccesoDeOdontologo(UUID pacienteId) {
        var usuario = SecurityUtils.getCurrentUser();
        if (usuario != null && "ODONTOLOGO".equalsIgnoreCase(usuario.rol())
                && !odontologoAtiendeAlPaciente(usuario.id(), pacienteId)) {
            throw new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo.");
        }
    }
}
