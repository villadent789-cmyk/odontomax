# Pruebas de aceptación para el piloto

Se hacen en la sede piloto, con el sistema ya desplegado y datos reales (o de práctica), **antes de abrir las demás sedes**.
Cada prueba la hace la persona que la usará en el día a día. Marca ✅ si funcionó o anota qué falló y en qué pantalla.
Si algo falla, no se arregla "sobre la marcha": se anota y se corrige después, para no perder el hilo.

## 0. Antes de empezar (dueño)

- [ ] Entro a `/app/login` con mi usuario y mi código de verificación en dos pasos.
- [ ] Creo un usuario de cada rol (recepción, odontólogo, administrador de sede) y verifico que cada uno entra.
- [ ] Como administrador general, restablezco la contraseña de otro usuario (Administración → Usuarios → Seguridad) y esa persona entra con la nueva.
- [ ] Las sedes, los doctores (con su sillón fijo) y el catálogo de servicios con precios están cargados.

## 1. Recepción

- [ ] Registro un paciente nuevo: al guardar me lleva directo a su ficha de ingreso.
- [ ] Busco ese paciente escribiendo el nombre **sin tilde y en minúsculas** y lo encuentro.
- [ ] Agendo una cita con doctor y hora libre; intento agendar sobre una hora ocupada y el sistema me avisa.
- [ ] Reprogramo y cancelo una cita (pide motivo y libera el horario).
- [ ] En la bandeja: marco la llegada, el paciente pasa a la sala de espera y luego «Pasar al sillón».
- [ ] Citas de mañana: pulso «Enviar WhatsApp», se abre el mensaje listo. Pruebo los cuatro resultados
      (Confirmó, Pidió reprogramar, Canceló, No responde): cada uno avisa y los dos del medio abren el diálogo.
- [ ] Una reserva hecha desde la web pública aparece en la bandeja y la puedo confirmar.

## 2. Odontólogo

- [ ] Solo veo a mis pacientes (los que tienen cita conmigo); abrir uno ajeno muestra «sin permiso».
- [ ] En «Nuevo tratamiento» veo los pacientes de hoy; toco uno y se abre con sus pasos.
- [ ] Marco el odontograma con varios hallazgos en una misma pieza, y una pieza como «sana» (queda con ✓).
- [ ] «Registrar llegada» guarda el odontograma inicial; al dar el alta guarda el final; los comparo y veo la vista previa.
- [ ] Imprimo el odontograma: salen los dientes con sus caras, los colores y la leyenda.
- [ ] Creo un plan con el botón «Agregar otro caso» (leve + severo), con adelanto, y el total suma bien.
- [ ] Guardo ese plan como plantilla y la vuelvo a usar con otro paciente.
- [ ] Hago un presupuesto, el paciente acepta solo una parte y se crea el plan solo con lo aceptado.
- [ ] Cambio las sesiones de un plan: me exige el motivo y queda registrado.
- [ ] Registro una orden de laboratorio y sigo sus pruebas (prueba 1, 2, 3) hasta finalizar.

## 3. Caja

- [ ] Abro el turno de caja en cero.
- [ ] Cobro en efectivo, Yape, Plin, tarjeta de débito y de crédito: cada cobro cae en su método y no pide número de operación.
- [ ] Registro un egreso: el tope por movimiento se respeta.
- [ ] Cierro el periodo: los totales por método coinciden con lo que cobré y pagué, las tarjetas se cuadran juntas.
- [ ] El comprobante (recibo) sale con los datos correctos.

## 4. Villamax (si aplica en la sede)

- [ ] Registro un estudio (radiografía y tomografía) de un paciente derivado: la comisión del doctor externo sale
      S/ 10 y S/ 40 por estudio.
- [ ] Un paciente estudiante con precio manual no genera comisión.

## 5. Dueño: seguimiento

- [ ] Rendimiento de doctores y meta mensual (sin descontar el laboratorio).
- [ ] Cierre de mes por sede.
- [ ] Auditoría: veo quién abrió cada historia clínica.
- [ ] Hago una **copia de seguridad y la restauro** en una base de prueba (ver `docs/despliegue.md`).

## 6. Web pública

- [ ] Hago una reserva completa desde el celular; recibo el código y puedo consultar su estado.
- [ ] Intento reservar 4 veces con el mismo DNI: la cuarta es rechazada con un mensaje claro.
- [ ] La política de privacidad se abre desde el pie y desde el formulario.

## Criterio para abrir las demás sedes

Una o dos semanas de uso diario en la sede piloto, sin errores graves abiertos y con la caja cuadrando todos los días.
