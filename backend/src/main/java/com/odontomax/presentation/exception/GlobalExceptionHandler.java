package com.odontomax.presentation.exception;

import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.domain.exception.CitaEnUsoException;
import com.odontomax.agenda.domain.exception.InicioAtencionNoPermitidoException;
import com.odontomax.agenda.domain.exception.SedeNoDisponibleException;
import com.odontomax.agenda.domain.exception.SillonInexistenteException;
import com.odontomax.agenda.domain.exception.SillonEspecialistaFijoException;
import com.odontomax.agenda.domain.exception.SillonNoAsignadoException;
import com.odontomax.domain.exception.ComprobanteSaldoPendienteException;
import com.odontomax.domain.exception.PagoExcedeSaldoTratamientoException;
import com.odontomax.domain.exception.PagoSinTratamientoException;
import com.odontomax.domain.exception.IngresoManualCajaNoPermitidoException;
import com.odontomax.domain.exception.DiferenciaCajaSinObservacionesException;
import com.odontomax.pacientes.domain.exception.PacienteConRegistrosProtegidosException;
import com.odontomax.pacientes.domain.exception.PacienteNoEncontradoException;
import com.odontomax.pacientes.domain.exception.PacienteDeudorException;
import com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CitaColisionException.class, CitaEnUsoException.class, InicioAtencionNoPermitidoException.class, com.odontomax.agenda.domain.exception.TransicionEstadoInvalidaException.class, com.odontomax.tratamientos.domain.exception.TransicionEstadoInvalidaException.class})
    public ResponseEntity<Map<String, Object>> handleColision(RuntimeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({SillonEspecialistaFijoException.class, SillonNoAsignadoException.class})
    public ResponseEntity<Map<String, Object>> handleSillonEspecialistaFijo(RuntimeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // B-023: sede inactiva o sillón fuera de rango al agendar o asignar especialistas
    @ExceptionHandler({SedeNoDisponibleException.class, SillonInexistenteException.class})
    public ResponseEntity<Map<String, Object>> handleSedeNoAdmite(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PacienteNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handlePacienteNoEncontrado(PacienteNoEncontradoException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PacienteDeudorException.class)
    public ResponseEntity<Map<String, Object>> handleDeudor(PacienteDeudorException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PagoSinTratamientoException.class)
    public ResponseEntity<Map<String, Object>> handlePagoSinTratamiento(PagoSinTratamientoException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PagoExcedeSaldoTratamientoException.class)
    public ResponseEntity<Map<String, Object>> handlePagoExcedeSaldo(PagoExcedeSaldoTratamientoException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ComprobanteSaldoPendienteException.class)
    public ResponseEntity<Map<String, Object>> handleComprobanteSaldoPendiente(ComprobanteSaldoPendienteException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PagoLaboratorioNoPermitidoException.class)
    public ResponseEntity<Map<String, Object>> handlePagoLaboratorioNoPermitido(PagoLaboratorioNoPermitidoException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({IngresoManualCajaNoPermitidoException.class, DiferenciaCajaSinObservacionesException.class})
    public ResponseEntity<Map<String, Object>> handleReglaCaja(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({PacienteConRegistrosProtegidosException.class, com.odontomax.pacientes.domain.exception.SolicitudCorreccionNoPendienteException.class})
    public ResponseEntity<Map<String, Object>> handleRegistrosProtegidos(RuntimeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(org.springframework.security.core.AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidations(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        String mensajeDetallado = errores.isEmpty()
                ? "Error de validación en la solicitud."
                : String.join("; ", errores.values());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validación fallida");
        body.put("detalles", errores);
        body.put("message", mensajeDetallado);
        body.put("mensaje", mensajeDetallado);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parámetro obligatorio faltante: " + ex.getParameterName());
    }

    // B-027: errores de formato del cliente → 400 con el formato estándar. El mensaje no incluye el valor recibido
    // ni el detalle de Jackson/Spring (clases, posiciones), y no se registra nada (no es un error del servidor).
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' tiene un valor inválido.");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido o tiene un formato incorrecto.");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo excede el tamaño máximo permitido de 15 MB.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Regla de negocio incumplida por el estado actual (caja ya abierta, pago ya anulado...): conflicto, no fallo del servidor.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return buildResponse(status, reason);
    }

    // FK o UNIQUE violados (p. ej. una sede que no existe en la tabla sedes): error del cliente, no del servidor
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        String causa = ex.getMostSpecificCause().getMessage();
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).warn("Violación de integridad de datos: {}", causa);
        if (causa != null && causa.contains("ux_pacientes_documento_por_negocio")) {
            return buildResponse(HttpStatus.CONFLICT, "Ya existe un paciente registrado con ese documento.");
        }
        return buildResponse(HttpStatus.CONFLICT, "La operación hace referencia a un registro inexistente (por ejemplo, una sede no registrada) o duplica uno existente.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Error interno no controlado en el servidor:", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno en el servidor. Por favor intente más tarde.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensaje);
        body.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
