package com.odontomax.pacientes.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "consentimientos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "tratamiento_id")
    private UUID tratamientoId;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(name = "plantilla_id")
    private UUID plantillaId;

    private String titulo;

    @Column(name = "contenido_aceptado", columnDefinition = "text")
    private String contenidoAceptado;

    @Column(name = "firma_imagen_key")
    private String firmaImagenKey;

    @Column(name = "pdf_key")
    private String pdfKey;

    @Column(name = "archivo_key")
    private String archivoKey;

    @Column(name = "nombre_firmante")
    private String nombreFirmante;

    @Column(name = "documento_firmante")
    private String documentoFirmante;

    @Column(name = "parentesco_firmante")
    private String parentescoFirmante;

    @Column(name = "ip_firmante")
    private String ipFirmante;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Builder.Default
    @Column(name = "firmado_en", updatable = false)
    private OffsetDateTime firmadoEn = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.firmadoEn == null) {
            this.firmadoEn = OffsetDateTime.now();
        }
    }
}
