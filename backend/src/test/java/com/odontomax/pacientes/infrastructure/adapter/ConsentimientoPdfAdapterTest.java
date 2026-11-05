package com.odontomax.pacientes.infrastructure.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ConsentimientoPdfAdapterTest {

    private final ConsentimientoPdfAdapter adapter = new ConsentimientoPdfAdapter();

    @Test
    @DisplayName("generarDocumentoPdf: genera un PDF valido con OpenPDF legible y con al menos 1 pagina")
    void generaPdfValido() {
        byte[] pdfBytes = adapter.generarDocumentoPdf(
                "Consentimiento Datos",
                "Carlos Jimenez",
                "TRATAMIENTO_DATOS",
                "Acepto el tratamiento de datos",
                "Carlos Jimenez",
                "71092834",
                OffsetDateTime.now()
        );

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        assertDoesNotThrow(() -> {
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
            assertTrue(reader.getNumberOfPages() >= 1, "El PDF generado debe tener al menos una página");
            reader.close();
        });
    }
}