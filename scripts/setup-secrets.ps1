<#
 Genera los secretos locales del proyecto en el archivo .env (equivalente a setup-secrets.sh para Windows).
   powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1            # rellena lo que falte
   powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1 -Rotar     # regenera todos los secretos
 Los valores se generan en este equipo y no se transmiten.
#>
param([switch]$Rotar)
$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$ejemplo = Join-Path $raiz '.env.example'
$destino = Join-Path $raiz '.env'
if (-not (Test-Path $ejemplo)) { throw "No se encontró $ejemplo" }

function Nuevo-Secreto {
    # 24 alfanuméricos + "Aa1": cumple la política de contraseñas de auth-service y no rompe URLs
    $caracteres = [char[]]'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $valor = -join ($bytes | ForEach-Object { $caracteres[$_ % $caracteres.Length] })
    return "${valor}Aa1"
}

if (-not (Test-Path $destino)) { Copy-Item $ejemplo $destino; Write-Host 'Creado .env a partir de .env.example' }

$contenido = [System.Collections.Generic.List[string]](Get-Content $destino)
$agregadas = 0; $generadas = 0

foreach ($linea in Get-Content $ejemplo) {
    if ($linea -match '^\s*(#|$)') { continue }
    $clave, $valorEjemplo = $linea -split '=', 2
    $indice = -1
    for ($i = 0; $i -lt $contenido.Count; $i++) { if ($contenido[$i] -like "$clave=*") { $indice = $i; break } }

    if ($indice -lt 0) { $contenido.Add("$clave=$valorEjemplo"); $indice = $contenido.Count - 1; $agregadas++ }
    $actual = ($contenido[$indice] -split '=', 2)[1]

    if ($valorEjemplo -eq 'CHANGE_ME' -and ($actual -eq 'CHANGE_ME' -or $Rotar)) {
        $contenido[$indice] = "$clave=$(Nuevo-Secreto)"; $generadas++
    }
}
# UTF-8 sin BOM y saltos LF, para que Docker Compose lea el archivo sin sorpresas
[System.IO.File]::WriteAllText($destino, (($contenido -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Listo: $generadas secreto(s) generado(s), $agregadas variable(s) agregada(s) en .env"
Write-Host 'Recuerde: SONAR_TOKEN se genera dentro de SonarQube y se pega a mano en .env.'
if ($Rotar) {
    Write-Host 'AVISO: tras rotar, las bases de datos y RabbitMQ conservan las contraseñas anteriores en sus volúmenes.'
    Write-Host '       Recréelos con: docker compose down -v (borra los datos locales) y luego docker compose up --build'
}
