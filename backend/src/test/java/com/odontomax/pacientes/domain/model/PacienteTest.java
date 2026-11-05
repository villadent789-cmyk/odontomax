package com.odontomax.pacientes.domain.model;

import com.odontomax.pacientes.domain.exception.PacienteDeudorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PacienteTest {

    private static Paciente.DatosPaciente datos(boolean promociones, String historia) {
        return new Paciente.DatosPaciente(" 12345678 ", " Ana ", " Lopez ", LocalDate.of(1990, 1, 2), "987654321",
                "a@b.pe", "FEMENINO", "LOCAL", null, null, null, promociones, historia, List.of("a", "b"),
                "ninguno", false, null);
    }

    @Test
    @DisplayName("registrar: recorta, deja saldo cero, no archivado, y la fecha de promociones solo si acepta")
    void registrar() {
        OffsetDateTime ahora = OffsetDateTime.now();
        Paciente con = Paciente.registrar(datos(true, null), "sede-1", ahora);
        Paciente sin = Paciente.registrar(datos(false, null), "sede-1", ahora);

        assertNotNull(con.getId());
        assertEquals("12345678", con.getDni());
        assertEquals("Ana", con.getNombres());
        assertEquals("Lopez", con.getApellidos());
        assertEquals("sede-1", con.getSedeId());
        assertEquals(0, BigDecimal.ZERO.compareTo(con.getSaldoPendiente()));
        assertFalse(con.isArchivado());
        assertEquals(ahora, con.getConsentimientoPromocionesFecha());
        assertNull(sin.getConsentimientoPromocionesFecha());
    }

    @Test
    @DisplayName("actualizarDatos: la fecha de promociones solo cambia de falso a verdadero y no se borra al revocar")
    void actualizarFechaPromociones() {
        OffsetDateTime antes = OffsetDateTime.now().minusDays(5);
        OffsetDateTime ahora = OffsetDateTime.now();
        Paciente p = Paciente.registrar(datos(false, null), "sede-1", antes);

        p.actualizarDatos(datos(true, null), "sede-1", ahora);
        assertEquals(ahora, p.getConsentimientoPromocionesFecha());

        p.actualizarDatos(datos(true, null), "sede-1", ahora.plusDays(1));
        assertEquals(ahora, p.getConsentimientoPromocionesFecha());

        p.actualizarDatos(datos(false, null), "sede-1", ahora.plusDays(2));
        assertFalse(p.isConsentimientoPromociones());
        assertEquals(ahora, p.getConsentimientoPromocionesFecha());
    }

    @Test
    @DisplayName("actualizarDatos: el numero de historia en blanco no sobrescribe y no toca saldo ni archivado")
    void actualizarConservaCampos() {
        Paciente p = Paciente.registrar(datos(false, "HC-1"), "sede-1", OffsetDateTime.now());
        p.setSaldoPendiente(new BigDecimal("30"));
        p.setArchivado(true);
        p.setMotivoArchivo("m");
        p.setTotalInasistencias(4);

        p.actualizarDatos(datos(false, "  "), "sede-2", OffsetDateTime.now());

        assertEquals("HC-1", p.getNumeroHistoriaClinica());
        assertEquals("sede-2", p.getSedeId());
        assertEquals(new BigDecimal("30"), p.getSaldoPendiente());
        assertTrue(p.isArchivado());
        assertEquals("m", p.getMotivoArchivo());
        assertEquals(4, p.getTotalInasistencias());
    }

    @Test
    @DisplayName("verificarArchivable: sin deuda no exige motivo; con deuda exige 5 caracteres tras trim")
    void verificarArchivable() {
        Paciente p = Paciente.builder().id(UUID.randomUUID()).saldoPendiente(BigDecimal.ZERO).build();
        assertDoesNotThrow(() -> p.verificarArchivable(null));

        p.setSaldoPendiente(new BigDecimal("150.00"));
        PacienteDeudorException ex = assertThrows(PacienteDeudorException.class, () -> p.verificarArchivable("  abc  "));
        assertTrue(ex.getMessage().contains("S/ 150.00"));
        assertDoesNotThrow(() -> p.verificarArchivable("Mudanza"));
    }

    @Test
    @DisplayName("actualizarAntecedentes: gestante solo cambia si viene informado; semanas se anula si gestante es falso; normaliza alergias")
    void actualizarAntecedentesReglas() {
        Paciente p = Paciente.builder()
                .gestante(true)
                .semanasGestacion(20)
                .build();

        // gestante = false -> anula semanas
        p.actualizarAntecedentes("Penicilina, Latex", "Ninguno", false, 15);
        assertFalse(p.isGestante());
        assertNull(p.getSemanasGestacion());
        assertEquals("Penicilina, Latex", p.getAlergiasTexto());
        assertEquals(List.of("Penicilina", " Latex"), p.getAlergias());
        assertEquals(List.of("Penicilina", "Latex"), p.alergiasConEspaciosNormalizados());

        // gestante = null -> no cambia gestante ni semanas
        p.actualizarAntecedentes("Sulfas", "Asma", null, null);
        assertFalse(p.isGestante());
        assertNull(p.getSemanasGestacion());
        assertEquals("Sulfas", p.getAlergiasTexto());
    }

    @Test
    @DisplayName("presentacion: aviso desde 2 inasistencias, cumpleanero del mes y numero de historia visible")
    void presentacion() {
        Paciente p = Paciente.builder().dni("45678912").fechaNacimiento(LocalDate.of(1990, 5, 17)).totalInasistencias(1).build();
        assertFalse(p.tieneAvisoInasistencias());
        p.setTotalInasistencias(2);
        assertTrue(p.tieneAvisoInasistencias());

        assertTrue(p.esCumpleaneroDelMes(LocalDate.of(2026, 5, 1)));
        assertFalse(p.esCumpleaneroDelMes(LocalDate.of(2026, 6, 1)));

        assertEquals("HC-45678912", p.numeroHistoriaVisible());
        p.setNumeroHistoriaClinica("H-9");
        assertEquals("H-9", p.numeroHistoriaVisible());
        p.setNumeroHistoriaClinica(" ");
        p.setDni(null);
        assertNull(p.numeroHistoriaVisible());
    }
}