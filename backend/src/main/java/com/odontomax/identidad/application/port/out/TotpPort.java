package com.odontomax.identidad.application.port.out;

import java.util.List;

/** TOTP (RFC 6238): secretos, códigos de respaldo, QR y verificación. */
public interface TotpPort {

    String generateSecret();

    List<String> generateBackupCodes();

    String getOtpAuthUrl(String email, String secret);

    String generateQrCodeDataUri(String otpauthUrl, int width, int height);

    boolean verifyCode(String base32Secret, int code);

    /**
     * Paso de tiempo (RFC 6238) al que corresponde un código ya verificado: el actual o uno contiguo (±1).
     * Devuelve -1 si el código no corresponde a ninguno de ellos.
     */
    long pasoDelCodigo(String base32Secret, int code);
}
