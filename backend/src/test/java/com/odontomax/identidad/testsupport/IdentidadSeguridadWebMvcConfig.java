package com.odontomax.identidad.testsupport;

import com.odontomax.identidad.infrastructure.adapter.InMemoryJwtUserCacheAdapter;
import com.odontomax.identidad.infrastructure.persistence.adapter.UsuarioJpaAdapter;
import com.odontomax.identidad.infrastructure.persistence.mapper.UsuarioMapperImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Beans de identidad que necesita JwtAuthenticationFilter en los tests {@code @WebMvcTest} (que no escanean
 * adaptadores): caché JWT y búsqueda de usuarios sobre el {@code SpringDataUsuarioRepository} que cada test
 * simula con {@code @MockBean}. Así los tests de otros módulos no referencian la persistencia de identidad.
 */
@TestConfiguration
@Import({InMemoryJwtUserCacheAdapter.class, UsuarioJpaAdapter.class, UsuarioMapperImpl.class})
public class IdentidadSeguridadWebMvcConfig {
}
