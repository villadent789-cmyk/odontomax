package com.odontomax.sedes.service;

import com.odontomax.sedes.dto.SedeRequestDTO;
import com.odontomax.sedes.dto.SedeResponseDTO;
import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SedeServiceTest {

    @Mock
    private SpringDataSedeRepository sedeRepository;

    @Mock
    private RegistrarAuditoriaApi registrarAuditoriaApi;

    @InjectMocks
    private SedeService sedeService;

    private ClinicaUserPrincipal admin;
    private SedeEntity chilca;

    @BeforeEach
    void setUp() {
        admin = new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));

        chilca = SedeEntity.builder()
                .id("sede-chilca-1").nombre("Sede Chilca 1").direccion("Calle Real 716").ciudad("Huancayo - Chilca")
                .telefono("943 672 062").whatsapp("51943672062").email("chilca1@odontomax.pe")
                .horarioTexto("Lun - Sáb: 9:00 a.m. - 8:00 p.m.").color("#C59B27").colorSecundario("#FEF3C7")
                .sillones(4).descripcion("Sede principal").fotoPortada("/sedes/chilca-1/fachada.jpg")
                .fotos(new String[]{"/sedes/chilca-1/fachada.jpg", "/sedes/chilca-1/recepcion.jpg"})
                .latitud(new BigDecimal("-12.081000")).longitud(new BigDecimal("-75.210000"))
                .activa(true).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SedeRequestDTO.SedeRequestDTOBuilder request(String nombre) {
        return SedeRequestDTO.builder().nombre(nombre).direccion("  Av. Giráldez 123 ").sillones(2);
    }

    private void guardarDevuelveArgumento() {
        when(sedeRepository.save(any(SedeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RegistroAuditoria auditoriaRegistrada() {
        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(registrarAuditoriaApi).registrar(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- listados

    @Test
    @DisplayName("Listar activas: mapea todos los campos del contrato (fotos como lista)")
    void testListarActivasMapeaCampos() {
        when(sedeRepository.findByActivaTrueOrderByNombreAsc()).thenReturn(List.of(chilca));

        List<SedeResponseDTO> res = sedeService.listarActivas();

        assertEquals(1, res.size());
        SedeResponseDTO dto = res.get(0);
        assertEquals("sede-chilca-1", dto.getId());
        assertEquals("Lun - Sáb: 9:00 a.m. - 8:00 p.m.", dto.getHorarioTexto());
        assertEquals("#FEF3C7", dto.getColorSecundario());
        assertEquals(4, dto.getSillones());
        assertEquals("/sedes/chilca-1/fachada.jpg", dto.getFotoPortada());
        assertEquals(List.of("/sedes/chilca-1/fachada.jpg", "/sedes/chilca-1/recepcion.jpg"), dto.getFotos());
        assertEquals(new BigDecimal("-12.081000"), dto.getLatitud());
        assertTrue(dto.isActiva());
    }

    @Test
    @DisplayName("Listar todas (admin): incluye inactivas y fotos nulas se devuelven como lista vacía")
    void testListarTodasIncluyeInactivas() {
        SedeEntity inactiva = SedeEntity.builder().id("sede-x").nombre("X").direccion("D").ciudad("Huancayo")
                .sillones(1).fotos(null).activa(false).build();
        when(sedeRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(chilca, inactiva));

        List<SedeResponseDTO> res = sedeService.listarTodas();

        assertEquals(2, res.size());
        assertFalse(res.get(1).isActiva());
        assertEquals(List.of(), res.get(1).getFotos());
    }

    // ---------------------------------------------------------------- crear

    @Test
    @DisplayName("Crear: id = 'sede-' + slug del nombre (minúsculas, sin tildes, guiones)")
    void testCrearGeneraIdSlug() {
        when(sedeRepository.existsById(anyString())).thenReturn(false);
        guardarDevuelveArgumento();

        SedeResponseDTO res = sedeService.crear(request("  Huancayo Centro – Ñaña & Él  ").build());

        assertEquals("sede-huancayo-centro-nana-el", res.getId());
        assertEquals("Huancayo Centro – Ñaña & Él", res.getNombre());
    }

    @Test
    @DisplayName("Crear: si el id ya existe agrega sufijo -2, -3...")
    void testCrearIdConSufijo() {
        when(sedeRepository.existsById("sede-pilcomayo")).thenReturn(true);
        when(sedeRepository.existsById("sede-pilcomayo-2")).thenReturn(true);
        when(sedeRepository.existsById("sede-pilcomayo-3")).thenReturn(false);
        guardarDevuelveArgumento();

        assertEquals("sede-pilcomayo-3", sedeService.crear(request("Pilcomayo").build()).getId());
    }

    @Test
    @DisplayName("Crear: aplica valores por defecto del esquema (ciudad, colores, fotos) y la sede nace activa")
    void testCrearValoresPorDefecto() {
        when(sedeRepository.existsById(anyString())).thenReturn(false);
        guardarDevuelveArgumento();

        sedeService.crear(request("Pilcomayo").ciudad("  ").telefono(" ").email("").build());

        ArgumentCaptor<SedeEntity> captor = ArgumentCaptor.forClass(SedeEntity.class);
        verify(sedeRepository).save(captor.capture());
        SedeEntity guardada = captor.getValue();
        assertEquals("Huancayo", guardada.getCiudad());
        assertEquals("#C59B27", guardada.getColor());
        assertEquals("#FEF3C7", guardada.getColorSecundario());
        assertArrayEquals(new String[0], guardada.getFotos());
        assertEquals("Av. Giráldez 123", guardada.getDireccion());
        assertNull(guardada.getTelefono());
        assertNull(guardada.getEmail());
        assertTrue(guardada.isActiva());
    }

    @Test
    @DisplayName("Crear: registra auditoría CREAR_SEDE con el usuario en sesión")
    void testCrearAudita() {
        when(sedeRepository.existsById(anyString())).thenReturn(false);
        guardarDevuelveArgumento();

        sedeService.crear(request("Pilcomayo").ciudad("Huancayo - Pilcomayo").sillones(3).build());

        RegistroAuditoria audit = auditoriaRegistrada();
        assertEquals("CREAR_SEDE", audit.accion());
        assertEquals("sedes", audit.tabla());
        assertEquals("CONFIGURACION", audit.modulo());
        assertEquals("sede-pilcomayo", audit.registroId());
        assertEquals(admin.id(), audit.usuarioId());
    }

    // ---------------------------------------------------------------- actualizar

    @Test
    @DisplayName("Actualizar: sede inexistente responde 404")
    void testActualizarNoExiste() {
        when(sedeRepository.findById("sede-nada")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sedeService.actualizar("sede-nada", request("X").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(sedeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar: cambia los campos editables y conserva id, estado, fotos y coordenadas")
    void testActualizarConservaCamposNoEditables() {
        when(sedeRepository.findById("sede-chilca-1")).thenReturn(Optional.of(chilca));
        guardarDevuelveArgumento();

        SedeResponseDTO res = sedeService.actualizar("sede-chilca-1", request("Chilca Renovada")
                .ciudad(" ").sillones(6).horarioTexto("Lun - Dom").color("#111111").colorSecundario(null).build());

        assertEquals("sede-chilca-1", res.getId());
        assertEquals("Chilca Renovada", res.getNombre());
        assertEquals("Av. Giráldez 123", res.getDireccion());
        assertEquals(6, res.getSillones());
        assertEquals("Lun - Dom", res.getHorarioTexto());
        assertEquals("#111111", res.getColor());
        // En blanco: se conservan los valores existentes de columnas NOT NULL / con valor por defecto
        assertEquals("Huancayo - Chilca", res.getCiudad());
        assertEquals("#FEF3C7", res.getColorSecundario());
        assertEquals(2, res.getFotos().size());
        assertEquals("/sedes/chilca-1/fachada.jpg", res.getFotoPortada());
        assertEquals(new BigDecimal("-75.210000"), res.getLongitud());
        assertTrue(res.isActiva());
        assertEquals("EDITAR_SEDE", auditoriaRegistrada().accion());
    }

    // ---------------------------------------------------------------- estado

    @Test
    @DisplayName("Estado: desactivar una sede la marca inactiva y audita DESACTIVAR_SEDE")
    void testDesactivar() {
        when(sedeRepository.findById("sede-chilca-1")).thenReturn(Optional.of(chilca));
        guardarDevuelveArgumento();

        SedeResponseDTO res = sedeService.cambiarEstado("sede-chilca-1", false);

        assertFalse(res.isActiva());
        assertEquals("DESACTIVAR_SEDE", auditoriaRegistrada().accion());
    }

    @Test
    @DisplayName("Estado: activar audita ACTIVAR_SEDE")
    void testActivar() {
        chilca.setActiva(false);
        when(sedeRepository.findById("sede-chilca-1")).thenReturn(Optional.of(chilca));
        guardarDevuelveArgumento();

        assertTrue(sedeService.cambiarEstado("sede-chilca-1", true).isActiva());
        assertEquals("ACTIVAR_SEDE", auditoriaRegistrada().accion());
    }

    @Test
    @DisplayName("Estado: sede inexistente responde 404")
    void testCambiarEstadoNoExiste() {
        when(sedeRepository.findById("sede-nada")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sedeService.cambiarEstado("sede-nada", false));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(registrarAuditoriaApi);
    }

    @Test
    @DisplayName("Unidad de Negocio: crear sede Villamax asigna VILLAMAX y RUC")
    void testCrearSedeVillamax() {
        guardarDevuelveArgumento();

        SedeRequestDTO req = request("Villamax Chilca")
                .unidadDeNegocio(com.odontomax.sedes.model.UnidadDeNegocio.VILLAMAX)
                .ruc("20601234567")
                .build();

        SedeResponseDTO res = sedeService.crear(req);

        assertEquals(com.odontomax.sedes.model.UnidadDeNegocio.VILLAMAX, res.getUnidadDeNegocio());
        assertEquals("20601234567", res.getRuc());
    }
}
