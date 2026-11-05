package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.Desactivar2faRequestDTO;
import com.odontomax.application.dto.request.TotpVerificarRequestDTO;
import com.odontomax.application.dto.response.TotpConfigurarResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.application.port.in.GestionarTotpUseCase;
import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.TotpPasoUsadoPort;
import com.odontomax.identidad.application.port.out.TotpPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.PoliticaSegundoFactor;
import com.odontomax.identidad.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class GestionarTotpService implements GestionarTotpUseCase {

    private final UsuarioRepository usuarioRepository;
    private final TotpPort totpPort;
    private final RegistroAuditoriaPort registroAuditoria;
    private final TotpPasoUsadoPort totpPasoUsado;
    private final PasswordEncoder passwordEncoder;
    private final BruteForceProtectionPort bruteForceProtection;

    static final String MSG_CODIGO_2FA_INVALIDO = "Código de autenticación 2FA o código de respaldo inválido.";
    /** Misma respuesta ante contraseña o código incorrectos en desactivar: no sirve de oráculo de contraseñas. */
    static final String MSG_DESACTIVAR_INVALIDO = "Contraseña actual o código de autenticación 2FA incorrectos.";

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TotpConfigurarResponseDTO configurar2FA(ClinicaUserPrincipal user, String codigoActual) {
        if (user == null) {
            throw new AccessDeniedException("Debe estar autenticado para configurar la autenticación en dos factores (2FA).");
        }

        Usuario usuario = buscar(user);

        // Con el 2FA activo no se puede sustituir el secreto (ni dejarlo pendiente) sin demostrar el factor actual.
        // El intento se reserva antes de verificar: 5 fallos bloquean la operación (BL-3)
        if (usuario.exigeCodigoParaReconfigurarTotp()) {
            String clave = reservarIntentoGestion(usuario);
            if (!verificarCodigoSegundoFactor(usuario, codigoActual)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CODIGO_2FA_INVALIDO);
            }
            bruteForceProtection.resetAttempts(clave);
        }

        String secret = totpPort.generateSecret();
        List<String> backupCodes = totpPort.generateBackupCodes();

        usuario.iniciarConfiguracionTotp(secret, backupCodes); // Requiere verificación antes de activar
        usuarioRepository.save(usuario);
        totpPasoUsado.olvidar(usuario.getId()); // los pasos usados con el secreto anterior no aplican al nuevo

        String otpauthUrl = totpPort.getOtpAuthUrl(usuario.getEmail(), secret);
        String qrCode = totpPort.generateQrCodeDataUri(otpauthUrl, 250, 250);

        return TotpConfigurarResponseDTO.builder()
                .secret(secret)
                .otpauthUrl(otpauthUrl)
                .qrCode(qrCode)
                .backupCodes(backupCodes)
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> verificarYActivar2FA(TotpVerificarRequestDTO req, ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("Debe estar autenticado para activar la autenticación en dos factores (2FA).");
        }

        Usuario usuario = buscar(user);

        if (usuario.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe iniciar primero la configuración de 2FA.");
        }

        boolean valid = totpPort.verifyCode(usuario.getTotpSecret(), req.getCodigo());
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código TOTP inválido. Verifique la sincronización de hora de su dispositivo e intente de nuevo.");
        }

        usuario.activarTotp();
        usuarioRepository.save(usuario);

        registroAuditoria.registrarEventoSeguridad(user, "ACTIVAR_2FA", "2FA (TOTP RFC 6238) activado para cuenta con rol " + user.rol());

        return Map.of("mensaje", "Autenticación de dos factores (2FA) activada con éxito.", "activo", true);
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> desactivar2FA(Desactivar2faRequestDTO req, ClinicaUserPrincipal user) {
        if (user == null) {
            throw new AccessDeniedException("Debe estar autenticado para gestionar 2FA.");
        }

        // Política estricta: ADMIN_GENERAL tiene 2FA obligatorio por seguridad institucional
        if (!Usuario.puedeDesactivarSuPropioTotp(user.rol())) {
            throw new AccessDeniedException("El Administrador General tiene 2FA obligatorio por política institucional. Use el procedimiento de contingencia autorizado.");
        }

        Usuario usuario = buscar(user);

        if (!usuario.isTotpActivado()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El 2FA no se encuentra activo.");
        }

        // Intento reservado antes de verificar (BL-3). Contraseña y código fallidos responden igual; el código solo se
        // comprueba (y un código de respaldo solo se consume) si la contraseña es correcta
        String clave = reservarIntentoGestion(usuario);
        boolean passwordOk = usuario.getPasswordHash() != null
                && passwordEncoder.matches(req.getPasswordActual(), usuario.getPasswordHash());
        if (!passwordOk || !verificarCodigoSegundoFactor(usuario, req.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_DESACTIVAR_INVALIDO);
        }
        bruteForceProtection.resetAttempts(clave);

        usuario.desactivarTotp();
        usuarioRepository.save(usuario);

        registroAuditoria.registrarEventoSeguridad(user, "DESACTIVAR_2FA", "2FA desactivado voluntariamente por el usuario");

        return Map.of("mensaje", "Autenticación de dos factores (2FA) desactivada.", "activo", false);
    }

    /**
     * Comprueba un código del segundo factor activo: TOTP de 6 dígitos (de un solo uso, B-013) o, si no lo es o no
     * vale, código de respaldo (que se consume). Nunca registra el código.
     */
    private boolean verificarCodigoSegundoFactor(Usuario usuario, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        String limpio = codigo.trim();
        if (limpio.matches("^\\d{6}$")) {
            // Una sola lectura del reloj: el paso que coincide (o -1) decide validez y reutilización
            long paso = totpPort.pasoDelCodigo(usuario.getTotpSecret(), Integer.parseInt(limpio));
            if (paso >= 0 && !totpPasoUsado.esReutilizacion(usuario.getId(), paso)) {
                return true;
            }
        }
        return usuario.consumirCodigoRespaldo(limpio);
    }

    /** Reserva atómica de un intento de verificación del 2FA activo; 429 si el usuario está bloqueado. */
    private String reservarIntentoGestion(Usuario usuario) {
        String clave = PoliticaSegundoFactor.claveGestion2fa(usuario.getId());
        OptionalLong bloqueo = bruteForceProtection.reservarIntento(clave);
        if (bloqueo.isPresent()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, String.format(
                    "Demasiados intentos fallidos de verificación 2FA. Intente de nuevo en %d minutos.", bloqueo.getAsLong()));
        }
        return clave;
    }

    private Usuario buscar(ClinicaUserPrincipal user) {
        return usuarioRepository.findById(user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}
