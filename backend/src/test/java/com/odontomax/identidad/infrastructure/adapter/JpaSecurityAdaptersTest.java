package com.odontomax.identidad.infrastructure.adapter;

import com.odontomax.identidad.infrastructure.persistence.entity.LoginIntentoEntity;
import com.odontomax.identidad.infrastructure.persistence.entity.RecuperacionSolicitudEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataLogin2faPendienteRepository;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataLoginIntentoRepository;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataRecuperacionSolicitudRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaSecurityAdaptersTest {

    @Mock
    private SpringDataLoginIntentoRepository loginIntentoRepository;

    @Mock
    private SpringDataLogin2faPendienteRepository login2faRepository;

    @Mock
    private SpringDataRecuperacionSolicitudRepository recuperacionRepository;

    @InjectMocks
    private JpaBruteForceAdapter bruteForceAdapter;

    @InjectMocks
    private JpaLogin2faPendienteAdapter login2faAdapter;

    @Test
    @DisplayName("JpaBruteForceAdapter: intento reservado por el upsert no bloquea")
    void bruteForcePrimerIntento() {
        when(loginIntentoRepository.sumarIntento(eq("user|1.1.1.1"), anyLong(), anyLong(), eq(5), any(Instant.class))).thenReturn(1);

        OptionalLong bloqueo = bruteForceAdapter.reservarIntento("user|1.1.1.1", 5);

        assertThat(bloqueo).isEmpty();
    }

    @Test
    @DisplayName("JpaBruteForceAdapter: si el upsert no reserva devuelve minutos restantes")
    void bruteForceSuperaMaximo() {
        long ahora = Instant.now().toEpochMilli();
        LoginIntentoEntity entity = new LoginIntentoEntity("user|1.1.1.1", 5, ahora, Instant.now().plusSeconds(900));
        when(loginIntentoRepository.sumarIntento(eq("user|1.1.1.1"), anyLong(), anyLong(), eq(5), any(Instant.class))).thenReturn(0);
        when(loginIntentoRepository.findById("user|1.1.1.1")).thenReturn(Optional.of(entity));

        OptionalLong bloqueo = bruteForceAdapter.reservarIntento("user|1.1.1.1", 5);

        assertThat(bloqueo).isPresent();
        assertThat(bloqueo.getAsLong()).isBetween(14L, 15L);
    }

    @Test
    @DisplayName("JpaLogin2faPendienteAdapter: reserva intentos y rechaza al superar maximo")
    void login2faReservaYRechaza() {
        when(login2faRepository.sumarIntento(eq("token123"), anyLong(), eq(3), any(Instant.class))).thenReturn(1, 0);

        assertThat(login2faAdapter.intentoRechazado("token123", 3)).isFalse();
        assertThat(login2faAdapter.intentoRechazado("token123", 3)).isTrue();
    }

}
