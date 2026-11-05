package com.odontomax.reportes.service;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi.MetaDoctor;
import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.PagoEntity;
import com.odontomax.infrastructure.persistence.entity.TratamientoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoRepository;
import com.odontomax.laboratorio.api.ConsultaLaboratorioModuloApi;
import com.odontomax.reportes.dto.MiAvanceResponseDTO;
import com.odontomax.reportes.dto.RendimientoDoctoresResponseDTO;
import com.odontomax.reportes.dto.RendimientoDoctoresResponseDTO.DoctorRendimientoDTO;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rendimiento mensual de los doctores de Villadent. Villamax es otro negocio y nunca entra en estos números.
 * Cobrado = pagos no anulados atribuidos al doctor; tiempos = marcas de llegada, inicio y fin de cada cita.
 */
@Service
@RequiredArgsConstructor
public class RendimientoDoctoresService {

    static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String UNIDAD_VILLADENT = "VILLADENT";

    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataTratamientoRepository tratamientoRepository;
    private final ConsultaCitasModuloApi consultaCitasApi;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private final ConsultaSedesModuloApi consultaSedesApi;
    private final ConsultaLaboratorioModuloApi consultaLaboratorioApi;

    @Transactional(readOnly = true)
    public RendimientoDoctoresResponseDTO obtenerRendimiento(String mes, String sedeId) {
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        if (usuario == null || !Set.of("ADMIN_GENERAL", "ADMIN_SEDE").contains(usuario.rol().toUpperCase())) {
            throw new AccessDeniedException("Acceso denegado: solo la administración consulta el rendimiento de los doctores.");
        }
        boolean esDueno = "ADMIN_GENERAL".equalsIgnoreCase(usuario.rol());
        YearMonth periodo = resolverMes(mes);

        List<String> sedesVilladent = sedesVilladent();
        List<String> sedesVisibles;
        String sedeConsultada = null;
        if (esDueno) {
            if (sedeId != null && !sedeId.isBlank()) {
                if (!sedesVilladent.contains(sedeId)) {
                    throw new IllegalArgumentException("La sede indicada no pertenece a Villadent.");
                }
                sedesVisibles = List.of(sedeId);
                sedeConsultada = sedeId;
            } else {
                sedesVisibles = sedesVilladent;
            }
        } else {
            String propia = usuario.sedeId();
            if (sedeId != null && !sedeId.isBlank()) {
                SecurityUtils.validarAccesoSede(sedeId);
            }
            if (propia == null || !sedesVilladent.contains(propia)) {
                throw new AccessDeniedException("Acceso denegado: su sede no tiene rendimiento de doctores.");
            }
            sedesVisibles = List.of(propia);
            sedeConsultada = propia;
        }

        Map<UUID, Map<String, BigDecimal>> cobradoPorDoctor = cobradoPorDoctorYSede(periodo, sedesVilladent);
        Map<UUID, BigDecimal> laboratorioPorDoctor =
                consultaLaboratorioApi.montoLaboratorioPorDoctor(sedesVilladent, periodo.atDay(1), periodo.atEndOfMonth());

        Map<UUID, List<CitaResponseDTO>> citasPorDoctor = new HashMap<>();
        for (String sede : sedesVisibles) {
            for (CitaResponseDTO c : consultaCitasApi.findByRangoFechasYSede(periodo.atDay(1), periodo.atEndOfMonth(), sede)) {
                if (c.getDoctorId() != null) {
                    citasPorDoctor.computeIfAbsent(c.getDoctorId(), k -> new ArrayList<>()).add(c);
                }
            }
        }

        Set<UUID> doctores = new LinkedHashSet<>(citasPorDoctor.keySet());
        for (String sede : sedesVisibles) {
            doctores.addAll(consultaEspecialistasApi.idsActivosEnSede(sede));
        }
        for (var entrada : cobradoPorDoctor.entrySet()) {
            if (entrada.getValue().keySet().stream().anyMatch(sedesVisibles::contains)) {
                doctores.add(entrada.getKey());
            }
        }

        List<DoctorRendimientoDTO> filas = new ArrayList<>();
        BigDecimal totalCobrado = BigDecimal.ZERO;
        for (UUID doctorId : doctores) {
            Map<String, BigDecimal> porSede = cobradoPorDoctor.getOrDefault(doctorId, Map.of());
            BigDecimal cobradoTotalDoctor = porSede.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, BigDecimal> visible = new LinkedHashMap<>();
            for (String sede : sedesVisibles) {
                visible.put(sede, porSede.getOrDefault(sede, BigDecimal.ZERO));
            }
            BigDecimal cobradoVisible = visible.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            totalCobrado = totalCobrado.add(cobradoVisible);

            // La meta es por mano de obra: lo de laboratorio se descuenta (sin pasar de lo cobrado).
            BigDecimal laboratorio = laboratorioPorDoctor.getOrDefault(doctorId, BigDecimal.ZERO).min(cobradoTotalDoctor);
            BigDecimal cobradoParaMeta = cobradoTotalDoctor.subtract(laboratorio);
            MetaDoctor meta = consultaEspecialistasApi.obtenerMeta(doctorId).orElse(new MetaDoctor(null, null));
            Metricas m = Metricas.de(citasPorDoctor.getOrDefault(doctorId, List.of()));
            filas.add(DoctorRendimientoDTO.builder()
                    .doctorId(doctorId)
                    .nombre(consultaEspecialistasApi.findNombreById(doctorId).orElse("Doctor"))
                    .cobrado(cobradoVisible)
                    .cobradoPorSede(visible)
                    .metaMensual(meta.metaMensual())
                    .laboratorioDescontado(laboratorio)
                    .cobradoParaMeta(cobradoParaMeta)
                    .porcentajeAvance(porcentajeAvance(cobradoParaMeta, meta.metaMensual()))
                    .comisionExcedentePorcentaje(esDueno ? meta.comisionExcedentePorcentaje() : null)
                    .comisionEstimada(esDueno ? meta.comisionEstimada(cobradoParaMeta) : null)
                    .citasAtendidas(m.atendidas())
                    .citasCanceladas(m.canceladas())
                    .citasNoAsistio(m.noAsistio())
                    .duracionRealPromedioMin(m.duracionReal())
                    .duracionPrevistaPromedioMin(m.duracionPrevista())
                    .porcentajeCitasQuePasaronDelTiempoPrevisto(m.porcentajeExcedidas())
                    .esperaPromedioMin(m.espera())
                    .build());
        }
        filas.sort(Comparator.comparing(DoctorRendimientoDTO::getCobrado).reversed()
                .thenComparing(DoctorRendimientoDTO::getNombre));

        List<RendimientoDoctoresResponseDTO.SedeDTO> sedes = sedesVisibles.stream()
                .map(id -> RendimientoDoctoresResponseDTO.SedeDTO.builder()
                        .id(id).nombre(consultaSedesApi.obtenerNombreSede(id).orElse(id)).build())
                .toList();
        return RendimientoDoctoresResponseDTO.builder()
                .mes(periodo.toString())
                .sedeId(sedeConsultada)
                .sedes(sedes)
                .totalCobrado(totalCobrado)
                .doctores(filas)
                .build();
    }

