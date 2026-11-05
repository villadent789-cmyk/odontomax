package com.odontomax.agenda.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Paciente que espera un hueco en la agenda. Pasa de ACTIVA a OFRECIDA cuando recepción le ofrece uno. */
public class EsperaHueco {

    public static final String ACTIVA = "ACTIVA";
    public static final String OFRECIDA = "OFRECIDA";
    public static final String AGENDADA = "AGENDADA";
    public static final String CANCELADA = "CANCELADA";

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            ACTIVA, Set.of(OFRECIDA, AGENDADA, CANCELADA),
            OFRECIDA, Set.of(ACTIVA, AGENDADA, CANCELADA));

    private final UUID id;
    private final String sedeId;
    private final UUID pacienteId;
    private final String pacienteNombre;
    private final String pacienteTelefono;
    private final UUID especialistaId;
    private final String motivo;
    private final String notas;
    private String estado;
    private final UUID creadoPor;
    private final Instant creadoEn;

    public EsperaHueco(UUID id, String sedeId, UUID pacienteId, String pacienteNombre, String pacienteTelefono,
                       UUID especialistaId, String motivo, String notas, String estado, UUID creadoPor, Instant creadoEn) {
        if (sedeId == null || sedeId.isBlank()) {
            throw new IllegalArgumentException("La sede es obligatoria.");
        }
        if (pacienteId == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (motivo == null || motivo.trim().length() < 3) {
            throw new IllegalArgumentException("Indica para qué espera el paciente (mínimo 3 caracteres).");
        }
        this.id = id;
        this.sedeId = sedeId;
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.pacienteTelefono = pacienteTelefono;
        this.especialistaId = especialistaId;
        this.motivo = motivo.trim();
        this.notas = notas != null && !notas.isBlank() ? notas.trim() : null;
        this.estado = estado != null ? estado : ACTIVA;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
    }

    public void cambiarEstado(String nuevo) {
        if (!TRANSICIONES.getOrDefault(estado, Set.of()).contains(nuevo)) {
            throw new IllegalArgumentException("No se puede pasar la espera de " + estado + " a " + nuevo + ".");
        }
        this.estado = nuevo;
    }

    /** Sirve para un hueco si no pidió doctor o pidió justo el del hueco. */
    public boolean aceptaDoctor(UUID doctorDelHueco) {
        return especialistaId == null || especialistaId.equals(doctorDelHueco);
    }

    public UUID getId() { return id; }
    public String getSedeId() { return sedeId; }
    public UUID getPacienteId() { return pacienteId; }
    public String getPacienteNombre() { return pacienteNombre; }
    public String getPacienteTelefono() { return pacienteTelefono; }
    public UUID getEspecialistaId() { return especialistaId; }
    public String getMotivo() { return motivo; }
    public String getNotas() { return notas; }
    public String getEstado() { return estado; }
    public UUID getCreadoPor() { return creadoPor; }
    public Instant getCreadoEn() { return creadoEn; }
}
