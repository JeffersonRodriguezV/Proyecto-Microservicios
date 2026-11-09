"""Pruebas de /metrics, del log JSON con traceId y de la configuración de trazas (Reto 8)."""

import json
import logging

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

from app import observabilidad


def test_metrics_expone_proceso_y_status_exacto(client):
    client.get("/departamentos/NO-EXISTE")
    cuerpo = client.get("/metrics").text
    assert "process_cpu_seconds_total" in cuerpo
    assert "process_resident_memory_bytes" in cuerpo
    assert "http_requests_total{" in cuerpo
    # status exacto, no agrupado ("4xx")
    assert 'status="404"' in cuerpo
    assert "4xx" not in cuerpo


def test_metrics_no_cuenta_health_ni_metrics(client):
    client.get("/health")
    cuerpo = client.get("/metrics").text
    assert 'handler="/health"' not in cuerpo
    assert 'handler="/metrics"' not in cuerpo


def test_agregar_trace_id_con_span_activo(monkeypatch):
    monkeypatch.setenv("OTEL_SDK_DISABLED", "false")  # con el SDK desactivado los spans no son válidos
    proveedor = TracerProvider()
    with proveedor.get_tracer("t").start_as_current_span("x") as span:
        evento = observabilidad._agregar_trace_id(None, "info", {})
        assert evento["traceId"] == format(span.get_span_context().trace_id, "032x")


def test_agregar_trace_id_sin_span():
    assert "traceId" not in observabilidad._agregar_trace_id(None, "info", {})


def test_log_de_acceso_sale_en_json(capsys):
    observabilidad.configurar_logging()
    app = FastAPI()
    observabilidad.registrar_log_de_acceso(app)

    @app.get("/ping")
    def ping():
        return {"ok": True}

    TestClient(app).get("/ping")
    lineas = [l for l in capsys.readouterr().out.splitlines() if l.startswith("{")]
    registro = next(r for r in map(json.loads, lineas) if r["message"] == "peticion")
    assert registro["message"] == "peticion"
    assert registro["path"] == "/ping" and registro["status"] == 200
    assert "timestamp" in registro and "level" in registro


def test_trazas_desactivadas_no_instala_exportador(monkeypatch):
    monkeypatch.setenv("OTEL_SDK_DISABLED", "true")
    observabilidad.configurar_trazas(FastAPI())  # no debe lanzar ni crear exportador


def test_trazas_activadas_configura_proveedor(monkeypatch):
    monkeypatch.setenv("OTEL_SDK_DISABLED", "false")
    monkeypatch.setenv("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://127.0.0.1:1/api/v2/spans")
    observabilidad.configurar_trazas(FastAPI())
    assert trace.get_tracer_provider() is not None
