package com.odontomax.tratamientos.domain.model;

import com.odontomax.tratamientos.domain.exception.TransicionEstadoInvalidaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Modelo de dominio para un plan de tratamiento y su máquina de estados.
 */
public class Tratamiento {

    public static final String ESTADO_PLANIFICADO = "PLANIFICADO";
    public static final String ESTADO_EN_PROCESO = "EN_PROCESO";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_TERMINADO = "TERMINADO";
    public static final String ESTADO_INTERRUMPIDO = "INTERRUMPIDO";
    public static final String ESTADO_CANCELADO = "CANCELADO";

    public static final Set<String> ESTADOS_FINALES = Set.of(
            ESTADO_COMPLETADO, ESTADO_TERMINADO, ESTADO_INTERRUMPIDO, ESTADO_CANCELADO
    );

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            ESTADO_PLANIFICADO, Set.of(ESTADO_EN_PROCESO, ESTADO_COMPLETADO, ESTADO_TERMINADO, ESTADO_INTERRUMPIDO, ESTADO_CANCELADO),
            ESTADO_EN_PROCESO, Set.of(ESTADO_COMPLETADO, ESTADO_TERMINADO, ESTADO_INTERRUMPIDO, ESTADO_CANCELADO)
    );

    private UUID id;
    private UUID pacienteId;
    private String sedeId;
    private UUID especialistaId;
    private String nombre;
    private String estado;
    private Integer totalSesiones;
    private Integer sesionesRealizadas;
    private BigDecimal costoTotal;
    private String motivoCierre;
    private Instant cerradoEn;

    public Tratamiento(UUID id, UUID pacienteId, String sedeId, UUID especialistaId, String nombre,
                       String estado, Integer totalSesiones, Integer sesionesRealizadas,
                       BigDecimal costoTotal, String motivoCierre, Instant cerradoEn) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.sedeId = sedeId;
        this.especialistaId = especialistaId;
        this.nombre = nombre;
        this.estado = estado != null ? estado.toUpperCase().trim() : ESTADO_PLANIFICADO;
        this.totalSesiones = totalSesiones != null ? totalSesiones : 1;
        this.sesionesRealizadas = sesionesRealizadas != null ? sesionesRealizadas : 0;
        this.costoTotal = costoTotal != null ? costoTotal : BigDecimal.ZERO;
        this.motivoCierre = motivoCierre;
        this.cerradoEn = cerradoEn;
    }

    public boolean esEstadoFinal() {
        return ESTADOS_FINALES.contains(this.estado);
    }

    public boolean puedeRegistrarSesion() {
        return !esEstadoFinal();
    }

    /**
     * Valida y ejecuta una transición de estado en la máquina de estados.
     */
    public void cambiarEstado(String destino, String motivo) {
        Objects.requireNonNull(destino, "El estado de destino no puede ser nulo");
        String normalizado = destino.toUpperCase().trim();

        if (normalizado.equals(this.estado)) {
            return;
        }

        if (esEstadoFinal()) {
            throw new TransicionEstadoInvalidaException(
                    String.format("El tratamiento está en estado final '%s' y no admite transiciones a '%s'", this.estado, normalizado)
            );
        }

        Set<String> destinosValidos = TRANSICIONES.get(this.estado);
        if (destinosValidos == null || !destinosValidos.contains(normalizado)) {
            throw new TransicionEstadoInvalidaException(
                    String.format("Transición de estado inválida de '%s' a '%s'", this.estado, normalizado)
            );
        }

        if (ESTADO_INTERRUMPIDO.equals(normalizado)) {
            if (motivo == null || motivo.trim().isBlank()) {
                throw new IllegalArgumentException("Debe ingresar un motivo para interrumpir el tratamiento");
            }
            this.motivoCierre = motivo.trim();
            this.cerradoEn = Instant.now();
        } else if (ESTADO_TERMINADO.equals(normalizado) || ESTADO_COMPLETADO.equals(normalizado)) {
            this.motivoCierre = (motivo != null && !motivo.trim().isBlank())
                    ? motivo.trim()
                    : "Tratamiento concluido con éxito";
            this.cerradoEn = Instant.now();
        } else if (ESTADO_CANCELADO.equals(normalizado)) {
            if (motivo != null && !motivo.trim().isBlank()) {
                this.motivoCierre = motivo.trim();
            }
            this.cerradoEn = Instant.now();
        }

        this.estado = normalizado;
    }

    /**
     * Registra una sesión realizada. Si el saldo es 0 y se completan todas las sesiones,
     * transiciona automáticamente a TERMINADO.
     */
    public void registrarSesion(BigDecimal saldo) {
        if (!puedeRegistrarSesion()) {
            throw new IllegalStateException(
                    String.format("No se pueden registrar sesiones en un tratamiento en estado final '%s'", this.estado)
            );
        }

        if (ESTADO_PLANIFICADO.equals(this.estado)) {
            cambiarEstado(ESTADO_EN_PROCESO, null);
        }

        this.sesionesRealizadas = (this.sesionesRealizadas != null ? this.sesionesRealizadas : 0) + 1;
        verificarCierreAutomatico(saldo);
    }

    /**
     * Cierre automático: saldo 0 Y todas las sesiones planificadas realizadas -> TERMINADO.
     * Si saldo es 0 pero faltan sesiones, no se cierra automáticamente.
     */
    public boolean verificarCierreAutomatico(BigDecimal saldo) {
        if (esEstadoFinal()) {
            return false;
        }
        if (saldo != null && saldo.compareTo(BigDecimal.ZERO) <= 0
                && totalSesiones != null && totalSesiones > 0
                && sesionesRealizadas != null && sesionesRealizadas >= totalSesiones) {
            cambiarEstado(ESTADO_TERMINADO, "Concluido con éxito (todas las sesiones realizadas y pago completado)");
            return true;
        }
        return false;
    }

    public void cerrarTerminado(String motivo) {
        cambiarEstado(ESTADO_TERMINADO, motivo);
    }

    public void cerrarInterrumpido(String motivo) {
        cambiarEstado(ESTADO_INTERRUMPIDO, motivo);
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getPacienteId() { return pacienteId; }
    public String getSedeId() { return sedeId; }
    public UUID getEspecialistaId() { return especialistaId; }
    public String getNombre() { return nombre; }
    public String getEstado() { return estado; }
    public Integer getTotalSesiones() { return totalSesiones; }
    public Integer getSesionesRealizadas() { return sesionesRealizadas; }
    public BigDecimal getCostoTotal() { return costoTotal; }
    public String getMotivoCierre() { return motivoCierre; }
    public Instant getCerradoEn() { return cerradoEn; }
}
