package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.request.RegistrarAdelantoRequestDTO;
import com.odontomax.agenda.application.dto.request.RegistrarContactoRequestDTO;
import com.odontomax.agenda.application.dto.response.BandejaTareasResponseDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.dto.response.ItemTareaBandejaDTO;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.application.port.out.*;
import com.odontomax.agenda.domain.event.AdelantoRegistradoEvent;
import com.odontomax.agenda.domain.event.CitaCanceladaEvent;
import com.odontomax.agenda.domain.event.CitaConfirmadaEvent;
import com.odontomax.agenda.domain.event.CitaReprogramadaEvent;
import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.domain.exception.SillonNoAsignadoException;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.application.port.out.CitaContactoPort;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CitaApplicationService implements
        AgendarCitaUseCase,
        ReprogramarCitaUseCase,
        CancelarCitaUseCase,
        CambiarEstadoCitaUseCase,
        IniciarAtencionCitaUseCase,
        ConsultarCitaUseCase,
        RegistrarAdelantoUseCase,
        RegistrarContactoUseCase,
        ConsultarBandejaTareasUseCase,
        ProcesarExpiracionUseCase {

    private final CitaRepositoryPort citaRepository;
    private final DoctorHorarioConsultaPort doctorHorarioPort;
    private final RegistroAuditoriaPort auditoriaPort;
    private final SedeValidacionPort sedeValidacionPort;
    private final PagoAdelantoPort pagoAdelantoPort;
    private final SaldoFavorPort saldoFavorPort;
    private final PacienteReservaPort pacienteReservaPort;
    private final CitaContactoPort citaContactoPort;
    private final ApplicationEventPublisher eventPublisher;
    private final TratamientoCitaPort tratamientoCitaPort;
    private final EspecialistaRepositoryPort especialistaRepository;
    private Clock clock = Clock.system(ZONE_LIMA);
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ComisionCitaPort comisionCitaPort;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.odontomax.agenda.application.port.out.CuotasVencidasPort cuotasVencidasPort;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.odontomax.agenda.application.port.out.EsperaHuecoRepositoryPort esperaHuecoRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.odontomax.agenda.application.port.out.ConsumoInsumosCitaPort consumoInsumosCitaPort;

    public void setConsumoInsumosCitaPort(com.odontomax.agenda.application.port.out.ConsumoInsumosCitaPort consumoInsumosCitaPort) {
        this.consumoInsumosCitaPort = consumoInsumosCitaPort;
    }

    public void setEsperaHuecoRepository(com.odontomax.agenda.application.port.out.EsperaHuecoRepositoryPort esperaHuecoRepository) {
        this.esperaHuecoRepository = esperaHuecoRepository;
    }

    public void setCuotasVencidasPort(com.odontomax.agenda.application.port.out.CuotasVencidasPort cuotasVencidasPort) {
        this.cuotasVencidasPort = cuotasVencidasPort;
    }

    public void setComisionCitaPort(ComisionCitaPort comisionCitaPort) {
        this.comisionCitaPort = comisionCitaPort;
    }

    public static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");
    static final int MAX_DIAS_RANGO_AGENDA = 400;

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public CitaResponseDTO agendarCita(CitaRequestDTO request, String sedeEfectiva) {
        if (sedeEfectiva == null || sedeEfectiva.isBlank()) {
            throw new IllegalArgumentException("Debe especificar una sede válida para agendar la cita.");
        }

        String doctorNombre = null;
        if (request.getDoctorId() != null) {
            doctorNombre = doctorHorarioPort.obtenerNombreDoctor(request.getDoctorId());
            if (doctorNombre == null) {
                throw new IllegalArgumentException("El especialista indicado no existe.");
            }
        }

        int duracion = request.getDuracionMinutos() != null ? request.getDuracionMinutos() : 30;
        Integer sillonNumero = resolverSillonCita(request.getDoctorId(), sedeEfectiva, request.getSillonNumero(),
                request.getFecha(), request.getHora(), duracion, null);
        // B-023: sede activa y sillón dentro de 1..sillones (una sede inexistente la sigue rechazando la FK, 409)
        sedeValidacionPort.obtenerSede(sedeEfectiva)
                .ifPresent(sede -> sede.verificarAdmiteCita(sillonNumero));


        boolean esVillamax = sedeValidacionPort.obtenerConfiguracionSede(sedeEfectiva)
                .map(c -> "VILLAMAX".equalsIgnoreCase(c.unidadDeNegocio()))
                .orElseGet(() -> sedeEfectiva.toLowerCase().contains("villamax"));
        if (esVillamax) {
            boolean directo = Boolean.TRUE.equals(request.getPacienteDirecto());
            if (!directo && request.getDoctorDerivadorId() == null) {
                throw new IllegalArgumentException("En Villamax el doctor derivador es obligatorio, o seleccione 'Paciente directo, sin derivación'.");
            }
        }

        // B-032: los datos del paciente y del especialista salen de sus registros; lo que envíe el cliente se ignora
        var paciente = pacienteReservaPort.findById(request.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("El paciente indicado no existe."));
        UUID tratamientoId = validarTratamientoVinculado(request.getTratamientoId(), request.getPacienteId());

        if (request.getDoctorId() != null) {
            validarDoctorDisponible(request.getDoctorId(), sedeEfectiva, request.getFecha(), request.getHora(), duracion);

            verificarDoctorLibre(request.getDoctorId(), request.getFecha(), request.getHora(), duracion, null,
                    "El doctor ya tiene una cita agendada en esa fecha y hora");
        }

        verificarCapacidadSillonesSede(sedeEfectiva, request.getFecha(), request.getHora(), duracion, null);

        if (sillonNumero != null) {
            verificarSillonLibre(sedeEfectiva, sillonNumero, request.getFecha(), request.getHora(), duracion, null);
        }

        // Configuración de sede para políticas de adelanto (Sección 4 de especificaciones)
        var configSedeOpt = sedeValidacionPort.obtenerConfiguracionSede(sedeEfectiva);
        boolean sedeRequiereAdelanto = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::requiereAdelanto).orElse(true);
        BigDecimal montoDefecto = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::montoAdelantoDefecto)
                .orElse(new BigDecimal("30.00"));
        int horasExpiracion = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::horasExpiracionAdelanto).orElse(2);

        String estadoInicial;
        boolean adelantoRequerido;
        BigDecimal montoAdelanto;
        String adelantoEstado;
        String adelantoMedioPago = null;
        String adelantoNumeroOperacion = null;
        OffsetDateTime adelantoRegistradoEn = null;
        String adelantoRegistradoPor = null;
        OffsetDateTime adelantoLimitePago = null;
        String estadoConfirmacion = "SIN_CONTACTAR";

        BigDecimal montoInicial = request.getMontoAdelantoInicial();
        boolean adelantoPagadoAlCrear = montoInicial != null && montoInicial.compareTo(BigDecimal.ZERO) > 0;
        String usuarioActual = SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().nombre() : "RECEPCION";

        if (adelantoPagadoAlCrear) {
            if (tratamientoId == null && request.getDoctorId() != null) {
                tratamientoId = tratamientoCitaPort.crearPlanParaAdelanto(
                        request.getPacienteId(), sedeEfectiva, request.getDoctorId(), request.getMotivoConsulta(), montoInicial);
            }
            estadoInicial = "CONFIRMADA";
            adelantoRequerido = true;
            montoAdelanto = montoInicial;
            adelantoEstado = "CONFIRMADO";
            adelantoMedioPago = (request.getMedioPagoAdelanto() != null && !request.getMedioPagoAdelanto().isBlank())
                    ? request.getMedioPagoAdelanto().toUpperCase().trim() : "EFECTIVO";
            adelantoNumeroOperacion = request.getNumeroOperacionAdelanto();
            adelantoRegistradoEn = OffsetDateTime.now();
            adelantoRegistradoPor = usuarioActual;
            estadoConfirmacion = "CONFIRMADA";
        } else if (!sedeRequiereAdelanto) {
            // Villamax: pago en atención, no cobra adelanto
            estadoInicial = "PROGRAMADA";
            adelantoRequerido = false;
            montoAdelanto = BigDecimal.ZERO;
            adelantoEstado = "NO_APLICA";
        } else {
            // Villadent: adelanto obligatorio
            adelantoRequerido = true;
            montoAdelanto = montoDefecto;
            estadoInicial = "PENDIENTE_DE_PAGO";
            adelantoEstado = "PENDIENTE";
            adelantoLimitePago = OffsetDateTime.now().plusHours(horasExpiracion);
        }

        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .pacienteId(request.getPacienteId())
                .pacienteNombre(paciente.nombreCompleto())
                .pacienteTelefono(paciente.telefono())
                .doctorId(request.getDoctorId())
                .doctorNombre(doctorNombre)
                .sedeId(sedeEfectiva)
                .fecha(request.getFecha())
                .hora(request.getHora())
                .duracionMinutos(duracion)
                .motivoConsulta(request.getMotivoConsulta())
                .sillonNumero(sillonNumero)
                .notas(request.getNotas())
                .estado(estadoInicial)
                .adelantoRequerido(adelantoRequerido)
                .adelantoMonto(montoAdelanto)
                .adelantoEstado(adelantoEstado)
                .adelantoMedioPago(adelantoMedioPago)
                .adelantoNumeroOperacion(adelantoNumeroOperacion)
                .adelantoRegistradoEn(adelantoRegistradoEn)
                .adelantoRegistradoPor(adelantoRegistradoPor)
                .adelantoLimitePago(adelantoLimitePago)
                .estadoConfirmacion(estadoConfirmacion)
                .tratamientoId(tratamientoId)
                .doctorDerivadorId(request.getDoctorDerivadorId())
                .pacienteDirecto(Boolean.TRUE.equals(request.getPacienteDirecto()))
                .build();

        Cita guardada = citaRepository.save(cita);

        if (adelantoPagadoAlCrear) {
            pagoAdelantoPort.registrarAdelanto(
                    guardada.getId(),
                    guardada.getPacienteId(),
                    guardada.getTratamientoId(),
                    guardada.getSedeId(),
                    montoInicial,
                    adelantoMedioPago,
                    adelantoNumeroOperacion,
                    usuarioActual
            );
            eventPublisher.publishEvent(new AdelantoRegistradoEvent(
                    guardada.getId(),
                    guardada.getPacienteId(),
                    guardada.getSedeId(),
                    montoInicial,
                    adelantoMedioPago,
                    adelantoNumeroOperacion,
                    usuarioActual,
                    OffsetDateTime.now()
            ));
            eventPublisher.publishEvent(new CitaConfirmadaEvent(
                    guardada.getId(),
                    guardada.getPacienteId(),
                    guardada.getPacienteNombre(),
                    guardada.getPacienteTelefono(),
                    guardada.getSedeId(),
                    guardada.getFecha(),
                    guardada.getHora(),
                    true,
                    montoInicial,
                    OffsetDateTime.now()
            ));
        }

        return CitaResponseDTO.fromDomain(guardada);
    }

    /** Si la cita es una sesión de un plan: el plan existe, es del mismo paciente y sigue abierto. */
    private UUID validarTratamientoVinculado(UUID tratamientoId, UUID pacienteId) {
        if (tratamientoId == null) {
            return null;
        }
        TratamientoCitaPort.TratamientoVinculable plan = tratamientoCitaPort.buscar(tratamientoId)
                .orElseThrow(() -> new IllegalArgumentException("El tratamiento indicado para la cita no existe."));
        if (!plan.pacienteId().equals(pacienteId)) {
            throw new IllegalArgumentException("El tratamiento indicado no pertenece al paciente de la cita.");
        }
        if (!plan.admiteNuevasSesiones()) {
            throw new IllegalArgumentException("El tratamiento '" + plan.nombre() + "' ya no admite nuevas sesiones (estado "
                    + plan.estado() + ").");
        }
        return plan.id();
    }

    private Integer resolverSillonCita(UUID especialistaId, String sedeId, Integer sillonSolicitado,
                                       LocalDate fecha, LocalTime hora, int duracion, UUID excluirCitaId) {
        if (especialistaId == null) {
            return sillonSolicitado;
        }
        var especialista = especialistaRepository.findById(especialistaId).orElseThrow(SillonNoAsignadoException::new);
        if (especialista.isInvitado()) {
            return sillonLibreParaInvitado(sedeId, sillonSolicitado, fecha, hora, duracion, excluirCitaId);
        }
        return especialista.resolverSillonCita(sedeId, sillonSolicitado);
    }

    /** Un doctor invitado no tiene sillón fijo: usa el sillón pedido o, si no, el primero libre de la sede a esa hora. */
    private Integer sillonLibreParaInvitado(String sedeId, Integer solicitado, LocalDate fecha, LocalTime hora, int duracion, UUID excluirCitaId) {
        if (solicitado != null) {
            return solicitado;
        }
        int sillones = sedeValidacionPort.obtenerSede(sedeId).map(com.odontomax.agenda.domain.model.SedeAgenda::sillones).orElse(0);
        for (int numero = 1; numero <= sillones; numero++) {
            final int n = numero;
            boolean ocupado = citaRepository.findOcupantesSillon(sedeId, n, fecha).stream()
                    .anyMatch(c -> c.ocupaAgenda() && !Objects.equals(c.getId(), excluirCitaId) && c.solapaCon(fecha, hora, duracion));
            if (!ocupado) {
                return n;
            }
        }
        throw new CitaColisionException("No hay un sillón libre en la sede para ese horario.");
    }

    @Override
    @Transactional
    public CitaResponseDTO registrarAdelanto(UUID citaId, RegistrarAdelantoRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Debe proporcionar los datos del pago de adelanto.");
        }

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));

        SecurityUtils.validarAccesoSede(cita.getSedeId());

        String usuario = SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().nombre() : "RECEPCION";

        cita.confirmarConAdelanto(
                request.getMedioPago(),
                request.getMonto(),
                request.getNumeroOperacion(),
                request.getComprobanteUrl(),
                usuario
        );

        if (cita.getTratamientoId() == null && cita.getDoctorId() != null) {
            cita.setTratamientoId(tratamientoCitaPort.crearPlanParaAdelanto(
                    cita.getPacienteId(), cita.getSedeId(), cita.getDoctorId(), cita.getMotivoConsulta(), request.getMonto()));
        }

        Cita guardada = citaRepository.save(cita);

        // Notificar al puerto de pagos / finanzas
        pagoAdelantoPort.registrarAdelanto(
                cita.getId(),
                cita.getPacienteId(),
                cita.getTratamientoId(),
                cita.getSedeId(),
                request.getMonto(),
                request.getMedioPago(),
                request.getNumeroOperacion(),
                usuario
        );

        // Eventos de dominio para el transactional outbox (Spring Modulith)
        eventPublisher.publishEvent(new AdelantoRegistradoEvent(
                cita.getId(),
                cita.getPacienteId(),
                cita.getSedeId(),
                request.getMonto(),
                request.getMedioPago(),
                request.getNumeroOperacion(),
                usuario,
                OffsetDateTime.now()
        ));

        eventPublisher.publishEvent(new CitaConfirmadaEvent(
                cita.getId(),
                cita.getPacienteId(),
                cita.getPacienteNombre(),
                cita.getPacienteTelefono(),
                cita.getSedeId(),
                cita.getFecha(),
                cita.getHora(),
                true,
                request.getMonto(),
                OffsetDateTime.now()
        ));

        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public CitaResponseDTO registrarContacto(UUID citaId, RegistrarContactoRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Debe proporcionar los datos del contacto.");
        }

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));

        SecurityUtils.validarAccesoSede(cita.getSedeId());

        String usuario = SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().nombre() : "RECEPCION";

        // Registrar en tabla histórica de contactos
        citaContactoPort.registrar(cita.getId(), request.getMedio().toUpperCase().trim(), request.getResultado().toUpperCase().trim(), request.getNotas(), usuario);

        // Actualizar estado de la cita
        cita.registrarContacto(request.getMedio(), request.getResultado(), usuario, request.getNotas());
        Cita guardada = citaRepository.save(cita);

        if ("CONFIRMO".equalsIgnoreCase(request.getResultado())) {
            eventPublisher.publishEvent(new CitaConfirmadaEvent(
                    cita.getId(),
                    cita.getPacienteId(),
                    cita.getPacienteNombre(),
                    cita.getPacienteTelefono(),
                    cita.getSedeId(),
                    cita.getFecha(),
                    cita.getHora(),
                    "PAGADO".equalsIgnoreCase(cita.getAdelantoEstado()),
                    cita.getAdelantoMonto(),
                    OffsetDateTime.now()
            ));
        }

        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public CitaResponseDTO reprogramarCita(UUID citaId, CitaReprogramarRequestDTO request) {
        if (request == null || request.getNuevaFecha() == null || request.getNuevoHorario() == null) {
            throw new IllegalArgumentException("Debe especificar la nueva fecha y el nuevo horario.");
        }
        if (request.getMotivo() == null || request.getMotivo().trim().length() < 5) {
            throw new IllegalArgumentException("El motivo de reprogramación debe tener al menos 5 caracteres.");
        }
        denegarSiEsOdontologo("reprogramar");

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));

        SecurityUtils.validarAccesoSede(cita.getSedeId());

        if ("CANCELADA".equalsIgnoreCase(cita.getEstado()) || "ATENDIDA".equalsIgnoreCase(cita.getEstado()) || "EXPIRADA".equalsIgnoreCase(cita.getEstado())) {
            throw new IllegalArgumentException("No se puede reprogramar una cita con estado " + cita.getEstado());
        }

        int duracion = cita.getDuracionMinutos() > 0 ? cita.getDuracionMinutos() : 30;
        Integer sillonFijo = resolverSillonCita(cita.getDoctorId(), cita.getSedeId(), null,
                request.getNuevaFecha(), request.getNuevoHorario(), duracion, cita.getId());
        Integer sillonNumero = sillonFijo != null ? sillonFijo : cita.getSillonNumero();

        if (cita.getDoctorId() != null) {
            validarDoctorDisponible(cita.getDoctorId(), cita.getSedeId(), request.getNuevaFecha(), request.getNuevoHorario(), duracion);

            verificarDoctorLibre(cita.getDoctorId(), request.getNuevaFecha(), request.getNuevoHorario(), duracion, cita.getId(),
                    "El doctor ya tiene una cita agendada en la nueva fecha y horario");
        }

        verificarCapacidadSillonesSede(cita.getSedeId(), request.getNuevaFecha(), request.getNuevoHorario(), duracion, cita.getId());

        if (sillonFijo != null) {
            sedeValidacionPort.obtenerSede(cita.getSedeId())
                    .ifPresent(sede -> sede.verificarAdmiteCita(sillonFijo));
        }

        if (sillonNumero != null) {
            verificarSillonLibre(cita.getSedeId(), sillonNumero, request.getNuevaFecha(), request.getNuevoHorario(),
                    duracion, cita.getId());
        }

        LocalDate fechaAnterior = cita.getFecha();
        LocalTime horaAnterior = cita.getHora();

        var user = SecurityUtils.getCurrentUser();
        String usuario = user != null ? user.nombre() : "RECEPCION";
        cita.setSillonNumero(sillonNumero);
        cita.reprogramar(request.getNuevaFecha(), request.getNuevoHorario(), request.getMotivo(), usuario);
        Cita guardada = citaRepository.save(cita);

        auditoriaPort.registrar(
                "REPROGRAMAR_CITA",
                "CITAS",
                cita.getSedeId(),
                "Cita " + cita.getId() + " reprogramada de " + fechaAnterior + " " + horaAnterior +
                        " a " + request.getNuevaFecha() + " " + request.getNuevoHorario() +
                        ". Motivo: " + request.getMotivo().trim()
        );

        eventPublisher.publishEvent(new CitaReprogramadaEvent(
                cita.getId(),
                cita.getPacienteId(),
                cita.getPacienteNombre(),
                cita.getPacienteTelefono(),
                cita.getSedeId(),
                fechaAnterior,
                horaAnterior,
                request.getNuevaFecha(),
                request.getNuevoHorario(),
                request.getMotivo(),
                OffsetDateTime.now()
        ));

        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public CitaResponseDTO cancelarCita(UUID citaId, CitaCancelarRequestDTO request) {
        denegarSiEsOdontologo("cancelar");
        String motivo = request != null ? request.getMotivo() : null;
        String motivoLimpio = motivo != null ? motivo.trim() : "";
        if (motivoLimpio.length() < 5) {
            throw new IllegalArgumentException("Debe indicar el motivo de la cancelación (mínimo 5 caracteres).");
        }

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));

        SecurityUtils.validarAccesoSede(cita.getSedeId());
        // B-028: se valida el estado antes de mover dinero (saldo a favor / adelanto perdido)
        cita.validarCancelable();

        // Política de cancelación (Sección 4 de especificaciones):
        // Cancela con >= 24h: adelanto pasa a saldo a favor.
        // Cancela con < 24h: pierde el adelanto (a menos que ADMIN_GENERAL autorice excepción).
        boolean saldoAFavor = false;
        if ("PAGADO".equalsIgnoreCase(cita.getAdelantoEstado()) && cita.getAdelantoMonto().compareTo(BigDecimal.ZERO) > 0) {
            var configSede = sedeValidacionPort.obtenerConfiguracionSede(cita.getSedeId());
            int horasLimite = configSede.map(SedeValidacionPort.SedeAgendaConfig::horasAnticipacionCancelacion).orElse(24);

            LocalDateTime fechaHoraCita = LocalDateTime.of(cita.getFecha(), cita.getHora());
            long horasAnticipacion = ChronoUnit.HOURS.between(LocalDateTime.now(ZONE_LIMA), fechaHoraCita);

            var user = SecurityUtils.getCurrentUser();
            boolean esExcepcionAdmin = Boolean.TRUE.equals(request != null ? request.getExcepcionAdministrador() : null)
                    && user != null && "ADMIN_GENERAL".equalsIgnoreCase(user.rol());

            if (horasAnticipacion >= horasLimite || esExcepcionAdmin) {
                saldoAFavor = true;
                cita.marcarSaldoFavor();
                saldoFavorPort.acreditarSaldoFavor(
                        cita.getPacienteId(),
                        cita.getId(),
                        cita.getAdelantoMonto(),
                        "Cancelación anticipada: " + motivoLimpio + (esExcepcionAdmin ? " [Excepción Admin]" : "")
                );
            } else {
                cita.marcarAdelantoPerdido();
            }
        }

        var currentUser = SecurityUtils.getCurrentUser();
        String usuario = currentUser != null ? currentUser.nombre() : "RECEPCION";
        cita.cancelar(motivoLimpio, usuario);
        Cita guardada = citaRepository.save(cita);

        if (comisionCitaPort != null) {
            comisionCitaPort.anularComisionSiAplica(guardada.getId(), motivoLimpio);
        }

        auditoriaPort.registrar(
                "CANCELAR_CITA",
                "CITAS",
                cita.getSedeId(),
                "Cita " + cita.getId() + " cancelada. Motivo: " + motivoLimpio +
                        (saldoAFavor ? " (Saldo a favor otorgado: S/ " + cita.getAdelantoMonto() + ")" : "")
        );

        eventPublisher.publishEvent(new CitaCanceladaEvent(
                cita.getId(),
                cita.getPacienteId(),
                cita.getSedeId(),
                motivoLimpio,
                saldoAFavor,
                saldoAFavor ? cita.getAdelantoMonto() : BigDecimal.ZERO,
                OffsetDateTime.now()
        ));

        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public CitaResponseDTO cambiarEstado(UUID citaId, String nuevoEstado, String motivo) {
        exigirQueElOdontologoSoloAtienda(nuevoEstado);
        if ("CANCELADA".equalsIgnoreCase(nuevoEstado)) {
            return cancelarCita(citaId, CitaCancelarRequestDTO.builder().motivo(motivo).build());
        }

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));

        SecurityUtils.validarAccesoSede(cita.getSedeId());
        exigirCitaPropiaSiEsOdontologo(cita);

        String estadoAnterior = cita.getEstado();
        cita.transicionarA(nuevoEstado, motivo, clock.instant());
        Cita guardada = citaRepository.save(cita);

        // Atender una cita que es sesión de un plan suma la sesión al plan (una sola vez, en la misma transacción).
        if ("ATENDIDA".equalsIgnoreCase(nuevoEstado) && !"ATENDIDA".equalsIgnoreCase(estadoAnterior)) {
            if (guardada.cuentaComoSesionDeTratamiento()) {
                tratamientoCitaPort.registrarSesionAtendida(guardada.getTratamientoId(), guardada.getId());
            }
            if (comisionCitaPort != null) {
                comisionCitaPort.generarComisionSiAplica(guardada.getId());
            }
            if (consumoInsumosCitaPort != null) {
                consumoInsumosCitaPort.descontarInsumos(guardada.getId(), guardada.getSedeId(), guardada.getMotivoConsulta());
            }
        }

        auditoriaPort.registrar(
                "CAMBIAR_ESTADO_CITA",
                "CITAS",
                cita.getSedeId(),
                "Cita " + cita.getId() + " cambió de estado: " + estadoAnterior + " → " + nuevoEstado
                        + (motivo != null && !motivo.isBlank() ? ". Motivo: " + motivo.trim() : "")
        );

        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public CitaResponseDTO iniciarAtencion(UUID citaId) {
        var usuario = SecurityUtils.getCurrentUser();
        if (usuario == null || !usuario.tieneAlgunRol("ADMIN_GENERAL", "ADMIN_SEDE", "RECEPCION")) {
            throw new AccessDeniedException("Acceso denegado: solo administración y recepción pueden iniciar la atención.");
        }

        Cita cita = citaRepository.findByIdParaActualizar(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));
        SecurityUtils.validarAccesoSede(cita.getSedeId());

        cita.iniciarAtencion(clock.instant());
        Cita guardada = citaRepository.save(cita);
        auditoriaPort.registrar(
                "INICIAR_ATENCION_CITA",
                "CITAS",
                cita.getSedeId(),
                "Cita " + cita.getId() + " inició atención en sillón."
        );
        return CitaResponseDTO.fromDomain(guardada);
    }

    @Override
    @Transactional
    public int procesarCitasExpiradas() {
        OffsetDateTime ahora = OffsetDateTime.now();
        List<Cita> vencidas = citaRepository.findCitasVencidasDePago(ahora);
        int total = 0;
        for (Cita vencida : vencidas) {
            // B-039: se recarga con bloqueo; si mientras tanto se pago o se cancelo, ya no expira
            Cita c = citaRepository.findByIdParaActualizar(vencida.getId()).orElse(null);
            if (c == null || !"PENDIENTE_DE_PAGO".equalsIgnoreCase(c.getEstado())) {
                continue;
            }
            c.marcarExpirada();
            citaRepository.save(c);
            total++;
            auditoriaPort.registrar(
                    "CITA_EXPIRADA_AUTO",
                    "CITAS",
                    c.getSedeId(),
                    "Cita " + c.getId() + " expiró tras 2 horas sin pago de adelanto. Horario liberado."
            );
        }
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public BandejaTareasResponseDTO obtenerBandejaTareas(String sedeId, LocalDate fecha) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        Instant ahora = clock.instant();
        LocalDate hoy = fecha != null ? fecha : LocalDate.now(clock);
        var configSedeOpt = sedeValidacionPort.obtenerConfiguracionSede(sedeEfectiva);
        String nombreSede = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::nombre).orElse(sedeEfectiva != null ? sedeEfectiva : "Red Odontológica");
        String whatsappSede = configSedeOpt.map(SedeValidacionPort.SedeAgendaConfig::whatsapp).orElse("51960112257");

        List<ItemTareaBandejaDTO> tareas = new ArrayList<>();

        // 1. Llamadas de Hoy (citas de hoy con confirmación pendiente ordenadas por hora)
        List<Cita> citasHoy = citaRepository.findByFechaAndSede(hoy, sedeEfectiva).stream()
                .filter(c -> !Set.of("CANCELADA", "EXPIRADA", "ATENDIDA", "EN_SALA", "CONFIRMADA").contains(c.getEstado()))
                .filter(c -> !"CONFIRMADA".equalsIgnoreCase(c.getEstadoConfirmacion()))
                .sorted(Comparator.comparing(Cita::getHora))
                .toList();

        for (Cita c : citasHoy) {
            String prioridad = "NO_RESPONDE".equalsIgnoreCase(c.getEstadoConfirmacion()) ? "ALTA"
                    : "SIN_CONTACTAR".equalsIgnoreCase(c.getEstadoConfirmacion()) ? "MEDIA" : "NORMAL";

            String mensajeSugerido = String.format(
                    "Hola %s, le saludamos de %s. Le recordamos su cita el día de hoy a las %s con %s para %s. ¿Nos confirma si se encuentra en camino? Muchas gracias.",
                    c.getPacienteNombre() != null ? c.getPacienteNombre() : "estimado(a) paciente",
                    nombreSede,
                    c.getHora() != null ? c.getHora().format(DateTimeFormatter.ofPattern("hh:mm a")) : "-",
                    c.getDoctorNombre() != null ? c.getDoctorNombre() : "nuestro especialista",
                    c.getMotivoConsulta() != null ? c.getMotivoConsulta() : "su consulta"
            );

            String enlaceWa = c.getPacienteTelefono() != null
                    ? "https://wa.me/51" + c.getPacienteTelefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(mensajeSugerido, StandardCharsets.UTF_8)
                    : null;

            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("LLAMADA-" + c.getId())
                    .tipo("LLAMADA_HOY")
                    .prioridad(prioridad)
                    .citaId(c.getId())
                    .pacienteId(c.getPacienteId())
                    .pacienteNombre(c.getPacienteNombre())
                    .pacienteTelefono(c.getPacienteTelefono())
                    .horaCita(c.getHora())
                    .doctorNombre(c.getDoctorNombre())
                    .motivoConsulta(c.getMotivoConsulta())
                    .estadoCita(c.getEstado())
                    .estadoConfirmacion(c.getEstadoConfirmacion())
                    .mensajeSugerido(mensajeSugerido)
                    .enlaceWhatsapp(enlaceWa)
                    .build());
        }

        // 2. Recordatorios WhatsApp 24h (citas de mañana para confirmación preventiva que aún no están confirmadas)
        LocalDate manana = hoy.plusDays(1);
        List<Cita> citasManana = citaRepository.findByFechaAndSede(manana, sedeEfectiva).stream()
                .filter(c -> !Set.of("CANCELADA", "EXPIRADA", "CONFIRMADA").contains(c.getEstado()))
                .filter(c -> !"CONFIRMADA".equalsIgnoreCase(c.getEstadoConfirmacion()))
                .sorted(Comparator.comparing(Cita::getHora))
                .toList();

        for (Cita c : citasManana) {
            String mensajeRecordatorio = String.format(
                    "Hola %s, le recordamos su cita mañana %s a las %s en %s con %s. Por favor confirme su asistencia respondiendo 'SÍ' a este mensaje. ¡Gracias!",
                    c.getPacienteNombre() != null ? c.getPacienteNombre() : "estimado(a) paciente",
                    manana.format(DateTimeFormatter.ofPattern("dd/MM")),
                    c.getHora() != null ? c.getHora().format(DateTimeFormatter.ofPattern("hh:mm a")) : "-",
                    nombreSede,
                    c.getDoctorNombre() != null ? c.getDoctorNombre() : "el especialista"
            );

            String enlaceWa = c.getPacienteTelefono() != null
                    ? "https://wa.me/51" + c.getPacienteTelefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(mensajeRecordatorio, StandardCharsets.UTF_8)
                    : null;

            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("REC24H-" + c.getId())
                    .tipo("RECORDATORIO_24H")
                    .prioridad("MEDIA")
                    .citaId(c.getId())
                    .pacienteId(c.getPacienteId())
                    .pacienteNombre(c.getPacienteNombre())
                    .pacienteTelefono(c.getPacienteTelefono())
                    .horaCita(c.getHora())
                    .doctorNombre(c.getDoctorNombre())
                    .motivoConsulta(c.getMotivoConsulta())
                    .estadoCita(c.getEstado())
                    .estadoConfirmacion(c.getEstadoConfirmacion())
                    .mensajeSugerido(mensajeRecordatorio)
                    .enlaceWhatsapp(enlaceWa)
                    .build());
        }

        // 3. Saludos de Cumpleaños de Hoy (con consentimiento Ley 29733)
        var cumpleaneros = pacienteReservaPort.obtenerCumpleanerosDelDia(sedeEfectiva, hoy.getMonthValue(), hoy.getDayOfMonth());
        for (var p : cumpleaneros) {
            String promoCumple = String.format(
                    "¡Feliz cumpleaños %s! 🎂 De parte de todo el equipo de %s le deseamos un día extraordinario. Tiene activo un beneficio especial de 20%% de descuento en Profilaxis Dental vigente durante los próximos 7 días. ¡Venga a sonreír con nosotros!",
                    p.nombreCompleto(), nombreSede
            );

            String enlaceWa = p.telefono() != null
                    ? "https://wa.me/51" + p.telefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(promoCumple, StandardCharsets.UTF_8)
                    : null;

            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("CUMPLE-" + p.id())
                    .tipo("SALUDO_CUMPLEANOS")
                    .prioridad("NORMAL")
                    .pacienteId(p.id())
                    .pacienteNombre(p.nombreCompleto())
                    .pacienteTelefono(p.telefono())
                    .pacienteDni(p.dni())
                    .totalInasistencias(p.totalInasistencias())
                    .avisoInasistencias(p.avisoInasistencias())
                    .mensajeSugerido(promoCumple)
                    .enlaceWhatsapp(enlaceWa)
                    .build());
        }

        // shortcut: quien nació un 29 de febrero no recibe recordatorio en años no bisiestos; mejorar si el negocio lo pide
        var cumpleanerosManana = pacienteReservaPort.obtenerCumpleanerosDelDia(
                sedeEfectiva, manana.getMonthValue(), manana.getDayOfMonth());
        for (var p : cumpleanerosManana) {
            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("CUMPLE-MANANA-" + p.id())
                    .tipo("RECORDATORIO_CUMPLEANOS")
                    .prioridad("NORMAL")
                    .pacienteId(p.id())
                    .pacienteNombre(p.nombreCompleto())
                    .pacienteTelefono(p.telefono())
                    .pacienteDni(p.dni())
                    .totalInasistencias(p.totalInasistencias())
                    .avisoInasistencias(p.avisoInasistencias())
                    .build());
        }

        for (Cita c : citaRepository.findByFechaAndSede(hoy, sedeEfectiva)) {
            if (!"EN_SALA".equalsIgnoreCase(c.getEstado()) || c.getInicioAtencionEn() != null) {
                continue;
            }
            boolean esperaMasDeQuinceMinutos = c.getLlegadaEn() != null
                    && Duration.between(c.getLlegadaEn(), ahora).compareTo(Duration.ofMinutes(15)) > 0;
            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("SILLON-" + c.getId())
                    .tipo("PASAR_AL_SILLON")
                    .prioridad(esperaMasDeQuinceMinutos ? "ALTA" : "NORMAL")
                    .citaId(c.getId())
                    .pacienteId(c.getPacienteId())
                    .pacienteNombre(c.getPacienteNombre())
                    .pacienteTelefono(c.getPacienteTelefono())
                    .horaCita(c.getHora())
                    .llegadaEn(c.getLlegadaEn())
                    .doctorNombre(c.getDoctorNombre())
                    .motivoConsulta(c.getMotivoConsulta())
                    .estadoCita(c.getEstado())
                    .build());
        }

        // 4. Citas Pendientes de Adelanto Yape por expirar
        List<Cita> pendientesPago = citaRepository.findCitasPendientesDePago(sedeEfectiva);
        for (Cita c : pendientesPago) {
            long minutos = c.getAdelantoLimitePago() != null
                    ? Math.max(0, Duration.between(OffsetDateTime.now(), c.getAdelantoLimitePago()).toMinutes())
                    : 0;

            String mensajeAdelanto = String.format(
                    "Hola %s, le saludamos de %s. Vemos que tiene una reserva pendiente para el %s a las %s. Le recordamos enviar la captura de su Yape (S/ %s) para confirmar definitivamente su turno antes de que expire el tiempo de reserva. ¡Muchas gracias!",
                    c.getPacienteNombre() != null ? c.getPacienteNombre() : "paciente",
                    nombreSede,
                    c.getFecha(),
                    c.getHora() != null ? c.getHora().format(DateTimeFormatter.ofPattern("hh:mm a")) : "-",
                    c.getAdelantoMonto()
            );

            String enlaceWa = c.getPacienteTelefono() != null
                    ? "https://wa.me/51" + c.getPacienteTelefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(mensajeAdelanto, StandardCharsets.UTF_8)
                    : null;

            tareas.add(ItemTareaBandejaDTO.builder()
                    .id("ADELANTO-" + c.getId())
                    .tipo("ADELANTO_POR_EXPIRAR")
                    .prioridad(minutos <= 30 ? "ALTA" : "MEDIA")
                    .citaId(c.getId())
                    .pacienteId(c.getPacienteId())
                    .pacienteNombre(c.getPacienteNombre())
                    .pacienteTelefono(c.getPacienteTelefono())
                    .horaCita(c.getHora())
                    .doctorNombre(c.getDoctorNombre())
                    .motivoConsulta(c.getMotivoConsulta())
                    .estadoCita(c.getEstado())
                    .estadoConfirmacion(c.getEstadoConfirmacion())
                    .minutosParaExpirar(minutos)
                    .mensajeSugerido(mensajeAdelanto)
                    .enlaceWhatsapp(enlaceWa)
                    .build());
        }

        tareas.addAll(tareasDeHuecosLibres(sedeEfectiva, hoy, nombreSede));

        // Cuotas vencidas de planes en cuotas (ortodoncia): recepción las cobra o las recuerda por WhatsApp.
        if (cuotasVencidasPort != null) {
            for (var cv : cuotasVencidasPort.cuotasVencidasDeSede(sedeEfectiva, hoy)) {
                String mensajeCuota = String.format(
                        "Hola %s, le saludamos de %s. Le recordamos que su %s de %s venció el %s y tiene un saldo de S/ %s. ¿Cuándo podría acercarse a regularizarlo? Muchas gracias.",
                        cv.pacienteNombre(), nombreSede, cv.concepto().toLowerCase(), cv.tratamiento(),
                        cv.venceEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), cv.saldo().toPlainString());
                tareas.add(ItemTareaBandejaDTO.builder()
                        .id("CUOTA-" + cv.tratamientoId())
                        .tipo("CUOTA_VENCIDA")
                        .prioridad("ALTA")
                        .pacienteId(cv.pacienteId())
                        .pacienteNombre(cv.pacienteNombre())
                        .pacienteTelefono(cv.pacienteTelefono())
                        .motivoConsulta(cv.tratamiento() + " · " + cv.concepto() + " · saldo S/ " + cv.saldo().toPlainString())
                        .mensajeSugerido(mensajeCuota)
                        .enlaceWhatsapp(cv.pacienteTelefono() != null
                                ? "https://wa.me/51" + cv.pacienteTelefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(mensajeCuota, StandardCharsets.UTF_8)
                                : null)
                        .build());
            }
        }

        int totalLlamadas = (int) tareas.stream().filter(t -> "LLAMADA_HOY".equals(t.getTipo())).count();
        int totalRecordatorios = (int) tareas.stream().filter(t -> "RECORDATORIO_24H".equals(t.getTipo())).count();
        int totalCumpleanos = (int) tareas.stream().filter(t -> "SALUDO_CUMPLEANOS".equals(t.getTipo())).count();
        int totalAdelantos = (int) tareas.stream().filter(t -> "ADELANTO_POR_EXPIRAR".equals(t.getTipo())).count();
        int totalPendientesHoy = (int) tareas.stream()
                .filter(t -> !"RECORDATORIO_CUMPLEANOS".equals(t.getTipo()))
                .count();

        return BandejaTareasResponseDTO.builder()
                .fecha(hoy)
                .sedeId(sedeEfectiva)
                .sedeNombre(nombreSede)
                .telefonoWhatsappSede(whatsappSede)
                .totalPendientes(totalPendientesHoy)
                .totalLlamadasPendientes(totalLlamadas)
                .totalRecordatoriosPendientes(totalRecordatorios)
                .totalCumpleanosPendientes(totalCumpleanos)
                .totalAdelantosPorExpirar(totalAdelantos)
                .tareas(tareas)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CitaResponseDTO obtenerPorId(UUID citaId) {
        return citaRepository.findById(citaId)
                .map(CitaResponseDTO::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + citaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponseDTO> obtenerCitasDelDia(LocalDate fecha, String sedeId) {
        return conInasistencias(soloVisibles(citaRepository.findByFechaAndSede(fecha, sedeId)).stream()
                .map(CitaResponseDTO::fromDomain)
                .toList());
    }

    /**
     * Huecos libres de los próximos 7 días (citas canceladas, que no asistieron o expiradas, sin otra cita en ese
     * horario con el mismo doctor) emparejados con el primer paciente de la lista de espera que los acepta.
     */
    private List<ItemTareaBandejaDTO> tareasDeHuecosLibres(String sedeId, LocalDate hoy, String nombreSede) {
        List<ItemTareaBandejaDTO> tareas = new ArrayList<>();
        if (esperaHuecoRepository == null || sedeId == null) {
            return tareas;
        }
        var espera = new ArrayList<>(esperaHuecoRepository.findVigentesBySede(sedeId));
        if (espera.isEmpty()) {
            return tareas;
        }
        var yaOfrecidos = new java.util.HashSet<UUID>();
        LocalTime ahora = LocalTime.now(clock.withZone(ZONE_LIMA));
        for (int d = 0; d < 7; d++) {
            LocalDate dia = hoy.plusDays(d);
            List<Cita> delDia = citaRepository.findByFechaAndSede(dia, sedeId);
            var vistos = new java.util.HashSet<String>();
            for (Cita libre : delDia) {
                if (!Cita.ESTADOS_QUE_LIBERAN_HORARIO.contains(libre.getEstado()) || libre.getHora() == null) continue;
                if (d == 0 && libre.getHora().isBefore(ahora)) continue;
                boolean ocupado = delDia.stream().anyMatch(o -> !Cita.ESTADOS_QUE_LIBERAN_HORARIO.contains(o.getEstado())
                        && libre.getHora().equals(o.getHora()) && java.util.Objects.equals(libre.getDoctorId(), o.getDoctorId()));
                if (ocupado || !vistos.add(libre.getHora() + "|" + libre.getDoctorId())) continue;
                var candidato = espera.stream()
                        .filter(e -> !yaOfrecidos.contains(e.getId()) && e.aceptaDoctor(libre.getDoctorId()))
                        .findFirst();
                if (candidato.isEmpty()) continue;
                var e = candidato.get();
                yaOfrecidos.add(e.getId());
                String cuando = (d == 0 ? "hoy" : d == 1 ? "mañana" : dia.format(DateTimeFormatter.ofPattern("dd/MM")))
                        + " a las " + libre.getHora().format(DateTimeFormatter.ofPattern("HH:mm"));
                String mensaje = String.format(
                        "Hola %s, le saludamos de %s. Se liberó un horario %s con %s. ¿Le gustaría tomarlo para %s? Avísenos y se lo reservamos.",
                        e.getPacienteNombre(), nombreSede, cuando,
                        libre.getDoctorNombre() != null ? libre.getDoctorNombre() : "nuestro especialista", e.getMotivo());
                tareas.add(ItemTareaBandejaDTO.builder()
                        .id("HUECO-" + libre.getId() + "-" + e.getId())
                        .tipo("HUECO_DISPONIBLE")
                        .prioridad("MEDIA")
                        .pacienteId(e.getPacienteId())
                        .pacienteNombre(e.getPacienteNombre())
                        .pacienteTelefono(e.getPacienteTelefono())
                        .horaCita(libre.getHora())
                        .doctorNombre(libre.getDoctorNombre())
                        .motivoConsulta("Hueco " + cuando + " · espera: " + e.getMotivo())
                        .mensajeSugerido(mensaje)
                        .enlaceWhatsapp(e.getPacienteTelefono() != null
                                ? "https://wa.me/51" + e.getPacienteTelefono().replaceAll("[^0-9]", "") + "?text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8)
                                : null)
                        .build());
            }
        }
        return tareas;
    }

    /** Agrega a cada cita el conteo de inasistencias de su paciente (una sola consulta para toda la lista). */
    private List<CitaResponseDTO> conInasistencias(List<CitaResponseDTO> citas) {
        var ids = citas.stream().map(CitaResponseDTO::getPacienteId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return citas;
        }
        var conteos = pacienteReservaPort.inasistenciasDe(ids);
        citas.forEach(c -> c.setPacienteInasistencias(conteos.getOrDefault(c.getPacienteId(), 0)));
        return citas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponseDTO> obtenerCitasPorRango(LocalDate desde, LocalDate hasta, String sedeId) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Debe indicar las fechas 'desde' y 'hasta'.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS_RANGO_AGENDA) {
            throw new IllegalArgumentException("El rango de la agenda no puede superar " + MAX_DIAS_RANGO_AGENDA + " días.");
        }
        return conInasistencias(soloVisibles(citaRepository.findByRangoFechas(desde, hasta, sedeId)).stream()
                .map(CitaResponseDTO::fromDomain)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponseDTO> obtenerReservasWebPendientes(String sedeId) {
        return soloVisibles(citaRepository.findReservasWebPendientes(sedeId)).stream()
                .map(CitaResponseDTO::fromDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitaResponseDTO> obtenerListaEspera(String sedeId) {
        LocalDate hoyLima = LocalDate.now(ZONE_LIMA);
        return soloVisibles(citaRepository.findByFechaAndSede(hoyLima, sedeId)).stream()
                .filter(c -> "EN_SALA".equalsIgnoreCase(c.getEstado()))
                .map(CitaResponseDTO::fromDomain)
                .toList();
    }

    // ---- B-035: un ODONTOLOGO solo ve y atiende las citas de su propio especialista ----

    private static boolean esOdontologo() {
        var user = SecurityUtils.getCurrentUser();
        return user != null && user.soloOdontologo();
    }

    /** Especialista vinculado al usuario ODONTOLOGO actual; vacío si no tiene perfil de especialista. */
    private Optional<UUID> especialistaPropio() {
        var user = SecurityUtils.getCurrentUser();
        if (user == null || user.id() == null) {
            return Optional.empty();
        }
        return especialistaRepository.findByUsuarioId(user.id()).map(e -> e.getId());
    }

    /** Un ODONTOLOGO recibe solo las citas de su especialista (lista vacía si no tiene); el resto de roles, todas. */
    private List<Cita> soloVisibles(List<Cita> citas) {
        if (!esOdontologo()) {
            return citas;
        }
        Optional<UUID> propio = especialistaPropio();
        if (propio.isEmpty()) {
            return List.of();
        }
        return citas.stream().filter(c -> propio.get().equals(c.getDoctorId())).toList();
    }

    private void denegarSiEsOdontologo(String accion) {
        if (esOdontologo()) {
            throw new AccessDeniedException("Acceso denegado: un odontólogo no puede " + accion + " citas.");
        }
    }

    private void exigirQueElOdontologoSoloAtienda(String nuevoEstado) {
        if (esOdontologo() && !"ATENDIDA".equalsIgnoreCase(nuevoEstado)) {
            throw new AccessDeniedException("Acceso denegado: un odontólogo solo puede marcar como atendidas sus propias citas.");
        }
    }

    private void exigirCitaPropiaSiEsOdontologo(Cita cita) {
        if (!esOdontologo()) {
            return;
        }
        boolean propia = cita.getDoctorId() != null && especialistaPropio().map(cita.getDoctorId()::equals).orElse(false);
        if (!propia) {
            throw new AccessDeniedException("Acceso denegado: solo puede atender las citas asignadas a usted.");
        }
    }

    // ---- B-030/B-031: colisión por intervalo [hora, hora + duración), ignorando las citas que liberan el hueco ----

    private void verificarDoctorLibre(UUID doctorId, LocalDate fecha, LocalTime hora, int duracion, UUID excluirCitaId, String mensaje) {
        citaRepository.findOcupantesDoctor(doctorId, fecha).stream()
                .filter(c -> c.ocupaAgenda() && !Objects.equals(c.getId(), excluirCitaId) && c.solapaCon(fecha, hora, duracion))
                .findFirst()
                .ifPresent(c -> {
                    throw new CitaColisionException(mensaje + " (se cruza con la cita de " + c.getHora() + " a "
                            + c.getHora().plusMinutes(c.getDuracionMinutos() > 0 ? c.getDuracionMinutos() : 30) + ").");
                });
    }

    private void verificarSillonLibre(String sedeId, Integer sillon, LocalDate fecha, LocalTime hora, int duracion, UUID excluirCitaId) {
        citaRepository.findOcupantesSillon(sedeId, sillon, fecha).stream()
                .filter(c -> c.ocupaAgenda() && !Objects.equals(c.getId(), excluirCitaId) && c.solapaCon(fecha, hora, duracion))
                .findFirst()
                .ifPresent(c -> {
                    throw new CitaColisionException("El sillón #" + sillon + " ya está ocupado en ese horario (se cruza con la cita de "
                            + c.getHora() + " a " + c.getHora().plusMinutes(c.getDuracionMinutos() > 0 ? c.getDuracionMinutos() : 30) + ").");
                });
    }

    private void validarDoctorDisponible(UUID doctorId, String sedeId, LocalDate fecha, LocalTime hora, int duracionMinutos) {
        if (!doctorHorarioPort.esDoctorActivo(doctorId)) {
            String nombreDoc = doctorHorarioPort.obtenerNombreDoctor(doctorId);
            throw new CitaColisionException("El especialista " + (nombreDoc != null ? nombreDoc : "") + " está inactivo y no recibe nuevas citas.");
        }

        // Doctor invitado: viene por cita previa, no tiene horario semanal; solo debe estar asignado a la sede.
        var invitado = especialistaRepository.findById(doctorId).filter(e -> e.isInvitado());
        if (invitado.isPresent()) {
            if (sedeId != null && invitado.get().getSedes().stream().noneMatch(sedeId::equalsIgnoreCase)) {
                throw new CitaColisionException("El doctor invitado no está asignado a esta sede.");
            }
            return;
        }

        boolean atiende = doctorHorarioPort.atiendeEnHorarioYSede(doctorId, sedeId, fecha, hora, duracionMinutos);
        if (!atiende) {
            throw new CitaColisionException("El especialista no atiende en el horario o sede seleccionada para ese día.");
        }
    }

    private void verificarCapacidadSillonesSede(String sedeId, LocalDate fecha, LocalTime hora, int duracion, UUID excluirCitaId) {
        var sedeOpt = sedeValidacionPort.obtenerSede(sedeId);
        if (sedeOpt.isEmpty()) {
            return;
        }
        int capacidad = sedeOpt.get().sillones();
        if (capacidad <= 0) {
            return;
        }
        citaRepository.bloquearCapacidadDeSede(sedeId, fecha);
        var citasSede = citaRepository.findByFechaAndSede(fecha, sedeId);
        if (citasSede == null) {
            return;
        }
        long solapadas = citasSede.stream()
                .filter(c -> c.ocupaAgenda() && !Objects.equals(c.getId(), excluirCitaId) && c.solapaCon(fecha, hora, duracion))
                .count();
        if (solapadas >= capacidad) {
            throw new CitaColisionException("La sede ha alcanzado el límite máximo de capacidad (" + capacidad + (capacidad > 1 ? " sillones" : " sillón") + ") para ese horario.");
        }
    }
}
