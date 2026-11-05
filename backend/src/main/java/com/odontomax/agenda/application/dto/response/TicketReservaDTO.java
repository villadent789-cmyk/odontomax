package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservaDTO {

    private String codigoTicket;
    private String sedeId;
    private String sedeNombre;
    private LocalDate fecha;
    private String turno;
    private String estado;
    private String especialidad;
    private String mensaje;

    // Campos de adelanto y contacto v2
    private boolean adelantoRequerido;
    private BigDecimal montoAdelanto;
    private String telefonoWhatsappSede;
}
