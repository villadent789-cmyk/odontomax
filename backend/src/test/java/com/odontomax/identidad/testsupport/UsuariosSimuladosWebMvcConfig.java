package com.odontomax.identidad.testsupport;

import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/**
 * Para tests {@code @WebMvcTest} de OTROS módulos: además de los beans de {@link IdentidadSeguridadWebMvcConfig},
 * simula el repositorio de usuarios que consulta el filtro JWT. Los usuarios se dan de alta con
 * {@link UsuarioTestFixtures#simularUsuarioActivo}, sin que el test referencie la persistencia de identidad.
 */
@TestConfiguration
@Import(IdentidadSeguridadWebMvcConfig.class)
public class UsuariosSimuladosWebMvcConfig {

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;
}
