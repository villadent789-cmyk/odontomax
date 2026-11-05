package com.odontomax.identidad.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La web del personal vive en otro dominio que la API: toda cabecera que envía debe estar permitida por CORS. */
class SecurityConfigCorsTest {

    @Test
    @DisplayName("CORS permite la cabecera X-Negocio que envía el frontend y el origen configurado")
    void permiteXNegocioYElOrigenConfigurado() {
        SecurityConfig config = new SecurityConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("https://odontomax.pages.dev"));

        CorsConfigurationSource fuente = config.corsConfigurationSource();
        CorsConfiguration cors = fuente.getCorsConfiguration(new MockHttpServletRequest("GET", "/citas"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("https://odontomax.pages.dev")).isEqualTo("https://odontomax.pages.dev");
        assertThat(cors.checkHeaders(List.of("authorization", "content-type", "x-negocio")))
                .containsExactlyInAnyOrder("authorization", "content-type", "x-negocio");
        assertThat(cors.checkOrigin("https://otro-sitio.com")).isNull();
    }
}
