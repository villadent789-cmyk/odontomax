package com.odontomax.identidad.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioFuncionesAdicionalesTest {

    @Test
    @DisplayName("Normaliza: mayúsculas, sin repetidas ni la del cargo principal")
    void normaliza() {
        assertThat(Usuario.normalizarRolesAdicionales("ODONTOLOGO", List.of("recepcion", "RECEPCION", "ODONTOLOGO")))
                .containsExactly("RECEPCION");
        assertThat(Usuario.normalizarRolesAdicionales("RECEPCION", null)).isEmpty();
    }

    @Test
    @DisplayName("Solo se admiten ODONTOLOGO y RECEPCION como funciones adicionales")
    void soloFuncionesPermitidas() {
        assertThatThrownBy(() -> Usuario.normalizarRolesAdicionales("RECEPCION", List.of("ADMIN_GENERAL")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El Administrador General no lleva funciones adicionales")
    void adminGeneralNoLasLleva() {
        assertThatThrownBy(() -> Usuario.normalizarRolesAdicionales("ADMIN_GENERAL", List.of("ODONTOLOGO")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
