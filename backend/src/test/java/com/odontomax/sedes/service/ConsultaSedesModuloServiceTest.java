package com.odontomax.sedes.service;

import com.odontomax.sedes.api.SedeResumen;
import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataServicioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaSedesModuloServiceTest {

    @Mock
    private SpringDataSedeRepository sedeRepository;

    @Mock
    private SpringDataServicioRepository servicioRepository;

    @InjectMocks
    private ConsultaSedesModuloService service;

    @Test
    @DisplayName("existeSede: null, vacío o en blanco devuelve false sin consultar el repositorio")
    void nuloOBlancoEsFalse() {
        assertFalse(service.existeSede(null));
        assertFalse(service.existeSede(""));
        assertFalse(service.existeSede("   "));
        verifyNoInteractions(sedeRepository);
    }

    @Test
    @DisplayName("existeSede: recorta espacios y consulta por id exacto")
    void recortaEspacios() {
        when(sedeRepository.existsById("sede-chilca-1")).thenReturn(true);

        assertTrue(service.existeSede("  sede-chilca-1  "));
        verify(sedeRepository).existsById("sede-chilca-1");
    }

    @Test
    @DisplayName("existeSede: id existente (activa o inactiva) devuelve true")
    void existenteEsTrue() {
        when(sedeRepository.existsById("sede-chilca-1")).thenReturn(true);

        assertTrue(service.existeSede("sede-chilca-1"));
    }

    @Test
    @DisplayName("existeSede: id inexistente o con otras mayúsculas devuelve false")
    void inexistenteEsFalse() {
        when(sedeRepository.existsById("SEDE-CHILCA-1")).thenReturn(false);
        when(sedeRepository.existsById("sede-nada")).thenReturn(false);

        assertFalse(service.existeSede("SEDE-CHILCA-1"));
        assertFalse(service.existeSede("sede-nada"));
    }

    @Test
    @DisplayName("obtenerSede (B-023): null o en blanco vacío sin consultar; recorta; devuelve id, activa y sillones; inexistente vacío")
    void obtenerSede() {
        assertTrue(service.obtenerSede(null).isEmpty());
        assertTrue(service.obtenerSede("  ").isEmpty());
        verifyNoInteractions(sedeRepository);

        when(sedeRepository.findById("sede-x")).thenReturn(Optional.of(
                SedeEntity.builder().id("sede-x").activa(false).sillones(2).build()));
        when(sedeRepository.findById("sede-nada")).thenReturn(Optional.empty());

        assertEquals(Optional.of(new SedeResumen("sede-x", false, 2)), service.obtenerSede("  sede-x "));
        assertTrue(service.obtenerSede("sede-nada").isEmpty());
    }

    private static ServicioEntity servicio(String id, String negocio, boolean activo) {
        return ServicioEntity.builder().id(id).nombre("Estudio " + id).especialidad("Radiología").duracionMin(15)
                .precioDesde(new java.math.BigDecimal("60.00")).comisionPorcentaje(new java.math.BigDecimal("10.00"))
                .unidadDeNegocio(negocio).activo(activo).build();
    }

    @Test
    @DisplayName("obtenerEstudioDeVillamax: devuelve nombre, tarifa y comisión de un estudio activo de Villamax")
    void estudioDeVillamax() {
        when(servicioRepository.findById("vmx-panoramica")).thenReturn(Optional.of(servicio("vmx-panoramica", "VILLAMAX", true)));

        var estudio = service.obtenerEstudioDeVillamax(" vmx-panoramica ").orElseThrow();

        assertEquals("vmx-panoramica", estudio.id());
        assertEquals(new java.math.BigDecimal("60.00"), estudio.precio());
        assertEquals(new java.math.BigDecimal("10.00"), estudio.comisionPorcentaje());
    }

    @Test
    @DisplayName("obtenerEstudioDeVillamax: un tratamiento de Villadent, un estudio inactivo o un id vacío no cuentan")
    void estudioQueNoCuenta() {
        when(servicioRepository.findById("limpieza")).thenReturn(Optional.of(servicio("limpieza", "VILLADENT", true)));
        when(servicioRepository.findById("viejo")).thenReturn(Optional.of(servicio("viejo", "VILLAMAX", false)));

        assertTrue(service.obtenerEstudioDeVillamax("limpieza").isEmpty());
        assertTrue(service.obtenerEstudioDeVillamax("viejo").isEmpty());
        assertTrue(service.obtenerEstudioDeVillamax("  ").isEmpty());
        assertTrue(service.obtenerEstudioDeVillamax(null).isEmpty());
    }
}
