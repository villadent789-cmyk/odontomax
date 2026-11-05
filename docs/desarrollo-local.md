# Desarrollo local (backend real + frontend)

Entorno para probar OdontoMax en tu PC con Windows sin Supabase ni secretos reales:
PostgreSQL 15 en Docker, backend con `mvnw` (perfil `local`) y frontend con `npm run dev`.

Todo lo de este entorno es SOLO DESARROLLO LOCAL: las claves de `application-local.yml` son de prueba
y el backend se niega a arrancar con el perfil `local` si la base de datos no está en `localhost`.

## Requisitos

- Docker Desktop (abierto y en marcha).
- JDK 17 (`java -version`).
- Node.js 18 o superior con npm (`node -v`).
- PowerShell. Si Windows bloquea los scripts, en esa terminal: `Set-ExecutionPolicy -Scope Process Bypass`.

## Arrancar

Terminal 1, en la raíz del repositorio:

```powershell
.\scripts\dev-up.ps1
```

Levanta PostgreSQL (contenedor `odontomax-local-db`, puerto `55432` del host), espera a que esté sano y
arranca el backend con el perfil `local`. En el primer arranque Flyway aplica V1 a V14 sobre la base vacía
y `LocalSeedRunner` siembra los datos de prueba (es idempotente: en los siguientes arranques no duplica nada).
El backend queda en primer plano: se detiene con Ctrl+C.

Para mostrar el sistema **sin datos de prueba**, arranca el backend con `--odontomax.local.seed=false` (por ejemplo
`./mvnw spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.arguments=--odontomax.local.seed=false"`):
así `LocalSeedRunner` no vuelve a sembrar usuarios, pacientes ni citas.

Terminal 2, el frontend, desde la raíz del repositorio (`npm install` solo hace falta la primera vez):

```powershell
cd frontend; npm install; npm run dev
```

Abre http://localhost:5173 (web pública) o http://localhost:5173/app/login (sistema).

## Qué se puede probar hoy

- Web pública: reservar una cita en `/reservar` (sede, servicio, fecha, turno, datos) y ver el ticket; consultar
  una reserva en `/reserva` con el código y el DNI (prueba con `RV-LOCAL001` y `70000001`). El pago de adelanto
  online no existe todavía en la API: el ticket indica que se paga en la clínica.
- Sistema: login con cualquiera de los usuarios de prueba, 2FA (ADMIN_GENERAL), restablecer la contraseña de un usuario
  desde Administración (no hay recuperación por correo), cambiar contraseña desde el menú de cuenta, y la carcasa con el
  menú por rol y el selector de sede.
- Aún no hay pantallas de agenda, pacientes, caja, clínica, estadísticas ni configuración: se van añadiendo a
  medida que se migra cada módulo del backend (ver `docs/migracion/estado.md`).

## URLs

- Frontend: http://localhost:5173
- API: http://localhost:8080/api/v1 (CORS abierto solo para http://localhost:5173)
- Salud: http://localhost:8080/api/v1/actuator/health
- Catálogo público: http://localhost:8080/api/v1/reservas-publicas/catalogo/servicios
- PostgreSQL: `localhost:55432`, base `odontomax`, usuario `odontomax`, contraseña `odontomax_local_dev`

## Usuarios de prueba

Contraseñas SOLO DESARROLLO LOCAL (las crea `LocalSeedRunner`, nunca una migración):

- ADMIN_GENERAL: `admin.general@odontomax.test` / `Local-AdminGeneral-2026` (sin sede)
- ADMIN_SEDE: `admin.sede@odontomax.test` / `Local-AdminSede-2026` (sede-chilca-1)
- ODONTOLOGO: `odontologo@odontomax.test` / `Local-Odontologo-2026` (sede-chilca-1, vinculado a la
  especialista Dra. Thalía Vilcapoma de V2)
- RECEPCION: `recepcion@odontomax.test` / `Local-Recepcion-2026` (sede-chilca-1)

Datos de ejemplo además de lo que ya siembran las migraciones (sedes, servicios, especialistas y sus
horarios): 4 pacientes ficticios (DNI 70000001 a 70000004) y 5 citas para hoy y los próximos días, una de
ellas web pendiente con ticket `RV-LOCAL001`. Las fechas se calculan en el primer arranque; para tener
citas "de hoy" otra vez, reinicia la base (ver abajo).

## 2FA del ADMIN_GENERAL

El ADMIN_GENERAL entra la primera vez sin 2FA y el login responde `requiereConfigurar2fa: true`.
Para configurarlo con una app autenticadora (Google Authenticator, Microsoft Authenticator, Aegis...):

1. Inicia sesión como `admin.general@odontomax.test`.
2. Desde el frontend (pantalla de configurar 2FA) o con la API: `POST /api/v1/auth/2fa/configurar` con el
   token Bearer y cuerpo vacío. Devuelve `secret`, `otpauthUrl`, `qrCode` y `backupCodes` (guárdalos).
3. Escanea el QR (o escribe el `secret`) en la app autenticadora.
4. Confirma con el código de 6 dígitos: `POST /api/v1/auth/2fa/verificar-activar` con `{"codigo": 123456}`.
5. Desde entonces el login devuelve un token temporal (`tokenTipo: "2FA_PENDING"`) y se completa con
   `POST /api/v1/auth/2fa/login` y `{"tempToken": "...", "codigo": "123456"}`.

Si pierdes el autenticador en local, lo más rápido es reiniciar la base (ver abajo).

## Contraseña olvidada

No hay recuperación por correo. Un administrador general restablece la contraseña desde Administración → Usuarios → Seguridad.

## Apagar y reiniciar la base

- Backend: Ctrl+C en su terminal.
- PostgreSQL (conserva los datos): `.\scripts\dev-down.ps1` (equivale a `docker compose down`).
- Base nueva desde cero (borra el volumen; el próximo `dev-up` vuelve a migrar y sembrar):
  `.\scripts\dev-down.ps1 -Reiniciar` (equivale a `docker compose down -v`).
- Solo la base, sin backend: `.\scripts\dev-up.ps1 -SoloBaseDatos` y luego, por ejemplo desde el IDE,
  arranca el backend con el perfil `local` (`SPRING_PROFILES_ACTIVE=local`).

## Limitaciones en local

- Radiografías: no hay Cloudflare R2; subir una radiografía responde con error. El resto funciona.
- Correo: el sistema no envía ninguno.
- Comprobantes SUNAT y demás integraciones externas no están configurados.
- Si el puerto `55432` está ocupado, define `$env:ODONTOMAX_DB_PORT = "55433"` antes de `dev-up.ps1`
  (aplica tanto a Docker como al backend).
