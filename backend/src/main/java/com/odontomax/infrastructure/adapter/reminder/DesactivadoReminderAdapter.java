package com.odontomax.infrastructure.adapter.reminder;

import com.odontomax.domain.model.Cita;
import com.odontomax.pacientes.domain.model.Paciente;
import com.odontomax.domain.port.out.ReminderChannelPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(name = "proveedorPagoReminderAdapter")
public class DesactivadoReminderAdapter implements ReminderChannelPort {

    @Override
    public boolean enviarRecordatorio(Cita cita, Paciente paciente) {
        log.info("[RECORDATORIO DESACTIVADO] Recordatorio de cita {} para el paciente ID {} omitido. " +
                        "No se ha configurado un proveedor de mensajería de pago (Twilio / WhatsApp Business Cloud API).",
                cita != null ? cita.getId() : "N/A",
                paciente != null ? paciente.getId() : "N/A");
        return true;
    }
}
