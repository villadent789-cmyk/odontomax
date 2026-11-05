package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.SedeNoDisponibleException;
import com.odontomax.agenda.domain.exception.SillonInexistenteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SedeAgendaTest {

    private final SedeAgenda activa = new SedeAgenda("sede-a", true, 3);
    private final SedeAgenda inactiva = new SedeAgenda("sede-b", false, 3);

    @Test
    @DisplayName("B-023: cita en sede activa con sillón 1..sillones o sin sillón se admite")
    void citaValida() {
        assertThatCode(() -> activa.verificarAdmiteCita(null)).doesNotThrowAnyException();
        assertThatCode(() -> activa.verificarAdmiteCita(1)).doesNotThrowAnyException();
        assertThatCode(() -> activa.verificarAdmiteCita(3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("B-023: sillón fuera de 1..sillones se rechaza")
    void sillonFueraDeRango() {
        for (int sillon : new int[]{0, -1, 4}) {
            assertThatThrownBy(() -> activa.verificarAdmiteCita(sillon))
                    .isInstanceOf(SillonInexistenteException.class)
                    .hasMessage("El sillón #" + sillon + " no existe en la sede 'sede-a' (sillones del 1 al 3).");
        }
    }

    @Test
    @DisplayName("B-023: sede inactiva no admite citas (aunque el sillón exista) ni especialistas")
    void sedeInactiva() {
        assertThatThrownBy(() -> inactiva.verificarAdmiteCita(1))
                .isInstanceOf(SedeNoDisponibleException.class)
                .hasMessage("La sede 'sede-b' está inactiva y no admite nuevas citas.");
        assertThatThrownBy(inactiva::verificarAdmiteEspecialista)
                .isInstanceOf(SedeNoDisponibleException.class)
                .hasMessage("La sede 'sede-b' está inactiva.");
        assertThatCode(activa::verificarAdmiteEspecialista).doesNotThrowAnyException();
    }
}
