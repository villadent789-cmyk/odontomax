package com.odontomax.identidad.infrastructure.persistence.repository;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataUsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {

    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);

    /**
     * Escribe directamente las credenciales 2FA ya cifradas. Necesario para el re-cifrado tras
     * rotar TOTP_ENCRYPTION_KEY: el texto plano no cambia, así que el dirty checking de
     * Hibernate no emitiría ningún UPDATE con save().
     */
    @Modifying
    @Query(value = "UPDATE usuarios SET totp_secret = :totpSecret, totp_backup_codes = :totpBackupCodes WHERE id = :id",
            nativeQuery = true)
    int actualizarCredencialesTotpCifradas(@Param("id") UUID id,
                                           @Param("totpSecret") String totpSecretCifrado,
                                           @Param("totpBackupCodes") String totpBackupCodesCifrados);

    /**
     * Indica si el usuario aparece en alguna FK sin ON DELETE CASCADE (historial clínico, caja, pagos...).
     * Se consulta ANTES de borrar: un DELETE que viola la integridad deja la transacción en rollback-only.
     * ReferenciasEsquemaTest verifica que esta lista cubra todas las referencias de las migraciones.
     */
    @Query(value = "SELECT (EXISTS (SELECT 1 FROM especialistas WHERE usuario_id = :id)"
            + " OR EXISTS (SELECT 1 FROM odontograma_registros WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM consentimientos WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM pagos WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM pagos WHERE anulado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM pagos_laboratorio WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM pagos_laboratorio WHERE anulado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM gastos WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM caja_sesiones WHERE abierta_por = :id)"
            + " OR EXISTS (SELECT 1 FROM caja_sesiones WHERE cerrada_por = :id)"
            + " OR EXISTS (SELECT 1 FROM caja_movimientos WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM inventario_movimientos WHERE registrado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM transferencias_inventario WHERE solicitado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM notas_evolucion WHERE autor_id = :id)"
            + " OR EXISTS (SELECT 1 FROM antecedentes_historial WHERE modificado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM historia_clinica_accesos WHERE usuario_id = :id)"
            + " OR EXISTS (SELECT 1 FROM tratamientos WHERE descuento_autorizado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM tratamientos WHERE ajuste_precio_por = :id)"
            + " OR EXISTS (SELECT 1 FROM consumos_insumos WHERE resuelto_por = :id)"
            + " OR EXISTS (SELECT 1 FROM registros_plantilla WHERE creado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM lista_espera_huecos WHERE creado_por = :id)"
            + " OR EXISTS (SELECT 1 FROM tratamiento_items WHERE descuento_autorizado_por = :id))",
            nativeQuery = true)
    boolean tieneRegistrosVinculados(@Param("id") UUID id);
}
