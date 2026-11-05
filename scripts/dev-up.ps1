# SOLO DESARROLLO LOCAL: levanta PostgreSQL en Docker y arranca el backend con el perfil "local".
# Uso (desde la raiz del repo):  .\scripts\dev-up.ps1            (Postgres + backend en primer plano)
#                                .\scripts\dev-up.ps1 -SoloBaseDatos   (solo Postgres)
# El backend se detiene con Ctrl+C. La base se apaga con .\scripts\dev-down.ps1
param(
    [switch]$SoloBaseDatos
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$contenedor = 'odontomax-local-db'

Push-Location $raiz
try {
    $puerto = if ($env:ODONTOMAX_DB_PORT) { $env:ODONTOMAX_DB_PORT } else { '' }

    if (-not $puerto) {
        $nativo5432Listo = $false
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", 5432)
            $nativo5432Listo = $tcp.Connected
            $tcp.Close()
        } catch {
            $nativo5432Listo = $false
        }

        if ($nativo5432Listo) {
            $puerto = '5432'
            Write-Host "==> PostgreSQL detectado y activo en localhost:5432." -ForegroundColor Green
        } else {
            $puerto = '55432'
        }
    }

    if ($puerto -eq '55432') {
        Write-Host '==> Comprobando Docker...' -ForegroundColor Cyan
        docker info --format '{{.ServerVersion}}' | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker no responde. Abra Docker Desktop y vuelva a intentarlo.'
        }

        Write-Host '==> Levantando PostgreSQL 15 (docker compose)...' -ForegroundColor Cyan
        docker compose up -d postgres
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up fallo.' }

        Write-Host '==> Esperando a que PostgreSQL este sano...' -ForegroundColor Cyan
        $limite = (Get-Date).AddSeconds(90)
        do {
            Start-Sleep -Seconds 2
            $estado = docker inspect -f '{{.State.Health.Status}}' $contenedor
            Write-Host "    estado: $estado"
            if ((Get-Date) -gt $limite) { throw 'PostgreSQL no quedo sano en 90 s. Revise: docker compose logs postgres' }
        } while ($estado -ne 'healthy')
    }

    $env:ODONTOMAX_DB_PORT = $puerto
    Write-Host "==> PostgreSQL listo en localhost:$puerto (base odontomax)." -ForegroundColor Green

    if ($SoloBaseDatos) { return }

    Write-Host '==> Arrancando backend con el perfil local (Ctrl+C para detenerlo)...' -ForegroundColor Cyan
    Write-Host '    API: http://localhost:8080/api/v1' -ForegroundColor Green
    Set-Location (Join-Path $raiz 'backend')
    & .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.jvmArguments=-DODONTOMAX_DB_PORT=$puerto" '-Dspring-boot.run.profiles=local'
}
finally {
    Pop-Location
}
