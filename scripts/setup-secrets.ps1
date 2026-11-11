<#
 Genera los secretos locales para Docker Compose Secrets (equivalente a setup-secrets.sh para Windows).
   powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1            # crea lo que falte
   powershell -ExecutionPolicy Bypass -File scripts\setup-secrets.ps1 -Rotar     # regenera todos los secretos
 Crea .env (config no secreta), secrets\<nombre> (un archivo por secreto), secrets\secrets.env y e2e-tests\.env.
 Orden por secreto: archivo existente > valor previo en .env (migración, no rompe las BD ya creadas) > aleatorio.
#>
param([switch]$Rotar)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)   # UTF-8 sin BOM y sin salto final

# nombre del archivo, clave en .env, tipo (corto|largo|fijo), valor fijo
$secretos = @(
  @('jwt_secret','JWT_SECRET','largo',''),
  @('admin_password','ADMIN_PASSWORD','corto',''),
  @('seed_user_password','SEED_USER_PASSWORD','corto',''),
  @('rabbitmq_password','RABBITMQ_PASSWORD','corto',''),
  @('db_empleados_password','DB_EMPLEADOS_PASSWORD','corto',''),
  @('db_empleados_root_password','DB_EMPLEADOS_ROOT_PASSWORD','corto',''),
  @('db_auth_password','DB_AUTH_PASSWORD','corto',''),
  @('db_auth_root_password','DB_AUTH_ROOT_PASSWORD','corto',''),
  @('db_departamentos_password','DB_DEPARTAMENTOS_PASSWORD','corto',''),
  @('grafana_admin_password','GRAFANA_ADMIN_PASSWORD','corto',''),
  @('discord_webhook_url','DISCORD_WEBHOOK_URL','fijo','https://discord.com/api/webhooks/0/CAMBIAR_POR_WEBHOOK_REAL'),
  @('jenkins_admin_password','JENKINS_ADMIN_PASSWORD','corto',''),
  @('sonar_token','SONAR_TOKEN','fijo','pendiente'),
  @('sonar_db_password','SONAR_DB_PASSWORD','corto','')
)

function Nuevo-Secreto([int]$largo) {
    # alfanuméricos + "Aa1": cumple la política de auth-service y no rompe URLs JDBC/AMQP
    $caracteres = [char[]]'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $bytes = New-Object byte[] $largo
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return ((-join ($bytes | ForEach-Object { $caracteres[$_ % $caracteres.Length] })) + 'Aa1')
}
function Leer-Env([string]$clave, [string]$ruta) {
    if (-not (Test-Path $ruta)) { return '' }
    $l = Get-Content $ruta | Where-Object { $_ -like "$clave=*" } | Select-Object -Last 1
    if ($null -eq $l) { return '' }
    return ($l -split '=', 2)[1].Trim()
}

$envRuta = Join-Path $raiz '.env'
if (-not (Test-Path $envRuta)) { Copy-Item (Join-Path $raiz '.env.example') $envRuta; Write-Host 'Creado .env a partir de .env.example' }
$dir = Join-Path $raiz 'secrets'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$nuevos = 0; $migrados = 0; $conservados = 0; $lineas = @()
foreach ($d in $secretos) {
    $nombre, $clave, $tipo, $fijo = $d
    $archivo = Join-Path $dir $nombre
    $valor = ''; $origen = ''
    if (-not $Rotar -and (Test-Path $archivo) -and ((Get-Item $archivo).Length -gt 0)) {
        $valor = [System.IO.File]::ReadAllText($archivo); $origen = 'conservado'
    }
    if (-not $valor -and -not $Rotar) {
        $previo = Leer-Env $clave $envRuta
        if ($previo -and $previo -ne 'CHANGE_ME' -and $previo -notlike 'CAMBIAR*') { $valor = $previo; $origen = 'migrado' }
    }
    if (-not $valor) {
        switch ($tipo) { 'largo' { $valor = Nuevo-Secreto 48 } 'fijo' { $valor = $fijo } default { $valor = Nuevo-Secreto 24 } }
        $origen = 'nuevo'
    }
    [System.IO.File]::WriteAllText($archivo, $valor, $utf8)
    $lineas += "$clave=$valor"
    switch ($origen) { 'nuevo' { $nuevos++ } 'migrado' { $migrados++ } default { $conservados++ } }
}
[System.IO.File]::WriteAllText((Join-Path $dir 'secrets.env'), (($lineas -join "`n") + "`n"), $utf8)

$e2e = Join-Path $raiz 'e2e-tests\.env'
if ((-not (Test-Path $e2e)) -or $Rotar) {
    $ae = Leer-Env 'ADMIN_EMAIL' $envRuta; if (-not $ae) { $ae = 'admin@empresa.com' }
    $ue = Leer-Env 'SEED_USER_EMAIL' $envRuta; if (-not $ue) { $ue = 'user@empresa.com' }
    $txt = "BASE_URL=http://localhost:8080`nADMIN_EMAIL=$ae`nADMIN_PASSWORD=$([System.IO.File]::ReadAllText((Join-Path $dir 'admin_password')))`nUSER_EMAIL=$ue`nUSER_PASSWORD=$([System.IO.File]::ReadAllText((Join-Path $dir 'seed_user_password')))`n"
    [System.IO.File]::WriteAllText($e2e, $txt, $utf8)
    Write-Host 'Generado e2e-tests\.env'
}

Write-Host "Listo: $nuevos nuevo(s), $migrados migrado(s) desde .env, $conservados conservado(s) en secrets\"
Write-Host 'Recuerde: sonar_token se genera dentro de SonarQube y se pega en secrets\sonar_token.'
Write-Host '          discord_webhook_url: pegue su webhook real en secrets\discord_webhook_url (sin salto de línea).'
if ($Rotar) {
    Write-Host 'AVISO: tras rotar, las bases de datos y RabbitMQ conservan las contraseñas anteriores en sus volúmenes.'
    Write-Host '       Recréelos con: docker compose down -v (borra los datos locales) y luego docker compose up --build'
}
