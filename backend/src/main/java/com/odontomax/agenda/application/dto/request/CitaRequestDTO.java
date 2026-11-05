package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequestDTO {

    @NotNull(message = "El ID del paciente es obligatorio")
    private UUID pacienteId;

    /** Se ignora: el servidor lo rellena a partir de {@code pacienteId} (B-032). Se conserva por compatibilidad del contrato. */
    private String pacienteNombre;
    /** Se ignora: el servidor lo rellena a partir de {@code pacienteId} (B-032). */
    private String pacienteTelefono;

    private UUID doctorId;
    /** Se ignora: el servidor lo rellena a partir de {@code doctorId} (B-032). */
    private String doctorNombre;

    private String sedeId;

    @NotNull(message = "La fecha de la cita es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La hora de la cita es obligatoria")
    private LocalTime hora;

    @Builder.Default
    private Integer duracionMinutos = 30;

    @NotBlank(message = "El motivo de la consulta es obligatorio")
    @Size(max = 500, message = "El motivo de la consulta admite como máximo 500 caracteres")
    private String motivoConsulta;
    private Integer sillonNumero;
    private String notas;

    /** Opcional: plan de tratamiento (del mismo paciente, planificado o en proceso) del que esta cita es una sesión. */
    private UUID tratamientoId;

    /** Opcional: registrar pago o adelanto inicial al momento de agendar la cita. */
    private java.math.BigDecimal montoAdelantoInicial;
    private String medioPagoAdelanto;
    private String numeroOperacionAdelanto;

    /** Doctor externo derivador (Villamax). */
    private UUID doctorDerivadorId;

    /** Indica si es paciente directo en Villamax sin derivación. */
    private Boolean pacienteDirecto;
}
