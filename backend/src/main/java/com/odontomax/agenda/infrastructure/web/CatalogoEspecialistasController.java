package com.odontomax.agenda.infrastructure.web;

import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/reservas-publicas/catalogo/especialistas")
@RequiredArgsConstructor
public class CatalogoEspecialistasController {

    private final ConsultarEspecialistaUseCase consultarEspecialistaUseCase;

    @GetMapping
    public ResponseEntity<List<EspecialistaResponseDTO>> listarEspecialistasPublicos(
            @RequestParam(name = "sedeId", required = false) String sedeId) {
        List<EspecialistaResponseDTO> publicos = (sedeId != null && !sedeId.isBlank())
                ? consultarEspecialistaUseCase.listarPublicosPorSede(sedeId.trim())
                : consultarEspecialistaUseCase.listarPublicos();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(publicos);
    }
}
