package com.odontomax.application.service;

import com.lowagie.text.pdf.PdfReader;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.request.RecetaRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecetaPdfServiceTest {

    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi = mock(ConsultaEspecialistasModuloApi.class);
    private final RecetaPdfService service = new RecetaPdfService(consultaEspecialistasApi);

    @Test
    @DisplayName("Genera PDF de receta odontológica válido y legible con OpenPDF")
    void testGenerarRecetaPdf() throws Exception {
        RecetaRequestDTO dto = RecetaRequestDTO.builder()
                .pacienteId(UUID.randomUUID())
                .pacienteNombre("Carlos Mendoza Alanya")
                .pacienteDni("45892147")
                .pacienteEdad(34)
                .doctorNombre("Dr. Alex Jhoel Ríos Romero")
                .doctorCop("COP-38291")
                .sedeNombre("Sede Chilca I")
                .fecha(LocalDate.now())
                .diagnostico("Pulpitis irreversible aguda pieza 2.6")
                .indicacionesGenerales("No consumir alimentos duros o calientes por 24 horas. Reposo relativo.")
                .medicamentos(List.of(
                        RecetaRequestDTO.ItemRecetaDTO.builder()
                                .medicamento("Amoxicilina + Ácido Clavulánico 875/125mg")
                                .dosis("1 tableta")
                                .frecuencia("Cada 12 horas")
                                .duracion("Por 7 días")
                                .indicaciones("Tomar al inicio de las comidas principales.")
                                .build(),
                        RecetaRequestDTO.ItemRecetaDTO.builder()
                                .medicamento("Ibuprofeno 400mg")
                                .dosis("1 tableta")
                                .frecuencia("Cada 8 horas")
                                .duracion("Por 3 días condicional al dolor")
                                .indicaciones("Tomar después de los alimentos.")
                                .build()
                ))
                .build();

        byte[] pdfBytes = service.generarRecetaPdf(dto);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "El PDF generado debe contener bytes significativos");

        // Validar que OpenPDF PdfReader lo parsea sin errores
        PdfReader reader = new PdfReader(pdfBytes);
        assertEquals(1, reader.getNumberOfPages(), "La receta odontológica estándar debe tener 1 página");
        reader.close();
    }
    @Test
    @DisplayName("Llama a exigirAccesoDeOdontologo cuando pacienteId está presente")
    void testGenerarRecetaPdf_ExigeAccesoOdontologo() {
        UUID pacienteId = UUID.randomUUID();
        RecetaRequestDTO dto = RecetaRequestDTO.builder()
                .pacienteId(pacienteId)
                .pacienteNombre("Carlos Mendoza")
                .doctorNombre("Dr. Test")
                .medicamentos(List.of(RecetaRequestDTO.ItemRecetaDTO.builder().medicamento("Paracetamol").build()))
                .build();

        byte[] pdfBytes = service.generarRecetaPdf(dto);
        assertNotNull(pdfBytes);
        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("Lanza AccessDeniedException si exigirAccesoDeOdontologo deniega acceso")
    void testGenerarRecetaPdf_SinAcceso_LanzaAccessDenied() {
        UUID pacienteId = UUID.randomUUID();
        RecetaRequestDTO dto = RecetaRequestDTO.builder()
                .pacienteId(pacienteId)
                .pacienteNombre("Carlos Mendoza")
                .doctorNombre("Dr. Test")
                .medicamentos(List.of(RecetaRequestDTO.ItemRecetaDTO.builder().medicamento("Paracetamol").build()))
                .build();

        doThrow(new AccessDeniedException("Acceso denegado")).when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThrows(AccessDeniedException.class, () -> service.generarRecetaPdf(dto));
    }
}
