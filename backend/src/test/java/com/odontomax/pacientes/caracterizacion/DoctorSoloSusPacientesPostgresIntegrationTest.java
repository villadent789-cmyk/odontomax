package com.odontomax.pacientes.caracterizacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Testcontainers(disabledWithoutDocker = true)
class DoctorSoloSusPacientesPostgresIntegrationTest extends PacientesPostgresTestBase {

    @Test
    @DisplayName("Dos odontólogos: cada uno solo ve a su paciente con cita y recibe 403 en ficha ajena; recepción ve ambos")
    void odontologoSoloVeSusPacientesConCita() throws Exception {
        String sede = crearSede();

        // 1. Usuarios: dos odontólogos y una recepcionista
        UsuarioPrueba docUser1 = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba docUser2 = crearUsuario("ODONTOLOGO", sede);
        UsuarioPrueba recepcion = crearUsuario("RECEPCION", sede);

        // 2. Especialistas enlazados a los usuarios odont�logos
        UUID espId1 = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, docUser1.id());
        UUID espId2 = jdbc.queryForObject("SELECT id FROM especialistas WHERE usuario_id = ?", UUID.class, docUser2.id());
        // 3. Dos pacientes en la misma sede
        UUID p1 = insertarPaciente(sede, "Paciente", "DeDoctorUno");
        UUID p2 = insertarPaciente(sede, "Paciente", "DeDoctorDos");

        // 4. Citas: Paciente 1 tiene cita con Especialista 1, Paciente 2 con Especialista 2
        UUID cita1 = UUID.randomUUID();
        jdbc.update("INSERT INTO citas (id, paciente_id, paciente_nombre, doctor_id, sede_id, motivo_consulta, fecha, hora, duracion_minutos, estado) " +
                        "VALUES (?, ?, 'Paciente 1', ?, ?, 'Consulta 1', CURRENT_DATE, '09:00', 30, 'PROGRAMADA')",
                cita1, p1, espId1, sede);

        UUID cita2 = UUID.randomUUID();
        jdbc.update("INSERT INTO citas (id, paciente_id, paciente_nombre, doctor_id, sede_id, motivo_consulta, fecha, hora, duracion_minutos, estado) " +
                        "VALUES (?, ?, 'Paciente 2', ?, ?, 'Consulta 2', CURRENT_DATE, '10:00', 30, 'PROGRAMADA')",
                cita2, p2, espId2, sede);

        // 5. DOCTOR 1 en listado no paginado (GET /pacientes): solo ve P1
        MvcResult rDoc1List = ejecutar(get("/pacientes"), docUser1);
        assertThat(rDoc1List.getResponse().getStatus()).isEqualTo(200);
        JsonNode jsonDoc1 = leer(rDoc1List);
        List<String> idsDoc1 = ids(jsonDoc1);
        assertThat(idsDoc1).contains(p1.toString());
        assertThat(idsDoc1).doesNotContain(p2.toString());

        // 6. DOCTOR 1 en listado paginado (GET /pacientes?page=0&size=10): solo ve P1
        MvcResult rDoc1Page = ejecutar(get("/pacientes?page=0&size=10"), docUser1);
        assertThat(rDoc1Page.getResponse().getStatus()).isEqualTo(200);
        JsonNode pageJsonDoc1 = leer(rDoc1Page);
        List<String> idsDoc1Page = ids(pageJsonDoc1.get("content"));
        assertThat(idsDoc1Page).contains(p1.toString());
        assertThat(idsDoc1Page).doesNotContain(p2.toString());

        // 7. DOCTOR 2 en listado no paginado: solo ve P2
        MvcResult rDoc2List = ejecutar(get("/pacientes"), docUser2);
        assertThat(rDoc2List.getResponse().getStatus()).isEqualTo(200);
        JsonNode jsonDoc2 = leer(rDoc2List);
        List<String> idsDoc2 = ids(jsonDoc2);
        assertThat(idsDoc2).contains(p2.toString());
        assertThat(idsDoc2).doesNotContain(p1.toString());

        // 8. DOCTOR 1 accede a ficha propia P1 -> 200 OK
        MvcResult rDoc1P1 = ejecutar(get("/pacientes/" + p1), docUser1);
        assertThat(rDoc1P1.getResponse().getStatus()).isEqualTo(200);

        // 9. DOCTOR 1 intenta acceder a ficha ajena P2 -> 403 FORBIDDEN
        MvcResult rDoc1P2 = ejecutar(get("/pacientes/" + p2), docUser1);
        assertThat(rDoc1P2.getResponse().getStatus()).isEqualTo(403);

        // 10. DOCTOR 2 intenta acceder a ficha ajena P1 -> 403 FORBIDDEN
        MvcResult rDoc2P1 = ejecutar(get("/pacientes/" + p1), docUser2);
        assertThat(rDoc2P1.getResponse().getStatus()).isEqualTo(403);

        // 11. DOCTOR 2 accede a ficha propia P2 -> 200 OK
        MvcResult rDoc2P2 = ejecutar(get("/pacientes/" + p2), docUser2);
        assertThat(rDoc2P2.getResponse().getStatus()).isEqualTo(200);

        // 12. RECEPCIÓN ve ambos pacientes en listado y tiene acceso a ambas fichas
        MvcResult rRecList = ejecutar(get("/pacientes"), recepcion);
        assertThat(rRecList.getResponse().getStatus()).isEqualTo(200);
        List<String> idsRec = ids(leer(rRecList));
        assertThat(idsRec).contains(p1.toString(), p2.toString());

        MvcResult rRecP1 = ejecutar(get("/pacientes/" + p1), recepcion);
        assertThat(rRecP1.getResponse().getStatus()).isEqualTo(200);

        MvcResult rRecP2 = ejecutar(get("/pacientes/" + p2), recepcion);
        assertThat(rRecP2.getResponse().getStatus()).isEqualTo(200);
    }
}
