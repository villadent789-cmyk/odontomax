package com.odontomax.identidad.infrastructure.persistence.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionConverterTest {

    private AesGcmEncryptionConverter converter;
    private String primaryKeyBase64;
    private String oldKeyBase64;

    private String generateRandomBase64Key(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @BeforeEach
    void setUp() {
        converter = new AesGcmEncryptionConverter();
        primaryKeyBase64 = generateRandomBase64Key(32);
        oldKeyBase64 = generateRandomBase64Key(32);
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, oldKeyBase64);
    }

    @Test
    @DisplayName("Cifrado y descifrado simétrico AES-256-GCM con clave activa")
    void encryptAndDecrypt_success() {
        String plaintextSecret = "JBSWY3DPEHPK3PXP";

        String encrypted = converter.convertToDatabaseColumn(plaintextSecret);
        assertThat(encrypted).startsWith("ENC:GCM:v1:");
        assertThat(encrypted).isNotEqualTo(plaintextSecret);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(plaintextSecret);
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isFalse();
    }

    @Test
    @DisplayName("Validación estricta de TOTP_ENCRYPTION_KEY obligatoria: falla si falta o tiene menos de 32 bytes")
    void initKeys_validatesMandatoryKey() {
        // Clave nula o vacía
        assertThatThrownBy(() -> AesGcmEncryptionConverter.initKeys(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_ENCRYPTION_KEY es obligatoria");

        assertThatThrownBy(() -> AesGcmEncryptionConverter.initKeys("   ", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_ENCRYPTION_KEY es obligatoria");

        // Clave no es Base64 válida
        assertThatThrownBy(() -> AesGcmEncryptionConverter.initKeys("esto-no-es-base64-valido!", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cadena Base64 válida");

        // Clave menor a 32 bytes (ej. 16 bytes = 128 bits)
        String shortKey = generateRandomBase64Key(16);
        assertThatThrownBy(() -> AesGcmEncryptionConverter.initKeys(shortKey, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32 bytes aleatorios");
    }

    @Test
    @DisplayName("Rotación de claves: dato cifrado con clave previa (OLD) se descifra con éxito y se marca para re-cifrado")
    void rotation_decryptsWithOldKeyAndMarksForReencryption() {
        // 1. Simular dato cifrado en el pasado con la clave previa
        AesGcmEncryptionConverter.initKeys(oldKeyBase64, null);
        String secretAntiguo = "SECRETO_GENERADO_EN_EL_PASADO";
        String ciphertextConClaveVieja = converter.convertToDatabaseColumn(secretAntiguo);

        // 2. Simular rotación: nueva clave activa y clave previa como OLD
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, oldKeyBase64);

        // 3. Al leer, descifra usando la clave vieja y marca lastDecryptedWithOldKey = true
        String descifrado = converter.convertToEntityAttribute(ciphertextConClaveVieja);
        assertThat(descifrado).isEqualTo(secretAntiguo);
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isTrue();

        // 4. Proceso de re-cifrado: reencrypt produce un nuevo ciphertext con la clave activa
        String nuevoCiphertext = converter.reencrypt(ciphertextConClaveVieja);
        assertThat(nuevoCiphertext).startsWith("ENC:GCM:v1:");
        assertThat(nuevoCiphertext).isNotEqualTo(ciphertextConClaveVieja);

        // 5. Configurar únicamente la nueva clave (sin clave anterior) y verificar que el nuevo ciphertext se descifra
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, null);
        String descifradoSoloConNueva = converter.convertToEntityAttribute(nuevoCiphertext);
        assertThat(descifradoSoloConNueva).isEqualTo(secretAntiguo);
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isFalse();
    }

    /** Reproduce el cifrado anterior a este cambio: clave AES = SHA-256(secreto en texto). */
    private String cifrarConEsquemaLegacy(String secretoLegacy, String plaintext) throws Exception {
        byte[] keyBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(secretoLegacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return "ENC:GCM:v1:" + Base64.getEncoder().encodeToString(payload);
    }

    @Test
    @DisplayName("Migración legacy: TOTP_ENCRYPTION_KEY_OLD con el JWT_SECRET anterior (texto) descifra datos SHA-256(secreto)")
    void legacyMigration_textSecretAsOldKey_decrypts() throws Exception {
        String jwtSecretAnterior = "UnSecretoJwtAnteriorQueNoEsBase64!!";
        String ciphertextLegacy = cifrarConEsquemaLegacy(jwtSecretAnterior, "JBSWY3DPEHPK3PXP");

        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, jwtSecretAnterior);

        assertThat(converter.convertToEntityAttribute(ciphertextLegacy)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isTrue();

        // Tras re-cifrar, el dato es legible solo con la clave nueva
        String recifrado = converter.reencrypt(ciphertextLegacy);
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, null);
        assertThat(converter.convertToEntityAttribute(recifrado)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isFalse();
    }

    @Test
    @DisplayName("Migración legacy: secreto anterior que también es Base64 válida (openssl rand -base64 48) descifra igualmente")
    void legacyMigration_base64LookingSecret_triesBothDerivations() throws Exception {
        String jwtSecretAnteriorBase64 = generateRandomBase64Key(48);
        String ciphertextLegacy = cifrarConEsquemaLegacy(jwtSecretAnteriorBase64, "SECRETO_2FA");

        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, jwtSecretAnteriorBase64);

        assertThat(converter.convertToEntityAttribute(ciphertextLegacy)).isEqualTo("SECRETO_2FA");
        assertThat(AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isTrue();
    }

    @Test
    @DisplayName("Migración legacy: sin TOTP_ENCRYPTION_KEY_OLD los datos legacy no se pueden descifrar")
    void legacyMigration_withoutOldKey_fails() throws Exception {
        String ciphertextLegacy = cifrarConEsquemaLegacy("SecretoAnterior", "SECRETO_2FA");
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, null);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(ciphertextLegacy))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Fallo al descifrar si la clave es errónea y no existe clave previa")
    void decryptFailure_withoutOldKey_throwsException() {
        String otraClaveBase64 = generateRandomBase64Key(32);
        AesGcmEncryptionConverter.initKeys(otraClaveBase64, null);
        String ciphertextDesconocido = converter.convertToDatabaseColumn("DatoDesconocido");

        // Cambiar a primaryKeyBase64 sin clave anterior
        AesGcmEncryptionConverter.initKeys(primaryKeyBase64, null);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(ciphertextDesconocido))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Datos nulos o en blanco se preservan sin alterar")
    void nullOrBlank_preserved() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("   ")).isEqualTo("   ");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("Retrocompatibilidad: datos legacy sin prefijo ENC: se retornan como texto plano")
    void legacyPlaintext_preserved() {
        String legacySecret = "PLAINTEXT_LEGACY_SECRET_123";
        String read = converter.convertToEntityAttribute(legacySecret);
        assertThat(read).isEqualTo(legacySecret);
    }

    @Test
    @DisplayName("Cifrado con vector de inicialización (IV) único produce diferentes salidas para el mismo texto")
    void randomizedIv_producesDifferentCiphertexts() {
        String secret = "JBSWY3DPEHPK3PXP";
        String enc1 = converter.convertToDatabaseColumn(secret);
        String enc2 = converter.convertToDatabaseColumn(secret);

        assertThat(enc1).isNotEqualTo(enc2); // IV aleatorio de 12 bytes
        assertThat(converter.convertToEntityAttribute(enc1)).isEqualTo(secret);
        assertThat(converter.convertToEntityAttribute(enc2)).isEqualTo(secret);
    }

    @Test
    @DisplayName("Payload cifrado corrupto o manipulado lanza excepción por fallo de integridad GCM")
    void corruptedPayload_throwsException() {
        String validEncrypted = converter.convertToDatabaseColumn("SecretData");
        String tampered = validEncrypted.substring(0, validEncrypted.length() - 4) + "AAAA";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(RuntimeException.class);
    }
}
