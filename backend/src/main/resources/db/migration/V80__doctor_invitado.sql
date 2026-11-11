-- Doctor invitado: especialista que viene por cita previa (implantes, odontopediatría...). No tiene cuenta ni horario fijo ni sillón
-- fijo: se le agenda en la fecha y hora acordadas, en cualquier sillón libre de la sede.
ALTER TABLE especialistas ADD COLUMN IF NOT EXISTS invitado boolean NOT NULL DEFAULT false;
