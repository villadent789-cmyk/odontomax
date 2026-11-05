package com.odontomax.application.dto.request;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EspecialistaFotoUrlDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean fotoInvalida(String url) {
        EspecialistaRequestDTO dto = EspecialistaRequestDTO.builder()
                .nombre("Dra. Thalía Vilcapoma").especialidad("Ortodoncia").sedes(List.of("sede-chilca-1"))
                .fotoUrl(url).build();
        return validator.validate(dto).stream().anyMatch(v -> v.getPropertyPath().toString().equals("fotoUrl"));
    }

    @Test
    @DisplayName("Foto del doctor: opcional; si se envía debe ser https")
    void fotoOpcionalYSoloHttps() {
        assertFalse(fotoInvalida(null));
        assertFalse(fotoInvalida(""));
        assertFalse(fotoInvalida("https://cdn.odontomax.pe/doctores/thalia.jpg"));
        assertTrue(fotoInvalida("http://sitio.pe/foto.jpg"));
        assertTrue(fotoInvalida("javascript:alert(1)"));
        assertTrue(fotoInvalida("https://con espacios.pe/x.jpg"));
    }
}
