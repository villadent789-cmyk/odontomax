package com.odontomax.identidad.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Cuenta de usuario del sistema (agregado de identidad). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    public static final String ROL_ADMIN_GENERAL = "ADMIN_GENERAL";

    private UUID id;
    private String nombre;
    private String email;
    private String passwordHash;
    private String rol;
    private String sedeId;
    private boolean activo;
    private OffsetDateTime ultimoAcceso;
    private String totpSecret;
    private boolean totpActivado;
    private String totpBackupCodes;
    private OffsetDateTime creadoEn;
    /** Las credenciales 2FA se leyeron con una clave de cifrado antigua y deben re-cifrarse. */
    private boolean recifradoTotpPendiente;
    /** Versión de credenciales: un JWT solo vale si lleva la versión vigente (B-008). */
    private int tokenVersion;

    /**
     * Sede que corresponde al rol: ADMIN_GENERAL no está restringido a una sede (se descarta la enviada) y los
     * roles de sede (ADMIN_SEDE, ODONTOLOGO, RECEPCION) la exigen.
     */
    public static String resolverSedeSegunRol(String rol, String sedeId) {
        if (ROL_ADMIN_GENERAL.equals(rol)) {
            return null;
        }
        if (("ADMIN_SEDE".equals(rol) || "ODONTOLOGO".equals(rol) || "RECEPCION".equals(rol)) && sedeId == null) {
            throw new IllegalArgumentException("El rol " + rol + " requiere asignar obligatoriamente una sede de atención.");
        }
        return sedeId;
    }

    /**
     * Evita que el administrador en sesión se quite a sí mismo el acceso (rol o estado) al editar su cuenta.
     *
     * @param idEnSesion id del administrador que edita (nulo si no hay sesión)
     */
    public void verificarAutoproteccionAlEditar(UUID idEnSesion, String nuevoRol, Boolean nuevoActivo) {
        boolean esSuPropiaCuenta = idEnSesion != null && idEnSesion.equals(id);
        if (esSuPropiaCuenta && ROL_ADMIN_GENERAL.equals(rol)
                && (!ROL_ADMIN_GENERAL.equals(nuevoRol) || Boolean.FALSE.equals(nuevoActivo))) {
            throw new IllegalArgumentException("No puede quitarse el rol de Administrador General ni desactivar su propia cuenta en sesión.");
        }
    }

    /** Aplica una edición administrativa; {@code nuevoActivo} nulo conserva el estado actual. */
    public void actualizarDatos(String nuevoNombre, String nuevoRol, String nuevaSedeId, Boolean nuevoActivo) {
        this.nombre = nuevoNombre;
        this.rol = nuevoRol;
        this.sedeId = nuevaSedeId;
        if (nuevoActivo != null) {
            this.activo = nuevoActivo;
        }
    }

    public boolean esAdminGeneral() {
        return ROL_ADMIN_GENERAL.equalsIgnoreCase(rol);
    }

    /** Política institucional: el rol ADMIN_GENERAL tiene 2FA obligatorio. */
    public static boolean rolExige2fa(String rol) {
        return ROL_ADMIN_GENERAL.equalsIgnoreCase(rol);
    }

    /**
     * ¿Puede un usuario con este rol desactivar él mismo su 2FA? No si el rol lo exige (solo puede reconfigurarlo
     * o pedir a otro ADMIN_GENERAL que se lo restablezca).
     */
    public static boolean puedeDesactivarSuPropioTotp(String rol) {
        return !rolExige2fa(rol);
    }

    /** Un administrador no puede restablecer su propio 2FA por la vía administrativa. */
    public void verificarRestablecimientoTotpPorOtro(UUID idAdministrador) {
        if (idAdministrador != null && idAdministrador.equals(id)) {
            throw new IllegalArgumentException("No puede restablecer su propio 2FA. Use /auth/2fa/configurar con un código válido.");
        }
    }

    /** Restablecimiento administrativo del 2FA: borra secreto y códigos, lo desactiva y cierra las sesiones. */
    public void restablecerTotpPorAdministrador() {
        desactivarTotp();
        invalidarSesiones();
    }

    public void registrarAcceso(OffsetDateTime momento) {
        this.ultimoAcceso = momento;
    }

    /**
     * Consume un código de respaldo 2FA (sin distinguir mayúsculas). Cada código sirve una sola vez.
     *
     * @return {@code true} si el código existía y se eliminó de la lista
     */
    public boolean consumirCodigoRespaldo(String codigo) {
        if (totpBackupCodes == null || totpBackupCodes.isBlank()) {
            return false;
        }
        List<String> codes = new ArrayList<>(Arrays.asList(totpBackupCodes.split(",")));
        if (!codes.remove(codigo.toUpperCase())) {
            return false;
        }
        totpBackupCodes = String.join(",", codes);
        return true;
    }

    /** Con el 2FA activo, reconfigurarlo exige demostrar el factor actual (código TOTP o de respaldo válido). */
    public boolean exigeCodigoParaReconfigurarTotp() {
        return totpActivado;
    }

    /** Guarda un secreto y códigos de respaldo nuevos; el 2FA queda inactivo hasta verificar el primer código. */
    public void iniciarConfiguracionTotp(String secreto, List<String> codigosRespaldo) {
        this.totpSecret = secreto;
        this.totpBackupCodes = String.join(",", codigosRespaldo);
        this.totpActivado = false;
    }

    public void activarTotp() {
        this.totpActivado = true;
    }

    public void desactivarTotp() {
        this.totpActivado = false;
        this.totpSecret = null;
        this.totpBackupCodes = null;
    }

    /** Invalida todos los JWT emitidos hasta ahora (llevan una versión anterior). */
    public void invalidarSesiones() {
        this.tokenVersion++;
    }

    /** ¿Un token con esta versión sigue siendo válido para la cuenta? */
    public boolean aceptaVersionDeToken(int versionDelToken) {
        return versionDelToken == tokenVersion;
    }

    /** Nueva contraseña (cambio propio o reseteo administrativo): los JWT anteriores dejan de valer. */
    public void cambiarPassword(String nuevoHash) {
        this.passwordHash = nuevoHash;
        invalidarSesiones();
    }

    /**
     * Contraseña restablecida con token. No toca el 2FA: si estaba activo lo sigue estando (nunca se omite) y, si no
     * había 2FA configurado, no se activa sin secreto (B-011). Los JWT anteriores dejan de valer.
     */
    public void restablecerPassword(String nuevoHash) {
        cambiarPassword(nuevoHash);
    }

    /** El rol exige 2FA (ADMIN_GENERAL) y la cuenta no lo tiene activo: debe configurarlo tras iniciar sesión. */
    public boolean requiereConfigurarTotp() {
        return rolExige2fa(rol) && !totpActivado;
    }
}
