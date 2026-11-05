package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.TransicionEstadoInvalidaException;
import com.odontomax.agenda.domain.exception.InicioAtencionNoPermitidoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Cita {

    /**
     * Máquina de estados (B-028): destinos permitidos desde cada estado. ATENDIDA, CANCELADA, NO_ASISTIO y EXPIRADA
     * son finales. Alineada con el flujo que ofrece el frontend (transiciones.ts); la única diferencia es que
     * CANCELADA se admite también desde PENDIENTE_WEB, CONFIRMADA y EN_SALA (siempre con motivo).
     */
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "PENDIENTE_WEB", Set.of("PROGRAMADA", "CONFIRMADA", "CANCELADA"),
            "PENDIENTE_DE_PAGO", Set.of("CONFIRMADA_CON_ADELANTO", "EXPIRADA", "CANCELADA"),
            "PROGRAMADA", Set.of("CONFIRMADA", "EN_SALA", "CANCELADA", "NO_ASISTIO"),
            "CONFIRMADA", Set.of("EN_SALA", "CANCELADA", "NO_ASISTIO"),
            "CONFIRMADA_CON_ADELANTO", Set.of("EN_SALA", "CANCELADA", "NO_ASISTIO"),
            "EN_SALA", Set.of("EN_ATENCION", "CANCELADA"),
            "EN_ATENCION", Set.of("ATENDIDA", "CANCELADA"));

    /**
     * B-038: estados desde los que se registra un adelanto. PENDIENTE_DE_PAGO es el flujo de las sedes con adelanto
     * (y de la reserva pública con adelanto); PENDIENTE_WEB, PROGRAMADA y CONFIRMADA son las citas vivas a las que
     * recepción les cobra el adelanto después. Quedan fuera los estados finales (ATENDIDA, CANCELADA, NO_ASISTIO,
     * EXPIRADA) y EN_SALA (el paciente ya está presente). CONFIRMADA_CON_ADELANTO admite el adelanto solo mientras no
     * esté PAGADO: PATCH /estado puede dejar la cita en ese estado sin cobro (R1), y el cobro aún debe poder registrarse.
     */
    private static final Set<String> ESTADOS_QUE_ADMITEN_ADELANTO =
            Set.of("PENDIENTE_DE_PAGO", "PENDIENTE_WEB", "PROGRAMADA", "CONFIRMADA");

    private UUID id;
    private UUID pacienteId;
    private String pacienteNombre;
    private String pacienteTelefono;
    private UUID doctorId;
    private String doctorNombre;
    private String sedeId;
    private Integer sillonNumero;

    private String motivoConsulta;
    private LocalDate fecha;
    private LocalTime hora;
    private int duracionMinutos;

    // Estados clínicos: PENDIENTE_WEB, PENDIENTE_DE_PAGO, CONFIRMADA_CON_ADELANTO, PROGRAMADA, CONFIRMADA, EN_SALA, ATENDIDA, CANCELADA, EXPIRADA, NO_ASISTIO
    private String estado;
    private Instant llegadaEn;
    private Instant inicioAtencionEn;
    private Instant finAtencionEn;
    private String motivoCancelacion;
    private String motivoCancelacionPor;
    private Instant canceladaEn;
    private String motivoReprogramacion;
    private String motivoReprogramacionPor;
    private Instant reprogramadaEn;
    private String notas;
    private String codigoTicket;

    // Gestión de adelantos (Sección 4 de especificaciones)
    private boolean adelantoRequerido;
    private BigDecimal adelantoMonto;
    private String adelantoEstado; // NO_APLICA, PENDIENTE, PAGADO, EXONERADO, SALDO_FAVOR, PERDIDO
    private String adelantoMedioPago; // YAPE, EFECTIVO
    private String adelantoNumeroOperacion;
    private String adelantoComprobanteUrl;
    private String adelantoRegistradoPor;
    private OffsetDateTime adelantoRegistradoEn;
    private OffsetDateTime adelantoLimitePago;

    // Control riguroso de confirmación y llamadas WhatsApp (Sección 5)
    private String estadoConfirmacion; // SIN_CONTACTAR, MENSAJE_ENVIADO, CONFIRMADA, NO_RESPONDE, CANCELADA_POR_PACIENTE
    private String ultimoContactoMedio; // WHATSAPP, LLAMADA
    private OffsetDateTime ultimoContactoFecha;
    private String ultimoContactoUsuario;
    private String ultimoContactoResultado;

    /** Plan de tratamiento al que corresponde esta cita (una sesión del plan). Opcional. */
    private UUID tratamientoId;
    private UUID doctorDerivadorId;
    private boolean pacienteDirecto;

    /** Estados que liberan el hueco de la agenda; coincide con el WHERE de las restricciones EXCLUDE de la base (V17). */
    public static final Set<String> ESTADOS_QUE_LIBERAN_HORARIO = Set.of("CANCELADA", "NO_ASISTIO", "EXPIRADA");

    public Cita() {
    }

    /** ¿La cita ocupa al especialista y al sillón? Las canceladas, inasistencias y expiradas no. */
    public boolean ocupaAgenda() {
        return !ESTADOS_QUE_LIBERAN_HORARIO.contains(normalizar(this.estado));
    }

    /** ¿El intervalo [hora, hora + duración) de esta cita se cruza con el indicado? Contiguos no se cruzan. */
    public boolean solapaCon(LocalDate otraFecha, LocalTime otraHora, int otraDuracionMinutos) {
        if (fecha == null || hora == null || otraFecha == null || otraHora == null) {
            return false;
        }
        java.time.LocalDateTime inicio = fecha.atTime(hora);
        java.time.LocalDateTime fin = inicio.plusMinutes(duracionMinutos > 0 ? duracionMinutos : 30);
        java.time.LocalDateTime otroInicio = otraFecha.atTime(otraHora);
        java.time.LocalDateTime otroFin = otroInicio.plusMinutes(otraDuracionMinutos > 0 ? otraDuracionMinutos : 30);
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    public Cita(UUID id, UUID pacienteId, String pacienteNombre, String pacienteTelefono,
                UUID doctorId, String doctorNombre, String sedeId, Integer sillonNumero,
                String motivoConsulta, LocalDate fecha, LocalTime hora, int duracionMinutos,
                String estado, String motivoCancelacion, String motivoReprogramacion,
                String notas, String codigoTicket, boolean adelantoRequerido,
                BigDecimal adelantoMonto, String adelantoEstado, String adelantoMedioPago,
                String adelantoNumeroOperacion, String adelantoComprobanteUrl,
                String adelantoRegistradoPor, OffsetDateTime adelantoRegistradoEn,
                OffsetDateTime adelantoLimitePago, String estadoConfirmacion,
                String ultimoContactoMedio, OffsetDateTime ultimoContactoFecha,
                String ultimoContactoUsuario, String ultimoContactoResultado) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.pacienteTelefono = pacienteTelefono;
        this.doctorId = doctorId;
        this.doctorNombre = doctorNombre;
        this.sedeId = sedeId;
        this.sillonNumero = sillonNumero;
        this.motivoConsulta = motivoConsulta;
        this.fecha = fecha;
        this.hora = hora;
        this.duracionMinutos = duracionMinutos > 0 ? duracionMinutos : 30;
        this.estado = estado;
        this.motivoCancelacion = motivoCancelacion;
        this.motivoReprogramacion = motivoReprogramacion;
        this.notas = notas;
        this.codigoTicket = codigoTicket;
        this.adelantoRequerido = adelantoRequerido;
        this.adelantoMonto = adelantoMonto != null ? adelantoMonto : BigDecimal.ZERO;
        this.adelantoEstado = adelantoEstado != null ? adelantoEstado : "NO_APLICA";
        this.adelantoMedioPago = adelantoMedioPago;
        this.adelantoNumeroOperacion = adelantoNumeroOperacion;
        this.adelantoComprobanteUrl = adelantoComprobanteUrl;
        this.adelantoRegistradoPor = adelantoRegistradoPor;
        this.adelantoRegistradoEn = adelantoRegistradoEn;
        this.adelantoLimitePago = adelantoLimitePago;
        this.estadoConfirmacion = estadoConfirmacion != null ? estadoConfirmacion : "SIN_CONTACTAR";
        this.ultimoContactoMedio = ultimoContactoMedio;
        this.ultimoContactoFecha = ultimoContactoFecha;
        this.ultimoContactoUsuario = ultimoContactoUsuario;
        this.ultimoContactoResultado = ultimoContactoResultado;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Lanza si la cita está en un estado desde el que no se registra un adelanto (B-038). */
    public void validarAdmiteAdelanto() {
        boolean confirmadaSinCobro = "CONFIRMADA_CON_ADELANTO".equals(normalizar(this.estado))
                && !"PAGADO".equalsIgnoreCase(this.adelantoEstado);
        if (!confirmadaSinCobro && !ESTADOS_QUE_ADMITEN_ADELANTO.contains(normalizar(this.estado))) {
            String motivo = "CONFIRMADA_CON_ADELANTO".equals(normalizar(this.estado)) ? ": ya tiene su adelanto registrado" : "";
            throw new IllegalArgumentException(
                    "No se puede registrar un adelanto en una cita con estado " + this.estado + motivo + ".");
        }
    }

    public void confirmarConAdelanto(String medioPago, BigDecimal monto, String numeroOperacion,
                                     String comprobanteUrl, String registradoPor) {
        validarAdmiteAdelanto();
        if (medioPago == null || (!"YAPE".equalsIgnoreCase(medioPago) && !"EFECTIVO".equalsIgnoreCase(medioPago))) {
            throw new IllegalArgumentException("El medio de pago del adelanto debe ser YAPE o EFECTIVO.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del adelanto debe ser mayor a cero.");
        }

        this.estado = "CONFIRMADA_CON_ADELANTO";
        this.adelantoEstado = "PAGADO";
        this.adelantoMedioPago = medioPago.toUpperCase().trim();
        this.adelantoMonto = monto;
        this.adelantoNumeroOperacion = numeroOperacion != null ? numeroOperacion.trim() : null;
        this.adelantoComprobanteUrl = comprobanteUrl != null ? comprobanteUrl.trim() : null;
        this.adelantoRegistradoPor = registradoPor != null ? registradoPor.trim() : "RECEPCION";
        this.adelantoRegistradoEn = OffsetDateTime.now();
        this.estadoConfirmacion = "CONFIRMADA";

        String notaPago = String.format("[ADELANTO REGISTRADO %s] Monto: S/ %s, Medio: %s, Op: %s por %s",
                LocalDate.now(), monto, this.adelantoMedioPago,
                this.adelantoNumeroOperacion != null ? this.adelantoNumeroOperacion : "-", this.adelantoRegistradoPor);
        appendNota(notaPago);
    }

    public void marcarExpirada() {
        if (!"PENDIENTE_DE_PAGO".equalsIgnoreCase(this.estado)) {
            throw new IllegalStateException("Solo citas en estado PENDIENTE_DE_PAGO pueden expirar.");
        }
        this.estado = "EXPIRADA";
        this.adelantoEstado = "PERDIDO";
        appendNota(String.format("[EXPIRADA %s] Tiempo de reserva de 2 horas agotado sin registro de pago de adelanto.", LocalDate.now()));
    }

    public void registrarContacto(String medio, String resultado, String usuario, String notasAdicionales) {
        if (medio == null || (!"WHATSAPP".equalsIgnoreCase(medio) && !"LLAMADA".equalsIgnoreCase(medio))) {
            throw new IllegalArgumentException("El medio de contacto debe ser WHATSAPP o LLAMADA.");
        }
        String res = resultado != null ? resultado.toUpperCase().trim() : "MENSAJE_ENVIADO";

        this.ultimoContactoMedio = medio.toUpperCase().trim();
        this.ultimoContactoFecha = OffsetDateTime.now();
        this.ultimoContactoUsuario = usuario != null ? usuario.trim() : "RECEPCION";
        this.ultimoContactoResultado = res;

        if ("CONFIRMO".equalsIgnoreCase(res)) {
            this.estadoConfirmacion = "CONFIRMADA";
            if ("PROGRAMADA".equalsIgnoreCase(this.estado) || "PENDIENTE_WEB".equalsIgnoreCase(this.estado)) {
                this.estado = "CONFIRMADA";
            }
        } else if ("NO_RESPONDE".equalsIgnoreCase(res)) {
            this.estadoConfirmacion = "NO_RESPONDE";
        } else if ("PIDIO_REPROGRAMAR".equalsIgnoreCase(res)) {
            this.estadoConfirmacion = "SIN_CONTACTAR";
        } else if ("CANCELO".equalsIgnoreCase(res)) {
            this.estadoConfirmacion = "CANCELADA_POR_PACIENTE";
        } else {
            this.estadoConfirmacion = "MENSAJE_ENVIADO";
        }

        String notaContacto = String.format("[CONTACTO %s vía %s] Resultado: %s por %s%s",
                LocalDate.now(), this.ultimoContactoMedio, res, this.ultimoContactoUsuario,
                (notasAdicionales != null && !notasAdicionales.isBlank() ? ". " + notasAdicionales.trim() : ""));
        appendNota(notaContacto);
    }

    public void marcarSaldoFavor() {
        this.adelantoEstado = "SALDO_FAVOR";
        appendNota(String.format("[SALDO A FAVOR] S/ %s acreditado a favor del paciente por cancelación anticipada.", this.adelantoMonto));
    }

    public void marcarAdelantoPerdido() {
        this.adelantoEstado = "PERDIDO";
        appendNota(String.format("[ADELANTO NO REEMBOLSABLE] Cancelación fuera de plazo o inasistencia."));
    }

    public void reprogramar(LocalDate nuevaFecha, LocalTime nuevoHorario, String motivo) {
        reprogramar(nuevaFecha, nuevoHorario, motivo, "RECEPCION");
    }

    public void reprogramar(LocalDate nuevaFecha, LocalTime nuevoHorario, String motivo, String usuario) {
        if (nuevaFecha == null || nuevoHorario == null) {
            throw new IllegalArgumentException("Debe especificar la nueva fecha y el nuevo horario.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("El motivo de reprogramación debe tener al menos 5 caracteres.");
        }
        if ("CANCELADA".equalsIgnoreCase(this.estado) || "ATENDIDA".equalsIgnoreCase(this.estado) || "EXPIRADA".equalsIgnoreCase(this.estado)) {
            throw new IllegalArgumentException("No se puede reprogramar una cita con estado " + this.estado);
        }

        LocalDate fechaAnterior = this.fecha;
        LocalTime horaAnterior = this.hora;

        this.fecha = nuevaFecha;
        this.hora = nuevoHorario;
        this.motivoReprogramacion = motivo.trim();
        this.motivoReprogramacionPor = usuario != null ? usuario.trim() : "RECEPCION";
        this.reprogramadaEn = Instant.now();
        this.llegadaEn = null;
        this.inicioAtencionEn = null;
        this.finAtencionEn = null;

        String notaReprog = String.format("[REPROGRAMACIÓN %s por %s] De %s %s a %s %s. Motivo: %s",
                LocalDate.now(), this.motivoReprogramacionPor, fechaAnterior, horaAnterior, nuevaFecha, nuevoHorario, motivo.trim());
        appendNota(notaReprog);
    }

    public void cancelar(String motivo) {
        cancelar(motivo, "RECEPCION");
    }

    public void cancelar(String motivo, String usuario) {
        String motivoLimpio = motivo != null ? motivo.trim() : "";
        if (motivoLimpio.length() < 5) {
            throw new IllegalArgumentException("Debe indicar el motivo de la cancelación (mínimo 5 caracteres).");
        }
        if (motivoLimpio.length() > 500) {
            motivoLimpio = motivoLimpio.substring(0, 500);
        }
        validarCancelable();

        this.estado = "CANCELADA";
        this.motivoCancelacion = motivoLimpio;
        this.motivoCancelacionPor = usuario != null ? usuario.trim() : "RECEPCION";
        this.canceladaEn = Instant.now();

        String notaCanc = String.format("[CANCELADA %s por %s] Motivo: %s", LocalDate.now(), this.motivoCancelacionPor, motivoLimpio);
        appendNota(notaCanc);
    }

    /** Lanza si la cita está en un estado final (o desconocido) desde el que no se puede cancelar. */
    public void validarCancelable() {
        if ("ATENDIDA".equalsIgnoreCase(this.estado)) {
            throw new TransicionEstadoInvalidaException("No se puede cancelar una cita que ya fue atendida.");
        }
        if ("CANCELADA".equalsIgnoreCase(this.estado)) {
            throw new TransicionEstadoInvalidaException("La cita ya se encuentra cancelada.");
        }
        Set<String> destinos = TRANSICIONES.get(normalizar(this.estado));
        if (destinos == null || !destinos.contains("CANCELADA")) {
            throw new TransicionEstadoInvalidaException("No se puede cancelar una cita con estado " + this.estado + ".");
        }
    }

    /**
     * Cambia el estado respetando la máquina de estados (B-028). CANCELADA exige motivo y pasa por {@link #cancelar}.
     * El motivo de cualquier otra transición solo queda en las notas: no es un motivo de cancelación.
     */
    public void transicionarA(String nuevoEstado, String motivo) {
        transicionarA(nuevoEstado, motivo, Instant.now());
    }

    public void transicionarA(String nuevoEstado, String motivo, Instant ahora) {
        String destino = normalizar(nuevoEstado);
        String origen = normalizar(this.estado);
        if (destino.isEmpty()) {
            throw new TransicionEstadoInvalidaException("Debe indicar el nuevo estado de la cita.");
        }
        if (destino.equals(origen)) {
            throw new TransicionEstadoInvalidaException("La cita ya se encuentra en estado " + destino + ".");
        }
        Set<String> permitidos = TRANSICIONES.get(origen);
        if (permitidos == null || !permitidos.contains(destino)) {
            throw new TransicionEstadoInvalidaException("No se puede pasar una cita de " + origen + " a " + destino + ".");
        }
        if ("CANCELADA".equals(destino)) {
            cancelar(motivo);
            return;
        }
        if ("EN_ATENCION".equals(destino)) {
            if (!"EN_SALA".equals(origen)) {
                throw new TransicionEstadoInvalidaException("Para pasar al sillón la cita debe estar en sala de espera.");
            }
            if (this.llegadaEn == null) {
                this.llegadaEn = ahora;
            }
            this.inicioAtencionEn = Objects.requireNonNull(ahora, "La hora de inicio es obligatoria.");
        } else if ("EN_SALA".equals(destino) && this.llegadaEn == null) {
            this.llegadaEn = Objects.requireNonNull(ahora, "La hora de llegada es obligatoria.");
        } else if ("ATENDIDA".equals(destino)) {
            if (!"EN_ATENCION".equals(origen)) {
                throw new TransicionEstadoInvalidaException("No se puede finalizar la atención sin haber pasado por En atención.");
            }
            if (this.inicioAtencionEn == null) {
                this.inicioAtencionEn = ahora;
            }
            this.finAtencionEn = Objects.requireNonNull(ahora, "La hora de término es obligatoria.");
        }
        this.estado = destino;
        if (motivo != null && !motivo.isBlank()) {
            appendNota("[Estado: " + destino + "] Motivo: " + motivo.trim());
        }
    }

    public void iniciarAtencion(Instant ahora) {
        if (!"EN_SALA".equalsIgnoreCase(normalizar(this.estado)) || this.llegadaEn == null || this.inicioAtencionEn != null) {
            throw new InicioAtencionNoPermitidoException(
                    "La atención solo puede iniciarse una vez cuando la cita está en sala de espera y con llegada registrada.");
        }
        this.inicioAtencionEn = Objects.requireNonNull(ahora, "La hora de inicio es obligatoria.");
        this.estado = "EN_ATENCION";
    }

    private static String normalizar(String estado) {
        return estado == null ? "" : estado.trim().toUpperCase();
    }

    private void appendNota(String nota) {
        if (this.notas == null || this.notas.isBlank()) {
            this.notas = nota;
        } else {
            this.notas = this.notas + "\n" + nota;
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPacienteId() { return pacienteId; }
    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }

    public String getPacienteNombre() { return pacienteNombre; }
    public void setPacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; }

    public String getPacienteTelefono() { return pacienteTelefono; }
    public void setPacienteTelefono(String pacienteTelefono) { this.pacienteTelefono = pacienteTelefono; }

    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(UUID doctorId) { this.doctorId = doctorId; }

    public String getDoctorNombre() { return doctorNombre; }
    public void setDoctorNombre(String doctorNombre) { this.doctorNombre = doctorNombre; }

    public String getSedeId() { return sedeId; }
    public void setSedeId(String sedeId) { this.sedeId = sedeId; }

    public Integer getSillonNumero() { return sillonNumero; }
    public void setSillonNumero(Integer sillonNumero) { this.sillonNumero = sillonNumero; }

    public String getMotivoConsulta() { return motivoConsulta; }
    public void setMotivoConsulta(String motivoConsulta) { this.motivoConsulta = motivoConsulta; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public int getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Instant getLlegadaEn() { return llegadaEn; }
    public void setLlegadaEn(Instant llegadaEn) { this.llegadaEn = llegadaEn; }

    public Instant getInicioAtencionEn() { return inicioAtencionEn; }
    public void setInicioAtencionEn(Instant inicioAtencionEn) { this.inicioAtencionEn = inicioAtencionEn; }

    public Instant getFinAtencionEn() { return finAtencionEn; }
    public void setFinAtencionEn(Instant finAtencionEn) { this.finAtencionEn = finAtencionEn; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }

    public String getMotivoCancelacionPor() { return motivoCancelacionPor; }
    public void setMotivoCancelacionPor(String motivoCancelacionPor) { this.motivoCancelacionPor = motivoCancelacionPor; }

    public Instant getCanceladaEn() { return canceladaEn; }
    public void setCanceladaEn(Instant canceladaEn) { this.canceladaEn = canceladaEn; }

    public String getMotivoReprogramacion() { return motivoReprogramacion; }
    public void setMotivoReprogramacion(String motivoReprogramacion) { this.motivoReprogramacion = motivoReprogramacion; }

    public String getMotivoReprogramacionPor() { return motivoReprogramacionPor; }
    public void setMotivoReprogramacionPor(String motivoReprogramacionPor) { this.motivoReprogramacionPor = motivoReprogramacionPor; }

    public Instant getReprogramadaEn() { return reprogramadaEn; }
    public void setReprogramadaEn(Instant reprogramadaEn) { this.reprogramadaEn = reprogramadaEn; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public String getCodigoTicket() { return codigoTicket; }
    public void setCodigoTicket(String codigoTicket) { this.codigoTicket = codigoTicket; }

    public boolean isAdelantoRequerido() { return adelantoRequerido; }
    public void setAdelantoRequerido(boolean adelantoRequerido) { this.adelantoRequerido = adelantoRequerido; }

    public BigDecimal getAdelantoMonto() { return adelantoMonto; }
    public void setAdelantoMonto(BigDecimal adelantoMonto) { this.adelantoMonto = adelantoMonto; }

    public String getAdelantoEstado() { return adelantoEstado; }
    public void setAdelantoEstado(String adelantoEstado) { this.adelantoEstado = adelantoEstado; }

    public String getAdelantoMedioPago() { return adelantoMedioPago; }
    public void setAdelantoMedioPago(String adelantoMedioPago) { this.adelantoMedioPago = adelantoMedioPago; }

    public String getAdelantoNumeroOperacion() { return adelantoNumeroOperacion; }
    public void setAdelantoNumeroOperacion(String adelantoNumeroOperacion) { this.adelantoNumeroOperacion = adelantoNumeroOperacion; }

    public String getAdelantoComprobanteUrl() { return adelantoComprobanteUrl; }
    public void setAdelantoComprobanteUrl(String adelantoComprobanteUrl) { this.adelantoComprobanteUrl = adelantoComprobanteUrl; }

    public String getAdelantoRegistradoPor() { return adelantoRegistradoPor; }
    public void setAdelantoRegistradoPor(String adelantoRegistradoPor) { this.adelantoRegistradoPor = adelantoRegistradoPor; }

    public OffsetDateTime getAdelantoRegistradoEn() { return adelantoRegistradoEn; }
    public void setAdelantoRegistradoEn(OffsetDateTime adelantoRegistradoEn) { this.adelantoRegistradoEn = adelantoRegistradoEn; }

    public OffsetDateTime getAdelantoLimitePago() { return adelantoLimitePago; }
    public void setAdelantoLimitePago(OffsetDateTime adelantoLimitePago) { this.adelantoLimitePago = adelantoLimitePago; }

    public String getEstadoConfirmacion() { return estadoConfirmacion; }
    public void setEstadoConfirmacion(String estadoConfirmacion) { this.estadoConfirmacion = estadoConfirmacion; }

    public String getUltimoContactoMedio() { return ultimoContactoMedio; }
    public void setUltimoContactoMedio(String ultimoContactoMedio) { this.ultimoContactoMedio = ultimoContactoMedio; }

    public OffsetDateTime getUltimoContactoFecha() { return ultimoContactoFecha; }
    public void setUltimoContactoFecha(OffsetDateTime ultimoContactoFecha) { this.ultimoContactoFecha = ultimoContactoFecha; }

    public String getUltimoContactoUsuario() { return ultimoContactoUsuario; }
    public void setUltimoContactoUsuario(String ultimoContactoUsuario) { this.ultimoContactoUsuario = ultimoContactoUsuario; }

    public String getUltimoContactoResultado() { return ultimoContactoResultado; }
    public void setUltimoContactoResultado(String ultimoContactoResultado) { this.ultimoContactoResultado = ultimoContactoResultado; }

    public UUID getTratamientoId() { return tratamientoId; }
    public void setTratamientoId(UUID tratamientoId) { this.tratamientoId = tratamientoId; }

    public UUID getDoctorDerivadorId() { return doctorDerivadorId; }
    public void setDoctorDerivadorId(UUID doctorDerivadorId) { this.doctorDerivadorId = doctorDerivadorId; }

    public boolean isPacienteDirecto() { return pacienteDirecto; }
    public void setPacienteDirecto(boolean pacienteDirecto) { this.pacienteDirecto = pacienteDirecto; }

    /** ¿Atenderla cuenta como una sesión de un plan de tratamiento? */
    public boolean cuentaComoSesionDeTratamiento() {
        return tratamientoId != null;
    }

    public static class Builder {
        private UUID id;
        private UUID pacienteId;
        private String pacienteNombre;
        private String pacienteTelefono;
        private UUID doctorId;
        private String doctorNombre;
        private String sedeId;
        private Integer sillonNumero;
        private String motivoConsulta;
        private LocalDate fecha;
        private LocalTime hora;
        private int duracionMinutos = 30;
        private String estado;
        private Instant llegadaEn;
        private Instant inicioAtencionEn;
        private Instant finAtencionEn;
        private String motivoCancelacion;
        private String motivoCancelacionPor;
        private Instant canceladaEn;
        private String motivoReprogramacion;
        private String motivoReprogramacionPor;
        private Instant reprogramadaEn;
        private String notas;
        private String codigoTicket;
        private boolean adelantoRequerido;
        private BigDecimal adelantoMonto = BigDecimal.ZERO;
        private String adelantoEstado = "NO_APLICA";
        private String adelantoMedioPago;
        private String adelantoNumeroOperacion;
        private String adelantoComprobanteUrl;
        private String adelantoRegistradoPor;
        private OffsetDateTime adelantoRegistradoEn;
        private OffsetDateTime adelantoLimitePago;
        private String estadoConfirmacion = "SIN_CONTACTAR";
        private String ultimoContactoMedio;
        private OffsetDateTime ultimoContactoFecha;
        private String ultimoContactoUsuario;
        private String ultimoContactoResultado;
        private UUID tratamientoId;
        private UUID doctorDerivadorId;
        private boolean pacienteDirecto;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder pacienteId(UUID pacienteId) { this.pacienteId = pacienteId; return this; }
        public Builder pacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; return this; }
        public Builder pacienteTelefono(String pacienteTelefono) { this.pacienteTelefono = pacienteTelefono; return this; }
        public Builder doctorId(UUID doctorId) { this.doctorId = doctorId; return this; }
        public Builder doctorNombre(String doctorNombre) { this.doctorNombre = doctorNombre; return this; }
        public Builder sedeId(String sedeId) { this.sedeId = sedeId; return this; }
        public Builder sillonNumero(Integer sillonNumero) { this.sillonNumero = sillonNumero; return this; }
        public Builder motivoConsulta(String motivoConsulta) { this.motivoConsulta = motivoConsulta; return this; }
        public Builder fecha(LocalDate fecha) { this.fecha = fecha; return this; }
        public Builder hora(LocalTime hora) { this.hora = hora; return this; }
        public Builder duracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder llegadaEn(Instant llegadaEn) { this.llegadaEn = llegadaEn; return this; }
        public Builder inicioAtencionEn(Instant inicioAtencionEn) { this.inicioAtencionEn = inicioAtencionEn; return this; }
        public Builder finAtencionEn(Instant finAtencionEn) { this.finAtencionEn = finAtencionEn; return this; }
        public Builder motivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; return this; }
        public Builder motivoCancelacionPor(String motivoCancelacionPor) { this.motivoCancelacionPor = motivoCancelacionPor; return this; }
        public Builder canceladaEn(Instant canceladaEn) { this.canceladaEn = canceladaEn; return this; }
        public Builder motivoReprogramacion(String motivoReprogramacion) { this.motivoReprogramacion = motivoReprogramacion; return this; }
        public Builder motivoReprogramacionPor(String motivoReprogramacionPor) { this.motivoReprogramacionPor = motivoReprogramacionPor; return this; }
        public Builder reprogramadaEn(Instant reprogramadaEn) { this.reprogramadaEn = reprogramadaEn; return this; }
        public Builder notas(String notas) { this.notas = notas; return this; }
        public Builder codigoTicket(String codigoTicket) { this.codigoTicket = codigoTicket; return this; }
        public Builder adelantoRequerido(boolean adelantoRequerido) { this.adelantoRequerido = adelantoRequerido; return this; }
        public Builder adelantoMonto(BigDecimal adelantoMonto) { this.adelantoMonto = adelantoMonto; return this; }
        public Builder adelantoEstado(String adelantoEstado) { this.adelantoEstado = adelantoEstado; return this; }
        public Builder adelantoMedioPago(String adelantoMedioPago) { this.adelantoMedioPago = adelantoMedioPago; return this; }
        public Builder adelantoNumeroOperacion(String adelantoNumeroOperacion) { this.adelantoNumeroOperacion = adelantoNumeroOperacion; return this; }
        public Builder adelantoComprobanteUrl(String adelantoComprobanteUrl) { this.adelantoComprobanteUrl = adelantoComprobanteUrl; return this; }
        public Builder adelantoRegistradoPor(String adelantoRegistradoPor) { this.adelantoRegistradoPor = adelantoRegistradoPor; return this; }
        public Builder adelantoRegistradoEn(OffsetDateTime adelantoRegistradoEn) { this.adelantoRegistradoEn = adelantoRegistradoEn; return this; }
        public Builder adelantoLimitePago(OffsetDateTime adelantoLimitePago) { this.adelantoLimitePago = adelantoLimitePago; return this; }
        public Builder estadoConfirmacion(String estadoConfirmacion) { this.estadoConfirmacion = estadoConfirmacion; return this; }
        public Builder ultimoContactoMedio(String ultimoContactoMedio) { this.ultimoContactoMedio = ultimoContactoMedio; return this; }
        public Builder ultimoContactoFecha(OffsetDateTime ultimoContactoFecha) { this.ultimoContactoFecha = ultimoContactoFecha; return this; }
        public Builder ultimoContactoUsuario(String ultimoContactoUsuario) { this.ultimoContactoUsuario = ultimoContactoUsuario; return this; }
        public Builder ultimoContactoResultado(String ultimoContactoResultado) { this.ultimoContactoResultado = ultimoContactoResultado; return this; }
        public Builder tratamientoId(UUID tratamientoId) { this.tratamientoId = tratamientoId; return this; }
        public Builder doctorDerivadorId(UUID doctorDerivadorId) { this.doctorDerivadorId = doctorDerivadorId; return this; }
        public Builder pacienteDirecto(boolean pacienteDirecto) { this.pacienteDirecto = pacienteDirecto; return this; }

        public Cita build() {
            Cita cita = new Cita(id, pacienteId, pacienteNombre, pacienteTelefono, doctorId, doctorNombre,
                    sedeId, sillonNumero, motivoConsulta, fecha, hora, duracionMinutos,
                    estado, motivoCancelacion, motivoReprogramacion, notas, codigoTicket,
                    adelantoRequerido, adelantoMonto, adelantoEstado, adelantoMedioPago,
                    adelantoNumeroOperacion, adelantoComprobanteUrl, adelantoRegistradoPor,
                    adelantoRegistradoEn, adelantoLimitePago, estadoConfirmacion,
                    ultimoContactoMedio, ultimoContactoFecha, ultimoContactoUsuario,
                    ultimoContactoResultado);
            cita.tratamientoId = tratamientoId;
            cita.llegadaEn = llegadaEn;
            cita.inicioAtencionEn = inicioAtencionEn;
            cita.finAtencionEn = finAtencionEn;
            cita.motivoCancelacionPor = motivoCancelacionPor;
            cita.canceladaEn = canceladaEn;
            cita.motivoReprogramacionPor = motivoReprogramacionPor;
            cita.reprogramadaEn = reprogramadaEn;
            cita.doctorDerivadorId = doctorDerivadorId;
            cita.pacienteDirecto = pacienteDirecto;
            return cita;
        }
    }
}
