package com.odontomax.agenda.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** B-030/B-031: el solape es por intervalo [hora, hora + duración) y solo cuentan los estados que ocupan la agenda. */
class CitaSolapeTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 5);

    private Cita cita(String hora, int duracion, String estado) {
        return Cita.builder().fecha(DIA).hora(LocalTime.parse(hora)).duracionMinutos(duracion).estado(estado).build();
    }

    @Test
    void solapeParcialYContenido() {
        Cita c = cita("10:00", 60, "PROGRAMADA");
        assertTrue(c.solapaCon(DIA, LocalTime.of(10, 0), 30));
        assertTrue(c.solapaCon(DIA, LocalTime.of(10, 30), 60));
        assertTrue(c.solapaCon(DIA, LocalTime.of(9, 45), 30));
        assertTrue(c.solapaCon(DIA, LocalTime.of(9, 0), 180));
    }

    @Test
    void contiguasYOtroDiaNoSolapan() {
        Cita c = cita("10:00", 60, "PROGRAMADA");
        assertFalse(c.solapaCon(DIA, LocalTime.of(11, 0), 30));
        assertFalse(c.solapaCon(DIA, LocalTime.of(9, 30), 30));
        assertFalse(c.solapaCon(DIA.plusDays(1), LocalTime.of(10, 0), 30));
    }

    @Test
    void cruceDeMedianoche() {
        Cita c = cita("23:45", 30, "PROGRAMADA");
        assertTrue(c.solapaCon(DIA.plusDays(1), LocalTime.of(0, 10), 30));
        assertFalse(c.solapaCon(DIA.plusDays(1), LocalTime.of(0, 15), 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADA", "NO_ASISTIO", "EXPIRADA"})
    void estadosQueLiberanElHueco(String estado) {
        assertFalse(cita("10:00", 30, estado).ocupaAgenda());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE_WEB", "PENDIENTE_DE_PAGO", "PROGRAMADA", "CONFIRMADA", "CONFIRMADA_CON_ADELANTO", "EN_SALA", "ATENDIDA"})
    void estadosQueOcupan(String estado) {
        assertTrue(cita("10:00", 30, estado).ocupaAgenda());
    }
}
