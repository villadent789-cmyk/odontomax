package com.odontomax.identidad.infrastructure.security;

import com.odontomax.identidad.application.port.out.TokenJwtPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenJwtPort {

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-ms:28800000}")
    private long jwtExpirationMs;

    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            throw new IllegalStateException("FATAL: La variable de entorno JWT_SECRET es obligatoria y debe tener al menos 32 caracteres (256 bits). Configure JWT_SECRET en el servidor.");
        }
        // Asegurar que la clave tenga al menos 256 bits (32 bytes) usando SHA-256
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
            this.key = Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Genera un token JWT firmado con claims clínicos (rol, sede, id de usuario) y la versión de credenciales.
     */
    @Override
    public String generateToken(UUID userId, String email, String nombre, String rol, String sedeId, int tokenVersion) {
        return construirTokenDeSesion(userId, email, nombre, rol, sedeId, tokenVersion);
    }

    private String construirTokenDeSesion(UUID userId, String email, String nombre, String rol, String sedeId,
                                          int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        var builder = Jwts.builder()
                .subject(email)
                .claim("userId", userId != null ? userId.toString() : null)
                .claim("email", email)
                .claim("nombre", nombre)
                .claim("rol", rol)
                .claim("sedeId", sedeId)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion);
        return builder
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    @Override
    public String generateTemp2FaToken(UUID userId, String email, String nombre, String rol, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 5 * 60 * 1000); // 5 minutos para ingresar el TOTP

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId != null ? userId.toString() : null)
                .claim("email", email)
                .claim("nombre", nombre)
                .claim("rol", rol)
                .claim("type", "2FA_PENDING")
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .id(UUID.randomUUID().toString()) // cada login pendiente es único (su estado se guarda por token)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Valida la firma criptográfica y vigencia del token
     */
    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrae los claims del token JWT
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public TokenClaims leerClaims(String token) {
        Claims claims = getClaimsFromToken(token);
        return new TokenClaims(claims.getSubject(), claims.get("type", String.class), getTokenVersion(claims));
    }

    /** Versión de credenciales del token; los tokens sin el claim (anteriores a B-008) cuentan como versión 0. */
    public int getTokenVersion(Claims claims) {
        Object version = claims.get(CLAIM_TOKEN_VERSION);
        return version instanceof Number n ? n.intValue() : 0;
    }

    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public String getRolFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object rol = claims.get("rol");
        if (rol == null) {
            // Compatibilidad con Supabase Auth (role o app_metadata.role)
            rol = claims.get("role");
        }
        return rol != null ? rol.toString() : "ODONTOLOGO";
    }

    public String getSedeIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object sedeId = claims.get("sedeId");
        return sedeId != null ? sedeId.toString() : null;
    }

    @Override
    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
