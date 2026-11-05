package com.odontomax.identidad.application.port.out;

import java.util.List;
import java.util.UUID;

/** Historial de hashes de contraseña (política de no reutilizar las últimas 3). */
public interface PasswordHistorialRepository {

    /** Hashes de las 3 contraseñas más recientes del usuario, de la más nueva a la más antigua. */
    List<String> findUltimosTresHashes(UUID usuarioId);

    void guardar(UUID usuarioId, String passwordHash);
}
