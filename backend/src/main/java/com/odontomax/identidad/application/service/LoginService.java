package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.application.dto.request.TotpLoginRequestDTO;
import com.odontomax.application.dto.response.LoginResponseDTO;
import com.odontomax.application.dto.response.UsuarioDTO;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.identidad.application.port.in.LoginUseCase;
import com.odontomax.identidad.application.port.out.BruteForceProtectionPort;
import com.odontomax.identidad.application.port.out.Login2faPendientePort;
import com.odontomax.identidad.application.port.out.TokenJwtPort;
import com.odontomax.identidad.application.port.out.TotpPasoUsadoPort;
import com.odontomax.identidad.application.port.out.TotpPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.PoliticaSegundoFactor;
import com.odontomax.identidad.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.OptionalLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private static final String MSG_CREDENCIALES_INVALIDAS = "Credenciales inválidas. Correo o contraseña incorrectos.";
    private static final String MSG_TOKEN_TEMPORAL_INVALIDO = "Token temporal inválido o expirado. Inicie sesión nuevamente.";
    private static final String MSG_CODIGO_2FA_INVALIDO = "Código de autenticación 2FA o código de respaldo inválido.";

    private final UsuarioRepository usuarioRepository;
    private final TokenJwtPort tokenJwtPort;
    private final PasswordEncoder passwordEncoder;
    private final BruteForceProtectionPort bruteForceProtection;
    private final TotpPort totpPort;
    private final Login2faPendientePort login2faPendiente;
    private final TotpPasoUsadoPort totpPasoUsado;

    /** Variante sin IP explícita: usa la IP de la petición en curso (127.0.0.1 fuera de una petición). */
    public LoginResponseDTO login(LoginRequestDTO request) {
        return login(request, SecurityUtils.getClientIp());
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponseDTO login(LoginRequestDTO request, String clientIp) {
        String email = request.getEmail().trim().toLowerCase();
        String rawPassword = request.getPassword() != null ? request.getPassword().trim() : "";
        String attemptKey = buildAttemptKey(email, clientIp);

        // 1. Verificar bloqueo por fuerza bruta (Email + IP)
        verificarNoBloqueado(attemptKey);

        // 2. Consulta a la base de datos
        Usuario user = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    bruteForceProtection.recordFailedAttempt(attemptKey);
                    return new BadCredentialsException(MSG_CREDENCIALES_INVALIDAS);
                });

        // 3. Validar estado de la cuenta
        if (!user.isActivo()) {
            bruteForceProtection.recordFailedAttempt(attemptKey);
            throw new BadCredentialsException(MSG_CREDENCIALES_INVALIDAS);
        }

        // 4. Verificación de contraseña con BCrypt
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            bruteForceProtection.recordFailedAttempt(attemptKey);
            throw new BadCredentialsException(MSG_CREDENCIALES_INVALIDAS);
        }

        // 5. Autenticación exitosa de contraseña: limpiar intentos. Con 2FA activo NO: los fallos de código acumulados
        // en esta clave solo se borran al completar el segundo factor (si no, cada login reiniciaría el límite)
        if (!user.isTotpActivado()) {
            bruteForceProtection.resetAttempts(attemptKey);
        }
        user.registrarAcceso(OffsetDateTime.now());
        usuarioRepository.save(user);

        UsuarioDTO usuarioDto = IdentidadDtos.usuarioDto(user);

        // 6. Verificar si el usuario requiere 2FA (TOTP)
        if (user.isTotpActivado()) {
            String tempToken = tokenJwtPort.generateTemp2FaToken(
                    user.getId(), user.getEmail(), user.getNombre(), user.getRol(), user.getTokenVersion()
            );
            log.info("Usuario ID {} requiere verificación 2FA. Token temporal emitido.", user.getId());
            return LoginResponseDTO.builder()
                    .token(tempToken)
                    .tokenTipo("2FA_PENDING")
                    .expiracionMs(300_000L) // 5 minutos
                    .usuario(usuarioDto)
                    .build();
        }

        // 7. Generar JWT final
        String token = tokenJwtPort.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getRol(),
                user.getSedeId(),
                user.getTokenVersion()
        );

        log.info("Inicio de sesión exitoso: user_id={} [Rol: {}, Sede: {}, IP: {}]", user.getId(), user.getRol(), user.getSedeId(), clientIp);

        return LoginResponseDTO.builder()
                .token(token)
                .tokenTipo("Bearer")
                .expiracionMs(tokenJwtPort.getExpirationMs())
                .usuario(usuarioDto)
                // Rol con 2FA obligatorio y sin 2FA configurado: entra, pero debe configurarlo (B-011)
                .requiereConfigurar2fa(user.requiereConfigurarTotp() ? Boolean.TRUE : null)
                .build();
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponseDTO completarLogin2FA(TotpLoginRequestDTO req, String clientIp) {
        if (!tokenJwtPort.validateToken(req.getTempToken())) {
            throw new BadCredentialsException(MSG_TOKEN_TEMPORAL_INVALIDO);
        }

        TokenJwtPort.TokenClaims claims = tokenJwtPort.leerClaims(req.getTempToken());
        if (!"2FA_PENDING".equalsIgnoreCase(claims.tipo())) {
            throw new BadCredentialsException("El token proporcionado no es un token temporal de segundo factor.");
        }

        // Reservas atómicas ANTES de verificar (el intento cuenta como fallido hasta que el código resulte válido), en
        // este orden: por cuenta (todas las IP), por correo + IP (la del login) y por token. Así solo crea estado del
        // token quien pasa los dos límites (revisión 2, obs. 2 y 3)
        String email = claims.subject();
        String emailNormalizado = email != null ? email.trim().toLowerCase() : "";
        String claveCuenta = PoliticaSegundoFactor.claveLogin2faCuenta(emailNormalizado);
        lanzarSiBloqueado(bruteForceProtection.reservarIntento(claveCuenta, PoliticaSegundoFactor.MAX_INTENTOS_POR_CUENTA));
        String attemptKey = buildAttemptKey(emailNormalizado, clientIp);
        lanzarSiBloqueado(bruteForceProtection.reservarIntento(attemptKey));

        // Por token (BL-2): rechaza si ya se agotaron sus intentos o ya se completó
        String clavePendiente = IdentidadDtos.sha256Hex(req.getTempToken());
        if (login2faPendiente.intentoRechazado(clavePendiente, PoliticaSegundoFactor.MAX_INTENTOS_POR_TOKEN)) {
            throw new BadCredentialsException(MSG_TOKEN_TEMPORAL_INVALIDO);
        }

        Usuario user = usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado o inactivo."));

        // Credenciales cambiadas después de emitir el token temporal (B-008)
        if (!user.aceptaVersionDeToken(claims.tokenVersion())) {
            throw new BadCredentialsException(MSG_TOKEN_TEMPORAL_INVALIDO);
        }

        String inputCode = req.getCodigo().trim();
        boolean valid = false;

        // Probar si es código TOTP de 6 dígitos (de un solo uso: se rechaza el mismo paso o uno anterior, B-013)
        if (inputCode.matches("^\\d{6}$")) {
            try {
                int codeInt = Integer.parseInt(inputCode);
                // Una sola lectura del reloj: el paso que coincide (o -1) decide validez y reutilización
                long paso = totpPort.pasoDelCodigo(user.getTotpSecret(), codeInt);
                valid = paso >= 0 && !totpPasoUsado.esReutilizacion(user.getId(), paso);
            } catch (Exception ignored) {
            }
        }

        // Si no es TOTP o falló, probar si es código de respaldo
        if (!valid && user.consumirCodigoRespaldo(inputCode)) {
            valid = true;
            usuarioRepository.save(user);
            log.info("Código de respaldo 2FA utilizado por user_id={}", user.getId());
        }

        if (!valid) {
            // El intento ya se contó al reservarlo (cuenta, email|ip y token)
            if (login2faPendiente.intentosReservados(clavePendiente) >= PoliticaSegundoFactor.MAX_INTENTOS_POR_TOKEN) {
                log.warn("Login 2FA pendiente de user_id={} invalidado: agotó sus {} intentos de código.",
                        user.getId(), PoliticaSegundoFactor.MAX_INTENTOS_POR_TOKEN);
            }
            throw new BadCredentialsException(MSG_CODIGO_2FA_INVALIDO);
        }

        // El token temporal es de un solo uso: si otra petición ya lo completó, se rechaza (y se revierte esta)
        if (login2faPendiente.invalidar(clavePendiente)) {
            throw new BadCredentialsException(MSG_TOKEN_TEMPORAL_INVALIDO);
        }

        bruteForceProtection.resetAttempts(attemptKey);
        bruteForceProtection.resetAttempts(claveCuenta);

        if (user.isRecifradoTotpPendiente()) {
            // save() no basta: el texto plano no cambió y Hibernate no emitiría UPDATE.
            usuarioRepository.recifrarCredencialesTotp(user);
            log.info("Credenciales 2FA de user_id={} re-cifradas automáticamente con la nueva clave activa.", user.getId());
        }

        String finalToken = tokenJwtPort.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getRol(),
                user.getSedeId(),
                user.getTokenVersion()
        );

        return LoginResponseDTO.builder()
                .token(finalToken)
                .tokenTipo("Bearer")
                .expiracionMs(tokenJwtPort.getExpirationMs())
                .usuario(IdentidadDtos.usuarioDto(user))
                .build();
    }

    private void verificarNoBloqueado(String attemptKey) {
        lanzarSiBloqueado(bruteForceProtection.minutosDeBloqueoRestantes(attemptKey));
    }

    private static void lanzarSiBloqueado(OptionalLong bloqueo) {
        if (bloqueo.isPresent()) {
            throw new BadCredentialsException(String.format(
                    "Demasiados intentos fallidos. Cuenta temporalmente bloqueada. Intente de nuevo en %d minutos.",
                    bloqueo.getAsLong()
            ));
        }
    }

    private String buildAttemptKey(String email, String clientIp) {
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        return email + "|" + ip;
    }
}
