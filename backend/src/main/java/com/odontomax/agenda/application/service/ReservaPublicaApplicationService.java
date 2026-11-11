package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.ReservaPublicaRequestDTO;
import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.PacienteReservaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaPublicaApplicationService implements
        RegistrarReservaPublicaUseCase,
        ConsultarReservaPublicaUseCase {

    private final CitaRepositoryPort citaRepository;
    private final PacienteReservaPort pacienteReservaPort;
    private final SedeValidacionPort sedeValidacionPort;

    @Autowired(required = false)
    private EspecialistaRepositoryPort especialistaRepository;

    public void setEspecialistaRepository(EspecialistaRepositoryPort especialistaRepository) {
        this.especialistaRepository = especialistaRepository;
    }

    public static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");
    public static final int MAX_PENDIENTES_WEB_POR_TURNO = 10;
    /** Reservas web sin confirmar que un mismo paciente (DNI) puede tener a la vez. */
    public static final int MAX_RESERVAS_WEB_ACTIVAS_POR_PACIENTE = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TICKET_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Base32 seguro sin ambiguos (0, 1, I, O)

    @Override
    @Transactional
    public TicketReservaDTO registrarReservaPublica(ReservaPublicaRequestDTO dto) {
        // 1. Validar que la fecha no sea pasada calculada con la zona horaria America/Lima
        LocalDate hoyLima = LocalDate.now(ZONE_LIMA);
        if (dto.getFechaPreferencia().isBefore(hoyLima)) {
            throw new IllegalArgumentException("La fecha de reserva no puede ser en el pasado.");
        }

        // 2. Validar disponibilidad: máximo N reservas PENDIENTE_WEB por sede/fecha/turno
        boolean isManana = "MANANA".equalsIgnoreCase(dto.getTurno().trim());
        long pendientesExistentes = citaRepository.countPendientesWebPorTurno(dto.getSedeId().trim(), dto.getFechaPreferencia(), isManana);
        if (pendientesExistentes >= MAX_PENDIENTES_WEB_POR_TURNO) {
            throw new IllegalArgumentException("No hay disponibilidad para nuevas reservas web en el turno seleccionado. Por favor seleccione otra fecha o turno.");
        }

        // 3. Consultar configuración de la sede para el adelanto y validar coherencia con el servicio
        var configSedeOpt = (sedeValidacionPort != null)
                ? sedeValidacionPort.obtenerConfiguracionSede(dto.getSedeId().trim())
                : java.util.Optional.<com.odontomax.agenda.application.port.out.SedeValidacionPort.SedeAgendaConfig>empty();

        if (configSedeOpt.isPresent() && sedeValidacionPort != null && dto.getMotivoConsulta() != null) {
            String negocioSede = configSedeOpt.get().unidadDeNegocio();
            var negocioServicioOpt = sedeValidacionPort.obtenerUnidadDeNegocioDelServicio(dto.getMotivoConsulta().trim());
            if (negocioServicioOpt.isPresent()) {
                String negocioServicio = negocioServicioOpt.get();
                if (!negocioSede.equalsIgnoreCase(negocioServicio)) {
                    throw new IllegalArgumentException(String.format(
                            "Combinación incoherente: el servicio seleccionado pertenece a %s pero la sede pertenece a %s.",
                            negocioServicio, negocioSede
                    ));
                }
            }
        }

        // 4. Determinar si el paciente ya existe para ese negocio (el DNI manda) o si es solicitud nueva
        boolean esNuevo = Boolean.TRUE.equals(dto.getPacienteNuevo())
                || !pacienteReservaPort.existePorDniEnSede(dto.getDni().trim(), dto.getSedeId().trim());

        // Busca o registra al paciente por DNI protegiendo la integridad: si ya existe, se ignora el nombre recibido
        var paciente = pacienteReservaPort.obtenerOCrearPaciente(
                dto.getDni().trim(),
                dto.getNombres().trim(),
                dto.getApellidos().trim(),
                dto.getTelefono().trim(),
                dto.getSedeId().trim()
        );

        // Tope por paciente: con un solo DNI no se puede llenar la agenda de reservas sin confirmar. Si falla, la transacción
        // se deshace (tampoco queda creado el paciente).
        if (citaRepository.countReservasWebActivasDelPaciente(paciente.id(), hoyLima) >= MAX_RESERVAS_WEB_ACTIVAS_POR_PACIENTE) {
            throw new IllegalArgumentException("Ya tienes " + MAX_RESERVAS_WEB_ACTIVAS_POR_PACIENTE
                    + " reservas pendientes de confirmar. Espera a que la clínica se comunique contigo o escríbenos por WhatsApp.");
        }

        // 5. Doctor preferido o sin preferencia
        UUID doctorId = null;
        String doctorNombre = null;
        if (dto.getDoctorId() != null && especialistaRepository != null) {
            var elegido = especialistaRepository.findBySedeIdAndActivoTrue(dto.getSedeId().trim()).stream()
                    .filter(e -> !e.isInvitado() && e.getId().equals(dto.getDoctorId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("El doctor seleccionado no atiende en la sede elegida."));
            doctorId = elegido.getId();
            doctorNombre = elegido.getNombre();
        }

        // 6. Genera un código de ticket único criptográficamente seguro (ej: OM-7K9M2P4X)
        String codigoTicket = generarCodigoTicketSeguro();

        // 7. Hora tentativa según el turno
        LocalTime horaTentativa = isManana ? LocalTime.of(9, 30) : LocalTime.of(16, 0);

        boolean requiereAdelanto = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::requiereAdelanto).orElse(false);
        BigDecimal montoAdelanto = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::montoAdelantoDefecto).orElse(new BigDecimal("30.00"));
        int horasExp = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::horasExpiracionAdelanto).orElse(2);
        String whatsappSede = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::whatsapp).orElse("51960112257");

        String estadoInicial;
        String adelantoEstado;
        OffsetDateTime limitePago;
        String mensajeRetorno;
        boolean adelantoEnTicket;
        BigDecimal adelantoMontoEnTicket;

        if (esNuevo) {
            // Solicitud de paciente nuevo: no fija hora exacta, queda PENDIENTE_WEB para confirmación de recepción
            estadoInicial = "PENDIENTE_WEB";
            adelantoEstado = "NO_APLICA";
            limitePago = null;
            adelantoEnTicket = false;
            adelantoMontoEnTicket = BigDecimal.ZERO;
            mensajeRetorno = "¡Solicitud registrada con éxito! La clínica se comunicará contigo vía WhatsApp para confirmar tu fecha, turno y profesional asignado.";
        } else {
            // Paciente ya registrado: reserva normal con doctor, fecha y turno
            estadoInicial = requiereAdelanto ? "PENDIENTE_DE_PAGO" : "PENDIENTE_WEB";
            adelantoEstado = requiereAdelanto ? "PENDIENTE" : "NO_APLICA";
            limitePago = requiereAdelanto ? OffsetDateTime.now().plusHours(horasExp) : null;
            adelantoEnTicket = requiereAdelanto;
            adelantoMontoEnTicket = requiereAdelanto ? montoAdelanto : BigDecimal.ZERO;
            mensajeRetorno = requiereAdelanto
                    ? String.format("¡Reserva registrada con éxito! Para asegurar tu turno en Villadent, realiza el adelanto de S/ %s vía Yape al número %s y envía tu comprobante dentro de las próximas %d horas.",
                    montoAdelanto, whatsappSede, horasExp)
                    : "¡Reserva registrada con éxito! La clínica se comunicará contigo vía WhatsApp para confirmar tu turno exacto.";
        }

        // 8. Crea la cita con sus políticas
        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .pacienteId(paciente.id())
                .pacienteNombre(paciente.nombreCompleto())
                .pacienteTelefono(paciente.telefono())
                .doctorId(doctorId)
                .doctorNombre(doctorNombre)
                .sedeId(dto.getSedeId().trim())
                .motivoConsulta(dto.getMotivoConsulta())
                .fecha(dto.getFechaPreferencia())
                .hora(horaTentativa)
                .duracionMinutos(30)
                .estado(estadoInicial)
                .codigoTicket(codigoTicket)
                .adelantoRequerido(adelantoEnTicket)
                .adelantoMonto(adelantoMontoEnTicket)
                .adelantoEstado(adelantoEstado)
                .adelantoLimitePago(limitePago)
                .build();

        citaRepository.save(cita);

        String nombreSede = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::nombre).orElse(dto.getSedeId().trim());

        // 9. Respuesta pública segura: NO expone datos privados del paciente ni IDs de usuario
        return TicketReservaDTO.builder()
                .codigoTicket(codigoTicket)
                .sedeId(dto.getSedeId().trim())
                .sedeNombre(nombreSede)
                .fecha(dto.getFechaPreferencia())
                .turno(dto.getTurno().toUpperCase())
                .estado(estadoInicial)
                .especialidad(dto.getMotivoConsulta())
                .mensaje(mensajeRetorno)
                .adelantoRequerido(adelantoEnTicket)
                .montoAdelanto(adelantoMontoEnTicket)
                .telefonoWhatsappSede(whatsappSede)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultaCitaResponseDTO consultarReservaPublica(String ticket, String dni) {
        if (ticket == null || ticket.isBlank() || dni == null || dni.isBlank()) {
            throw new IllegalArgumentException("Debe proporcionar tanto el código de ticket como su número de DNI.");
        }

        Cita cita = citaRepository.findByCodigoTicket(ticket.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ninguna reserva activa con el ticket y DNI proporcionados."));

        var pacienteOpt = pacienteReservaPort.findById(cita.getPacienteId());
        if (pacienteOpt.isEmpty() || !pacienteOpt.get().dni().equalsIgnoreCase(dni.trim())) {
            throw new IllegalArgumentException("No se encontró ninguna reserva activa con el ticket y DNI proporcionados.");
        }

        String nombreSede = (sedeValidacionPort != null)
                ? sedeValidacionPort.obtenerConfiguracionSede(cita.getSedeId())
                        .map(SedeValidacionPort.SedeAgendaConfig::nombre)
                        .orElse(cita.getSedeId())
                : cita.getSedeId();

        return ConsultaCitaResponseDTO.builder()
                .ticket(cita.getCodigoTicket())
                .estado(cita.getEstado())
                .fecha(cita.getFecha())
                .turno(cita.getHora().isBefore(LocalTime.of(13, 0)) ? "MANANA" : "TARDE")
                .sedeId(cita.getSedeId())
                .sedeNombre(nombreSede)
                .build();
    }

    private String generarCodigoTicketSeguro() {
        StringBuilder sb = new StringBuilder("OM-");
        for (int i = 0; i < 8; i++) {
            int index = SECURE_RANDOM.nextInt(TICKET_ALPHABET.length());
            sb.append(TICKET_ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}
