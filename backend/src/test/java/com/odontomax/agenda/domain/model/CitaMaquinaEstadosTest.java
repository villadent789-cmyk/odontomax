package com.odontomax.agenda.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** B-028: la máquina de estados vive en {@code Cita.transicionarA}. */
class CitaMaquinaEstadosTest {

    private Cita cita(String estado) {
        return Cita.builder().id(UUID.randomUUID()).sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 5)).hora(LocalTime.of(10, 0)).estado(estado).build();
    }

    @ParameterizedTest(name = "{0} → {1} es válida")
    @CsvSource({
            "PENDIENTE_WEB,PROGRAMADA", "PENDIENTE_WEB,CONFIRMADA",
            "PROGRAMADA,CONFIRMADA", "PROGRAMADA,EN_SALA", "PROGRAMADA,NO_ASISTIO",
            "CONFIRMADA,EN_SALA", "CONFIRMADA,NO_ASISTIO",
            "EN_SALA,EN_ATENCION", "EN_ATENCION,ATENDIDA",
            "PENDIENTE_DE_PAGO,CONFIRMADA_CON_ADELANTO", "PENDIENTE_DE_PAGO,EXPIRADA",
            "CONFIRMADA_CON_ADELANTO,EN_SALA", "CONFIRMADA_CON_ADELANTO,NO_ASISTIO"
    })
    @DisplayName("Transiciones del flujo normal")
    void transicionesValidas(String origen, String destino) {
        Cita c = cita(origen);
        if ("EN_SALA".equals(origen)) {
            c.setLlegadaEn(java.time.Instant.now());
        } else if ("EN_ATENCION".equals(origen)) {
            c.setLlegadaEn(java.time.Instant.now().minusSeconds(600));
            c.setInicioAtencionEn(java.time.Instant.now());
        }
        c.transicionarA(destino, null);
        assertEquals(destino, c.getEstado());
    }

    @ParameterizedTest(name = "{0} → CANCELADA con motivo es válida")
    @ValueSource(strings = {"PENDIENTE_WEB", "PENDIENTE_DE_PAGO", "PROGRAMADA", "CONFIRMADA", "CONFIRMADA_CON_ADELANTO", "EN_SALA", "EN_ATENCION"})
    void cancelarDesdeEstadosAbiertos(String origen) {
        Cita c = cita(origen);
        c.transicionarA("CANCELADA", "Paciente avisa que no viene");
        assertEquals("CANCELADA", c.getEstado());
        assertEquals("Paciente avisa que no viene", c.getMotivoCancelacion());
    }

    @ParameterizedTest(name = "{0} → {1} se rechaza")
    @CsvSource({
            "ATENDIDA,PROGRAMADA", "ATENDIDA,CONFIRMADA", "ATENDIDA,EN_SALA", "ATENDIDA,NO_ASISTIO",
            "CANCELADA,CONFIRMADA", "CANCELADA,PROGRAMADA", "CANCELADA,EN_SALA",
            "NO_ASISTIO,CONFIRMADA", "NO_ASISTIO,ATENDIDA", "EXPIRADA,PROGRAMADA",
            "PROGRAMADA,ATENDIDA", "CONFIRMADA,ATENDIDA", "EN_SALA,ATENDIDA", "PENDIENTE_WEB,EN_SALA", "PENDIENTE_WEB,ATENDIDA",
            "EN_SALA,PROGRAMADA", "EN_SALA,CONFIRMADA", "EN_SALA,NO_ASISTIO",
            "CONFIRMADA,PROGRAMADA", "PROGRAMADA,PENDIENTE_WEB", "PROGRAMADA,EXPIRADA"
    })
    @DisplayName("Transiciones fuera del flujo (atrás, saltos y desde estados finales) son un error de negocio")
    void transicionesInvalidas(String origen, String destino) {
        Cita c = cita(origen);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> c.transicionarA(destino, null));
        assertTrue(ex.getMessage().contains(origen) && ex.getMessage().contains(destino), ex.getMessage());
        assertEquals(origen, c.getEstado());
    }

    @ParameterizedTest(name = "pasar {0} a {0} se rechaza")
    @ValueSource(strings = {"PROGRAMADA", "CONFIRMADA", "EN_SALA", "ATENDIDA", "CANCELADA", "NO_ASISTIO"})
    void mismoEstadoSeRechaza(String estado) {
        Cita c = cita(estado);
        assertThrows(IllegalArgumentException.class, () -> c.transicionarA(estado, "Motivo suficiente"));
        assertEquals(estado, c.getEstado());
    }

    @ParameterizedTest(name = "cancelar desde {0} se rechaza")
    @ValueSource(strings = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "EXPIRADA"})
    void noSeCancelaDesdeEstadosFinales(String estado) {
        Cita c = cita(estado);
        assertThrows(IllegalArgumentException.class, () -> c.cancelar("Motivo suficiente"));
        assertThrows(IllegalArgumentException.class, () -> c.transicionarA("CANCELADA", "Motivo suficiente"));
        assertEquals(estado, c.getEstado());
        assertNull(c.getMotivoCancelacion());
    }

    @Test
    @DisplayName("Un estado destino desconocido o vacío se rechaza")
    void destinoDesconocido() {
        Cita c = cita("PROGRAMADA");
        assertThrows(IllegalArgumentException.class, () -> c.transicionarA("INVENTADO", null));
        assertThrows(IllegalArgumentException.class, () -> c.transicionarA(null, null));
        assertEquals("PROGRAMADA", c.getEstado());
    }

    @Test
    @DisplayName("El motivo de una transición que no es cancelar no se guarda como motivo de cancelación: queda en las notas")
    void motivoNoCancelaNoSeGuardaComoCancelacion() {
        Cita c = cita("PROGRAMADA");
        c.transicionarA("CONFIRMADA", "Confirmó por llamada");
        assertNull(c.getMotivoCancelacion());
        assertTrue(c.getNotas().contains("Confirmó por llamada"));
        assertEquals("CONFIRMADA", c.getEstado());
    }
}
