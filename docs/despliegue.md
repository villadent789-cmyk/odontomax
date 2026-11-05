# Despliegue (resumen)

El sistema usa **Supabase** para la base de datos (PostgreSQL) y **Cloudflare R2** para los archivos (radiografías, resultados de
estudios, firmas y PDF de consentimientos). Más adelante se puede pasar todo a Supabase (base de datos y archivos) sin tocar la
lógica del sistema: los archivos pasan por una sola interfaz (`StoragePort`), hoy implementada por `CloudflareR2Adapter`; para
migrar basta escribir un adaptador de Supabase Storage con la misma interfaz y activarlo por configuración, y copiar los archivos.

## Variables de entorno del backend (solo los nombres; los valores van en el servidor, nunca en el repositorio)

| Para qué | Variables |
|---|---|
| Base de datos (Supabase, pooler 6543) | `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` |
| Migraciones (conexión directa 5432) | `FLYWAY_DB_URL`, `FLYWAY_DB_USER`, `FLYWAY_DB_PASSWORD`, `FLYWAY_ENABLED` |
| Seguridad | `JWT_SECRET`, `TOTP_ENCRYPTION_KEY` (y `TOTP_ENCRYPTION_KEY_OLD` al rotarla) |
| Archivos (Cloudflare R2) | `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_R2_ACCESS_KEY`, `CLOUDFLARE_R2_SECRET_KEY`, `CLOUDFLARE_R2_BUCKET` |
| Dirección del sistema | `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` |
| Primer dueño | `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, `ADMIN_BOOTSTRAP_NOMBRE` |

## Usuarios reales (dos dueños)

1. Con la base vacía, el primer arranque crea al primer dueño (`ADMIN_BOOTSTRAP_NOMBRE=Max`) con su correo y contraseña. Solo lo hace
   si no existe ningún usuario; nunca cambia contraseñas de usuarios existentes. Después se quitan `ADMIN_BOOTSTRAP_PASSWORD`
   del servidor.
2. Max entra, activa su verificación en dos pasos y crea a la segunda dueña (Ángela, rol Administración general) desde
   Administración → Usuarios. Cada dueño tiene su propia cuenta; no se comparte ninguna.
3. Los doctores, recepción y administradores de sede se crean después desde la misma pantalla.
4. **No hay recuperación de contraseña por correo** (se quitó a propósito: así el sistema no guarda ninguna cuenta de correo ni
   contraseña de correo). Si alguien olvida su contraseña, un administrador general se la restablece en Administración →
   Usuarios → Seguridad, y esa persona la cambia luego desde el menú de su cuenta. Por eso conviene que **siempre haya dos
   administradores generales** (los dos dueños): si uno se bloquea, el otro lo destraba. Si se bloquean los dos, la única
   salida es cambiar el hash de la contraseña directamente en la base de datos, y eso solo debería poder hacerlo el cliente.

## Dos direcciones separadas

- **Página web pública** (inicio, servicios y reserva): la dirección principal del dominio. No tiene enlaces al sistema.
- **Sistema del personal**: se entra por su propia dirección, `/app/login` (por ejemplo `https://app.midominio.pe/app/login`).
  Esa dirección solo se entrega a trabajadores y dueños.

## Comprobantes

El sistema emite **recibos internos** (con DNI o RUC del cliente si lo pide). No existen boletas ni facturas electrónicas de SUNAT.
Si más adelante se contrata un proveedor OSE/PSE, se conecta por la interfaz `ComprobanteElectronicoPort`.

## La web (frontend)

- Se construye con `npm run build` dentro de `frontend/` y se publica la carpeta `dist/` en un hosting de archivos estáticos
  (Cloudflare Pages u otro). Variable al construir: `VITE_API_URL` con la dirección pública del backend, por ejemplo
  `https://api.midominio.pe/api/v1`. Sin ella, la web busca el backend en `/api/v1` de su propio dominio.
