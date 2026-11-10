-- Una persona puede tener un cargo principal y funciones adicionales (odontólogo y recepcionista a la vez) con una sola cuenta.
-- Sus permisos se suman. Solo se admiten ODONTOLOGO y RECEPCION como funciones adicionales, separadas por coma.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS roles_adicionales text NOT NULL DEFAULT '';
ALTER TABLE usuarios ADD CONSTRAINT usuarios_roles_adicionales_ck
    CHECK (roles_adicionales IN ('', 'ODONTOLOGO', 'RECEPCION', 'ODONTOLOGO,RECEPCION'));
