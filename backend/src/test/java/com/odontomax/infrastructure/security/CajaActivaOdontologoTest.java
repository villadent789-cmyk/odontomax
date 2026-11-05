package com.odontomax.infrastructure.security;

import com.odontomax.application.dto.response.CajaSesionResponseDTO;
import com.odontomax.application.port.out.CajaPeriodoPort;
import com.odontomax.application.port.out.CajaTotalesSesion;
import com.odontomax.application.service.CajaService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.infrastructure.persistence.entity.CajaSesionEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaActivaOdontologoTest {

    @Mock
    private SpringDataCajaSesionRepository cajaSesionRepository;

    @Mock
    private SpringDataCajaMovimientoRepository cajaMovimientoRepository;

    @Mock
    private SpringDataPagoRepository pagoRepository;

    @Mock
    private SpringDataAuditoriaRepository auditoriaRepository;

    @Mock
    private CajaPeriodoPort cajaPeriodoPort;

    @Mock
    private ConsultaUsuarioModuloApi consultaUsuarioModuloApi;

    @InjectMocks
    private CajaService cajaService;

    @BeforeEach
    void setUp() {
        CajaSesionEntity sesion = CajaSesionEntity.builder()
                .id(UUID.randomUUID())
                .sedeId("sede-chilca-1")
                .abiertaPor(UUID.randomUUID())
                .montoApertura(new BigDecimal("100.00"))
                .build();
        when(cajaSesionRepository.findBySedeIdAndCerradaEnIsNull("sede-chilca-1")).thenReturn(Optional.of(sesion));
        when(cajaMovimientoRepository.findBySesionIdOrderByRegistradoEnAsc(sesion.getId())).thenReturn(List.of());
        when(cajaPeriodoPort.calcularTotales(sesion.getId())).thenReturn(new CajaTotalesSesion(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null));
        when(consultaUsuarioModuloApi.buscarPorId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        cajaService.setCajaPeriodoPort(cajaPeriodoPort);
        cajaService.setConsultaUsuarioModuloApi(consultaUsuarioModuloApi);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String rol) {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), rol.toLowerCase() + "@odontomax.pe", "Usuario " + rol, rol, "sede-chilca-1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, null));
    }

    @Test
    @DisplayName("El odontólogo recibe solo id, sede y estado de la caja activa: sin montos ni movimientos")
    void odontologoSoloRecibeElEstado() {
        autenticar("ODONTOLOGO");

        CajaSesionResponseDTO dto = cajaService.obtenerSesionActiva("sede-chilca-1").orElseThrow();

        assertThat(dto.getEstado()).isEqualTo("ABIERTA");
        assertThat(dto.getSedeId()).isEqualTo("sede-chilca-1");
        assertThat(dto.getMontoApertura()).isNull();
        assertThat(dto.getTotalIngresosEfectivo()).isNull();
        assertThat(dto.getTotalYape()).isNull();
        assertThat(dto.getAbiertaPorNombre()).isNull();
        assertThat(dto.getMovimientos()).isEmpty();
    }

    @Test
    @DisplayName("Recepción y administradores siguen recibiendo la sesión completa")
    void otrosRolesRecibenLaSesionCompleta() {
        for (String rol : new String[] {"RECEPCION", "ADMIN_SEDE", "ADMIN_GENERAL"}) {
            autenticar(rol);

            CajaSesionResponseDTO dto = cajaService.obtenerSesionActiva("sede-chilca-1").orElseThrow();

            assertThat(dto.getMontoApertura()).as(rol).isEqualByComparingTo("100.00");
            assertThat(dto.getTotalIngresosEfectivo()).as(rol).isNotNull();
        }
    }
}