- `frontend/public/_redirects` hace que cualquier ruta (`/app/pacientes`, `/reservar`…) cargue la web; `frontend/public/_headers`
  añade las cabeceras de seguridad. Los dos archivos los entienden Cloudflare Pages y Netlify.
- La política de privacidad está en `/privacidad` y se enlaza desde el pie de la web y desde el formulario de reserva.

## Copias de seguridad y cómo restaurarlas

La copia diaria la hace `.github/workflows/backup-db-r2.yml` (02:00 hora de Lima): `pg_dump` en formato custom, comprimido y
cifrado con AES-256, subido a R2; guarda 30 días. Secretos del repositorio que necesita: `DB_HOST`, `DB_PORT`, `DB_USER`,
`DB_PASSWORD`, `DB_NAME`, `BACKUP_PASSPHRASE`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_ENDPOINT`, `R2_BUCKET`.
**Guarda `BACKUP_PASSPHRASE` también fuera de GitHub: sin ella la copia no se puede abrir.**

Para restaurar (en una base vacía, nunca encima de la que está en uso sin avisar):

```bash
# 1. Descargar la copia elegida de R2 (aws s3 cp ... --endpoint-url $R2_ENDPOINT) y descifrarla
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -in odontomax_AAAAMMDD_HHMMSS.sql.gz.enc -out copia.gz -pass env:BACKUP_PASSPHRASE
# 2. Descomprimir y restaurar
gunzip -c copia.gz > copia.dump
pg_restore --no-owner --exit-on-error -h HOST -U USUARIO -d BASE_VACIA copia.dump
```

`scripts/probar-restauracion.sh` hace todo esto con una base de prueba en Docker y compara las filas de cada tabla con la
original. Se probó el 31/10/2026 con la base de desarrollo: 65 tablas idénticas. Conviene repetirla una vez con la base real
antes de abrir al público, y cada vez que se cambie el flujo de copia.

## Qué se verificó antes de desplegar

- La imagen del backend (`backend/Dockerfile`) se construye y arranca **sin** el perfil `local`: responde `UP` en
  `/api/v1/actuator/health`, no siembra usuarios de prueba, rechaza orígenes CORS ajenos, exige token en la API y no expone
  Swagger ni la documentación OpenAPI. Sin `JWT_SECRET` ni `TOTP_ENCRYPTION_KEY` no arranca (a propósito).
- La API responde con cabeceras de seguridad (`frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Permissions-Policy`).
- Las dependencias de la web que viajan a producción no tienen vulnerabilidades conocidas (`npm audit --omit=dev`). El CI
  lo vuelve a comprobar en cada cambio, junto con tipos, lint, pruebas, build y las pruebas E2E.
- Reservas públicas: límite de 5 por IP y por teléfono cada 10 minutos, de 20 por IP y 8 por teléfono al día, un campo trampa
  contra bots y un máximo de 3 reservas pendientes por paciente. Si más adelante hay abuso, se puede sumar Cloudflare Turnstile
  (gratis) sin quitar nada de esto.

## Antes de abrir al público

1. Completar en `frontend/src/public/privacidad/PrivacidadPage.tsx` la razón social, el RUC, el domicilio y un correo de contacto,
   y hacer revisar el texto por quien asesora legalmente a la clínica (Ley N.º 29733: registro del banco de datos personales).
2. Definir con el contador cómo se emiten boletas y facturas (hoy solo recibos internos).
3. Probar que un administrador general puede restablecer la contraseña de un usuario (Administración → Usuarios → Seguridad).
4. Crear a los dueños, activar su verificación en dos pasos y quitar `ADMIN_BOOTSTRAP_PASSWORD` del servidor.
5. Probar la restauración de una copia con la base real y guardar la clave de las copias fuera de GitHub.
6. Piloto en una sola sede durante una o dos semanas antes de abrir las demás.
