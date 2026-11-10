package com.odontomax.identidad.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Usuario autenticado. {@code rol} es su cargo principal; {@code rolesAdicionales} son las funciones que se le suman
 * (ODONTOLOGO y/o RECEPCION) y cuyos permisos valen a la vez.
 */
public record ClinicaUserPrincipal(
        UUID id,
        String email,
        String nombre,
        String rol,
        String sedeId,
        List<String> rolesAdicionales
) implements Principal, Serializable {

    public ClinicaUserPrincipal {
        rolesAdicionales = rolesAdicionales == null ? List.of() : List.copyOf(rolesAdicionales);
    }

    public ClinicaUserPrincipal(UUID id, String email, String nombre, String rol, String sedeId) {
        this(id, email, nombre, rol, sedeId, List.of());
    }

    @Override
    public String getName() {
        return email;
    }

    /** ¿Cumple esta función, ya sea como cargo principal o como función adicional? */
    public boolean tieneRol(String r) {
        return r != null && (r.equalsIgnoreCase(rol) || rolesAdicionales.stream().anyMatch(r::equalsIgnoreCase));
    }

    /** ¿Cumple alguna de estas funciones? */
    public boolean tieneAlgunRol(String... roles) {
        for (String r : roles) {
            if (tieneRol(r)) {
                return true;
            }
        }
        return false;
    }

    /** Todas sus funciones: la principal y las adicionales. */
    public List<String> roles() {
        List<String> todos = new java.util.ArrayList<>();
        if (rol != null) {
            todos.add(rol.toUpperCase());
        }
        rolesAdicionales.forEach(r -> todos.add(r.toUpperCase()));
        return todos;
    }

    /**
     * Odontólogo que no tiene además función de administración ni de recepción: solo ve a los pacientes que atiende.
     * Si suma recepción o administra, ve a todos los de su sede (los permisos se suman).
     */
    public boolean soloOdontologo() {
        return tieneRol("ODONTOLOGO") && !tieneAlgunRol("RECEPCION", "ADMIN_SEDE", "ADMIN_GENERAL");
    }

    /** Recepcionista sin ninguna función clínica ni de administración. */
    public boolean soloRecepcion() {
        return tieneRol("RECEPCION") && !tieneAlgunRol("ODONTOLOGO", "ADMIN_SEDE", "ADMIN_GENERAL");
    }

    /** Pone en el registro de auditoría todas sus funciones, p. ej. {@code ODONTOLOGO+RECEPCION}. */
    public String rolesParaAuditoria() {
        return String.join("+", roles());
    }
}
