package com.odontomax.pacientes.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FirmaManuscritaTest {

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
    };

    @Test
    @DisplayName("desdeBase64: decodifica correctamente imagen PNG con y sin prefijo data URL")
    void decodificaValida() {
        String rawBase64 = Base64.getEncoder().encodeToString(VALID_PNG);
        FirmaManuscrita f1 = FirmaManuscrita.desdeBase64(rawBase64);
        assertNotNull(f1.getBytes());
        assertEquals(VALID_PNG.length, f1.getBytes().length);

        String dataUrl = "data:image/png;base64," + rawBase64;
        FirmaManuscrita f2 = FirmaManuscrita.desdeBase64(dataUrl);
        assertNotNull(f2.getBytes());
        assertEquals(VALID_PNG.length, f2.getBytes().length);
    }

    @Test
    @DisplayName("desdeBase64: rechaza nulo o en blanco")
    void rechazaVacio() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FirmaManuscrita.desdeBase64("   "));
        assertTrue(ex.getMessage().contains("requerida"));
    }

    @Test
    @DisplayName("desdeBase64: rechaza firma mayor a 500 KB")
    void rechazaMayorA500KB() {
        byte[] huge = new byte[501 * 1024];
        String base64 = Base64.getEncoder().encodeToString(huge);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FirmaManuscrita.desdeBase64(base64));
        assertTrue(ex.getMessage().contains("500 KB"));
    }

    @Test
    @DisplayName("desdeBase64: rechaza bytes con cabecera no PNG")
    void rechazaNoPng() {
        byte[] badHeader = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        String base64 = Base64.getEncoder().encodeToString(badHeader);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FirmaManuscrita.desdeBase64(base64));
        assertTrue(ex.getMessage().contains("PNG válida"));
    }
}