-- B-008 (Fase 2.5): versión de credenciales del usuario.
-- Se incluye en cada JWT (claim "tokenVersion") y JwtAuthenticationFilter rechaza los tokens cuya versión no
-- coincide con la de la BD. Se incrementa al cambiar o restablecer la contraseña y al restablecer el 2FA.
-- Los JWT emitidos antes de esta migración no llevan el claim y se tratan como versión 0 (el valor inicial),
-- así que siguen valiendo hasta su expiración o hasta el primer cambio de credenciales del usuario.
ALTER TABLE usuarios ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
