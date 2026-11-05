package com.odontomax.identidad.infrastructure.security;

import com.odontomax.identidad.application.port.out.TotpPort;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class TotpService implements TotpPort {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[20]; // 160 bits
        SECURE_RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    @Override
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                int idx = SECURE_RANDOM.nextInt(BASE32_CHARS.length());
                sb.append(BASE32_CHARS.charAt(idx));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    @Override
    public String getOtpAuthUrl(String email, String secret) {
        return String.format("otpauth://totp/OdontoMax:%s?secret=%s&issuer=OdontoMax", email, secret);
    }

    @Override
    public String generateQrCodeDataUri(String otpauthUrl, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpauthUrl, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar código QR para enrolamiento 2FA", e);
        }
    }

    @Override
    public boolean verifyCode(String base32Secret, int code) {
        return pasoDelCodigo(base32Secret, code) >= 0;
    }

    @Override
    public long pasoDelCodigo(String base32Secret, int code) {
        if (base32Secret == null || base32Secret.isBlank()) {
            return -1;
        }

        long currentWindow = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        // Check window -1, 0, +1 to account for minor clock drift
        for (int i = -1; i <= 1; i++) {
            try {
                int generated = generateCode(base32Secret, currentWindow + i);
                if (generated == code) {
                    return currentWindow + i;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private int generateCode(String base32Secret, long timeWindow) throws Exception {
        byte[] key = base32Decode(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;
        int binary = ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

        return binary % (int) Math.pow(10, CODE_DIGITS);
    }

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer bytes = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_CHARS.indexOf(clean.charAt(i));
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                bytes.put((byte) ((buffer >> bitsLeft) & 0xFF));
            }
        }
        byte[] out = new byte[bytes.position()];
        bytes.rewind();
        bytes.get(out);
        return out;
    }
}
