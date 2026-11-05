package com.odontomax.pacientes.domain.model;

import lombok.Getter;

import java.util.Base64;

@Getter
public class FirmaManuscrita {

    private static final int MAX_BYTES = 500 * 1024;
    private static final byte[] MAGIC_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final byte[] bytes;

    public FirmaManuscrita(byte[] bytes) {
        this.bytes = bytes;
    }

    public static FirmaManuscrita desdeBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("La firma manuscrita en formato base64 es requerida.");
        }
        String clean = base64.contains(",") ? base64.split(",")[1] : base64;
        byte[] decoded = Base64.getDecoder().decode(clean.trim());

        if (decoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo de la firma excede el tamaño máximo permitido de 500 KB.");
        }

        if (decoded.length < 8 ||
                decoded[0] != MAGIC_PNG[0] ||
                decoded[1] != MAGIC_PNG[1] ||
                decoded[2] != MAGIC_PNG[2] ||
                decoded[3] != MAGIC_PNG[3] ||
                decoded[4] != MAGIC_PNG[4] ||
                decoded[5] != MAGIC_PNG[5] ||
                decoded[6] != MAGIC_PNG[6] ||
                decoded[7] != MAGIC_PNG[7]) {
            throw new IllegalArgumentException("El archivo de firma debe ser una imagen PNG válida (firma mágica PNG inválida).");
        }

        return new FirmaManuscrita(decoded);
    }
}