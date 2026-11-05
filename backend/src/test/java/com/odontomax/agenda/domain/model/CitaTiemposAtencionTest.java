package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.InicioAtencionNoPermitidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitaTiemposAtencionTest {

    private Cita cita(String estado) {
        return Cita.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 5))
                .hora(LocalTime.of(10, 0))
                .estado(estado)
                .build();
    }

    @Test
    @DisplayName("EN_SALA sella la llegada e iniciar atención solo funciona una vez en sala")
    void llegadaEInicioAtencion() {
        Instant llegada = Instant.parse("2026-10-05T15:00:00Z");
        Instant inicio = llegada.plusSeconds(600);
        Cita cita = cita("PROGRAMADA");

        cita.transicionarA("EN_SALA", null, llegada);
        assertEquals(llegada, cita.getLlegadaEn());
        assertNull(cita.getInicioAtencionEn());

        cita.iniciarAtencion(inicio);
        assertEquals(inicio, cita.getInicioAtencionEn());
        assertThrows(InicioAtencionNoPermitidoException.class, () -> cita.iniciarAtencion(inicio.plusSeconds(1)));
    }

    @Test
    @DisplayName("ATENDIDA exige pasar por EN_ATENCION y reprogramar limpia las tres marcas")
    void finSinInicioYReprogramacionLimpiaMarcas() {
        Instant llegada = Instant.parse("2026-10-05T15:00:00Z");
        Instant inicio = llegada.plusSeconds(600);
        Instant fin = llegada.plusSeconds(1800);
        Cita cita = cita("PROGRAMADA");
        cita.transicionarA("EN_SALA", null, llegada);

        // No se puede finalizar sin pasar por EN_ATENCION
        assertThrows(IllegalArgumentException.class, () -> cita.transicionarA("ATENDIDA", null, fin));

        cita.iniciarAtencion(inicio);
        assertEquals("EN_ATENCION", cita.getEstado());
        cita.transicionarA("ATENDIDA", null, fin);

        assertEquals(fin, cita.getFinAtencionEn());
        assertEquals(inicio, cita.getInicioAtencionEn());

        Cita porReprogramar = cita("EN_SALA");
        porReprogramar.setLlegadaEn(llegada);
        porReprogramar.setInicioAtencionEn(llegada.plusSeconds(600));
        porReprogramar.setFinAtencionEn(fin);
        porReprogramar.reprogramar(LocalDate.of(2026, 10, 6), LocalTime.of(11, 0), "Cambio de horario");

        assertNull(porReprogramar.getLlegadaEn());
        assertNull(porReprogramar.getInicioAtencionEn());
        assertNull(porReprogramar.getFinAtencionEn());
    }

    @Test
    @DisplayName("Iniciar atención fuera de EN_SALA se rechaza")
    void iniciarFueraDeSalaSeRechaza() {
        Cita cita = cita("PROGRAMADA");

        assertThrows(InicioAtencionNoPermitidoException.class,
                () -> cita.iniciarAtencion(Instant.parse("2026-10-05T15:00:00Z")));
        assertEquals("PROGRAMADA", cita.getEstado());
        assertNull(cita.getInicioAtencionEn());
    }
}
