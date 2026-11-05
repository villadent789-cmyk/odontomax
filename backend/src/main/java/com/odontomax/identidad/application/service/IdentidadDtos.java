package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.response.UsuarioDTO;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.domain.model.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Utilidades compartidas por los casos de uso de identidad. */
final class IdentidadDtos {

    private IdentidadDtos() {
    }

    static UsuarioDTO usuarioDto(Usuario user) {
        return UsuarioDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nombre(user.getNombre())
                .rol(user.getRol())
                .sedeId(user.getSedeId())
                .build();
    }

    /** Indica si la contraseña en claro coincide con alguno de los hashes del historial (evaluados en orden). */
    static boolean coincideConHistorial(PasswordEncoder passwordEncoder, String rawPassword, List<String> hashes) {
        for (String hash : hashes) {
            if (passwordEncoder.matches(rawPassword, hash)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Desaloja la entrada del usuario en la caché del filtro JWT para que su nueva versión de credenciales rija al
     * instante (B-008). Se repite tras el commit por si una petición concurrente la recargó con la versión anterior.
     */
    static void desalojarCacheJwt(JwtUserCachePort cache, Usuario usuario) {
        if (usuario.getEmail() == null) {
            return;
        }
        String clave = usuario.getEmail().trim().toLowerCase();
        int version = usuario.getTokenVersion();
        cache.desalojar(clave, version);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.desalojar(clave, version);
                }
            });
        }
    }

    /** SHA-256 en hexadecimal (para usar un token como clave sin guardarlo en claro). */
    static String sha256Hex(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la plataforma Java", e);
        }
    }
}
