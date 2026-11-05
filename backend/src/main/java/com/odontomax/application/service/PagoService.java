package com.odontomax.application.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.domain.exception.ComprobanteSaldoPendienteException;
import com.odontomax.domain.exception.PagoSinTratamientoException;
import com.odontomax.application.dto.request.PagoAnulacionRequestDTO;
import com.odontomax.application.dto.request.PagoCreateRequestDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.domain.port.out.ComprobanteElectronicoPort;
import com.odontomax.infrastructure.persistence.entity.*;
import com.odontomax.infrastructure.persistence.repository.*;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.tratamientos.api.TratamientoCobroModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataPacienteRepository pacienteRepository;
    private final SpringDataTratamientoRepository tratamientoRepository;
    private final SpringDataCajaSesionRepository cajaSesionRepository;
    private final SpringDataCajaMovimientoRepository cajaMovimientoRepository;
    private final SpringDataAuditoriaRepository auditoriaRepository;
    private final ComprobanteElectronicoPort comprobantePort;
    private final CajaService cajaService;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private TratamientoCobroModuloApi tratamientoCobroApi;

    private com.odontomax.radiologia.service.ComisionRadiologicaService comisionRadiologicaService;

    @Autowired(required = false)
    public void setComisionRadiologicaService(com.odontomax.radiologia.service.ComisionRadiologicaService comisionRadiologicaService) {
        this.comisionRadiologicaService = comisionRadiologicaService;
    }

    private com.odontomax.agenda.api.CobroDeCitaModuloApi cobroDeCitaApi;

    @Autowired(required = false)
    public void setCobroDeCitaApi(com.odontomax.agenda.api.CobroDeCitaModuloApi cobroDeCitaApi) {
        this.cobroDeCitaApi = cobroDeCitaApi;
    }

    @Autowired
    public void setTratamientoCobroApi(TratamientoCobroModuloApi tratamientoCobroApi) {
        this.tratamientoCobroApi = tratamientoCobroApi;
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDTO> listarPorPaciente(UUID pacienteId) {
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        return soloLosDelOdontologo(pagoRepository.findByPacienteIdOrderByPagadoEnDesc(pacienteId))
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDTO> listarPorSede(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return soloLosDelOdontologo(pagoRepository.findBySedeIdOrderByPagadoEnDesc(sedeId))
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /** Un odontólogo solo ve los cobros atribuidos a su especialista; el resto de roles, todos los de su sede. */
    private List<PagoEntity> soloLosDelOdontologo(List<PagoEntity> pagos) {
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        if (usuario == null || !"ODONTOLOGO".equalsIgnoreCase(usuario.rol())) {
            return pagos;
        }
        Optional<UUID> propio = consultaEspecialistasApi.findEspecialistaIdByUsuarioId(usuario.id());
        return propio.map(id -> pagos.stream().filter(p -> id.equals(p.getEspecialistaId())).toList()).orElse(List.of());
    }

    /** Doctor al que se atribuye el cobro: el propio si cobra un odontólogo; si no, el elegido o el del tratamiento. */
    private UUID resolverEspecialista(ClinicaUserPrincipal usuario, UUID solicitado, UUID especialistaTratamiento) {
        if ("ODONTOLOGO".equalsIgnoreCase(usuario.rol())) {
            return consultaEspecialistasApi.findEspecialistaIdByUsuarioId(usuario.id())
                    .orElseThrow(() -> new IllegalArgumentException("Su usuario no tiene perfil de especialista para registrar cobros"));
        }
        UUID elegido = solicitado != null ? solicitado : especialistaTratamiento;
        if (elegido != null && !consultaEspecialistasApi.existsById(elegido)) {
            throw new IllegalArgumentException("Especialista no encontrado");
        }
        return elegido;
    }

    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPorId(UUID id) {
        PagoEntity pago = pagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado con ID: " + id));

        SecurityUtils.validarAccesoSede(pago.getSedeId());
        if (soloLosDelOdontologo(List.of(pago)).isEmpty()) {
            throw new AccessDeniedException("Acceso denegado: el cobro no está atribuido a este odontólogo.");
        }
        return toResponseDTO(pago);
    }

    @Transactional
    public PagoResponseDTO registrarPago(PagoCreateRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        SecurityUtils.validarAccesoSede(dto.getSedeId());

        consultaEspecialistasApi.exigirAccesoDeOdontologo(dto.getPacienteId());

        PacienteEntity paciente = pacienteRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado con ID: " + dto.getPacienteId()));

        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        if (dto.getTratamientoId() == null) {
            throw new PagoSinTratamientoException();
        }

        TratamientoCobroModuloApi.TratamientoParaCobro tratamiento =
                tratamientoCobroApi.validarSaldoParaCobro(paciente.getId(), dto.getTratamientoId(), dto.getMonto());

        String tipoComp = (dto.getComprobanteTipo() != null && !dto.getComprobanteTipo().isBlank())
                ? dto.getComprobanteTipo().toUpperCase().trim()
                : "RECIBO";

        if (("BOLETA".equals(tipoComp) || "FACTURA".equals(tipoComp))
                && tratamiento.saldoRestante().compareTo(BigDecimal.ZERO) > 0) {
            throw new ComprobanteSaldoPendienteException(tratamiento.saldoRestante());
        }

        String metodo = dto.getMetodoPago().toUpperCase().trim();
        if (!List.of("EFECTIVO", "YAPE", "PLIN", "TARJETA_DEBITO", "TARJETA_CREDITO").contains(metodo)) {
            throw new IllegalArgumentException("El método de pago debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO");
        }

        // 1. Vincular a sesión de caja abierta ignorando cajaSesionId del cliente
        Optional<CajaSesionEntity> sesionAbierta = cajaSesionRepository
                .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(dto.getSedeId(), currentUser.id())
                .or(() -> cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(dto.getSedeId()));

        // Todo cobro pertenece a una caja y genera un movimiento, también Yape y Plin.
        CajaSesionEntity sesionCaja;
        if (sesionAbierta.isPresent()) {
            sesionCaja = sesionAbierta.get();
        } else {
            sesionCaja = cajaService.obtenerOAbrirSesionAutomatica(dto.getSedeId());
        }
        UUID sesionCajaId = sesionCaja.getId();

        // 2. Emitir comprobante electrónico o recibo a través del puerto
        ComprobanteElectronicoPort.ComprobanteEmitido emitido = comprobantePort.emitir(
                new ComprobanteElectronicoPort.SolicitudEmision(
                        tipoComp,
                        dto.getSedeId(),
                        paciente.getNombreCompleto(),
                        paciente.getDni() != null ? paciente.getDni() : "00000000",
                        paciente.getDni() != null ? "DNI" : "SIN_DOCUMENTO",
                        dto.getMonto(),
                        metodo,
                        tratamiento.nombre(),
                        dto.getTratamientoId() != null ? dto.getTratamientoId().toString() : null,
                        tratamiento.saldoRestante()
                )
        );

        // 3. Crear entidad de Pago
        PagoEntity pago = PagoEntity.builder()
                .sedeId(dto.getSedeId())
                .pacienteId(paciente.getId())
                .tratamientoId(dto.getTratamientoId())
                .monto(dto.getMonto())
                .metodoPago(metodo)
                .numeroOperacion(dto.getNumeroOperacion())
                .comprobante(emitido.serie() + "-" + emitido.numero())
                .comprobanteTipo(emitido.tipoComprobante())
                .comprobanteSerie(emitido.serie())
                .comprobanteNumero(emitido.numero())
                .comprobanteHash(emitido.codigoHash())
                .cajaSesionId(sesionCajaId)
                .especialistaId(resolverEspecialista(currentUser, dto.getEspecialistaId(), tratamiento.especialistaId()))
                .registradoPor(currentUser.id())
                .anulado(false)
                .build();

        PagoEntity guardado = pagoRepository.save(pago);

        // 4. Registrar todos los cobros en movimientos; la lectura histórica agrega solo pagos sin movimiento.
        final String nombreTratamientoFinal = tratamiento.nombre();
        String conceptoMovimiento = String.format(
                "Pago de %s: %s (Comp: %s)",
                paciente.getNombreCompleto(), nombreTratamientoFinal,
                guardado.getComprobante());

        cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                .sesion(sesionCaja)
                .tipo("INGRESO")
                .concepto(conceptoMovimiento)
                .monto(dto.getMonto())
                .metodoPago(metodo)
                .referenciaTipo("PAGO_TRATAMIENTO")
                .referenciaId(guardado.getId())
                .registradoPor(currentUser.id())
                .build());

        String detalleAuditoria = String.format(
                "Cobro de S/ %s registrado (%s) para paciente %s. Comprobante: %s",
                guardado.getMonto(), metodo, paciente.getNombreCompleto(),
                guardado.getComprobante());

        // 5. Registrar auditoría inmutable
        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(dto.getSedeId())
                .modulo("PAGOS")
                .accion("COBRO_REGISTRADO")
                .tabla("pagos")
                .registroId(guardado.getId().toString())
                .detalle(
                        detalleAuditoria)
                .build());

        tratamientoCobroApi.verificarYCompletarSiSaldoCero(dto.getTratamientoId());
        // Si el estudio de Villamax ya estaba atendido, este cobro puede ser el que lo completa: nace la comisión.
        if (cobroDeCitaApi != null) {
            cobroDeCitaApi.alRegistrarseCobro(dto.getTratamientoId());
        }

        return toResponseDTO(guardado);
    }

    @Transactional
    public PagoResponseDTO anularPago(UUID id, PagoAnulacionRequestDTO dto) {
        ClinicaUserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("No autenticado");
        }

        PagoEntity pago = pagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado con ID: " + id));

        String sedeId = pago.getSedeId();
        SecurityUtils.validarAccesoSede(sedeId);

        if (Boolean.TRUE.equals(pago.getAnulado())) {
            throw new IllegalStateException("El pago ya ha sido anulado previamente");
        }

        pago.setAnulado(true);
        pago.setAnuladoEn(Instant.now());
        pago.setAnuladoPor(currentUser.id());
        pago.setMotivoAnulacion(dto.getMotivoAnulacion());

        PagoEntity actualizado = pagoRepository.save(pago);

        // La anulación revierte el ingreso original con el mismo método de pago.
        String metodoPago = pago.getMetodoPago();
        Optional<CajaSesionEntity> sesionActiva = cajaSesionRepository
                .findBySedeIdAndAbiertaPorAndCerradaEnIsNull(sedeId, currentUser.id())
                .or(() -> cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId));
        if (sesionActiva.isEmpty() && "EFECTIVO".equalsIgnoreCase(metodoPago)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede anular un pago en EFECTIVO porque no existe ninguna caja abierta actualmente en la sede para registrar el EGRESO compensatorio."
            );
        }
        CajaSesionEntity sesionDestino = sesionActiva
                .orElseGet(() -> cajaService.obtenerOAbrirSesionAutomatica(
                        sedeId));
        // Los pagos digitales también deben revertirse en una sesión para cuadrar su método.
        cajaMovimientoRepository.save(CajaMovimientoEntity.builder()
                .sesion(sesionDestino)
                .tipo("EGRESO")
                .concepto(String.format("Anulación de pago (%s): %s", pago.getComprobante(), dto.getMotivoAnulacion()))
                .monto(pago.getMonto())
                .metodoPago(metodoPago)
                .referenciaTipo("PAGO_TRATAMIENTO")
                .referenciaId(pago.getId())
                .registradoPor(currentUser.id())
                .build());

        // Comunicar baja o anulación al adaptador SUNAT
        if (pago.getComprobanteSerie() != null && pago.getComprobanteNumero() != null) {
            comprobantePort.anular(pago.getComprobanteSerie(), pago.getComprobanteNumero(), dto.getMotivoAnulacion());
        }

        // Registrar auditoría de anulación
        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(currentUser.id())
                .usuarioNombre(currentUser.nombre())
                .rol(currentUser.rol())
                .sedeId(sedeId)
                .modulo("PAGOS")
                .accion("ANULAR_PAGO")
                .tabla("pagos")
                .registroId(id.toString())
                .detalle(String.format("Pago de S/ %s anulado. Motivo: %s", pago.getMonto(), dto.getMotivoAnulacion()))
                .build());

        PagoResponseDTO response = toResponseDTO(actualizado);
        if (response.getTratamientoId() != null && comisionRadiologicaService != null) {
            try {
                comisionRadiologicaService.anularComisionPorTratamiento(response.getTratamientoId(), dto.getMotivoAnulacion());
            } catch (Exception e) {
                // Continuar sin romper la anulación del pago
            }
        }
        return response;
    }

    private PagoResponseDTO toResponseDTO(PagoEntity p) {
        String pacienteNombre = "Paciente";
        String pacienteDni = "";
        Optional<PacienteEntity> pac = pacienteRepository.findById(p.getPacienteId());
        if (pac.isPresent()) {
            pacienteNombre = pac.get().getNombreCompleto();
            pacienteDni = pac.get().getDni();
        }

        String tratamientoNombre = "Abono a Cuenta";
        if (p.getTratamientoId() != null) {
            tratamientoNombre = tratamientoRepository.findById(p.getTratamientoId())
                    .map(TratamientoEntity::getNombre)
                    .orElse("Tratamiento");
        }

        return PagoResponseDTO.builder()
                .id(p.getId())
                .sedeId(p.getSedeId())
                .sedeNombre(p.getSedeId())
                .pacienteId(p.getPacienteId())
                .pacienteNombre(pacienteNombre)
                .pacienteDni(pacienteDni)
                .tratamientoId(p.getTratamientoId())
                .tratamientoNombre(tratamientoNombre)
                .monto(p.getMonto())
                .metodoPago(p.getMetodoPago())
                .numeroOperacion(p.getNumeroOperacion())
                .comprobante(p.getComprobante())
                .comprobanteTipo(p.getComprobanteTipo())
                .comprobanteSerie(p.getComprobanteSerie())
                .comprobanteNumero(p.getComprobanteNumero())
                .comprobanteHash(p.getComprobanteHash())
                .cajaSesionId(p.getCajaSesionId())
                .especialistaId(p.getEspecialistaId())
                .especialistaNombre(p.getEspecialistaId() == null ? null
                        : consultaEspecialistasApi.findNombreById(p.getEspecialistaId()).orElse(null))
                .pagadoEn(p.getPagadoEn())
                .registradoPor(p.getRegistradoPor())
                .anulado(p.getAnulado())
                .anuladoEn(p.getAnuladoEn())
                .anuladoPor(p.getAnuladoPor())
                .motivoAnulacion(p.getMotivoAnulacion())
                .creadoEn(p.getCreadoEn())
                .build();
    }
}
