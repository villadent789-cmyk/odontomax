package com.odontomax.radiologia.service;

import com.odontomax.radiologia.dto.ComisionRadiologicaCreateRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaLiquidarRequestDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaResponseDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorResponseDTO;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import com.odontomax.application.dto.response.ResumenComisionesResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import com.odontomax.infrastructure.persistence.entity.DoctorDerivadorEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataDoctorDerivadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComisionRadiologicaService {

    private static final Set<String> METODOS_DE_CAJA = Set.of("EFECTIVO", "YAPE", "PLIN");

    private final SpringDataDoctorDerivadorRepository doctorDerivadorRepository;
    private final SpringDataComisionRadiologicaRepository comisionRepository;
    private final ConsultaSedesModuloApi sedesApi;
    private final ConsultaEspecialistasModuloApi especialistasApi;
    private final CajaEgresoModuloApi cajaEgresoApi;

    // =========================================================================
    // Catálogo de Doctores Derivadores
    // =========================================================================

    @Transactional(readOnly = true)
    public List<DoctorDerivadorResponseDTO> listarDoctoresDerivadores(Boolean soloActivos) {
        String negocio = SecurityUtils.negocioDeLaPeticion();
        if ("VILLADENT".equalsIgnoreCase(negocio) || "VILLADENT".equalsIgnoreCase(SecurityUtils.negocioActivo())) {
            return List.of();
        }
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if ("ODONTOLOGO".equalsIgnoreCase(user.rol())) {
                if (!atiendeEn("sede-villamax-chilca", user)) {
                    return List.of();
                }
            } else if (!"sede-villamax-chilca".equalsIgnoreCase(user.sedeId())) {
                return List.of();
            }
        }

        List<DoctorDerivadorEntity> doctores = Boolean.TRUE.equals(soloActivos)
                ? doctorDerivadorRepository.findByActivoTrueOrderByNombreAsc()
                : doctorDerivadorRepository.findAllByOrderByNombreAsc();

        return doctores.stream().map(doc -> {
            BigDecimal pendientes = comisionRepository.sumComisionesPendientesPorDoctor(doc.getId());
            long totalEstudios = comisionRepository.findByDoctorDerivadorIdOrderByFechaEmisionDesc(doc.getId()).size();
            return DoctorDerivadorResponseDTO.builder()
                    .id(doc.getId())
                    .nombre(doc.getNombre())
                    .cop(doc.getCop())
                    .clinicaOrigen(doc.getClinicaOrigen())
                    .telefono(doc.getTelefono())
                    .banco(doc.getBanco())
                    .numeroCuentaOYape(doc.getNumeroCuentaOYape())
                    .porcentajeOTarifaFija(doc.getPorcentajeOTarifaFija())
                    .activo(doc.isActivo())
                    .notas(doc.getNotas())
                    .totalComisionesPendientes(pendientes != null ? pendientes : BigDecimal.ZERO)
                    .cantidadEstudiosDerivados(totalEstudios)
                    .creadoEn(doc.getCreadoEn())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DoctorDerivadorResponseDTO obtenerDoctorDerivador(UUID id) {
        DoctorDerivadorEntity doc = doctorDerivadorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor derivador no encontrado"));

        BigDecimal pendientes = comisionRepository.sumComisionesPendientesPorDoctor(doc.getId());
        long totalEstudios = comisionRepository.findByDoctorDerivadorIdOrderByFechaEmisionDesc(doc.getId()).size();

        return DoctorDerivadorResponseDTO.builder()
                .id(doc.getId())
                .nombre(doc.getNombre())
                .cop(doc.getCop())
                .clinicaOrigen(doc.getClinicaOrigen())
                .telefono(doc.getTelefono())
                .banco(doc.getBanco())
                .numeroCuentaOYape(doc.getNumeroCuentaOYape())
                .porcentajeOTarifaFija(doc.getPorcentajeOTarifaFija())
                .activo(doc.isActivo())
                .notas(doc.getNotas())
                .totalComisionesPendientes(pendientes != null ? pendientes : BigDecimal.ZERO)
                .cantidadEstudiosDerivados(totalEstudios)
                .creadoEn(doc.getCreadoEn())
                .build();
    }

    @Transactional
    public DoctorDerivadorResponseDTO crearDoctorDerivador(DoctorDerivadorRequestDTO req) {
        DoctorDerivadorEntity entidad = DoctorDerivadorEntity.builder()
                .nombre(req.getNombre())
                .cop(req.getCop())
                .clinicaOrigen(req.getClinicaOrigen())
                .telefono(req.getTelefono())
                .banco(req.getBanco())
                .numeroCuentaOYape(req.getNumeroCuentaOYape())
                .porcentajeOTarifaFija(req.getPorcentajeOTarifaFija() != null ? req.getPorcentajeOTarifaFija() : BigDecimal.valueOf(10.00))
                .activo(req.getActivo() == null || req.getActivo())
                .notas(req.getNotas())
                .build();

        DoctorDerivadorEntity guardado = doctorDerivadorRepository.save(entidad);
        return obtenerDoctorDerivador(guardado.getId());
    }

    @Transactional
    public DoctorDerivadorResponseDTO actualizarDoctorDerivador(UUID id, DoctorDerivadorRequestDTO req) {
        DoctorDerivadorEntity doc = doctorDerivadorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor derivador no encontrado"));

        doc.setNombre(req.getNombre());
        doc.setCop(req.getCop());
        doc.setClinicaOrigen(req.getClinicaOrigen());
        doc.setTelefono(req.getTelefono());
        doc.setBanco(req.getBanco());
        doc.setNumeroCuentaOYape(req.getNumeroCuentaOYape());
        if (req.getPorcentajeOTarifaFija() != null) {
            doc.setPorcentajeOTarifaFija(req.getPorcentajeOTarifaFija());
        }
        if (req.getActivo() != null) {
            doc.setActivo(req.getActivo());
        }
        doc.setNotas(req.getNotas());

        DoctorDerivadorEntity guardado = doctorDerivadorRepository.save(doc);
        return obtenerDoctorDerivador(guardado.getId());
    }

    // =========================================================================
    // Gestión de Comisiones Radiológicas
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ComisionRadiologicaResponseDTO> listarComisiones(UUID doctorDerivadorId, String estado) {
        String negocio = SecurityUtils.negocioDeLaPeticion();
        if ("VILLADENT".equalsIgnoreCase(negocio) || "VILLADENT".equalsIgnoreCase(SecurityUtils.negocioActivo())) {
            return List.of();
        }

        List<ComisionRadiologicaEntity> lista;
        if (doctorDerivadorId != null && estado != null && !estado.isBlank()) {
            lista = comisionRepository.findByDoctorDerivadorIdAndEstadoOrderByFechaEmisionDesc(doctorDerivadorId, estado.toUpperCase());
        } else if (doctorDerivadorId != null) {
            lista = comisionRepository.findByDoctorDerivadorIdOrderByFechaEmisionDesc(doctorDerivadorId);
        } else if (estado != null && !estado.isBlank()) {
            lista = comisionRepository.findByEstadoOrderByFechaEmisionDesc(estado.toUpperCase());
        } else {
            lista = comisionRepository.findAllByOrderByFechaEmisionDesc();
        }

        Map<UUID, DoctorDerivadorEntity> doctoresMap = doctorDerivadorRepository.findAll().stream()
                .collect(Collectors.toMap(DoctorDerivadorEntity::getId, d -> d));

        return lista.stream()
                .filter(c -> puedeVer(c.getSedeId()))
                .map(c -> mapearComisionResponse(c, doctoresMap.get(c.getDoctorDerivadorId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ComisionRadiologicaResponseDTO obtenerComisionPorId(UUID id) {
        ComisionRadiologicaEntity comision = comisionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comisión radiológica no encontrada"));
        exigirAcceso(comision.getSedeId(), SecurityUtils.getCurrentUser());

        DoctorDerivadorEntity doc = doctorDerivadorRepository.findById(comision.getDoctorDerivadorId()).orElse(null);
        return mapearComisionResponse(comision, doc);
    }

    @Transactional
    public ComisionRadiologicaResponseDTO registrarComision(ComisionRadiologicaCreateRequestDTO req, ClinicaUserPrincipal user) {
        DoctorDerivadorEntity doc = doctorDerivadorRepository.findById(req.getDoctorDerivadorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor derivador no encontrado"));

        String sedeId = req.getSedeId() != null ? req.getSedeId() : "sede-villamax-chilca";
        exigirAcceso(sedeId, user);

        // Con el estudio del catálogo, el nombre, el precio y la comisión salen de él; lo escrito a mano manda.
        String tipoEstudio = req.getTipoEstudio();
        BigDecimal precio = req.getPrecioCobrado();
        BigDecimal monto = req.getMontoComision();
        int cantidad = req.getCantidad() != null ? req.getCantidad() : 1;
        boolean esEstudiante = Boolean.TRUE.equals(req.getEsEstudiante());
        if (esEstudiante && precio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para un paciente estudiante indica el precio convenido: es un precio manual");
        }
        if (req.getEstudioId() != null && !req.getEstudioId().isBlank()) {
            var estudio = sedesApi.obtenerEstudioDeVillamax(req.getEstudioId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estudio no existe en el catálogo de Villamax"));
            tipoEstudio = estudio.nombre();
            if (precio == null && estudio.precio() != null) {
                precio = estudio.precio().multiply(BigDecimal.valueOf(cantidad));
            }
            if (monto == null && estudio.comisionFija() != null) {
                // Monto fijo por estudio × cantidad (10 radiografías de S/ 10 = S/ 100).
                monto = estudio.comisionFija().multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
            } else if (monto == null && precio != null) {
                BigDecimal porcentaje = estudio.comisionPorcentaje() != null ? estudio.comisionPorcentaje() : doc.getPorcentajeOTarifaFija();
                monto = precio.multiply(porcentaje).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }
        if (esEstudiante) {
            monto = BigDecimal.ZERO;
        }
        if (tipoEstudio == null || tipoEstudio.isBlank() || precio == null || monto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indica el estudio del catálogo, o el tipo de estudio con su precio y su comisión");
        }

        ComisionRadiologicaEntity comision = ComisionRadiologicaEntity.builder()
                .doctorDerivadorId(doc.getId())
                .pacienteNombre(req.getPacienteNombre())
                .pacienteId(req.getPacienteId())
                .citaId(req.getCitaId())
                .tratamientoId(req.getTratamientoId())
                .tipoEstudio(tipoEstudio)
                .precioCobrado(precio)
                .montoComision(monto)
                .cantidad(cantidad)
                .esEstudiante(esEstudiante)
                .estado("PENDIENTE")
                .sedeId(sedeId)
                .nota(req.getNota())
                .registradoPor(user != null ? user.nombre() : "Recepción")
                .build();

        ComisionRadiologicaEntity guardada = comisionRepository.save(comision);
        log.info("Comisión registrada para doctor {} por estudio {}. Monto: S/ {}",
                doc.getNombre(), guardada.getTipoEstudio(), guardada.getMontoComision());

        return mapearComisionResponse(guardada, doc);
    }

    @Transactional
    public ComisionRadiologicaResponseDTO liquidarComision(UUID id, ComisionRadiologicaLiquidarRequestDTO req, ClinicaUserPrincipal user) {
        ComisionRadiologicaEntity comision = comisionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comisión radiológica no encontrada"));

        exigirAcceso(comision.getSedeId(), user);
        if ("LIQUIDADO".equalsIgnoreCase(comision.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La comisión ya ha sido liquidada previamente");
        }
        // La comisión sale de la caja de Villamax, que solo maneja estos tres métodos.
        String metodo = req.getMetodoPago() != null ? req.getMetodoPago().trim().toUpperCase() : "YAPE";
        if (!METODOS_DE_CAJA.contains(metodo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El método de pago debe ser EFECTIVO, YAPE o PLIN");
        }

        comision.setEstado("LIQUIDADO");
        comision.setFechaLiquidacion(OffsetDateTime.now());
        comision.setMetodoPago(metodo);
        comision.setCodigoOperacion(req.getCodigoOperacion());
        if (req.getNota() != null && !req.getNota().isBlank()) {
            String notaExistente = comision.getNota() != null ? comision.getNota() + " | " : "";
            comision.setNota(notaExistente + req.getNota());
        }

        ComisionRadiologicaEntity guardada = comisionRepository.save(comision);
        DoctorDerivadorEntity doc = doctorDerivadorRepository.findById(guardada.getDoctorDerivadorId()).orElse(null);
        if (guardada.getMontoComision().signum() > 0) {
            String concepto = String.format("Comisión %s · %s · %s",
                    doc != null ? doc.getNombre() : "doctor derivador", guardada.getTipoEstudio(), guardada.getPacienteNombre());
            cajaEgresoApi.registrarEgresoDeSistema(guardada.getSedeId(), guardada.getMontoComision(), metodo,
                    concepto, "COMISION_RADIOLOGICA", guardada.getId());
        }

        log.info("Comisión {} liquidada vía {} por {}", id, guardada.getMetodoPago(), user != null ? user.nombre() : "Usuario");
        return mapearComisionResponse(guardada, doc);
    }

    @Transactional(readOnly = true)
    public ResumenComisionesResponseDTO obtenerResumen() {
        String negocio = SecurityUtils.negocioDeLaPeticion();
        if ("VILLADENT".equalsIgnoreCase(negocio) || "VILLADENT".equalsIgnoreCase(SecurityUtils.negocioActivo())) {
            return ResumenComisionesResponseDTO.builder()
                    .totalMontoPendiente(BigDecimal.ZERO)
                    .cantidadPendientes(0L)
                    .totalMontoLiquidado(BigDecimal.ZERO)
                    .cantidadLiquidados(0L)
                    .totalDoctoresDerivadoresActivos(0L)
                    .build();
        }

        List<ComisionRadiologicaEntity> todas = comisionRepository.findAll().stream()
                .filter(c -> puedeVer(c.getSedeId()))
                .toList();
        BigDecimal montoPendiente = BigDecimal.ZERO;
        long cantidadPendientes = 0;
        BigDecimal montoLiquidado = BigDecimal.ZERO;
        long cantidadLiquidados = 0;

        for (ComisionRadiologicaEntity c : todas) {
            if ("PENDIENTE".equalsIgnoreCase(c.getEstado())) {
                montoPendiente = montoPendiente.add(c.getMontoComision());
                cantidadPendientes++;
            } else if ("LIQUIDADO".equalsIgnoreCase(c.getEstado())) {
                montoLiquidado = montoLiquidado.add(c.getMontoComision());
                cantidadLiquidados++;
            }
        }

        long doctoresActivos = doctorDerivadorRepository.findByActivoTrueOrderByNombreAsc().size();

        return ResumenComisionesResponseDTO.builder()
                .totalMontoPendiente(montoPendiente)
                .cantidadPendientes(cantidadPendientes)
                .totalMontoLiquidado(montoLiquidado)
                .cantidadLiquidados(cantidadLiquidados)
                .totalDoctoresDerivadoresActivos(doctoresActivos)
                .build();
    }

    /**
     * Las comisiones son de Villamax y de nadie más. El administrador general las ve según el negocio activo, quien
     * tiene sede solo las de su sede, y un odontólogo solo las de la sede de Villamax donde atiende.
     */
    private boolean puedeVer(String sedeId) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return true;
        }
        if ("ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            return SecurityUtils.sedeEnNegocioActivo(sedeId);
        }
        return "ODONTOLOGO".equalsIgnoreCase(user.rol())
                ? atiendeEn(sedeId, user)
                : sedeId != null && sedeId.equals(user.sedeId());
    }

    private boolean atiendeEn(String sedeId, ClinicaUserPrincipal user) {
        return especialistasApi.findEspecialistaIdByUsuarioId(user.id())
                .map(id -> especialistasApi.idsActivosEnSede(sedeId).contains(id))
                .orElse(false);
    }

    private void exigirAcceso(String sedeId, ClinicaUserPrincipal user) {
        if (user == null || "ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            return;
        }
        if ("ODONTOLOGO".equalsIgnoreCase(user.rol())) {
            if (!atiendeEn(sedeId, user)) {
                throw new AccessDeniedException("Acceso denegado: no atiende en esta sede.");
            }
            return;
        }
        SecurityUtils.validarAccesoSede(sedeId);
    }

    /** La comisión de quien deriva solo la ve el administrador general; para el resto el monto va vacío. */
    private static boolean veComisiones() {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        return user != null && "ADMIN_GENERAL".equalsIgnoreCase(user.rol());
    }

    @Transactional
    public void anularComisionPorCita(UUID citaId, String motivo) {
        if (citaId == null) return;
        comisionRepository.findByCitaId(citaId).ifPresent(comision -> {
            if ("PENDIENTE".equalsIgnoreCase(comision.getEstado())) {
                comision.setEstado("ANULADO");
                String notaExistente = comision.getNota() != null ? comision.getNota() + " | " : "";
                comision.setNota(notaExistente + "Comisión anulada: " + (motivo != null ? motivo : "Cancelación de cita"));
                comisionRepository.save(comision);
                log.info("Comisión {} anulada para cita {}", comision.getId(), citaId);
            }
        });
    }

    @Transactional
    public void anularComisionPorTratamiento(UUID tratamientoId, String motivo) {
        if (tratamientoId == null) return;
        comisionRepository.findByTratamientoId(tratamientoId).ifPresent(comision -> {
            if ("PENDIENTE".equalsIgnoreCase(comision.getEstado())) {
                comision.setEstado("ANULADO");
                String notaExistente = comision.getNota() != null ? comision.getNota() + " | " : "";
                comision.setNota(notaExistente + "Comisión anulada: " + (motivo != null ? motivo : "Anulación de pago"));
                comisionRepository.save(comision);
                log.info("Comisión {} anulada para tratamiento {}", comision.getId(), tratamientoId);
            }
        });
    }

    private ComisionRadiologicaResponseDTO mapearComisionResponse(ComisionRadiologicaEntity c, DoctorDerivadorEntity doc) {
        return ComisionRadiologicaResponseDTO.builder()
                .id(c.getId())
                .doctorDerivadorId(c.getDoctorDerivadorId())
                .doctorDerivadorNombre(doc != null ? doc.getNombre() : "Doctor Desconocido")
                .doctorDerivadorCop(doc != null ? doc.getCop() : null)
                .doctorDerivadorClinica(doc != null ? doc.getClinicaOrigen() : null)
                .doctorDerivadorTelefono(doc != null ? doc.getTelefono() : null)
                .doctorDerivadorCuenta(doc != null ? doc.getNumeroCuentaOYape() : null)
                .pacienteNombre(c.getPacienteNombre())
                .pacienteId(c.getPacienteId())
                .citaId(c.getCitaId())
                .tipoEstudio(c.getTipoEstudio())
                .precioCobrado(c.getPrecioCobrado())
                .cantidad(c.getCantidad())
                .esEstudiante(c.isEsEstudiante())
                .montoComision(veComisiones() ? c.getMontoComision() : null)
                .estado(c.getEstado())
                .fechaEmision(c.getFechaEmision())
                .fechaLiquidacion(c.getFechaLiquidacion())
                .metodoPago(c.getMetodoPago())
                .codigoOperacion(c.getCodigoOperacion())
                .nota(c.getNota())
                .sedeId(c.getSedeId())
                .registradoPor(c.getRegistradoPor())
                .creadoEn(c.getCreadoEn())
                .build();
    }
}
