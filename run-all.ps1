# ============================================================================
# run-all.ps1 — COMANDO UNICO del Reto 6 para Windows PowerShell
# Mismo contrato que run-all.sh:
#   1) levanta el sistema con docker compose
#   2) corre unitarias + integracion (empleados, auth, vacaciones, perfiles)
#   3) corre la suite BDD completa en el contenedor bdd-tests
#   4) apaga el sistema (salvo KEEP_UP=1)
# Devuelve 0 solo si TODO paso; si cualquier suite falla, sale con codigo != 0.
#
# Uso:       .\run-all.ps1                       (desde la raiz del repositorio)
#            $env:KEEP_UP=1; .\run-all.ps1       (deja el sistema levantado)
# Requisito: Docker Desktop encendido (Testcontainers lo necesita).
# ============================================================================
$ErrorActionPreference = 'Continue'

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location $root

$script:exit = 0
$resultados  = New-Object System.Collections.Generic.List[string]

function Paso([string]$m)  { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok([string]$m)    { Write-Host "[OK] $m"   -ForegroundColor Green }
function Fallo([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red }

function Registrar([string]$nombre, [int]$code) {
  if ($code -eq 0) {
    $resultados.Add("OK   - $nombre")
    Ok $nombre
  } else {
    $resultados.Add("FAIL - $nombre (codigo $code)")
    Fallo $nombre
    $script:exit = 1
  }
}

# ---------- 0. Prerrequisitos ----------
Paso "0/7 Comprobando prerrequisitos"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Fallo "Docker no esta en PATH"; exit 1
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
  Fallo "Docker no responde (Docker Desktop apagado?)"; exit 1
}
if (-not (Test-Path ".env")) {
  Fallo "Falta .env en la raiz (copia .env.example)"; exit 1
}
Ok "Docker y .env presentes"

# ---------- 1. Levantar el sistema ----------
Paso "1/7 docker compose up --build -d"
docker compose up --build -d
Registrar "docker compose up" $LASTEXITCODE
if ($script:exit -ne 0) {
  $resultados | ForEach-Object { Write-Host $_ }
  exit 1
}

# ---------- 2. Esperar health del gateway ----------
Paso "2/7 Esperando a http://localhost:8080/actuator/health"
$healthy = $false
for ($i = 0; $i -lt 40 -and -not $healthy; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-WebRequest "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 3
    $healthy = ($r.StatusCode -eq 200)
  } catch { $healthy = $false }
}
if ($healthy) { Ok "Gateway healthy" } else { Fallo "Gateway no respondio a tiempo" }

# ---------- 3. Java (unit + integracion con Testcontainers) ----------
foreach ($svc in @("empleados-service", "auth-service")) {
  Paso "3/7 $svc -> mvnw clean verify"
  Push-Location $svc
  if (Test-Path ".\mvnw.cmd") { .\mvnw.cmd -q clean verify } else { ./mvnw -q clean verify }
  $code = $LASTEXITCODE
  Pop-Location
  Registrar "$svc clean verify" $code
}

# ---------- 4. vacaciones-service (unit) ----------
if (Test-Path "vacaciones-service") {
  Paso "4/7 vacaciones-service -> npm ci && npm test"
  Push-Location vacaciones-service
  npm ci --silent
  $ciCode = $LASTEXITCODE
  npm test
  $testCode = $LASTEXITCODE
  Pop-Location
  $finalCode = if ($ciCode -ne 0) { $ciCode } else { $testCode }
  Registrar "vacaciones-service npm test" $finalCode
} else {
  Paso "4/7 vacaciones-service (no encontrado, se omite)"
}

# ---------- 5. perfiles-service (.NET) ----------
if (Test-Path "perfiles-service.Tests") {
  Paso "5/7 perfiles-service.Tests"
  if (Test-Path "perfiles-service.Tests\run-tests.sh") {
    Push-Location perfiles-service.Tests
    bash run-tests.sh
    $code = $LASTEXITCODE
    Pop-Location
    Registrar "perfiles-service run-tests.sh" $code
  } elseif (Get-Command dotnet -ErrorAction SilentlyContinue) {
    Push-Location perfiles-service.Tests
    dotnet test
    $code = $LASTEXITCODE
    Pop-Location
    Registrar "perfiles-service dotnet test" $code
  } else {
    Fallo "perfiles-service: sin run-tests.sh ni dotnet en PATH"
    Registrar "perfiles-service" 1
  }
} else {
  Paso "5/7 perfiles-service.Tests (no encontrado, se omite)"
}

# ---------- 6. BDD (contenedor) ----------
Paso "6/7 BDD -> docker compose --profile tests run --build --rm bdd-tests"
docker compose --profile tests run --build --rm bdd-tests
Registrar "BDD e2e-tests" $LASTEXITCODE

# ---------- 7. Resumen ----------
Paso "7/7 Resumen"
$resultados | ForEach-Object { Write-Host $_ }
if ($script:exit -eq 0) { Write-Host "Todo en verde" -ForegroundColor Green }
else                    { Write-Host "Hubo fallos"   -ForegroundColor Red   }

# ---------- Tear down opcional ----------
if ($env:KEEP_UP -ne "1") {
  Paso "docker compose down"
  docker compose down
} else {
  Write-Host "`n[KEEP_UP=1] Sistema dejado levantado." -ForegroundColor Yellow
}

exit $script:exit