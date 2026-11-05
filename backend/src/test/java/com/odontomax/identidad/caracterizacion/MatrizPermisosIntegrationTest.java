package com.odontomax.identidad.caracterizacion;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.AgendarCitaUseCase;
import com.odontomax.agenda.application.port.in.CambiarEstadoCitaUseCase;
import com.odontomax.agenda.application.port.in.CancelarCitaUseCase;
import com.odontomax.agenda.application.port.in.GestionarListaEsperaUseCase;
import com.odontomax.agenda.application.port.in.ReintentarComisionUseCase;
import com.odontomax.agenda.application.port.in.ConsultarCitaUseCase;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.ReprogramarCitaUseCase;
import com.odontomax.agenda.application.port.out.RateLimitPort;
import com.odontomax.application.service.*;
import com.odontomax.sedes.service.SedeService;
import com.odontomax.sedes.service.ServicioService;
import com.odontomax.identidad.application.port.in.CambiarPasswordUseCase;
import com.odontomax.identidad.application.port.in.ConsultarPerfilUseCase;
import com.odontomax.identidad.application.port.in.ConsultarUsuarioUseCase;
import com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase;
import com.odontomax.identidad.application.port.in.GestionarTotpUseCase;
import com.odontomax.identidad.application.port.in.LoginUseCase;
import com.odontomax.pacientes.domain.model.Paciente;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.infrastructure.security.TestSecurityEnvironmentInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import com.odontomax.laboratorio.service.LaboratorioService;
import com.odontomax.radiologia.service.ComisionRadiologicaService;
import com.odontomax.application.service.ReporteDiarioCajaService;
import com.odontomax.radiologia.service.LiquidacionComisionesService;
import com.odontomax.reportes.service.ReporteService;
import com.odontomax.tratamientos.service.TratamientoService;
import com.odontomax.clinica.service.OdontogramaPdfService;
import com.odontomax.inventario.service.InventarioService;
import com.odontomax.pacientes.application.port.in.GestionarAntecedentesUseCase;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;

