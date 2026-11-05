package com.odontomax.pacientes.infrastructure.persistence.entity;

import com.odontomax.pacientes.application.dto.request.FichaIngresoDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pacientes_ficha_ingreso")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaIngresoEntity {

    @Id
    @Column(name = "paciente_id")
    private UUID pacienteId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false, columnDefinition = "jsonb")
    private FichaIngresoDTO datos;

    @Builder.Default
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @Column(name = "actualizado_por_nombre")
    private String actualizadoPorNombre;
}
