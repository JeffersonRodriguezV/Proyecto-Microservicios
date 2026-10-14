#!/usr/bin/env bash
# Se ejecuta DENTRO del contenedor mcr.microsoft.com/dotnet/sdk:10.0, con el repo montado en /repo
# (solo lectura) y la carpeta de reportes montada en /reports.
# Copia las fuentes a /work para compilar alli y no dejar bin/ y obj/ en el repositorio.
set -euo pipefail

rm -rf /work && mkdir -p /work
cp -r /repo/perfiles-service /repo/perfiles-service.Tests /work/
rm -rf /work/perfiles-service/bin /work/perfiles-service/obj

cd /work/perfiles-service.Tests
dotnet test \
  --settings coverlet.runsettings \
  --logger "junit;LogFilePath=/reports/junit.xml" \
  --results-directory /reports/coverage
