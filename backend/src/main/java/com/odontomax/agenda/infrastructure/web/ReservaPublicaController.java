package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.dto.request.ConsultaReservaRequestDTO;
import com.odontomax.agenda.application.dto.request.ReservaPublicaRequestDTO;
import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.out.RateLimitPort;
import com.odontomax.identidad.api.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/reservas-publicas")
@RequiredArgsConstructor
public class ReservaPublicaController {

    private final RegistrarReservaPublicaUseCase registrarReservaPublicaUseCase;
    private final ConsultarReservaPublicaUseCase consultarReservaPublicaUseCase;
    private final RateLimitPort rateLimitPort;

    // Rate Limiting anti-abuso: Máximo 5 solicitudes de reserva por cada 10 minutos por IP o Teléfono
    private static final int MAX_RESERVAS_PER_WINDOW = 5;
    private static final int MAX_CONSULTAS_PER_WINDOW = 10;
    // Topes por día: frenan el goteo lento que la ventana de 10 minutos no ve (un bot que manda pocas por tanda).
    private static final int MAX_RESERVAS_POR_IP_AL_DIA = 20;
    private static final int MAX_RESERVAS_POR_TELEFONO_AL_DIA = 8;
    private static final long UN_DIA_EN_SEGUNDOS = 86_400;

    /**
     * Endpoint invocado desde la Landing Page cuando el paciente confirma el Wizard
     */
    @PostMapping
    public ResponseEntity<?> registrarReserva(
            @Valid @RequestBody ReservaPublicaRequestDTO request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = SecurityUtils.getClientIp(servletRequest);

        // Campo trampa: solo un bot lo llena. No se registra nada y la respuesta no explica por qué.
        if (request.getSitioWeb() != null && !request.getSitioWeb().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", 400, "error", "Bad Request", "mensaje", "No se pudo procesar la solicitud."));
        }

        // Se limita por IP y por Teléfono (NO por DNI, para evitar que terceros bloqueen a un paciente)
        if (rateLimitPort.isRateLimited("reserva_ip:" + clientIp, MAX_RESERVAS_PER_WINDOW)
                || rateLimitPort.isRateLimited("reserva_tel:" + request.getTelefono().trim(), MAX_RESERVAS_PER_WINDOW)
                || rateLimitPort.isRateLimited("reserva_ip_dia:" + clientIp, MAX_RESERVAS_POR_IP_AL_DIA, UN_DIA_EN_SEGUNDOS)
                || rateLimitPort.isRateLimited("reserva_tel_dia:" + request.getTelefono().trim(), MAX_RESERVAS_POR_TELEFONO_AL_DIA, UN_DIA_EN_SEGUNDOS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "status", 429,
                            "error", "Too Many Requests",
                            "mensaje", "Ha superado el límite de intentos de reserva. Por favor espere unos minutos o contáctenos por WhatsApp."
                    ));
        }

        TicketReservaDTO ticket = registrarReservaPublicaUseCase.registrarReservaPublica(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    /**
     * Endpoint público para consultar el estado de una cita médica
     * Exige obligatoriamente código de ticket y DNI coincidente en el cuerpo (POST).
     * Evita filtrar DNI en logs o query strings.
     */
    @PostMapping("/consulta")
    public ResponseEntity<?> consultarCita(
            @Valid @RequestBody ConsultaReservaRequestDTO request,
            HttpServletRequest servletRequest
    ) {
        String ticket = request.getTicket().trim();
        String dni = request.getDni().trim();

        String clientIp = SecurityUtils.getClientIp(servletRequest);
        if (rateLimitPort.isRateLimited("consulta_ip:" + clientIp, MAX_CONSULTAS_PER_WINDOW)
                || rateLimitPort.isRateLimited("consulta_tkt:" + ticket.toUpperCase(), MAX_CONSULTAS_PER_WINDOW)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "status", 429,
                            "error", "Too Many Requests",
                            "mensaje", "Ha superado el límite de consultas permitidas. Intente más tarde."
                    ));
        }

        ConsultaCitaResponseDTO resultado = consultarReservaPublicaUseCase.consultarReservaPublica(ticket, dni);
        return ResponseEntity.ok(resultado);
    }
}