    @Transactional(readOnly = true)
    public MiAvanceResponseDTO obtenerMiAvance(String mes) {
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        if (usuario == null || !"ODONTOLOGO".equalsIgnoreCase(usuario.rol())) {
            throw new AccessDeniedException("Acceso denegado: solo un odontólogo consulta su propio avance.");
        }
        UUID doctorId = consultaEspecialistasApi.findEspecialistaIdByUsuarioId(usuario.id())
                .orElseThrow(() -> new AccessDeniedException("Acceso denegado: su usuario no tiene perfil de especialista."));
        YearMonth periodo = resolverMes(mes);
        List<String> sedesVilladent = sedesVilladent();

        BigDecimal cobrado = cobradoPorDoctorYSede(periodo, sedesVilladent)
                .getOrDefault(doctorId, Map.of()).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CitaResponseDTO> citas = new ArrayList<>();
        for (String sede : sedesVilladent) {
            consultaCitasApi.findByRangoFechasYSede(periodo.atDay(1), periodo.atEndOfMonth(), sede).stream()
                    .filter(c -> doctorId.equals(c.getDoctorId()))
                    .forEach(citas::add);
        }
        Metricas m = Metricas.de(citas);
        BigDecimal laboratorio = consultaLaboratorioApi
                .montoLaboratorioPorDoctor(sedesVilladent, periodo.atDay(1), periodo.atEndOfMonth())
                .getOrDefault(doctorId, BigDecimal.ZERO).min(cobrado);
        BigDecimal cobradoParaMeta = cobrado.subtract(laboratorio);
        MetaDoctor meta = consultaEspecialistasApi.obtenerMeta(doctorId).orElse(new MetaDoctor(null, null));
        return MiAvanceResponseDTO.builder()
                .mes(periodo.toString())
                .doctorId(doctorId)
                .nombre(consultaEspecialistasApi.findNombreById(doctorId).orElse("Doctor"))
                .cobrado(cobrado)
                .metaMensual(meta.metaMensual())
                .laboratorioDescontado(laboratorio)
                .cobradoParaMeta(cobradoParaMeta)
                .porcentajeAvance(porcentajeAvance(cobradoParaMeta, meta.metaMensual()))
                .citasAtendidas(m.atendidas())
                .duracionRealPromedioMin(m.duracionReal())
                .duracionPrevistaPromedioMin(m.duracionPrevista())
                .porcentajeCitasQuePasaronDelTiempoPrevisto(m.porcentajeExcedidas())
                .esperaPromedioMin(m.espera())
                .build();
    }

