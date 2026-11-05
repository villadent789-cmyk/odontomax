package com.odontomax.agenda.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "citas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "paciente_nombre")
    private String pacienteNombre;

    @Column(name = "paciente_telefono")
    private String pacienteTelefono;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "doctor_nombre")
    private String doctorNombre;

    @Column(name = "sede_id", nullable = false)
    private String sedeId;

    @Column(name = "sillon_numero")
    private Integer sillonNumero;

    @Column(name = "motivo_consulta", nullable = false)
    private String motivoConsulta;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Builder.Default
    @Column(name = "duracion_minutos")
    private Integer duracionMinutos = 30;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "llegada_en")
    private Instant llegadaEn;

    @Column(name = "inicio_atencion_en")
    private Instant inicioAtencionEn;

    @Column(name = "fin_atencion_en")
    private Instant finAtencionEn;

    @Column(name = "motivo_cancelacion", columnDefinition = "text")
    private String motivoCancelacion;

    @Column(name = "motivo_cancelacion_por")
    private String motivoCancelacionPor;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "motivo_reprogramacion", columnDefinition = "text")
    private String motivoReprogramacion;

    @Column(name = "motivo_reprogramacion_por")
    private String motivoReprogramacionPor;

    @Column(name = "reprogramada_en")
    private Instant reprogramadaEn;

    @Column(columnDefinition = "text")
    private String notas;

    @Column(name = "codigo_ticket", length = 20)
    private String codigoTicket;

    @Builder.Default
    @Column(name = "adelanto_requerido", nullable = false)
    private Boolean adelantoRequerido = false;

    @Builder.Default
    @Column(name = "adelanto_monto", nullable = false)
    private java.math.BigDecimal adelantoMonto = java.math.BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "adelanto_estado", nullable = false)
    private String adelantoEstado = "NO_APLICA";

    @Column(name = "adelanto_medio_pago")
    private String adelantoMedioPago;

    @Column(name = "adelanto_numero_operacion")
    private String adelantoNumeroOperacion;

    @Column(name = "adelanto_comprobante_url")
    private String adelantoComprobanteUrl;

    @Column(name = "adelanto_registrado_por")
    private String adelantoRegistradoPor;

    @Column(name = "adelanto_registrado_en")
    private OffsetDateTime adelantoRegistradoEn;

    @Column(name = "adelanto_limite_pago")
    private OffsetDateTime adelantoLimitePago;

    @Builder.Default
    @Column(name = "estado_confirmacion", nullable = false)
    private String estadoConfirmacion = "SIN_CONTACTAR";

    @Column(name = "ultimo_contacto_medio")
    private String ultimoContactoMedio;

    @Column(name = "ultimo_contacto_fecha")
    private OffsetDateTime ultimoContactoFecha;

    @Column(name = "ultimo_contacto_usuario")
    private String ultimoContactoUsuario;

    @Column(name = "ultimo_contacto_resultado")
    private String ultimoContactoResultado;

    /** Plan de tratamiento al que corresponde la cita (columna de V1, en uso desde V23). Solo el id: la agenda no conoce la entidad del tratamiento. */
    @Column(name = "tratamiento_id")
    private UUID tratamientoId;

    @Column(name = "doctor_derivador_id")
    private UUID doctorDerivadorId;

    @Builder.Default
    @Column(name = "paciente_directo")
    private Boolean pacienteDirecto = false;

    @Builder.Default
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = OffsetDateTime.now();
        }
        if (this.duracionMinutos == null) {
            this.duracionMinutos = 30;
        }
    }
}
