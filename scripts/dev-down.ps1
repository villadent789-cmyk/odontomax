# SOLO DESARROLLO LOCAL: apaga el PostgreSQL de Docker.
# Uso (desde la raiz del repo):  .\scripts\dev-down.ps1           (conserva los datos)
#                                .\scripts\dev-down.ps1 -Reiniciar (borra el volumen: base nueva en el proximo dev-up)
# El backend lanzado por dev-up.ps1 corre en primer plano: detengalo con Ctrl+C en su terminal.
param(
    [switch]$Reiniciar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

Push-Location $raiz
try {
    if ($Reiniciar) {
        Write-Host '==> Apagando PostgreSQL y BORRANDO el volumen de datos local...' -ForegroundColor Yellow
        docker compose down -v
    }
    else {
        Write-Host '==> Apagando PostgreSQL (los datos se conservan)...' -ForegroundColor Cyan
        docker compose down
    }
    if ($LASTEXITCODE -ne 0) { throw 'docker compose down fallo.' }
    Write-Host '==> Listo.' -ForegroundColor Green
}
finally {
    Pop-Location
}
