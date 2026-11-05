package com.odontomax.auditoria.infrastructure;

import com.odontomax.application.dto.response.AuditoriaResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditoriaResponseDTOTest {

    @Test
    @DisplayName("AuditoriaResponseDTO: tolera sede inexistente o eliminada como sede-chilca-2 sin fallar")
    void toleraSedeInexistente() {
        AuditoriaLogEntity entity = AuditoriaLogEntity.builder()
                .id(1L)
                .creadoEn(Instant.now())
                .usuarioId(UUID.randomUUID())
                .usuarioNombre("Admin")
                .rol("ADMIN_GENERAL")
                .sedeId("sede-chilca-2")
                .modulo("HISTORIA_CLINICA")
                .accion("ACTUALIZAR_PIEZA")
                .tabla("odontograma_registros")
                .registroId("123")
                .detalle("Detalle de prueba")
                .ip("127.0.0.1")
                .build();

        AuditoriaResponseDTO dto = AuditoriaResponseDTO.fromEntity(entity);

        assertNotNull(dto);
        assertEquals("sede-chilca-2", dto.getSedeId());
        assertEquals("sede-chilca-2", dto.getSedeNombre());
    }
}