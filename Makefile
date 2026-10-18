# ============================================================================
# Makefile — COMANDO UNICO del Reto 6 (alternativa a run-all.sh / run-all.ps1)
#   make test        -> levanta, corre unitarias + integracion + BDD y apaga.
#                       Falla (codigo != 0) si CUALQUIER suite falla.
#   make bdd         -> solo la suite BDD en contenedor.
#   make bdd-3x      -> 3 corridas consecutivas de BDD (detecta flakiness).
#   make test-all    -> delega al script principal run-all.sh.
#   make up / down   -> control manual del stack.
# Requisito: Docker encendido (Testcontainers lo necesita).
# ============================================================================
COMPOSE := docker compose

.PHONY: test up down unitarias bdd bdd-3x test-all

# ---------- Orquestacion completa con teardown garantizado ----------
# 'trap … EXIT' asegura que 'docker compose down' corra AUN si una suite falla.
test:
	@set -e; \
	trap 'echo "==> docker compose down"; $(COMPOSE) down' EXIT; \
	echo "==> docker compose up --build -d"; $(COMPOSE) up --build -d; \
	$(MAKE) --no-print-directory unitarias; \
	$(MAKE) --no-print-directory bdd; \
	echo "==> TODO EN VERDE: unitarias, integracion y BDD."

# ---------- Stack ----------
up:
	$(COMPOSE) up --build -d

down:
	$(COMPOSE) down

# ---------- Unitarias + integracion ----------
# Cada linea corre en su propia shell; 'make' se detiene en el primer fallo.
unitarias:
	cd empleados-service   && ./mvnw clean verify
	cd auth-service        && ./mvnw clean verify
	cd vacaciones-service  && npm ci --silent && npm test
	@if [ -d perfiles-service.Tests ]; then \
	  if [ -x perfiles-service.Tests/run-tests.sh ]; then \
	    ( cd perfiles-service.Tests && ./run-tests.sh ); \
	  elif command -v dotnet >/dev/null 2>&1; then \
	    ( cd perfiles-service.Tests && dotnet test ); \
	  else \
	    echo "[AVISO] perfiles-service: sin run-tests.sh ni dotnet; se omite."; \
	  fi \
	fi

# ---------- Suite BDD en contenedor ----------
bdd:
	$(COMPOSE) --profile tests run --build --rm bdd-tests

# ---------- Consistencia: 3 corridas consecutivas ----------
bdd-3x:
	@set -e; \
	for i in 1 2 3; do \
	  echo "==> Corrida $$i"; \
	  $(COMPOSE) --profile tests run --build --rm bdd-tests; \
	done
	@echo "==> 3 corridas consecutivas en verde."

# ---------- Delegar al script principal (paridad de contrato) ----------
test-all:
	bash run-all.sh