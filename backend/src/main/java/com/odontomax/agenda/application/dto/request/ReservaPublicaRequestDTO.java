package com.odontomax.agenda.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaPublicaRequestDTO {

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^[0-9]{8,12}$", message = "El DNI o documento debe contener entre 8 y 12 dígitos")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @NotBlank(message = "El teléfono celular es obligatorio")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Ingrese un número de teléfono o WhatsApp válido")
    private String telefono;

    @NotBlank(message = "Debe seleccionar la especialidad o motivo de consulta")
    @JsonAlias({"motivo", "servicio"})
    private String motivoConsulta;

    @NotBlank(message = "Debe indicar la sede deseada")
    @Pattern(regexp = "^sede-[a-z0-9-]{1,60}$", message = "Sede inválida")
    private String sedeId;

    private UUID doctorId;

    private Boolean pacienteNuevo;

    // Sin @FutureOrPresent: usaría el reloj UTC del servidor y rechazaría reservas para "hoy" después de las 19:00 de Lima.
    // Se valida contra la fecha de America/Lima.
    @NotNull(message = "La fecha de preferencia es obligatoria")
    @JsonAlias({"fecha"})
    private LocalDate fechaPreferencia;

    @NotBlank(message = "El turno (MAÑANA o TARDE) es obligatorio")
    @Pattern(regexp = "^(?i)(MANANA|TARDE)$", message = "El turno debe ser MANANA o TARDE")
    private String turno; // "MANANA" o "TARDE"

    /**
     * Campo trampa anti-bots (honeypot): la web lo deja vacío y oculto; una persona nunca lo llena, un bot que rellena todos
     * los campos sí. Si llega con texto, la reserva se rechaza sin registrar nada.
     */
    private String sitioWeb;
}
