package com.odontomax.identidad.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de credenciales del agregado Usuario (Fase 2.5). */
class UsuarioCredencialesTest {

    private static Usuario usuario(String rol) {
        return Usuario.builder().email("u@odontomax.pe").rol(rol).passwordHash("hash-0").activo(true).build();
    }

    @Test
    @DisplayName("B-008: cambiar o restablecer la contraseña sube la versión de token; solo vale la versión vigente")
    void cambiarPasswordInvalidaSesiones() {
        Usuario u = usuario("RECEPCION");
        assertThat(u.getTokenVersion()).isZero();
        assertThat(u.aceptaVersionDeToken(0)).isTrue();

        u.cambiarPassword("hash-1");
        assertThat(u.getPasswordHash()).isEqualTo("hash-1");
        assertThat(u.getTokenVersion()).isEqualTo(1);
        assertThat(u.aceptaVersionDeToken(0)).isFalse();
        assertThat(u.aceptaVersionDeToken(1)).isTrue();

        u.restablecerPassword("hash-2");
        assertThat(u.getTokenVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("B-011: restablecer la contraseña no toca el 2FA; un ADMIN_GENERAL sin 2FA activo debe configurarlo")
    void restablecerNoActiva2faSinSecreto() {
        Usuario sin2fa = usuario("ADMIN_GENERAL");
        sin2fa.restablecerPassword("hash-1");
        assertThat(sin2fa.isTotpActivado()).isFalse();
        assertThat(sin2fa.requiereConfigurarTotp()).isTrue();

        Usuario con2fa = usuario("ADMIN_GENERAL");
        con2fa.iniciarConfiguracionTotp("SECRETO", java.util.List.of("A1"));
        con2fa.activarTotp();
        con2fa.restablecerPassword("hash-1");
        assertThat(con2fa.isTotpActivado()).isTrue();
        assertThat(con2fa.requiereConfigurarTotp()).isFalse();

        assertThat(usuario("RECEPCION").requiereConfigurarTotp()).isFalse();
    }

    @Test
    @DisplayName("B-009: solo los roles sin 2FA obligatorio lo desactivan ellos mismos; el restablecimiento admin limpia todo y cierra sesiones")
    void desactivarYRestablecerTotp() {
        assertThat(Usuario.puedeDesactivarSuPropioTotp("ADMIN_GENERAL")).isFalse();
        assertThat(Usuario.puedeDesactivarSuPropioTotp("ODONTOLOGO")).isTrue();

        Usuario u = usuario("ADMIN_GENERAL");
        u.setId(java.util.UUID.randomUUID());
        u.iniciarConfiguracionTotp("SECRETO", java.util.List.of("A1"));
        u.activarTotp();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> u.verificarRestablecimientoTotpPorOtro(u.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        u.verificarRestablecimientoTotpPorOtro(java.util.UUID.randomUUID());

        u.restablecerTotpPorAdministrador();
        assertThat(u.isTotpActivado()).isFalse();
        assertThat(u.getTotpSecret()).isNull();
        assertThat(u.getTotpBackupCodes()).isNull();
        assertThat(u.getTokenVersion()).isEqualTo(1);
        assertThat(u.requiereConfigurarTotp()).isTrue();
    }

    @Test
    @DisplayName("B-010: reconfigurar el 2FA exige código solo si ya está activo")
    void reconfigurarExigeCodigoSiActivo() {
        Usuario u = usuario("ADMIN_GENERAL");
        assertThat(u.exigeCodigoParaReconfigurarTotp()).isFalse();
        u.iniciarConfiguracionTotp("SECRETO", java.util.List.of("A1"));
        assertThat(u.exigeCodigoParaReconfigurarTotp()).isFalse();
        u.activarTotp();
        assertThat(u.exigeCodigoParaReconfigurarTotp()).isTrue();
    }
}
