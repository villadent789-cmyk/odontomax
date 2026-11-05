package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.SillonEspecialistaFijoException;
import com.odontomax.agenda.domain.exception.SillonInexistenteException;
import com.odontomax.agenda.domain.exception.SillonNoAsignadoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EspecialistaSillonTest {

    @Test
    void asignarSillonValidoEnSede() {
        Especialista especialista = especialista();

        especialista.asignarSillonEnSede("sede-chilca-1", 2, new SedeAgenda("sede-chilca-1", true, 4));

        assertEquals(2, especialista.obtenerSillonEnSede("sede-chilca-1"));
    }

    @Test
    void rechazaSillonFueraDeLaCapacidadDeLaSede() {
        Especialista especialista = especialista();

        assertThrows(SillonInexistenteException.class, () ->
                especialista.asignarSillonEnSede("sede-chilca-1", 5, new SedeAgenda("sede-chilca-1", true, 4)));
    }

    @Test
    void noResuelveUnaCitaSiElEspecialistaNoTieneSillonFijo() {
        Especialista especialista = especialista();

        assertThrows(SillonNoAsignadoException.class, () ->
                especialista.resolverSillonCita("sede-chilca-1", 3));
        assertThrows(SillonNoAsignadoException.class, () ->
                especialista.resolverSillonCita("sede-chilca-1", null));
    }

    @Test
    void rechazaUnaPeticionQueContradiceElSillonFijo() {
        Especialista especialista = especialista();
        especialista.asignarSillonEnSede("sede-chilca-1", 2, new SedeAgenda("sede-chilca-1", true, 4));

        assertEquals(2, especialista.resolverSillonCita("sede-chilca-1", null));
        assertThrows(SillonEspecialistaFijoException.class, () ->
                especialista.resolverSillonCita("sede-chilca-1", 1));
    }

    private static Especialista especialista() {
        return Especialista.builder()
                .id(UUID.randomUUID())
                .sedes(List.of("sede-chilca-1"))
                .build();
    }
}
