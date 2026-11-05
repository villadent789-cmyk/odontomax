# Script para cargar .env y ejecutar el backend de Spring Boot localmente
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("=", 2)
        if ($parts.Count -eq 2) {
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

if (-not [System.Environment]::GetEnvironmentVariable("ADMIN_BOOTSTRAP_EMAIL", "Process")) {
    [System.Environment]::SetEnvironmentVariable("ADMIN_BOOTSTRAP_EMAIL", "admin@odontomax.pe", "Process")
}
if (-not [System.Environment]::GetEnvironmentVariable("ADMIN_BOOTSTRAP_PASSWORD", "Process")) {
    # Sin contraseña fija en el repositorio: defínala en .env (ADMIN_BOOTSTRAP_PASSWORD=...)
    Write-Host "Falta ADMIN_BOOTSTRAP_PASSWORD en .env (contraseña inicial del ADMIN_GENERAL)." -ForegroundColor Yellow
}

# Deshabilitar Flyway localmente si ya está creado el esquema en Supabase
[System.Environment]::SetEnvironmentVariable("FLYWAY_ENABLED", "false", "Process")

Write-Host "Iniciando backend OdontoMax en http://localhost:8080/api/v1 ..." -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run