/**
 * Matriz de permisos EJECUTADA (plan identidad, secciones 10.3 y 10.4): 173 endpoints × 5 sujetos
 * (107 + POST /usuarios/{id}/2fa/restablecer, Fase 2.5 B-009).
 *
 * Contexto MVC con TODOS los controladores, la SecurityFilterChain real, JwtAuthenticationFilter real con
 * JWT firmados reales y @EnableMethodSecurity (los @PreAuthorize se evalúan de verdad). Solo se simulan los
 * servicios/casos de uso detrás de los controladores y el repositorio de usuarios que consulta el filtro.
 *
 * La tabla esperada está en src/test/resources/seguridad/matriz-permisos.csv. Si una celda no coincide con la
 * realidad, el test falla: NO se corrige la expectativa en silencio.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class MatrizPermisosIntegrationTest {

    static final String CSV = "/seguridad/matriz-permisos.csv";
    static final List<String> SUJETOS = List.of("ANONIMO", "ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION");
    static final Set<Integer> PERMITIDO_POR_DATOS = Set.of(400, 404, 409, 422, 429);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

    // Repositorio que consulta JwtAuthenticationFilter + repositorio que usa AuditoriaController
    @MockBean private SpringDataUsuarioRepository usuarioRepository;
    @MockBean private SpringDataAuditoriaRepository auditoriaRepository;
    // agenda
    @MockBean private com.odontomax.pacientes.application.port.in.GestionarFichaIngresoUseCase gestionarFichaIngresoUseCase;
    @MockBean private com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase solicitudesCorreccionUseCase;
    @MockBean private com.odontomax.pacientes.application.port.in.GestionarSolicitudesEliminacionUseCase solicitudesEliminacionUseCase;
    @MockBean private com.odontomax.pacientes.application.port.in.GestionarFotoPacienteUseCase gestionarFotoPacienteUseCase;
    @MockBean private com.odontomax.clinica.service.EpisodioOdontogramaService episodioOdontogramaService;
    @MockBean private com.odontomax.tratamientos.service.PresupuestoService presupuestoService;
    @MockBean private com.odontomax.tratamientos.service.PlantillaPlanService plantillaPlanService;
    @MockBean private com.odontomax.tratamientos.service.PacientesSinPlanService pacientesSinPlanService;
    @MockBean private com.odontomax.pacientes.application.port.in.GestionarDescuentosRecomendacionUseCase gestionarDescuentosRecomendacionUseCase;
    @MockBean private AgendarCitaUseCase agendarCitaUseCase;
    @MockBean private ReprogramarCitaUseCase reprogramarCitaUseCase;
    @MockBean private CancelarCitaUseCase cancelarCitaUseCase;
    @MockBean private CambiarEstadoCitaUseCase cambiarEstadoCitaUseCase;
    @MockBean private com.odontomax.agenda.application.port.in.IniciarAtencionCitaUseCase iniciarAtencionCitaUseCase;
    @MockBean private ConsultarCitaUseCase consultarCitaUseCase;
    @MockBean private com.odontomax.agenda.application.port.in.RegistrarAdelantoUseCase registrarAdelantoUseCase;
    @MockBean private com.odontomax.agenda.application.port.in.RegistrarContactoUseCase registrarContactoUseCase;
    @MockBean private com.odontomax.agenda.application.port.in.ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;
    @MockBean private com.odontomax.agenda.application.port.in.ProcesarExpiracionUseCase procesarExpiracionUseCase;
    @MockBean private ConsultarEspecialistaUseCase consultarEspecialistaUseCase;
    @MockBean private GestionEspecialistaUseCase gestionEspecialistaUseCase;
    @MockBean private GestionHorariosEspecialistaUseCase gestionHorariosEspecialistaUseCase;
    @MockBean private ConsultarDisponibilidadUseCase consultarDisponibilidadUseCase;
    @MockBean private RegistrarReservaPublicaUseCase registrarReservaPublicaUseCase;
    @MockBean private ConsultarReservaPublicaUseCase consultarReservaPublicaUseCase;
    @MockBean private RateLimitPort rateLimitPort;
    // servicios legacy
    // identidad
    @MockBean private LoginUseCase loginUseCase;
    @MockBean private ConsultarPerfilUseCase consultarPerfilUseCase;
    @MockBean private CambiarPasswordUseCase cambiarPasswordUseCase;
    @MockBean private GestionarTotpUseCase gestionarTotpUseCase;
    @MockBean private ConsultarUsuarioUseCase consultarUsuarioUseCase;
    @MockBean private GestionarUsuarioUseCase gestionarUsuarioUseCase;
    @MockBean private GestionarAntecedentesUseCase antecedentesService;
    @MockBean private CajaService cajaService;
    @MockBean private ServicioService servicioService;
    @MockBean private GestionarConsentimientosUseCase consentimientoService;
    @MockBean private GastoService gastoService;
    @MockBean private GestionarAccesoHistoriaUseCase historiaClinicaAccesoService;
    @MockBean private InventarioService inventarioService;
    @MockBean private MantenimientoService mantenimientoService;
    @MockBean private NotaEvolucionService notaEvolucionService;
    @MockBean private OdontogramaService odontogramaService;
    @MockBean private OdontogramaPdfService odontogramaPdfService;
    @MockBean private com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase eliminarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase consultarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.RegistrarPacienteUseCase registrarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ActualizarPacienteUseCase actualizarPacienteUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.in.ArchivarPacienteUseCase archivarPacienteUseCase;
    @MockBean private ExportarExpedienteUseCase exportacionPacienteService;
    @MockBean private PagoService pagoService;
    @MockBean private RadiografiaService radiografiaService;
    @MockBean private RecetaPdfService recetaPdfService;
    @MockBean private ReporteService reporteService;
    @MockBean private com.odontomax.reportes.service.RendimientoDoctoresService rendimientoDoctoresService;
    @MockBean private SedeService sedeService;
    @MockBean private TratamientoService tratamientoService;
    @MockBean private LaboratorioService laboratorioService;
    @MockBean private ComisionRadiologicaService comisionRadiologicaService;
    @MockBean private GestionarListaEsperaUseCase gestionarListaEsperaUseCase;
    @MockBean private ReintentarComisionUseCase reintentarComisionUseCase;
    @MockBean private LiquidacionComisionesService liquidacionComisionesService;
    @MockBean private ReporteDiarioCajaService reporteDiarioCajaService;
    @MockBean private com.odontomax.plantillas.service.PlantillasService plantillasService;
    @MockBean private com.odontomax.inventario.service.ConsumoInsumosService consumoInsumosService;

    @BeforeEach
    void prepararSujetosYDatos() {
        for (String rol : SUJETOS.subList(1, SUJETOS.size())) {
            when(usuarioRepository.findByEmailIgnoreCase(email(rol))).thenReturn(Optional.of(UsuarioEntity.builder()
                    .id(idDe(rol)).email(email(rol)).nombre("Sujeto " + rol).rol(rol)
                    .sedeId(MatrizPermisosFixtures.SEDE).activo(true).build()));
        }
        // Datos mínimos para que los controladores que leen el recurso antes de validar la sede no fallen con 500
        when(consultarCitaUseCase.obtenerPorId(any())).thenReturn(CitaResponseDTO.builder()
                .id(MatrizPermisosFixtures.ID).sedeId(MatrizPermisosFixtures.SEDE).build());
        when(consultarBandejaTareasUseCase.obtenerBandejaTareas(any(), any()))
                .thenReturn(com.odontomax.agenda.application.dto.response.BandejaTareasResponseDTO.builder().build());
        when(registrarAdelantoUseCase.registrarAdelanto(any(), any()))
                .thenReturn(CitaResponseDTO.builder().id(MatrizPermisosFixtures.ID).sedeId(MatrizPermisosFixtures.SEDE).build());
        when(registrarContactoUseCase.registrarContacto(any(), any()))
                .thenReturn(CitaResponseDTO.builder().id(MatrizPermisosFixtures.ID).sedeId(MatrizPermisosFixtures.SEDE).build());
        when(consultarPacienteUseCase.obtenerPorId(any())).thenReturn(
                com.odontomax.pacientes.application.dto.response.PacienteResponseDTO.builder()
                .id(MatrizPermisosFixtures.ID).dni("12345678").nombres("Ana").apellidos("Rojas")
                .sedeId(MatrizPermisosFixtures.SEDE).build());
        when(radiografiaService.obtenerUrlFirmada(any(), anyInt())).thenReturn("https://r2.example/firmada");
        when(recetaPdfService.generarRecetaPdf(any())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
    }

    static String email(String rol) {
        return "matriz-" + rol.toLowerCase().replace('_', '-') + "@odontomax.pe";
    }

    static UUID idDe(String rol) {
        return UUID.nameUUIDFromBytes(rol.getBytes(StandardCharsets.UTF_8));
    }

    record Fila(String num, String verbo, String ruta, Map<String, String> esperado) {}

    static List<Fila> leerCsv() throws Exception {
        List<Fila> filas = new ArrayList<>();
        try (var in = MatrizPermisosIntegrationTest.class.getResourceAsStream(CSV);
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.isBlank() || linea.startsWith("#") || linea.startsWith("num;")) {
                    continue;
                }
                String[] c = linea.split(";");
                filas.add(new Fila(c[0], c[1], c[2], Map.of(SUJETOS.get(0), c[3], SUJETOS.get(1), c[4],
                        SUJETOS.get(2), c[5], SUJETOS.get(3), c[6], SUJETOS.get(4), c[7])));
            }
        }
        return filas;
    }

    static Stream<Arguments> celdas() throws Exception {
        return leerCsv().stream().flatMap(f -> SUJETOS.stream()
                .map(s -> Arguments.of(f.num(), f.verbo(), f.ruta(), s, f.esperado().get(s))));
    }

    @ParameterizedTest(name = "#{0} {1} {2} como {3} → {4}")
    @MethodSource("celdas")
    @DisplayName("Matriz de permisos 173 × 5; un odontólogo no consulta datos de negocio")
    void celda(String num, String verbo, String ruta, String sujeto, String esperado) throws Exception {
        MockHttpServletRequestBuilder req = MatrizPermisosFixtures.peticion(verbo, ruta);
        if (!"ANONIMO".equals(sujeto)) {
            req.header("Authorization", "Bearer " + jwtTokenProvider.generateToken(
                    idDe(sujeto), email(sujeto), "Sujeto " + sujeto, sujeto, MatrizPermisosFixtures.SEDE, 0));
        }
        MvcResult r = mockMvc.perform(req).andReturn();
        int codigo = r.getResponse().getStatus();
        String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(clasificar(codigo)).as("#%s %s %s como %s (HTTP %d, cuerpo %s)", num, verbo, ruta, sujeto, codigo, cuerpo)
                .isEqualTo(esperado);

        // Emisor de la respuesta (plan 10.3)
        if (codigo == 401) {
            if ("/auth/me".equals(ruta)) {
                assertThat(cuerpo).as("/auth/me anónimo: 401 sin cuerpo").isEmpty();
            } else {
                JsonNode b = objectMapper.readTree(cuerpo);
                assertThat(b.get("error").asText()).isEqualTo("Unauthorized");
                assertThat(b.has("path")).as("401 de JwtAuthenticationEntryPoint").isTrue();
                assertThat(b.has("mensaje")).isFalse();
            }
        } else if (codigo == 403) {
            JsonNode b = objectMapper.readTree(cuerpo);
            assertThat(b.get("error").asText()).isEqualTo("Forbidden");
            assertThat(b.has("mensaje")).as("403 de GlobalExceptionHandler, no de CustomAccessDeniedHandler").isTrue();
            assertThat(b.has("path")).isFalse();
        }
    }

    static String clasificar(int codigo) {
        if (codigo == 401 || codigo == 403) {
            return String.valueOf(codigo);
        }
        if ((codigo >= 200 && codigo < 300) || PERMITIDO_POR_DATOS.contains(codigo)) {
            return "PERMITIDO";
        }
        return "INESPERADO_" + codigo;
    }

    @Test
    @DisplayName("El CSV cubre exactamente los endpoints registrados en RequestMappingHandlerMapping (173)")
    void csvCubreTodosLosEndpoints() throws Exception {
        Set<String> registrados = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, metodo) -> {
            if (!metodo.getBeanType().getPackageName().startsWith("com.odontomax")) {
                return;
            }
            info.getMethodsCondition().getMethods().forEach(m ->
                    info.getPatternValues().forEach(p -> registrados.add(m.name() + " " + p)));
        });
        Set<String> enCsv = new TreeSet<>();
        leerCsv().forEach(f -> enCsv.add(f.verbo() + " " + f.ruta()));

        Set<String> faltanEnCsv = new TreeSet<>(registrados);
        faltanEnCsv.removeAll(enCsv);
        Set<String> sobranEnCsv = new TreeSet<>(enCsv);
        sobranEnCsv.removeAll(registrados);

        assertThat(faltanEnCsv).as("Endpoints nuevos sin fila en " + CSV).isEmpty();
        assertThat(sobranEnCsv).as("Filas del CSV que ya no existen como endpoint").isEmpty();
        assertThat(registrados).hasSize(196);
        assertThat(leerCsv()).hasSize(196);
        enCsv.forEach(k -> assertThat(MatrizPermisosFixtures.tieneFixture(k.split(" ")[0], k.split(" ")[1]))
                .as("fixture de petición para %s", k).isTrue());
    }
}
