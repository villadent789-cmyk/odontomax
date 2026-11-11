package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.SillonEspecialistaFijoException;
import com.odontomax.agenda.domain.exception.SillonNoAsignadoException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Especialista {
    public static final String ESPECIALIDAD_POR_DEFECTO = "Odontología General";
    // Horario de atención Villadent (V24): lunes a domingo de 09:00 a 20:00.
    public static final List<Integer> DIAS_HABITUALES = List.of(1, 2, 3, 4, 5, 6, 7);
    public static final LocalTime INICIO_HABITUAL = LocalTime.of(9, 0);
    public static final LocalTime FIN_HABITUAL = LocalTime.of(20, 0);

    private UUID id;
    private UUID usuarioId;
    private String nombre;
    private String especialidad;
    private String cop;
    private String email;
    private String telefono;
    private String fotoUrl;
    private boolean activo;
    /** Viene por cita previa: sin cuenta, sin horario ni sillón fijo (se agenda en cualquier hueco y sillón libre). */
    private boolean invitado;
    private BigDecimal metaMensual;
    private BigDecimal comisionExcedentePorcentaje;

    @Builder.Default
    private List<String> sedes = new ArrayList<>();

    @Builder.Default
    private Map<String, Integer> sillonesPorSede = new TreeMap<>();

    @Builder.Default
    private List<HorarioEspecialista> horarios = new ArrayList<>();

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void vincularUsuario(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** Define la meta mensual de cobranza y el porcentaje de comisión sobre el excedente; ambos son opcionales. */
    public void definirMeta(BigDecimal meta, BigDecimal porcentajeComision) {
        if (meta != null && meta.signum() < 0) {
            throw new IllegalArgumentException("La meta mensual no puede ser negativa.");
        }
        if (porcentajeComision != null
                && (porcentajeComision.signum() < 0 || porcentajeComision.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("La comisión debe estar entre 0 y 100 por ciento.");
        }
        this.metaMensual = meta;
        this.comisionExcedentePorcentaje = porcentajeComision;
    }

    public Integer obtenerSillonEnSede(String sedeId) {
        return sillonesPorSede.get(sedeId);
    }

    public void asignarSillonEnSede(String sedeId, Integer sillonNumero, SedeAgenda sede) {
        if (!sedes.contains(sedeId)) {
            throw new IllegalArgumentException("El especialista no está asignado a la sede '" + sedeId + "'.");
        }
        if (!sede.id().equals(sedeId)) {
            throw new IllegalArgumentException("La capacidad indicada no corresponde a la sede '" + sedeId + "'.");
        }
        sede.verificarAdmiteCita(sillonNumero);
        if (sillonNumero == null) {
            sillonesPorSede.remove(sedeId);
        } else {
            sillonesPorSede.put(sedeId, sillonNumero);
        }
    }

    public Integer resolverSillonCita(String sedeId, Integer sillonSolicitado) {
        Integer sillonFijo = obtenerSillonEnSede(sedeId);
        if (sillonFijo == null) {
            throw new SillonNoAsignadoException();
        }
        if (sillonSolicitado != null && !sillonFijo.equals(sillonSolicitado)) {
            throw new SillonEspecialistaFijoException("El especialista tiene asignado el sillón #" + sillonFijo
                    + " en la sede '" + sedeId + "'.");
        }
        return sillonFijo;
    }

    public void reemplazarHorarios(List<HorarioEspecialista> nuevosHorarios) {
        List<HorarioEspecialista> lista = nuevosHorarios != null ? nuevosHorarios : Collections.emptyList();
        Set<String> clavesVistas = new HashSet<>();

        for (HorarioEspecialista h : lista) {
            String sede = h.getSedeId() != null ? h.getSedeId().trim() : "";
            if (!this.sedes.contains(sede)) {
                throw new IllegalArgumentException("El especialista no está asignado a la sede '" + sede + "'. Asígnele la sede antes de definir su horario.");
            }
            if (h.getDiaSemana() == null || h.getDiaSemana() < 1 || h.getDiaSemana() > 7) {
                throw new IllegalArgumentException("El día debe estar entre 1 (lunes) y 7 (domingo).");
            }
            if (h.getHoraInicio() == null || h.getHoraFin() == null || !h.getHoraFin().isAfter(h.getHoraInicio())) {
                throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
            }
            if (!clavesVistas.add(sede + "#" + h.getDiaSemana())) {
                throw new IllegalArgumentException("Solo se permite un bloque horario por sede y día.");
            }
        }
        this.horarios = new ArrayList<>(lista);
    }

    public static List<HorarioEspecialista> generarHorariosHabituales(UUID especialistaId, String sedeId) {
        return DIAS_HABITUALES.stream()
                .map(dia -> HorarioEspecialista.builder()
                        .especialistaId(especialistaId)
                        .sedeId(sedeId)
                        .diaSemana(dia)
                        .horaInicio(INICIO_HABITUAL)
                        .horaFin(FIN_HABITUAL)
                        .activo(true)
                        .build())
                .toList();
    }
}
