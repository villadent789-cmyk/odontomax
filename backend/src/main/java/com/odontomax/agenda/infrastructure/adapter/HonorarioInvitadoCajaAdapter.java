package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.HonorarioInvitadoPort;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.repository.SpringDataCajaSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/** Paga los honorarios del doctor invitado como egreso de caja (módulo caja, por su API pública). */
@Component
@RequiredArgsConstructor
public class HonorarioInvitadoCajaAdapter implements HonorarioInvitadoPort {

    private final CajaEgresoModuloApi cajaEgresoApi;
    private final SpringDataCajaSesionRepository cajaSesionRepository;

    @Value("${caja.egreso.tope-movimiento:200.00}")
    private BigDecimal topeMovimiento;

    @Override
    public void pagar(UUID citaId, String sedeId, String doctorNombre, String pacienteNombre, BigDecimal monto, String metodoPago) {
        String metodo = metodoPago == null || metodoPago.isBlank() ? "EFECTIVO" : metodoPago.trim().toUpperCase();

        // El pago de honorarios solo se registra con la caja del día abierta: así nunca queda una atención sin su pago.
        if (cajaSesionRepository.findBySedeIdAndCerradaEnIsNull(sedeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La caja de la sede está cerrada. Ábrala para registrar el pago al doctor invitado y poder finalizar la atención.");
        }

        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        boolean esAdmin = usuario != null && usuario.tieneAlgunRol("ADMIN_GENERAL", "ADMIN_SEDE");
        if ("EFECTIVO".equals(metodo) && monto.compareTo(topeMovimiento) > 0 && !esAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, String.format(
                    "Los pagos en efectivo mayores a S/ %s requieren autorización de un Administrador de Sede o Administrador General.",
                    topeMovimiento));
        }

        cajaEgresoApi.registrarEgresoDeSistema(sedeId, monto, metodo,
                "Honorarios Dr. invitado " + doctorNombre + " — paciente " + pacienteNombre,
                "HONORARIO_INVITADO", citaId);
    }
}
