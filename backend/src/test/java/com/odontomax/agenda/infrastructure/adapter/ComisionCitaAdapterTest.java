package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import com.odontomax.radiologia.dto.ComisionRadiologicaCreateRequestDTO;
import com.odontomax.radiologia.service.ComisionRadiologicaService;
import com.odontomax.radiologia.service.LiquidacionComisionesService;
import com.odontomax.tratamientos.service.TratamientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComisionCitaAdapterTest {

    @Mock private CitaRepositoryPort citaRepository;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private ComisionRadiologicaService comisionService;
    @Mock private LiquidacionComisionesService liquidacionService;
    @Mock private SpringDataComisionRadiologicaRepository comisionRepository;
    @Mock private TratamientoService tratamientoService;

    private ComisionCitaAdapter comisionCitaAdapter;

    private final UUID pacienteId = UUID.randomUUID();
    private final UUID doctorDerivadorId = UUID.randomUUID();
    private final String sedeVillamax = "sede-villamax-chilca";

    @BeforeEach
    void setUp() {
        comisionCitaAdapter = new ComisionCitaAdapter(
                citaRepository, sedeValidacionPort, comisionService, liquidacionService,
                comisionRepository, tratamientoService
        );
    }

    @Test
    @DisplayName("Al cobrar y atender estudio en Villamax, se genera comisión automática sobre monto cobrado (precio - descuento)")
    void comisionAutomatica_alFinalizarAtencion_creaComisionIdempotenteConMontoCobrado() {
        UUID citaId = UUID.randomUUID();
        UUID tratamientoId = UUID.randomUUID();
        Cita cita = Cita.builder()
                .id(citaId)
                .pacienteId(pacienteId)
                .pacienteNombre("Carlos Quispe")
                .sedeId(sedeVillamax)
                .motivoConsulta("Radiografía panorámica")
                .doctorDerivadorId(doctorDerivadorId)
                .pacienteDirecto(false)
                .tratamientoId(tratamientoId)
                .estado("ATENDIDA")
                .build();

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(
                        sedeVillamax, "Villamax", "VILLAMAX", true,
                        null, null, null, 0, 0, 0, null, 0
                )
        ));
        when(comisionRepository.findByCitaId(citaId)).thenReturn(Optional.empty());
        when(comisionRepository.findByTratamientoId(tratamientoId)).thenReturn(Optional.empty());

        // Plan de estudio: Precio 60, Descuento 15 -> Total costo = 45, Saldo pendiente = 0
        TratamientoResponseDTO trat = TratamientoResponseDTO.builder()
                .id(tratamientoId)
                .costoTotal(BigDecimal.valueOf(45.00))
                .descuentoTotal(BigDecimal.valueOf(15.00))
                .montoPagado(BigDecimal.valueOf(45.00))
                .saldoPendiente(BigDecimal.ZERO)
                .build();
        when(tratamientoService.buscarParaCita(tratamientoId)).thenReturn(Optional.of(trat));

        comisionCitaAdapter.generarComisionSiAplica(citaId);

        ArgumentCaptor<ComisionRadiologicaCreateRequestDTO> captor = ArgumentCaptor.forClass(ComisionRadiologicaCreateRequestDTO.class);
        verify(comisionService).registrarComision(captor.capture(), any());

        ComisionRadiologicaCreateRequestDTO req = captor.getValue();
        assertThat(req.getDoctorDerivadorId()).isEqualTo(doctorDerivadorId);
        assertThat(req.getCitaId()).isEqualTo(citaId);
        assertThat(req.getTratamientoId()).isEqualTo(tratamientoId);
        assertThat(req.getPrecioCobrado()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("Idempotencia: si ya existe una comisión para la cita o tratamiento, no se duplica")
    void comisionAutomatica_idempotente_noDuplica() {
        UUID citaId = UUID.randomUUID();
        Cita cita = Cita.builder()
                .id(citaId)
                .sedeId(sedeVillamax)
                .doctorDerivadorId(doctorDerivadorId)
                .pacienteDirecto(false)
                .build();

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(sedeVillamax, "Villamax", "VILLAMAX", true, null, null, null, 0, 0, 0, null, 0)
        ));
        when(comisionRepository.findByCitaId(citaId)).thenReturn(Optional.of(new ComisionRadiologicaEntity()));

        comisionCitaAdapter.generarComisionSiAplica(citaId);

        verify(comisionService, never()).registrarComision(any(), any());
    }

    @Test
    @DisplayName("Paciente directo no genera comisión radiológica")
    void comisionAutomatica_pacienteDirecto_noGeneraComision() {
        UUID citaId = UUID.randomUUID();
        Cita cita = Cita.builder()
                .id(citaId)
                .sedeId(sedeVillamax)
                .pacienteDirecto(true)
                .build();

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(sedeVillamax, "Villamax", "VILLAMAX", true, null, null, null, 0, 0, 0, null, 0)
        ));

        comisionCitaAdapter.generarComisionSiAplica(citaId);

        verify(comisionService, never()).registrarComision(any(), any());
    }

    @Test
    @DisplayName("Anular comisión invoca a servicio de radiología")
    void anularComision_invocaServicioRadiologia() {
        UUID citaId = UUID.randomUUID();
        comisionCitaAdapter.anularComisionSiAplica(citaId, "Cancelación de prueba");
        verify(comisionService).anularComisionPorCita(citaId, "Cancelación de prueba");

        UUID tratamientoId = UUID.randomUUID();
        comisionCitaAdapter.anularComisionPorTratamientoSiAplica(tratamientoId, "Anulación de prueba");
        verify(comisionService).anularComisionPorTratamiento(tratamientoId, "Anulación de prueba");
    }

    @Test
    @DisplayName("Si la comisión falla al generarse, el cobro sigue pero queda registrada como pendiente de generar")
    void comisionQueFalla_quedaRegistradaParaReintentar() {
        UUID citaId = UUID.randomUUID();
        UUID tratamientoId = UUID.randomUUID();
        Cita cita = Cita.builder().id(citaId).pacienteId(pacienteId).pacienteNombre("Carlos Quispe").sedeId(sedeVillamax)
                .motivoConsulta("Radiografía panorámica").doctorDerivadorId(doctorDerivadorId).pacienteDirecto(false)
                .tratamientoId(tratamientoId).estado("ATENDIDA").build();
        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(sedeValidacionPort.obtenerConfiguracionSede(sedeVillamax)).thenReturn(Optional.of(
                new SedeValidacionPort.SedeAgendaConfig(sedeVillamax, "Villamax", "VILLAMAX", true, null, null, null, 0, 0, 0, null, 0)));
        when(comisionRepository.findByCitaId(citaId)).thenReturn(Optional.empty());
        when(comisionRepository.findByTratamientoId(tratamientoId)).thenReturn(Optional.empty());
        when(tratamientoService.buscarParaCita(tratamientoId)).thenReturn(Optional.of(TratamientoResponseDTO.builder()
                .id(tratamientoId).costoTotal(BigDecimal.valueOf(50)).saldoPendiente(BigDecimal.ZERO).build()));
        when(comisionService.registrarComision(any(), any())).thenThrow(new IllegalArgumentException("El doctor derivador está inactivo"));

        comisionCitaAdapter.generarComisionSiAplica(citaId);

        verify(liquidacionService).registrarFalloAlGenerar(eq(citaId), eq(sedeVillamax), eq("Carlos Quispe"),
                eq("Radiografía panorámica"), contains("inactivo"));
        verify(liquidacionService, never()).resolverFallo(citaId);
    }
}
