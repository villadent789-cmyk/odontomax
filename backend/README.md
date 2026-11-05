# OdontoMax - Backend Odontológico (Spring Boot 3 + Clean Architecture)

Núcleo del backend para clínicas odontológicas multisede: **Spring Boot 3 (Java 17)**, **PostgreSQL (Supabase)** y almacenamiento de radiografías en **Cloudflare R2**.

> [!IMPORTANT]
> **Secretos:** `JWT_SECRET` (`openssl rand -base64 48`) y `TOTP_ENCRYPTION_KEY` (`openssl rand -base64 32`) son obligatorios y se inyectan solo por variables de entorno. Nunca subas el archivo `.env`.

---

## 🏛️ Arquitectura del Sistema (Clean Architecture / Uncle Bob)

```
com.odontomax/
├── domain/                  # Entidades y Reglas de Negocio Puras (Sin dependencias de frameworks)
│   ├── model/               # Paciente, Cita, OdontogramaPieza, Ortodoncia, Radiografia
│   ├── exception/           # CitaColisionException, PacienteDeudorException
│   └── port/out/            # Interfaces de salida (Repositorios y Storage)
│
├── application/             # Casos de Uso y Orquestación del Negocio
│   ├── service/             # CitaService, PacienteService, RadiografiaService
│   └── dto/                 # DTOs de entrada y salida (Reserva pública, tickets, etc.)
│
├── infrastructure/          # Adaptadores Técnicos y Frameworks
│   ├── persistence/         # Entidades JPA y Repositorios PostgreSQL (Supabase)
│   ├── storage/             # Adaptador Cloudflare R2 (API S3 compatible)
│   └── security/            # Spring Security y Políticas CORS
│
└── presentation/            # Controladores REST Web
    ├── controller/          # CitaController, PacienteController, RadiografiaController
    │                        # ReservaPublicaController (Para la Landing Page)
    └── exception/           # GlobalExceptionHandler con respuestas en español
```

---

## 🚀 Endpoints Principales

### 1. Landing Page Pública (Sin Autenticación)
- `POST /api/v1/reservas-publicas`: Registra la solicitud del paciente desde el wizard de la web y genera el ticket digital.

### 2. Panel Administrativo Clínico
- `GET /api/v1/citas?fecha=2026-09-23&sedeId=sede-centro`: Lista las citas del día para la agenda.
- `GET /api/v1/citas/reservas-web-pendientes`: Bandeja de reservas entrantes desde la landing page.
- `POST /api/v1/citas`: Agendamiento interno con prevención de colisiones de doctor y sillón.
- `PATCH /api/v1/citas/{id}/estado`: Check-in a sala de espera o atendido.
- `GET /api/v1/pacientes?busqueda=Juan`: Búsqueda reactiva de pacientes.
- `POST /api/v1/pacientes/{id}/archivar`: Archivo con validación de deudas pendientes.
- `POST /api/v1/radiografias/subir`: Subida directa de imágenes odontológicas a **Cloudflare R2**.
- `GET /api/v1/radiografias/paciente/{id}`: Consulta de estudios para el visor radiológico.

---

## ⚙️ Variables de Entorno para Producción

Configura estas variables en tu proveedor de hosting (**Render**, **Railway**, etc.):

```env
# Base de Datos (Supabase)
SUPABASE_DB_URL=jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:6543/postgres?sslmode=require
SUPABASE_DB_USER=postgres.[tu-proyecto-id]
SUPABASE_DB_PASSWORD=tu-contrasena-supabase

# Almacenamiento (Cloudflare R2)
CLOUDFLARE_ACCOUNT_ID=tu-account-id
CLOUDFLARE_R2_ACCESS_KEY=tu-access-key
CLOUDFLARE_R2_SECRET_KEY=tu-secret-key
CLOUDFLARE_R2_BUCKET=radiografias-odontomax
CLOUDFLARE_R2_PUBLIC_DOMAIN=https://pub-xyz.r2.dev
```
