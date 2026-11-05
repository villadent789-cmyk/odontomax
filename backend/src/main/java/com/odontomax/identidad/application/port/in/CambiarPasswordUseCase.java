package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.request.CambioPasswordRequestDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;

import java.util.Map;

public interface CambiarPasswordUseCase {

    Map<String, String> cambiarPassword(CambioPasswordRequestDTO request, ClinicaUserPrincipal user);
}
