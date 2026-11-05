package com.odontomax.identidad.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Caché de los datos de usuario que usa el filtro JWT (rol, sede y estado se leen de BD, no del token).
 * Una entrada vale 60 s y no se invalida al cambiar el estado ni el rol (B-007); al cambiar las credenciales
 * (contraseña, 2FA) los casos de uso la desalojan para que la nueva versión de token rija al instante (B-008).
 */
public interface JwtUserCachePort {

    /** Entrada vigente para el correo (en minúsculas); vacío si no existe o ya expiró. */
    Optional<UsuarioAutenticado> get(String emailLower);

    void put(String emailLower, UsuarioAutenticado usuario);

    void evict(String emailLower);

    /**
     * Desaloja la entrada tras un cambio de credenciales y recuerda la versión vigente: un {@link #put} posterior con
     * una versión menor (lectura de BD anterior al cambio) se ignora.
     */
    void desalojar(String emailLower, int versionVigente);

    /** Datos cacheados del usuario, incluido su estado {@code activo} (también se cachean cuentas inactivas). */
    record UsuarioAutenticado(UUID id, String email, String nombre, String rol, String sedeId, boolean activo,
                              int tokenVersion) {
    }
}
