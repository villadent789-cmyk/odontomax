package com.odontomax.tratamientos.domain.model;

import com.odontomax.tratamientos.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TratamientoDominioTest {

    private Tratamiento crearTratamiento(String estado, int sesionesRealizadas, int totalSesiones, BigDecimal costoTotal) {
        return new Tratamiento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "sede-chilca-1",
                UUID.randomUUID(),
                "Ortodoncia fija",
                estado,
                totalSesiones,
                sesionesRealizadas,
                costoTotal,
                null,
                null
        );
    }

    @Test
    @DisplayName("Transición válida de PLANIFICADO a EN_PROCESO y a TERMINADO")
    void transicionesValidas_PlanificadoAEnProcesoYTerminado() {
        Tratamiento t = crearTratamiento("PLANIFICADO", 0, 3, new BigDecimal("600.00"));

        t.cambiarEstado("EN_PROCESO", null);
        assertEquals("EN_PROCESO", t.getEstado());
        assertFalse(t.esEstadoFinal());

        t.cerrarTerminado("Todas las fases concluidas");
        assertEquals("TERMINADO", t.getEstado());
        assertEquals("Todas las fases concluidas", t.getMotivoCierre());
        assertNotNull(t.getCerradoEn());
        assertTrue(t.esEstadoFinal());
    }

    @Test
    @DisplayName("Transición a INTERRUMPIDO exige motivo no vacío")
    void transicionAInterrumpido_ExigeMotivo() {
        Tratamiento t = crearTratamiento("EN_PROCESO", 2, 5, new BigDecimal("1000.00"));

        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class,
                () -> t.cerrarInterrumpido(null));
        assertTrue(exNull.getMessage().contains("Debe ingresar un motivo para interrumpir el tratamiento"));

        IllegalArgumentException exBlanco = assertThrows(IllegalArgumentException.class,
                () -> t.cerrarInterrumpido("   "));
        assertTrue(exBlanco.getMessage().contains("Debe ingresar un motivo para interrumpir el tratamiento"));

        t.cerrarInterrumpido("El paciente se muda a otra región");
        assertEquals("INTERRUMPIDO", t.getEstado());
        assertEquals("El paciente se muda a otra región", t.getMotivoCierre());
        assertNotNull(t.getCerradoEn());
        assertTrue(t.esEstadoFinal());
    }

    @Test
    @DisplayName("Un plan en estado final no permite transiciones adicionales")
    void estadoFinal_RechazaTransiciones() {
        Tratamiento tTerminado = crearTratamiento("TERMINADO", 4, 4, new BigDecimal("400.00"));
        assertTrue(tTerminado.esEstadoFinal());

        assertThrows(TransicionEstadoInvalidaException.class,
                () -> tTerminado.cambiarEstado("EN_PROCESO", null));

        Tratamiento tInterrumpido = crearTratamiento("INTERRUMPIDO", 1, 4, new BigDecimal("400.00"));
        assertTrue(tInterrumpido.esEstadoFinal());

        assertThrows(TransicionEstadoInvalidaException.class,
                () -> tInterrumpido.cambiarEstado("EN_PROCESO", null));
    }

    @Test
    @DisplayName("Un plan en estado final no permite registrar nuevas sesiones")
    void estadoFinal_NoPermiteRegistrarSesiones() {
        Tratamiento t = crearTratamiento("TERMINADO", 4, 4, new BigDecimal("400.00"));
        assertFalse(t.puedeRegistrarSesion());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> t.registrarSesion(BigDecimal.ZERO));
        assertTrue(ex.getMessage().contains("No se pueden registrar sesiones en un tratamiento en estado final"));
    }

    @Test
    @DisplayName("Cierre automático cuando saldo es 0 Y todas las sesiones están realizadas")
    void cierreAutomatico_SaldoCeroYTodasLasSesiones_PasaATerminado() {
        Tratamiento t = crearTratamiento("EN_PROCESO", 3, 4, new BigDecimal("400.00"));

        // Al registrar la 4ta sesión con saldo 0
        t.registrarSesion(BigDecimal.ZERO);

        assertEquals(4, t.getSesionesRealizadas());
        assertEquals("TERMINADO", t.getEstado());
        assertTrue(t.esEstadoFinal());
        assertNotNull(t.getCerradoEn());
        assertTrue(t.getMotivoCierre().contains("Concluido con éxito"));
    }

    @Test
    @DisplayName("Saldo 0 pero faltan sesiones: no se cierra automáticamente (sigue EN_PROCESO)")
    void saldoCero_FaltanSesiones_NoSeCierraSolo() {
        Tratamiento t = crearTratamiento("EN_PROCESO", 2, 5, new BigDecimal("500.00"));

        boolean cerrado = t.verificarCierreAutomatico(BigDecimal.ZERO);

        assertFalse(cerrado);
        assertEquals("EN_PROCESO", t.getEstado());
        assertFalse(t.esEstadoFinal());
        assertNull(t.getCerradoEn());
    }

    @Test
    @DisplayName("Todas las sesiones completadas pero con saldo pendiente: no se cierra automáticamente")
    void todasLasSesiones_ConSaldoPendiente_NoSeCierraSolo() {
        Tratamiento t = crearTratamiento("EN_PROCESO", 4, 4, new BigDecimal("400.00"));

        boolean cerrado = t.verificarCierreAutomatico(new BigDecimal("100.00"));

        assertFalse(cerrado);
        assertEquals("EN_PROCESO", t.getEstado());
        assertFalse(t.esEstadoFinal());
    }
}
