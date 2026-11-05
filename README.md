# OdontoMax

Sistema de gestión odontológica multisede.

- `backend/` — API REST en Spring Boot 3 (Java 17) + PostgreSQL (Supabase) + Cloudflare R2. Ver [backend/README.md](backend/README.md).
- El frontend se desarrollará por separado con su propio diseño y consumirá la API en `/api/v1`.

## Ejecutar el backend

```bash
cd backend
cp .env.example .env   # completar credenciales y secretos
./mvnw spring-boot:run
```

Pruebas: `./mvnw verify` (las pruebas `*PostgresIntegrationTest` requieren Docker).
