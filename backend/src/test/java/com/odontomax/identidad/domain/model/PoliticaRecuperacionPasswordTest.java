package com.odontomax.identidad.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Claves de los límites de B-006 (revisión BL-4). */
class PoliticaRecuperacionPasswordTest {

    @Test
    @DisplayName("IPv4: la clave por IP es la dirección completa")
    void ipv4() {
        assertThat(PoliticaRecuperacionPassword.claveIp("10.0.0.7")).isEqualTo("ip|10.0.0.7");
        assertThat(PoliticaRecuperacionPassword.claveIp(" 10.0.0.7 ")).isEqualTo("ip|10.0.0.7");
        assertThat(PoliticaRecuperacionPassword.claveIp(null)).isEqualTo("ip|unknown");
    }

    @Test
    @DisplayName("IPv6: todas las direcciones del mismo /64 comparten clave, escritas como se escriban")
    void ipv6PorPrefijo64() {
        String a = PoliticaRecuperacionPassword.claveIp("2001:db8:abcd:12::1");
        String b = PoliticaRecuperacionPassword.claveIp("2001:0DB8:ABCD:0012:ffff:1:2:3");
        String c = PoliticaRecuperacionPassword.claveIp("2001:db8:abcd:12:0:0:0:99%eth0");
        assertThat(a).isEqualTo("ip6|2001:db8:abcd:12::/64").isEqualTo(b).isEqualTo(c);
        assertThat(PoliticaRecuperacionPassword.claveIp("2001:db8:abcd:13::1")).isNotEqualTo(a);
        assertThat(PoliticaRecuperacionPassword.claveIp("::1")).isEqualTo("ip6|0:0:0:0::/64");
    }

    @Test
    @DisplayName("IPv6 mal formada: se usa la cadena completa como clave")
    void ipv6Invalida() {
        assertThat(PoliticaRecuperacionPassword.claveIp("1::2::3")).isEqualTo("ip6|1::2::3");
    }
}
