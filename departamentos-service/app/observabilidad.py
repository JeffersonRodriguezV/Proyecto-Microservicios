"""
Observabilidad (Reto 8): métricas Prometheus, trazas OpenTelemetry (Zipkin) y logs JSON con traceId.

Se configura todo desde aquí para que main.py solo tenga que llamar a `configurar_observabilidad(app)`.
"""

import logging
import os
import sys
import time

import structlog
from fastapi import FastAPI, Request
from opentelemetry import trace
from opentelemetry.exporter.zipkin.json import ZipkinExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from prometheus_fastapi_instrumentator import Instrumentator

SERVICIO = "departamentos-service"

# Rutas que Docker (healthcheck) y Prometheus consultan cada pocos segundos: ni trazas, ni métricas, ni log.
RUTAS_SILENCIOSAS = ("/health", "/metrics")


# ----------------------------------------------------------------------------- logs

def _agregar_trace_id(_logger, _metodo, evento: dict) -> dict:
    """Procesador de structlog: añade `traceId` si hay un span activo."""
    contexto = trace.get_current_span().get_span_context()
    if contexto.is_valid:
        evento["traceId"] = format(contexto.trace_id, "032x")
    return evento


def configurar_logging() -> None:
    """structlog -> JSON por stdout; el logging estándar (uvicorn, sqlalchemy...) pasa por el mismo formato."""
    pre_cadena = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.processors.TimeStamper(fmt="iso", key="timestamp"),
        _agregar_trace_id,
    ]
    structlog.configure(
        processors=pre_cadena + [structlog.stdlib.ProcessorFormatter.wrap_for_formatter],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )
    formato = structlog.stdlib.ProcessorFormatter(
        foreign_pre_chain=pre_cadena,
        processors=[
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            structlog.processors.EventRenamer("message"),
            structlog.processors.format_exc_info,
            structlog.processors.JSONRenderer(),
        ],
    )
    manejador = logging.StreamHandler(sys.stdout)
    manejador.setFormatter(formato)
    raiz = logging.getLogger()
    raiz.handlers = [manejador]
    raiz.setLevel(logging.INFO)
    # uvicorn: su logger de accesos se reemplaza por el middleware de abajo (con traceId)
    for nombre in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        registro = logging.getLogger(nombre)
        registro.handlers = []
        registro.propagate = True
    logging.getLogger("uvicorn.access").disabled = True
    # El log de acceso lo escribe el middleware de abajo con traceId; los SQL no se imprimen.
    logging.getLogger("sqlalchemy.engine").setLevel(logging.WARNING)


log = structlog.get_logger(SERVICIO)


# ----------------------------------------------------------------------------- trazas

def configurar_trazas(app: FastAPI, motor=None) -> None:
    """OpenTelemetry con exportador Zipkin y propagación W3C (traceparent, por defecto del SDK)."""
    if os.getenv("OTEL_SDK_DISABLED", "false").lower() == "true":
        return
    proveedor = TracerProvider(resource=Resource.create({"service.name": os.getenv("OTEL_SERVICE_NAME", SERVICIO)}))
    endpoint = os.getenv("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://zipkin:9411/api/v2/spans")
    proveedor.add_span_processor(BatchSpanProcessor(ZipkinExporter(endpoint=endpoint)))
    trace.set_tracer_provider(proveedor)
    FastAPIInstrumentor.instrument_app(app, excluded_urls="health,metrics", tracer_provider=proveedor)
    if motor is not None:
        SQLAlchemyInstrumentor().instrument(engine=motor, tracer_provider=proveedor)


# ----------------------------------------------------------------------------- métricas

def configurar_metricas(app: FastAPI) -> None:
    """GET /metrics: CPU/memoria del proceso (process_*) y http_requests_total / http_request_duration_seconds.

    should_group_status_codes=False deja el código exacto (200, 404, 500) en la etiqueta `status`; agrupado
    ("2xx") no coincidiría con las reglas de Prometheus que buscan status=~"5..".
    """
    Instrumentator(
        should_group_status_codes=False,
        excluded_handlers=list(RUTAS_SILENCIOSAS),
    ).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)


# ----------------------------------------------------------------------------- middleware de acceso

def registrar_log_de_acceso(app: FastAPI) -> None:
    @app.middleware("http")
    async def log_de_acceso(request: Request, call_next):
        inicio = time.perf_counter()
        respuesta = await call_next(request)
        if request.url.path not in RUTAS_SILENCIOSAS:
            log.info(
                "peticion",
                method=request.method,
                path=request.url.path,
                status=respuesta.status_code,
                duracionMs=round((time.perf_counter() - inicio) * 1000, 2),
            )
        return respuesta


def configurar_observabilidad(app: FastAPI, motor=None) -> None:
    configurar_logging()
    registrar_log_de_acceso(app)   # primero: así queda dentro del span de FastAPIInstrumentor y lleva traceId
    configurar_metricas(app)
    configurar_trazas(app, motor)
