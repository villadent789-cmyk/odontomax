package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoResponseDTO {

    private UUID id;
    private UUID pacienteId;
    private String pacienteNombre;
    private String sedeId;
    private String sedeNombre;
    private UUID especialistaId;
    private String especialistaNombre;
    private String procedimientoId;
    private String nombre;
    private String piezaDental;
    private String diagnostico;
    private String modalidadPago;
    private BigDecimal costoTotal;
    private BigDecimal descuentoTotal;
    private UUID descuentoAutorizadoPor;
    private BigDecimal montoPagado;
    private BigDecimal pagadoAntesSistema;
    private BigDecimal saldoPendiente;
    private String estado;
    private Boolean aceptadoPorPaciente;
    private Instant fechaAceptacion;
    private LocalDate fechaInicio;
    private LocalDate fechaFinEstimada;
    private Integer totalSesiones;
    private Integer sesionesRealizadas;
    private LocalDate proximoControl;
    private Boolean requiereSeguimiento;
    private String observaciones;
    private String motivoCierre;
    private BigDecimal precioCatalogo;
    private String motivoAjustePrecio;
    /** Cronograma con lo pagado repartido en orden; vacío si el plan no es en cuotas. */
    @Builder.Default
    private List<CuotaPlanResponseDTO> cuotas = new ArrayList<>();
    private Integer cuotasVencidas;
    private Instant cerradoEn;

    @Builder.Default
    private List<TratamientoItemResponseDTO> items = new ArrayList<>();

    private Instant creadoEn;
    private Instant actualizadoEn;
    /** Cambios del número de sesiones, con su motivo, del más antiguo al más reciente. */
    @Builder.Default
    private List<CambioSesionesResponseDTO> cambiosSesiones = new ArrayList<>();
}
