package com.odontomax.identidad.caracterizacion;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Generador RFC 6238 independiente (HmacSHA1, 30 s, 6 dígitos) para calcular códigos válidos en los tests. */
final class TotpTestUtil {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpTestUtil() {
    }

    static int codigoActual(String base32Secret) {
        return codigo(base32Secret, System.currentTimeMillis() / 1000 / 30);
    }

    static int codigo(String base32Secret, long ventana) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(ventana).array());
            int offset = hash[hash.length - 1] & 0xF;
            int binario = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return binario % 1_000_000;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un código de 6 dígitos que no coincide con ninguna ventana aceptada (-1, 0, +1). */
    static int codigoInvalido(String base32Secret) {
        long v = System.currentTimeMillis() / 1000 / 30;
        int candidato = (codigo(base32Secret, v) + 500_000) % 1_000_000;
        while (candidato == codigo(base32Secret, v - 1) || candidato == codigo(base32Secret, v)
                || candidato == codigo(base32Secret, v + 1)) {
            candidato = (candidato + 1) % 1_000_000;
        }
        return candidato;
    }

    static String seisDigitos(int codigo) {
        return String.format("%06d", codigo);
    }

    private static byte[] decodificar(String base32) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : base32.toUpperCase().toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
