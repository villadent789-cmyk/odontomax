package com.odontomax.pacientes.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteRequestDTO {

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe tener exactamente 8 dígitos numéricos")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
    private LocalDate fechaNacimiento;

    @Pattern(regexp = "^(9\\d{8})?$", message = "El celular debe tener 9 dígitos y empezar con 9")
    private String telefono;

    private String email;

    private String sedeId;

    @Pattern(regexp = "^(MASCULINO|FEMENINO|OTRO)?$", message = "El género debe ser MASCULINO, FEMENINO u OTRO")
    private String genero;

    @Pattern(regexp = "^(FACEBOOK|INSTAGRAM|TIKTOK|GOOGLE|REFERIDO_PACIENTE|REFERIDO_DOCTOR|LOCAL|OTRO)?$",
            message = "Fuente de captación no válida")
    private String fuenteCaptacion;

    private java.util.UUID referidoPorPacienteId;

    private String referidoPorPacienteNombre;

    private String doctorReferidor;

    private boolean consentimientoPromociones;

    private String numeroHistoriaClinica;

    private List<String> alergias;

    private String antecedentesMedicos;

    private boolean gestante;

    private Integer semanasGestacion;
}
