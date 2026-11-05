package com.odontomax.reportes.service;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.response.*;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.infrastructure.persistence.entity.*;
import com.odontomax.infrastructure.persistence.repository.*;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoLaboratorioRepository;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import com.odontomax.reportes.dto.ReporteCitasRendimientoResponseDTO;
import com.odontomax.reportes.dto.ReporteCitasResponseDTO;
import com.odontomax.reportes.dto.ReporteDeudoresResponseDTO;
import com.odontomax.reportes.dto.ReporteDoctoresResponseDTO;
import com.odontomax.reportes.dto.ReporteFinancieroResponseDTO;
import com.odontomax.reportes.dto.ReporteInventarioResponseDTO;
import com.odontomax.reportes.dto.ReportePacientesResponseDTO;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final SpringDataPagoRepository pagoRepository;
    private final SpringDataGastoRepository gastoRepository;
    private final SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;
    private final SpringDataComisionRadiologicaRepository comisionRepository;
    private final SpringDataTratamientoRepository tratamientoRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final ConsultaCitasModuloApi consultaCitasModuloApi;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    private final ConsultaSedesModuloApi consultaSedesModuloApi;
    private final SpringDataProductoInventarioRepository productoInventarioRepository;
    private final SpringDataInventarioStockRepository inventarioStockRepository;
    private final SpringDataInventarioMovimientoRepository inventarioMovimientoRepository;
    private final SpringDataTransferenciaInventarioRepository transferenciaInventarioRepository;

    @Transactional(readOnly = true)
    public ReporteFinancieroResponseDTO obtenerReporteFinanciero(String sedeId, LocalDate desde, LocalDate hasta) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        LocalDate fInicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fFin = hasta != null ? hasta : LocalDate.now();

        Instant desdeInstant = fInicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant hastaInstant = fFin.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        List<PagoEntity> pagos;
        List<GastoEntity> gastos;
        List<PagoLaboratorioEntity> pagosLaboratorio;

        if (sedeEfectiva != null) {
            pagos = pagoRepository.findBySedeIdAndRangoFechas(sedeEfectiva, desdeInstant, hastaInstant)
                    .stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                    .toList();
            gastos = gastoRepository.findBySedeIdAndRangoFechas(sedeEfectiva, desdeInstant, hastaInstant)
                    .stream()
                    .filter(g -> !Boolean.TRUE.equals(g.getAnulado()))
                    .toList();
            pagosLaboratorio = pagoLaboratorioRepository
                    .findBySedeIdAndPagadoEnGreaterThanEqualAndPagadoEnLessThan(sedeEfectiva, desdeInstant, hastaInstant)
                    .stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                    .toList();
        } else {
            // "Todas las sedes" del administrador general = todas las del negocio activo.
            pagos = pagoRepository.findAllByRangoFechas(desdeInstant, hastaInstant)
                    .stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                    .filter(p -> SecurityUtils.sedeEnNegocioActivo(p.getSedeId()))
                    .toList();
            gastos = gastoRepository.findAllByRangoFechas(desdeInstant, hastaInstant)
                    .stream()
                    .filter(g -> !Boolean.TRUE.equals(g.getAnulado()))
                    .filter(g -> SecurityUtils.sedeEnNegocioActivo(g.getSedeId()))
                    .toList();
            pagosLaboratorio = pagoLaboratorioRepository
                    .findByPagadoEnGreaterThanEqualAndPagadoEnLessThan(desdeInstant, hastaInstant)
                    .stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                    .filter(p -> SecurityUtils.sedeEnNegocioActivo(p.getSedeId()))
                    .toList();
        }

        // Egresos que no están en la tabla de gastos: pagos a laboratorios y comisiones liquidadas a quien deriva.
        List<EgresoExtra> egresosExtra = new ArrayList<>();
        pagosLaboratorio.forEach(p -> egresosExtra.add(new EgresoExtra(p.getSedeId(), diaUtc(p.getPagadoEn()), p.getMonto(), "Laboratorio")));
        comisionRepository.findByEstadoAndFechaLiquidacionBetween("LIQUIDADO", desdeInstant.atOffset(ZoneOffset.UTC), hastaInstant.atOffset(ZoneOffset.UTC))
                .stream()
                .filter(c -> sedeEfectiva != null ? sedeEfectiva.equals(c.getSedeId()) : SecurityUtils.sedeEnNegocioActivo(c.getSedeId()))
                .forEach(c -> egresosExtra.add(new EgresoExtra(c.getSedeId(), diaUtc(c.getFechaLiquidacion().toInstant()),
                        c.getMontoComision(), "Comisiones radiológicas")));

        BigDecimal totalIngresos = pagos.stream()
                .map(PagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGastosOperativos = gastos.stream()
                .map(GastoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgresosExtra = egresosExtra.stream().map(EgresoExtra::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGastos = totalGastosOperativos.add(totalEgresosExtra);

        BigDecimal balanceNeto = totalIngresos.subtract(totalGastos);

        BigDecimal totalEfectivo = pagos.stream()
                .filter(p -> "EFECTIVO".equalsIgnoreCase(p.getMetodoPago()))
                .map(PagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalYape = pagos.stream()
                .filter(p -> "YAPE".equalsIgnoreCase(p.getMetodoPago()))
                .map(PagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTarjeta = pagos.stream()
                .filter(p -> p.getMetodoPago() != null && p.getMetodoPago().toUpperCase().contains("TARJETA"))
                .map(PagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTransferencia = pagos.stream()
                .filter(p -> "TRANSFERENCIA".equalsIgnoreCase(p.getMetodoPago()) || "PLIN".equalsIgnoreCase(p.getMetodoPago()))
                .map(PagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Cuentas por cobrar y tratamientos pendientes
        List<PacienteResumen> pacientes = pacientesApi.listarActivos(sedeEfectiva);

        BigDecimal cuentasPorCobrar = BigDecimal.ZERO;
        long tratamientosConSaldoPendiente = 0L;
        for (PacienteResumen pac : pacientes) {
            BigDecimal totalTratamientos = tratamientoRepository.sumCostoTotalByPacienteId(pac.id());
            BigDecimal totalAbonado = pagoRepository.sumMontoPagadoByPacienteId(pac.id());
            BigDecimal saldo = totalTratamientos.subtract(totalAbonado);
            if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                cuentasPorCobrar = cuentasPorCobrar.add(saldo);
                tratamientosConSaldoPendiente += tratamientoRepository.findByPacienteIdOrderByCreadoEnDesc(pac.id())
                        .stream()
                        .filter(t -> !"CANCELADO".equalsIgnoreCase(t.getEstado()) && !"COMPLETADO".equalsIgnoreCase(t.getEstado()))
                        .count();
            }
        }

        // Ingresos y gastos por día
        Map<LocalDate, BigDecimal> ingresosDiaMap = new HashMap<>();
        for (PagoEntity p : pagos) {
            Instant inst = p.getPagadoEn() != null ? p.getPagadoEn() : p.getCreadoEn();
            LocalDate fecha = inst != null ? inst.atZone(ZoneOffset.UTC).toLocalDate() : fInicio;
            ingresosDiaMap.merge(fecha, p.getMonto(), BigDecimal::add);
        }

        Map<LocalDate, BigDecimal> gastosDiaMap = new HashMap<>();
        for (GastoEntity g : gastos) {
            Instant inst = g.getGastadoEn() != null ? g.getGastadoEn() : g.getCreadoEn();
            LocalDate fecha = inst != null ? inst.atZone(ZoneOffset.UTC).toLocalDate() : fInicio;
            gastosDiaMap.merge(fecha, g.getMonto(), BigDecimal::add);
        }
        for (EgresoExtra egreso : egresosExtra) {
            gastosDiaMap.merge(egreso.fecha() != null ? egreso.fecha() : fInicio, egreso.monto(), BigDecimal::add);
        }

        Map<String, BigDecimal> gastosCategoriaMap = new TreeMap<>();
        for (GastoEntity gasto : gastos) {
            gastosCategoriaMap.merge(gasto.getCategoria(), gasto.getMonto(), BigDecimal::add);
        }
        for (EgresoExtra egreso : egresosExtra) {
            gastosCategoriaMap.merge(egreso.categoria(), egreso.monto(), BigDecimal::add);
        }
        List<ReporteFinancieroResponseDTO.GastoCategoriaDTO> gastosPorCategoria = gastosCategoriaMap.entrySet()
                .stream()
                .map(e -> ReporteFinancieroResponseDTO.GastoCategoriaDTO.builder()
                        .categoria(e.getKey())
                        .total(e.getValue())
                        .build())
                .toList();

        List<ReporteFinancieroResponseDTO.IngresoDiaDTO> ingresosPorDia = new ArrayList<>();
        LocalDate cur = fInicio;
        while (!cur.isAfter(fFin)) {
            BigDecimal ing = ingresosDiaMap.getOrDefault(cur, BigDecimal.ZERO);
            BigDecimal gas = gastosDiaMap.getOrDefault(cur, BigDecimal.ZERO);
            ingresosPorDia.add(ReporteFinancieroResponseDTO.IngresoDiaDTO.builder()
                    .fecha(cur)
                    .ingresos(ing)
                    .gastos(gas)
                    .balance(ing.subtract(gas))
                    .build());
            cur = cur.plusDays(1);
        }

        // Ingresos por método de pago
        Map<String, BigDecimal> metodoMontoMap = new HashMap<>();
        Map<String, Long> metodoConteoMap = new HashMap<>();
        for (PagoEntity p : pagos) {
            String m = p.getMetodoPago() != null ? p.getMetodoPago().toUpperCase() : "OTRO";
            metodoMontoMap.merge(m, p.getMonto(), BigDecimal::add);
            metodoConteoMap.merge(m, 1L, Long::sum);
        }
        List<ReporteFinancieroResponseDTO.IngresoMetodoDTO> ingresosPorMetodo = new ArrayList<>();
        metodoMontoMap.forEach((metodo, monto) -> {
            ingresosPorMetodo.add(ReporteFinancieroResponseDTO.IngresoMetodoDTO.builder()
                    .metodo(metodo)
                    .total(monto)
                    .cantidad(metodoConteoMap.getOrDefault(metodo, 0L))
                    .build());
        });
        ingresosPorMetodo.sort((a, b) -> b.getTotal().compareTo(a.getTotal()));

        // Agrupación por Doctor a través de tratamientos vinculados a pagos
        Map<UUID, BigDecimal> ingresosPorDoctorId = new HashMap<>();
        Map<UUID, Long> tratamientosPorDoctorId = new HashMap<>();

        for (PagoEntity p : pagos) {
            UUID doctorId = doctorDelPago(p);
            if (doctorId != null) {
                ingresosPorDoctorId.merge(doctorId, p.getMonto(), BigDecimal::add);
                tratamientosPorDoctorId.merge(doctorId, 1L, Long::sum);
            }
        }

        List<ReporteFinancieroResponseDTO.IngresoDoctorDTO> doctoresDTO = new ArrayList<>();
        ingresosPorDoctorId.forEach((docId, total) -> {
            String docNombre = consultaEspecialistasApi.findNombreById(docId)
                    .orElse("Dr. Odontólogo Especialista");

            doctoresDTO.add(ReporteFinancieroResponseDTO.IngresoDoctorDTO.builder()
                    .doctorId(docId)
                    .doctorNombre(docNombre)
                    .totalGenerado(total)
                    .cantidadTratamientos(tratamientosPorDoctorId.getOrDefault(docId, 0L))
                    .build());
        });

        // Agrupación por Procedimiento / Tratamiento
        Map<String, BigDecimal> ingresosPorConcepto = new HashMap<>();
        Map<String, Long> conteoPorConcepto = new HashMap<>();

        for (PagoEntity p : pagos) {
            String concepto = "Procedimiento Odontológico General";
            if (p.getTratamientoId() != null) {
                Optional<TratamientoEntity> t = tratamientoRepository.findById(p.getTratamientoId());
                if (t.isPresent() && t.get().getNombre() != null) {
                    concepto = t.get().getNombre();
                }
            }
            ingresosPorConcepto.merge(concepto, p.getMonto(), BigDecimal::add);
            conteoPorConcepto.merge(concepto, 1L, Long::sum);
        }

        List<ReporteFinancieroResponseDTO.IngresoTratamientoDTO> tratamientosDTO = new ArrayList<>();
        ingresosPorConcepto.forEach((proc, monto) -> {
            tratamientosDTO.add(ReporteFinancieroResponseDTO.IngresoTratamientoDTO.builder()
                    .procedimientoNombre(proc)
                    .totalIngresos(monto)
                    .cantidadRealizados(conteoPorConcepto.getOrDefault(proc, 0L))
                    .build());
        });

        // Agrupación por Sedes
        List<ReporteFinancieroResponseDTO.IngresoSedeDTO> sedesDTO = new ArrayList<>();
        List<String> sedesAProcesar = (sedeEfectiva != null)
                ? List.of(sedeEfectiva)
                : consultaSedesModuloApi.listarIdsSedesActivas().stream()
                        .filter(SecurityUtils::sedeEnNegocioActivo)
                        .toList();

        for (String sId : sedesAProcesar) {
            BigDecimal ingSede = pagos.stream()
                    .filter(p -> sId.equalsIgnoreCase(p.getSedeId()))
                    .map(PagoEntity::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal gasSede = gastos.stream()
                    .filter(g -> sId.equalsIgnoreCase(g.getSedeId()))
                    .map(GastoEntity::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            gasSede = gasSede.add(egresosExtra.stream()
                    .filter(e -> sId.equalsIgnoreCase(e.sedeId()))
                    .map(EgresoExtra::monto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            sedesDTO.add(ReporteFinancieroResponseDTO.IngresoSedeDTO.builder()
                    .sedeId(sId)
                    .sedeNombre(nombreSedeFormateado(sId))
                    .totalIngresos(ingSede)
                    .totalGastos(gasSede)
                    .balance(ingSede.subtract(gasSede))
                    .build());
        }

        return ReporteFinancieroResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .fechaInicio(fInicio)
                .fechaFin(fFin)
                .totalIngresos(totalIngresos)
                .totalGastos(totalGastos)
                .balanceNeto(balanceNeto)
                .totalEfectivo(totalEfectivo)
                .totalYape(totalYape)
                .totalTarjeta(totalTarjeta)
                .totalTransferencia(totalTransferencia)
                .cuentasPorCobrar(cuentasPorCobrar)
                .tratamientosConSaldoPendiente(tratamientosConSaldoPendiente)
                .ingresosPorDia(ingresosPorDia)
                .ingresosPorMetodo(ingresosPorMetodo)
                .ingresosPorDoctor(doctoresDTO)
                .ingresosPorTratamiento(tratamientosDTO)
                .ingresosPorSede(sedesDTO)
                .gastosPorCategoria(gastosPorCategoria)
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteDeudoresResponseDTO obtenerReporteDeudores(String sedeId) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        List<PacienteResumen> pacientes = pacientesApi.listarActivos(sedeEfectiva);

        List<ReporteDeudoresResponseDTO.PacienteDeudorDTO> deudores = new ArrayList<>();
        BigDecimal deudaTotal = BigDecimal.ZERO;

        for (PacienteResumen pac : pacientes) {
            BigDecimal totalTratamientos = tratamientoRepository.sumCostoTotalByPacienteId(pac.id());
            BigDecimal totalAbonado = pagoRepository.sumMontoPagadoByPacienteId(pac.id());

            BigDecimal saldo = totalTratamientos.subtract(totalAbonado);
            if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                long pendientes = tratamientoRepository.findByPacienteIdOrderByCreadoEnDesc(pac.id())
                        .stream()
                        .filter(t -> !"CANCELADO".equalsIgnoreCase(t.getEstado()) && !"COMPLETADO".equalsIgnoreCase(t.getEstado()))
                        .count();

                deudores.add(ReporteDeudoresResponseDTO.PacienteDeudorDTO.builder()
                        .pacienteId(pac.id())
                        .pacienteNombre(nombreCompleto(pac))
                        .dni(pac.dni())
                        .telefono(pac.telefono())
                        .sedeId(pac.sedeId())
                        .sedeNombre(nombreSedeFormateado(pac.sedeId()))
                        .totalTratamientos(totalTratamientos)
                        .totalAbonado(totalAbonado)
                        .saldoPendiente(saldo)
                        .tratamientosPendientes(pendientes)
                        .build());

                deudaTotal = deudaTotal.add(saldo);
            }
        }

        // Ordenar con mayor deuda primero
        deudores.sort((a, b) -> b.getSaldoPendiente().compareTo(a.getSaldoPendiente()));

        return ReporteDeudoresResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .deudaTotalAcumulada(deudaTotal)
                .totalPacientesDeudores((long) deudores.size())
                .pacientes(deudores)
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteCitasRendimientoResponseDTO obtenerReporteCitasRendimiento(String sedeId, LocalDate desde, LocalDate hasta) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        LocalDate fInicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fFin = hasta != null ? hasta : LocalDate.now();

        List<CitaResponseDTO> citas = consultaCitasModuloApi.findByRangoFechasYSede(fInicio, fFin, sedeEfectiva);

        long totalCitas = citas.size();
        long atendidas = citas.stream().filter(c -> "ATENDIDA".equalsIgnoreCase(c.getEstado())).count();
        long canceladas = citas.stream().filter(c -> "CANCELADA".equalsIgnoreCase(c.getEstado())).count();
        long noAsistio = citas.stream().filter(c -> "FALTO".equalsIgnoreCase(c.getEstado()) || "NO_ASISTIO".equalsIgnoreCase(c.getEstado())).count();
        long programadas = citas.stream().filter(c ->
                "PROGRAMADA".equalsIgnoreCase(c.getEstado()) ||
                "CONFIRMADA".equalsIgnoreCase(c.getEstado()) ||
                "EN_SALA".equalsIgnoreCase(c.getEstado()) ||
                "PENDIENTE_WEB".equalsIgnoreCase(c.getEstado())
        ).count();

        double tasa = 0.0;
        if (totalCitas > 0) {
            tasa = BigDecimal.valueOf((double) atendidas / totalCitas * 100.0)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return ReporteCitasRendimientoResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .fechaInicio(fInicio)
                .fechaFin(fFin)
                .totalCitas(totalCitas)
                .citasAtendidas(atendidas)
                .citasCanceladas(canceladas)
                .citasNoAsistio(noAsistio)
                .citasProgramadas(programadas)
                .tasaAsistencia(tasa)
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteCitasResponseDTO obtenerReporteCitas(String sedeId, LocalDate desde, LocalDate hasta) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        LocalDate fInicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fFin = hasta != null ? hasta : LocalDate.now();

        List<CitaResponseDTO> citas = consultaCitasModuloApi.findByRangoFechasYSede(fInicio, fFin, sedeEfectiva);

        long total = citas.size();
        long atendidas = citas.stream().filter(c -> "ATENDIDA".equalsIgnoreCase(c.getEstado())).count();
        long confirmadas = citas.stream().filter(c -> "CONFIRMADA".equalsIgnoreCase(c.getEstado())).count();
        long programadas = citas.stream().filter(c ->
                "PROGRAMADA".equalsIgnoreCase(c.getEstado()) ||
                "EN_SALA".equalsIgnoreCase(c.getEstado()) ||
                "PENDIENTE_WEB".equalsIgnoreCase(c.getEstado())
        ).count();
        long canceladas = citas.stream().filter(c -> "CANCELADA".equalsIgnoreCase(c.getEstado())).count();
        long noAsistio = citas.stream().filter(c -> "FALTO".equalsIgnoreCase(c.getEstado()) || "NO_ASISTIO".equalsIgnoreCase(c.getEstado())).count();
        long reprogramadas = citas.stream().filter(c ->
                "REPROGRAMADA".equalsIgnoreCase(c.getEstado()) ||
                (c.getMotivoReprogramacion() != null && !c.getMotivoReprogramacion().isBlank())
        ).count();

        double tasaAsistencia = 0.0;
        if (total > 0) {
            tasaAsistencia = BigDecimal.valueOf((double) atendidas / total * 100.0)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        // Desglose por día
        Map<LocalDate, List<CitaResponseDTO>> citasPorFecha = citas.stream()
                .filter(c -> c.getFecha() != null)
                .collect(Collectors.groupingBy(CitaResponseDTO::getFecha));

        List<ReporteCitasResponseDTO.CitasPorDiaDTO> porDia = new ArrayList<>();
        LocalDate cursor = fInicio;
        while (!cursor.isAfter(fFin)) {
            List<CitaResponseDTO> delDia = citasPorFecha.getOrDefault(cursor, List.of());
            porDia.add(ReporteCitasResponseDTO.CitasPorDiaDTO.builder()
                    .fecha(cursor)
                    .programadas(delDia.stream().filter(c -> "PROGRAMADA".equalsIgnoreCase(c.getEstado()) || "CONFIRMADA".equalsIgnoreCase(c.getEstado())).count())
                    .atendidas(delDia.stream().filter(c -> "ATENDIDA".equalsIgnoreCase(c.getEstado())).count())
                    .canceladas(delDia.stream().filter(c -> "CANCELADA".equalsIgnoreCase(c.getEstado())).count())
                    .noAsistio(delDia.stream().filter(c -> "FALTO".equalsIgnoreCase(c.getEstado()) || "NO_ASISTIO".equalsIgnoreCase(c.getEstado())).count())
                    .build());
            cursor = cursor.plusDays(1);
        }

        // Desglose por estado
        Map<String, Long> porEstadoMap = citas.stream()
                .collect(Collectors.groupingBy(c -> c.getEstado() != null ? c.getEstado().toUpperCase() : "DESCONOCIDO", Collectors.counting()));

        List<ReporteCitasResponseDTO.CitasPorEstadoDTO> porEstado = new ArrayList<>();
        porEstadoMap.forEach((estado, cant) -> {
            double pct = total > 0 ? BigDecimal.valueOf((double) cant / total * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.0;
            porEstado.add(ReporteCitasResponseDTO.CitasPorEstadoDTO.builder()
                    .estado(estado)
                    .cantidad(cant)
                    .porcentaje(pct)
                    .build());
        });
        porEstado.sort((a, b) -> Long.compare(b.getCantidad(), a.getCantidad()));

        return ReporteCitasResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .fechaInicio(fInicio)
                .fechaFin(fFin)
                .totalCitas(total)
                .programadas(programadas)
                .confirmadas(confirmadas)
                .atendidas(atendidas)
                .canceladas(canceladas)
                .noAsistio(noAsistio)
                .reprogramadas(reprogramadas)
                .tasaAsistencia(tasaAsistencia)
                .citasPorDia(porDia)
                .citasPorEstado(porEstado)
                .build();
    }

    @Transactional(readOnly = true)
    public ReportePacientesResponseDTO obtenerReportePacientes(String sedeId, LocalDate desde, LocalDate hasta) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        LocalDate fInicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fFin = hasta != null ? hasta : LocalDate.now();

        List<PacienteResumen> todosPacientes = pacientesApi.listarActivos(sedeEfectiva);

        long totalPacientes = todosPacientes.size();

        // Pacientes nuevos en el rango de fechas
        List<PacienteResumen> nuevos = todosPacientes.stream()
                .filter(p -> {
                    if (p.creadoEn() == null) return false;
                    LocalDate fechaReg = p.creadoEn().toLocalDate();
                    return !fechaReg.isBefore(fInicio) && !fechaReg.isAfter(fFin);
                })
                .toList();
        long pacientesNuevos = nuevos.size();
        long pacientesRecurrentes = Math.max(0, totalPacientes - pacientesNuevos);

        // Fuentes de captación
        Map<String, Long> fuentes = todosPacientes.stream()
                .map(p -> (p.fuenteCaptacion() != null && !p.fuenteCaptacion().isBlank()) ? p.fuenteCaptacion() : "Directo / No especificado")
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));

        List<ReportePacientesResponseDTO.CaptacionDTO> fuentesDTO = new ArrayList<>();
        fuentes.forEach((fuente, cant) -> {
            double pct = totalPacientes > 0 ? BigDecimal.valueOf((double) cant / totalPacientes * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.0;
            fuentesDTO.add(ReportePacientesResponseDTO.CaptacionDTO.builder()
                    .fuente(fuente)
                    .cantidad(cant)
                    .porcentaje(pct)
                    .build());
        });
        fuentesDTO.sort((a, b) -> Long.compare(b.getCantidad(), a.getCantidad()));

        // Próximos cumpleaños (próximos 30 días)
        LocalDate hoy = LocalDate.now();
        List<ReportePacientesResponseDTO.CumpleaneroDTO> cumpleanos = todosPacientes.stream()
                .filter(p -> p.fechaNacimiento() != null)
                .filter(p -> {
                    LocalDate nac = p.fechaNacimiento();
                    LocalDate cumpleEsteAno = nac.withYear(hoy.getYear());
                    if (cumpleEsteAno.isBefore(hoy)) {
                        cumpleEsteAno = cumpleEsteAno.plusYears(1);
                    }
                    long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, cumpleEsteAno);
                    return dias >= 0 && dias <= 30;
                })
                .map(p -> {
                    int edad = hoy.getYear() - p.fechaNacimiento().getYear();
                    return ReportePacientesResponseDTO.CumpleaneroDTO.builder()
                            .id(p.id())
                            .nombreCompleto(nombreCompleto(p))
                            .dni(p.dni())
                            .telefono(p.telefono())
                            .fechaNacimiento(p.fechaNacimiento())
                            .edad(edad)
                            .sedeId(p.sedeId())
                            .sedeNombre(nombreSedeFormateado(p.sedeId()))
                            .build();
                })
                .limit(20)
                .toList();

        // Pacientes por mes (últimos 6 meses)
        List<ReportePacientesResponseDTO.PacientesMesDTO> porMes = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate m = hoy.minusMonths(i);
            int year = m.getYear();
            int month = m.getMonthValue();
            String mesLabel = m.getMonth().name().substring(0, 3) + " " + year;

            long regMes = todosPacientes.stream()
                    .filter(p -> p.creadoEn() != null &&
                            p.creadoEn().getYear() == year &&
                            p.creadoEn().getMonthValue() == month)
                    .count();

            porMes.add(ReportePacientesResponseDTO.PacientesMesDTO.builder()
                    .mes(mesLabel)
                    .nuevos(regMes)
                    .recurrentes(Math.max(0, totalPacientes - regMes))
                    .build());
        }

        return ReportePacientesResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .fechaInicio(fInicio)
                .fechaFin(fFin)
                .totalPacientes(totalPacientes)
                .pacientesNuevos(pacientesNuevos)
                .pacientesRecurrentes(pacientesRecurrentes)
                .pacientesInactivos(Math.max(0, totalPacientes / 10))
                .fuentesCaptacion(fuentesDTO)
                .proximosCumpleanos(cumpleanos)
                .pacientesPorMes(porMes)
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteDoctoresResponseDTO obtenerReporteDoctores(String sedeId, LocalDate desde, LocalDate hasta) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        LocalDate fInicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fFin = hasta != null ? hasta : LocalDate.now();

        List<CitaResponseDTO> citas = consultaCitasModuloApi.findByRangoFechasYSede(fInicio, fFin, sedeEfectiva);

        Instant desdeInstant = fInicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant hastaInstant = fFin.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        List<PagoEntity> pagos = (sedeEfectiva != null)
                ? pagoRepository.findBySedeIdAndRangoFechas(sedeEfectiva, desdeInstant, hastaInstant)
                : pagoRepository.findAllByRangoFechas(desdeInstant, hastaInstant);
        pagos = pagos.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getAnulado()))
                .filter(p -> sedeEfectiva != null || SecurityUtils.sedeEnNegocioActivo(p.getSedeId()))
                .toList();

        // Ingresos y tratamientos por doctor
        Map<UUID, BigDecimal> ingresosPorDoctor = new HashMap<>();
        Map<UUID, Long> tratamientosPorDoctor = new HashMap<>();
        for (PagoEntity p : pagos) {
            UUID doctorId = doctorDelPago(p);
            if (doctorId != null) {
                ingresosPorDoctor.merge(doctorId, p.getMonto(), BigDecimal::add);
                tratamientosPorDoctor.merge(doctorId, 1L, Long::sum);
            }
        }

        // Doctores con citas o ingresos
        Set<UUID> doctorIds = new HashSet<>();
        citas.forEach(c -> {
            if (c.getDoctorId() != null) doctorIds.add(c.getDoctorId());
        });
        doctorIds.addAll(ingresosPorDoctor.keySet());

        List<ReporteDoctoresResponseDTO.DoctorRendimientoDTO> listaDoctores = new ArrayList<>();
        for (UUID docId : doctorIds) {
            List<CitaResponseDTO> citasDoc = citas.stream().filter(c -> docId.equals(c.getDoctorId())).toList();
            long atendidas = citasDoc.stream().filter(c -> "ATENDIDA".equalsIgnoreCase(c.getEstado())).count();
            long canceladas = citasDoc.stream().filter(c -> "CANCELADA".equalsIgnoreCase(c.getEstado())).count();
            long noAsistio = citasDoc.stream().filter(c -> "FALTO".equalsIgnoreCase(c.getEstado()) || "NO_ASISTIO".equalsIgnoreCase(c.getEstado())).count();
            long totalCitas = citasDoc.size();

            String nombre = citasDoc.stream()
                    .map(CitaResponseDTO::getDoctorNombre)
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElseGet(() -> consultaEspecialistasApi.findNombreById(docId).orElse("Dr. Odontólogo"));

            BigDecimal ingresos = ingresosPorDoctor.getOrDefault(docId, BigDecimal.ZERO);
            long cantTrat = tratamientosPorDoctor.getOrDefault(docId, 0L);

            BigDecimal ticketPromedio = (atendidas > 0)
                    ? ingresos.divide(BigDecimal.valueOf(atendidas), 2, RoundingMode.HALF_UP)
                    : (cantTrat > 0 ? ingresos.divide(BigDecimal.valueOf(cantTrat), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            double tasaEfectividad = (totalCitas > 0)
                    ? BigDecimal.valueOf((double) atendidas / totalCitas * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 100.0;

            listaDoctores.add(ReporteDoctoresResponseDTO.DoctorRendimientoDTO.builder()
                    .doctorId(docId)
                    .nombre(nombre)
                    .especialidad("Odontología Clínica")
                    .citasAtendidas(atendidas)
                    .citasCanceladas(canceladas)
                    .citasNoAsistio(noAsistio)
                    .tratamientosRealizados(cantTrat)
                    .ingresosGenerados(ingresos)
                    .ticketPromedio(ticketPromedio)
                    .tasaEfectividad(tasaEfectividad)
                    .build());
        }

        listaDoctores.sort((a, b) -> b.getIngresosGenerados().compareTo(a.getIngresosGenerados()));

        return ReporteDoctoresResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .fechaInicio(fInicio)
                .fechaFin(fFin)
                .doctores(listaDoctores)
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteInventarioResponseDTO obtenerReporteInventario(String sedeId) {
        String sedeEfectiva = resolverSedePermitida(sedeId);

        List<ProductoInventarioEntity> productos = productoInventarioRepository.findAll();
        Map<UUID, ProductoInventarioEntity> productoMap = productos.stream()
                .collect(Collectors.toMap(ProductoInventarioEntity::getId, p -> p));

        List<InventarioStockEntity> stocks = (sedeEfectiva != null)
                ? inventarioStockRepository.findBySedeId(sedeEfectiva)
                : inventarioStockRepository.findAll().stream()
                        .filter(s -> SecurityUtils.sedeEnNegocioActivo(s.getSedeId()))
                        .toList();

        LocalDate hoy = LocalDate.now();

        // Map productoId -> stock cantidad
        Map<UUID, Integer> stockPorProducto = new HashMap<>();
        stocks.forEach(s -> stockPorProducto.merge(s.getProductoId(), s.getCantidad(), Integer::sum));

        // Valorización total
        BigDecimal valorizacionTotal = BigDecimal.ZERO;
        List<ReporteInventarioResponseDTO.ProductoAlertaDTO> alertasStock = new ArrayList<>();

        for (ProductoInventarioEntity prod : productos) {
            int stockActual = stockPorProducto.getOrDefault(prod.getId(), 0);
            int stockMin = prod.getStockMinimo() != null ? prod.getStockMinimo() : 5;

            if (prod.getPrecioUnitario() != null) {
                valorizacionTotal = valorizacionTotal.add(prod.getPrecioUnitario().multiply(BigDecimal.valueOf(stockActual)));
            }

            if (stockActual <= stockMin) {
                alertasStock.add(ReporteInventarioResponseDTO.ProductoAlertaDTO.builder()
                        .productoId(prod.getId())
                        .codigo(prod.getCodigo())
                        .nombre(prod.getNombre())
                        .categoria(prod.getCategoria())
                        .sedeId(sedeEfectiva != null ? sedeEfectiva : "Global")
                        .stockActual(stockActual)
                        .stockMinimo(stockMin)
                        .estado(stockActual == 0 ? "CRITICO" : "BAJO")
                        .build());
            }
        }

        // Lotes por vencer / vencidos
        List<InventarioMovimientoEntity> movimientos = (sedeEfectiva != null)
                ? inventarioMovimientoRepository.findBySedeIdOrderByCreadoEnDesc(sedeEfectiva)
                : inventarioMovimientoRepository.findAll().stream()
                        .filter(m -> SecurityUtils.sedeEnNegocioActivo(m.getSedeId()))
                        .toList();

        List<ReporteInventarioResponseDTO.LoteAlertaDTO> lotesVencimiento = new ArrayList<>();
        long vencidos = 0;
        long porVencer = 0;

        for (InventarioMovimientoEntity m : movimientos) {
            if (m.getFechaVencimiento() != null && m.getLote() != null && !m.getLote().isBlank()) {
                long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, m.getFechaVencimiento());
                if (dias <= 60) {
                    boolean esVencido = dias < 0;
                    if (esVencido) vencidos++; else porVencer++;

                    ProductoInventarioEntity p = productoMap.get(m.getProductoId());
                    String pNombre = p != null ? p.getNombre() : "Insumo Odontológico";

                    lotesVencimiento.add(ReporteInventarioResponseDTO.LoteAlertaDTO.builder()
                        .movimientoId(m.getId())
                        .productoNombre(pNombre)
                        .lote(m.getLote())
                        .fechaVencimiento(m.getFechaVencimiento())
                        .diasParaVencer((int) dias)
                        .sedeId(m.getSedeId())
                        .estado(esVencido ? "VENCIDO" : "POR_VENCER")
                        .build());
                }
            }
        }

        // Transferencias pendientes
        List<TransferenciaInventarioEntity> transferencias = (sedeEfectiva != null)
                ? transferenciaInventarioRepository.findBySede(sedeEfectiva)
                : transferenciaInventarioRepository.findAllByOrderByCreadoEnDesc().stream()
                        .filter(t -> SecurityUtils.sedeEnNegocioActivo(t.getSedeOrigenId()) || SecurityUtils.sedeEnNegocioActivo(t.getSedeDestinoId()))
                        .toList();

        List<ReporteInventarioResponseDTO.TransferenciaResumenDTO> transfDTO = transferencias.stream()
                .filter(t -> "PENDIENTE".equalsIgnoreCase(t.getEstado()) || "EN_TRANSITO".equalsIgnoreCase(t.getEstado()))
                .map(t -> {
                    ProductoInventarioEntity p = productoMap.get(t.getProductoId());
                    String pNombre = p != null ? p.getNombre() : "Insumo Transferido";
                    return ReporteInventarioResponseDTO.TransferenciaResumenDTO.builder()
                            .id(t.getId())
                            .productoNombre(pNombre)
                            .cantidad(t.getCantidad())
                            .sedeOrigenId(t.getSedeOrigenId())
                            .sedeOrigenNombre(nombreSedeFormateado(t.getSedeOrigenId()))
                            .sedeDestinoId(t.getSedeDestinoId())
                            .sedeDestinoNombre(nombreSedeFormateado(t.getSedeDestinoId()))
                            .estado(t.getEstado())
                            .fecha(t.getCreadoEn().toLocalDate())
                            .build();
                })
                .limit(10)
                .toList();

        // Movimientos recientes
        List<ReporteInventarioResponseDTO.MovimientoResumenDTO> movsDTO = movimientos.stream()
                .limit(10)
                .map(m -> {
                    ProductoInventarioEntity p = productoMap.get(m.getProductoId());
                    String pNombre = p != null ? p.getNombre() : "Insumo Dental";
                    return ReporteInventarioResponseDTO.MovimientoResumenDTO.builder()
                            .id(m.getId())
                            .productoNombre(pNombre)
                            .tipo(m.getTipo())
                            .cantidad(m.getCantidad())
                            .motivo(m.getMotivo())
                            .sedeId(m.getSedeId())
                            .fecha(m.getCreadoEn().toLocalDate())
                            .build();
                })
                .toList();

        return ReporteInventarioResponseDTO.builder()
                .sedeId(sedeEfectiva)
                .sedeNombre(sedeEfectiva != null ? nombreSedeFormateado(sedeEfectiva) : "Todas las Sedes")
                .totalProductos((long) productos.size())
                .productosStockBajo((long) alertasStock.size())
                .productosPorVencer(porVencer)
                .productosVencidos(vencidos)
                .transferenciasPendientes((long) transfDTO.size())
                .valorizacionTotal(valorizacionTotal)
                .alertasStock(alertasStock)
                .lotesVencimiento(lotesVencimiento)
                .transferencias(transfDTO)
                .movimientosRecientes(movsDTO)
                .build();
    }

    private String resolverSedePermitida(String sedeParam) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return sedeParam;
        }

        if ("ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (sedeParam != null && !sedeParam.isBlank()) {
                SecurityUtils.validarAccesoSede(sedeParam);
                return sedeParam.trim();
            }
            return null;
        }

        // Si no es ADMIN_GENERAL, solo puede consultar su sede asignada
        if (sedeParam != null && !sedeParam.isBlank()) {
            SecurityUtils.validarAccesoSede(sedeParam);
            return sedeParam;
        }

        return user.sedeId();
    }

    private static String nombreCompleto(PacienteResumen p) {
        return (p.nombres() + (p.apellidos() != null && !p.apellidos().isBlank() ? " " + p.apellidos() : "")).trim();
    }

    private String nombreSedeFormateado(String sedeId) {
        if (sedeId == null) return "Clínica Dental";
        return consultaSedesModuloApi.obtenerNombreSede(sedeId).orElse(sedeId);
    }

    /** Doctor al que se atribuye un cobro: el guardado en el pago o, en los antiguos, el de su tratamiento. */
    private UUID doctorDelPago(PagoEntity p) {
        if (p.getEspecialistaId() != null) {
            return p.getEspecialistaId();
        }
        return p.getTratamientoId() == null ? null
                : tratamientoRepository.findById(p.getTratamientoId()).map(TratamientoEntity::getEspecialistaId).orElse(null);
    }

    /** Egreso del periodo que no viene de la tabla de gastos. */
    private record EgresoExtra(String sedeId, LocalDate fecha, BigDecimal monto, String categoria) {}

    private static LocalDate diaUtc(Instant instante) {
        return instante == null ? null : instante.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
