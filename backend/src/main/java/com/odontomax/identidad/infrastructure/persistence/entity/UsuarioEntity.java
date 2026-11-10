package com.odontomax.identidad.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "auth_user_id")
    private UUID authUserId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String rol;

    /** Funciones adicionales separadas por coma (vacío si no tiene). */
    @Column(name = "roles_adicionales", nullable = false)
    @Builder.Default
    private String rolesAdicionales = "";

    @Column(name = "sede_id")
    private String sedeId;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Convert(converter = com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter.class)
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_activado", nullable = false)
    @Builder.Default
    private boolean totpActivado = false;

    @Convert(converter = com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter.class)
    @Column(name = "totp_backup_codes")
    private String totpBackupCodes;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private OffsetDateTime actualizadoEn;

    @jakarta.persistence.Transient
    private boolean reencryptionNeeded;

    @jakarta.persistence.PostLoad
    public void checkReencryptionNeeded() {
        if (com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter.consumeLastDecryptedWithOldKey()) {
            this.reencryptionNeeded = true;
        }
    }

    public boolean isReencryptionNeeded() {
        return reencryptionNeeded;
    }
}
