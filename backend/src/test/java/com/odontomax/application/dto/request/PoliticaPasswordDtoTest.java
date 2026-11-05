package com.odontomax.application.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoliticaPasswordDtoTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static boolean errorEnCampo(Object dto, String campo) {
        return validator.validate(dto).stream().anyMatch(v -> v.getPropertyPath().toString().equals(campo));
    }

    @Test
    @DisplayName("Política simple: 4 caracteres sin mayúsculas ni símbolos son válidos; 3 no")
    void minimoCuatroCaracteresSinComplejidad() {
        UsuarioCreateRequestDTO crear = UsuarioCreateRequestDTO.builder()
                .nombre("Dra. Ana Ríos").email("ana@odontomax.pe").rol("ODONTOLOGO").sedeId("sede-chilca-1")
                .password("abcd").build();
        assertFalse(errorEnCampo(crear, "password"));
        crear.setPassword("abc");
        assertTrue(errorEnCampo(crear, "password"));

        UsuarioResetPasswordDTO reset = new UsuarioResetPasswordDTO();
        reset.setNewPassword("1234");
        assertFalse(errorEnCampo(reset, "newPassword"));
        reset.setNewPassword("123");
        assertTrue(errorEnCampo(reset, "newPassword"));

        CambioPasswordRequestDTO cambio = new CambioPasswordRequestDTO();
        cambio.setPasswordActual("actual");
        cambio.setPasswordNuevo("hola");
        assertFalse(errorEnCampo(cambio, "passwordNuevo"));
        cambio.setPasswordNuevo("hol");
        assertTrue(errorEnCampo(cambio, "passwordNuevo"));
    }
}
