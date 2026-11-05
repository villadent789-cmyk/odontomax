package com.odontomax.identidad.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record ClinicaUserPrincipal(
        UUID id,
        String email,
        String nombre,
        String rol,
        String sedeId
) implements Principal, Serializable {

    @Override
    public String getName() {
        return email;
    }
}