    private YearMonth resolverMes(String mes) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(ZONA_LIMA);
        }
        try {
            return YearMonth.parse(mes.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("El mes debe tener el formato aaaa-mm.");
        }
    }

    /** Sedes activas del negocio Villadent; Villamax queda fuera. */
    private List<String> sedesVilladent() {
        return consultaSedesApi.listarIdsSedesActivas().stream()
                .filter(id -> consultaSedesApi.obtenerConfiguracionSede(id)
                        .map(c -> UNIDAD_VILLADENT.equalsIgnoreCase(c.unidadDeNegocio()))
                        .orElse(false))
                .toList();
    }

    /** Cobrado del mes por doctor y sede, con los pagos no anulados de las sedes indicadas. */
    private Map<UUID, Map<String, BigDecimal>> cobradoPorDoctorYSede(YearMonth periodo, List<String> sedes) {
        Instant desde = periodo.atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        Instant hasta = periodo.plusMonths(1).atDay(1).atStartOfDay(ZONA_LIMA).toInstant().minusMillis(1);
        Map<UUID, UUID> doctorDelTratamiento = new HashMap<>();
        Map<UUID, Map<String, BigDecimal>> resultado = new HashMap<>();
        for (PagoEntity p : pagoRepository.findAllByRangoFechas(desde, hasta)) {
            if (Boolean.TRUE.equals(p.getAnulado()) || !sedes.contains(p.getSedeId())) {
                continue;
            }
            UUID doctor = p.getEspecialistaId();
            if (doctor == null && p.getTratamientoId() != null) {
                doctor = doctorDelTratamiento.computeIfAbsent(p.getTratamientoId(),
                        id -> tratamientoRepository.findById(id).map(TratamientoEntity::getEspecialistaId).orElse(null));
            }
            if (doctor != null) {
                resultado.computeIfAbsent(doctor, k -> new HashMap<>()).merge(p.getSedeId(), p.getMonto(), BigDecimal::add);
            }
        }
        return resultado;
    }

    private static BigDecimal porcentajeAvance(BigDecimal cobrado, BigDecimal meta) {
        if (meta == null || meta.signum() == 0) {
            return null;
        }
        return cobrado.multiply(BigDecimal.valueOf(100)).divide(meta, 1, RoundingMode.HALF_UP);
    }

    /** Conteos y promedios de tiempos de un conjunto de citas. Solo cuentan las citas con las marcas completas. */
    record Metricas(long atendidas, long canceladas, long noAsistio, Double duracionReal, Double duracionPrevista,
                    Double porcentajeExcedidas, Double espera) {

        static Metricas de(List<CitaResponseDTO> citas) {
            long atendidas = 0;
            long canceladas = 0;
            long noAsistio = 0;
            List<Long> reales = new ArrayList<>();
            List<Integer> previstas = new ArrayList<>();
            List<Long> esperas = new ArrayList<>();
            long excedidas = 0;
            for (CitaResponseDTO c : citas) {
                String estado = c.getEstado() == null ? "" : c.getEstado().toUpperCase();
                switch (estado) {
                    case "ATENDIDA" -> atendidas++;
                    case "CANCELADA" -> canceladas++;
                    case "NO_ASISTIO" -> noAsistio++;
                    default -> { }
                }
                if ("ATENDIDA".equals(estado) && c.getInicioAtencionEn() != null && c.getFinAtencionEn() != null) {
                    long real = Duration.between(c.getInicioAtencionEn(), c.getFinAtencionEn()).toMinutes();
                    reales.add(real);
                    previstas.add(c.getDuracionMinutos());
                    if (real > c.getDuracionMinutos()) {
                        excedidas++;
                    }
                }
                if (c.getLlegadaEn() != null && c.getInicioAtencionEn() != null) {
                    esperas.add(Duration.between(c.getLlegadaEn(), c.getInicioAtencionEn()).toMinutes());
                }
            }
            return new Metricas(atendidas, canceladas, noAsistio,
                    promedio(reales), promedio(previstas),
                    reales.isEmpty() ? null : redondear(excedidas * 100.0 / reales.size()),
                    promedio(esperas));
        }

        private static Double promedio(List<? extends Number> valores) {
            return valores.isEmpty() ? null
                    : redondear(valores.stream().mapToDouble(Number::doubleValue).average().orElse(0));
        }

        private static double redondear(double valor) {
            return Math.round(valor * 10.0) / 10.0;
        }
    }
}
