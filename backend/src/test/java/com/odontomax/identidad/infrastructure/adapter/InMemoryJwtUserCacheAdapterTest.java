package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.application.port.out.JwtUserCachePort.UsuarioAutenticado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryJwtUserCacheAdapterTest {

    private final InMemoryJwtUserCacheAdapter cache = new InMemoryJwtUserCacheAdapter();
    private final UsuarioAutenticado ana =
            new UsuarioAutenticado(UUID.randomUUID(), "ana@odontomax.pe", "Ana", "ADMIN_SEDE", "sede-1", false, 0);

    @Test
    @DisplayName("Sin entrada devuelve vacío")
    void sinEntrada() {
        assertThat(cache.get("ana@odontomax.pe")).isEmpty();
    }

    @Test
    @DisplayName("put/get devuelve la entrada vigente, incluido el estado inactivo")
    void putGet() {
        cache.put("ana@odontomax.pe", ana);

        assertThat(cache.get("ana@odontomax.pe")).contains(ana);
    }

    @Test
    @DisplayName("evict elimina la entrada")
    void evict() {
        cache.put("ana@odontomax.pe", ana);
        cache.evict("ana@odontomax.pe");

        assertThat(cache.get("ana@odontomax.pe")).isEmpty();
    }

    @Test
    @DisplayName("Revisión obs. 1: tras desalojar con la versión vigente, una lectura anterior (versión menor) no vuelve a cachearse")
    void desalojarImpideCachearVersionAnterior() {
        UUID id = UUID.randomUUID();
        cache.put("bea@odontomax.pe", new UsuarioAutenticado(id, "bea@odontomax.pe", "Bea", "RECEPCION", "sede-1", true, 0));
        cache.desalojar("bea@odontomax.pe", 1);
        assertThat(cache.get("bea@odontomax.pe")).isEmpty();

        cache.put("bea@odontomax.pe", new UsuarioAutenticado(id, "bea@odontomax.pe", "Bea", "RECEPCION", "sede-1", true, 0));
        assertThat(cache.get("bea@odontomax.pe")).isEmpty();

        cache.put("bea@odontomax.pe", new UsuarioAutenticado(id, "bea@odontomax.pe", "Bea", "RECEPCION", "sede-1", true, 1));
        assertThat(cache.get("bea@odontomax.pe")).map(UsuarioAutenticado::tokenVersion).contains(1);

        // Una marca más antigua no rebaja la vigente
        cache.desalojar("bea@odontomax.pe", 0);
        cache.put("bea@odontomax.pe", new UsuarioAutenticado(id, "bea@odontomax.pe", "Bea", "RECEPCION", "sede-1", true, 0));
        assertThat(cache.get("bea@odontomax.pe")).isEmpty();
    }
}
