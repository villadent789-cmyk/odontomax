package com.odontomax.identidad.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsNegocioTest {

    @BeforeEach
    void proveedor() {
        SecurityUtils.registrarProveedorNegocio(negocio ->
                "VILLAMAX".equals(negocio) ? List.of("sede-villamax-chilca") : List.of("sede-chilca-1", "sede-eltambo"));
    }

    @AfterEach
    void limpiar() {
        SecurityUtils.registrarProveedorNegocio(null);
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static void peticion(String rol, String sedeId, String header) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(UUID.randomUUID(), "u@odontomax.pe", "Usuario", rol, sedeId), null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(SecurityUtils.HEADER_NEGOCIO, header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("El administrador general trabaja sobre el negocio del header y 'todas' son las sedes de ese negocio")
    void administradorGeneralAcotaPorNegocio() {
        peticion("ADMIN_GENERAL", null, "villamax");

        assertThat(SecurityUtils.negocioActivo()).isEqualTo("VILLAMAX");
        assertThat(SecurityUtils.sedesDelNegocioActivo()).containsExactly("sede-villamax-chilca");
        assertThat(SecurityUtils.sedeEnNegocioActivo("sede-villamax-chilca")).isTrue();
        assertThat(SecurityUtils.sedeEnNegocioActivo("sede-chilca-1")).isFalse();

        peticion("ADMIN_GENERAL", null, "VILLADENT");
        assertThat(SecurityUtils.sedesDelNegocioActivo()).containsExactly("sede-chilca-1", "sede-eltambo");
        assertThat(SecurityUtils.sedeEnNegocioActivo("sede-villamax-chilca")).isFalse();
    }

    @Test
    @DisplayName("Sin header no hay filtro: todo pasa")
    void sinHeaderNoHayFiltro() {
        peticion("ADMIN_GENERAL", null, null);

        assertThat(SecurityUtils.negocioActivo()).isNull();
        assertThat(SecurityUtils.sedesDelNegocioActivo()).isNull();
        assertThat(SecurityUtils.sedeEnNegocioActivo("cualquiera")).isTrue();
    }

    @Test
    @DisplayName("Quien tiene una sede ignora el header: su alcance lo fija su sede")
    void rolesConSedeIgnoranElHeader() {
        for (String rol : List.of("ADMIN_SEDE", "RECEPCION", "ODONTOLOGO")) {
            peticion(rol, "sede-chilca-1", "VILLAMAX");

            assertThat(SecurityUtils.negocioActivo()).as(rol).isNull();
            assertThat(SecurityUtils.sedesDelNegocioActivo()).as(rol).isNull();
        }
    }

    @Test
    @DisplayName("Un negocio desconocido es un error")
    void negocioDesconocido() {
        peticion("ADMIN_GENERAL", null, "CLINICA");

        assertThatThrownBy(SecurityUtils::negocioActivo).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sin petición web o sin usuario no hay negocio activo")
    void sinPeticionOSinUsuario() {
        assertThat(SecurityUtils.negocioActivo()).isNull();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(UUID.randomUUID(), "a@odontomax.pe", "Admin", "ADMIN_GENERAL", null), null, List.of()));
        assertThat(SecurityUtils.negocioActivo()).isNull();
    }

    @Test
    @DisplayName("Un negocio sin sedes no deja pasar ninguna sede")
    void negocioSinSedes() {
        SecurityUtils.registrarProveedorNegocio(negocio -> List.of());
        peticion("ADMIN_GENERAL", null, "VILLAMAX");

        assertThat(SecurityUtils.sedeEnNegocioActivo("sede-chilca-1")).isFalse();
        assertThat(SecurityUtils.sedeEnNegocioActivo("sede-villamax-chilca")).isFalse();
    }
}
