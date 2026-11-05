package com.odontomax.identidad.infrastructure.persistence.converter;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Converter
@Component
@Slf4j
public class AesGcmEncryptionConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "ENC:GCM:v1:";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recomendado para AES-GCM
    private static final int GCM_TAG_LENGTH = 128; // bits

    private static SecretKey secretKey;
    private static List<SecretKey> oldSecretKeys = List.of();

    private static final ThreadLocal<Boolean> lastDecryptedWithOldKey = ThreadLocal.withInitial(() -> false);

    @Value("${security.totp.encryption-key:${TOTP_ENCRYPTION_KEY:}}")
    private String totpKeyMaterial;

    @Value("${security.totp.encryption-key-old:${TOTP_ENCRYPTION_KEY_OLD:}}")
    private String totpKeyOldMaterial;

    @PostConstruct
    public void init() {
        initKeys(totpKeyMaterial, totpKeyOldMaterial);
    }

    /**
     * Inicializa las claves criptográficas para el convertidor.
     * TOTP_ENCRYPTION_KEY es obligatoria y debe ser una cadena Base64 con al menos 32 bytes (256 bits).
     * No admite valores por defecto ni caídas a JWT_SECRET: si falta, la aplicación no arranca.
     * TOTP_ENCRYPTION_KEY_OLD es opcional para soportar rotación de secretos.
     */
    public static synchronized void initKeys(String keyMaterial, String oldKeyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException(
                    "ERROR CRÍTICO DE CONFIGURACIÓN: La variable TOTP_ENCRYPTION_KEY es obligatoria y no puede estar vacía. " +
                    "La aplicación no puede iniciar sin esta clave de cifrado en reposo para 2FA."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyMaterial.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ERROR CRÍTICO: La variable TOTP_ENCRYPTION_KEY debe ser una cadena Base64 válida.", e
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "ERROR CRÍTICO: La clave TOTP_ENCRYPTION_KEY debe contener al menos 32 bytes aleatorios (256 bits). " +
                    "Longitud actual decodificada: " + keyBytes.length + " bytes."
            );
        }

        // AES-256 utiliza los primeros 32 bytes exactos
        secretKey = new SecretKeySpec(keyBytes, 0, 32, "AES");

        oldSecretKeys = derivarClavesAnteriores(oldKeyMaterial);
        if (!oldSecretKeys.isEmpty()) {
            log.info("Clave anterior TOTP_ENCRYPTION_KEY_OLD configurada para rotación y re-cifrado.");
        }
    }

    /**
     * TOTP_ENCRYPTION_KEY_OLD admite dos formatos, probados en orden:
     * 1. Base64 con al menos 32 bytes (formato actual, uso directo).
     * 2. Secreto en texto del esquema legacy, cuya clave AES era SHA-256(secreto).
     *    Antes de este cambio el secreto era TOTP_ENCRYPTION_KEY o, en su ausencia, JWT_SECRET.
     * GCM autentica el descifrado, así que un candidato incorrecto falla sin producir datos erróneos.
     */
    private static List<SecretKey> derivarClavesAnteriores(String oldKeyMaterial) {
        if (oldKeyMaterial == null || oldKeyMaterial.isBlank()) {
            return List.of();
        }
        List<SecretKey> candidatas = new ArrayList<>();
        try {
            byte[] oldBytes = Base64.getDecoder().decode(oldKeyMaterial.trim());
            if (oldBytes.length >= 32) {
                candidatas.add(new SecretKeySpec(oldBytes, 0, 32, "AES"));
            }
        } catch (IllegalArgumentException ignored) {
            // No es Base64: solo aplica la derivación legacy
        }
        try {
            byte[] legacyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(oldKeyMaterial.getBytes(StandardCharsets.UTF_8));
            candidatas.add(new SecretKeySpec(legacyBytes, "AES"));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
        return List.copyOf(candidatas);
    }

    public static synchronized SecretKey getKey() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "TOTP_ENCRYPTION_KEY no ha sido inicializada. La aplicación requiere esta variable para operar."
            );
        }
        return secretKey;
    }

    public static boolean wasLastDecryptedWithOldKey() {
        return Boolean.TRUE.equals(lastDecryptedWithOldKey.get());
    }

    public static boolean consumeLastDecryptedWithOldKey() {
        boolean wasOld = wasLastDecryptedWithOldKey();
        lastDecryptedWithOldKey.remove();
        return wasOld;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), spec);

            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Error al cifrar dato sensible en reposo con AES-GCM", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        if (!dbData.startsWith(PREFIX)) {
            // Retrocompatibilidad con valores legacy en texto plano
            return dbData;
        }
        try {
            String base64Data = dbData.substring(PREFIX.length());
            byte[] raw = Base64.getDecoder().decode(base64Data);

            if (raw.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Payload cifrado corrupto o incompleto");
            }

            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // 1. Intentar descifrar con la clave activa actual
            try {
                byte[] decrypted = decryptWithKey(iv, ciphertext, getKey());
                lastDecryptedWithOldKey.set(false);
                return new String(decrypted, StandardCharsets.UTF_8);
            } catch (Exception currentKeyEx) {
                // 2. Si falla y se configuró una clave previa (rotación), intentar con sus candidatas
                if (oldSecretKeys.isEmpty()) {
                    throw currentKeyEx;
                }
                for (SecretKey oldKey : oldSecretKeys) {
                    try {
                        byte[] decrypted = decryptWithKey(iv, ciphertext, oldKey);
                        lastDecryptedWithOldKey.set(true);
                        log.debug("Dato descifrado exitosamente con TOTP_ENCRYPTION_KEY_OLD (marcado para re-cifrado).");
                        return new String(decrypted, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        // Probar la siguiente candidata
                    }
                }
                throw new RuntimeException("Error al descifrar dato con clave actual y clave previa", currentKeyEx);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al descifrar dato sensible en reposo con AES-GCM", e);
        }
    }

    /**
     * Re-cifra un valor almacenado (ya sea cifrado con clave previa o texto plano)
     * produciendo un nuevo texto cifrado con la clave activa y un IV fresco.
     */
    public String reencrypt(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        String plaintext = convertToEntityAttribute(dbData);
        return convertToDatabaseColumn(plaintext);
    }

    private byte[] decryptWithKey(byte[] iv, byte[] ciphertext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }
}
