"""Pruebas del endpoint /health: formato común, 200 si UP y 503 si DOWN."""

from app import database


def test_health_up_responde_200(client, monkeypatch):
    monkeypatch.setattr(database, "verificar_conexion", lambda: True)
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {
        "status": "UP",
        "service": "departamentos-service",
        "checks": {"database": "UP"},
    }


def test_health_down_responde_503(client, monkeypatch):
    monkeypatch.setattr(database, "verificar_conexion", lambda: False)
    r = client.get("/health")
    assert r.status_code == 503
    assert r.json()["status"] == "DOWN"
    assert r.json()["checks"]["database"] == "DOWN"


def test_verificar_conexion_false_si_la_bd_no_responde(monkeypatch):
    class MotorRoto:
        def connect(self):
            raise RuntimeError("sin conexión")

    monkeypatch.setattr(database, "engine", MotorRoto())
    assert database.verificar_conexion() is False


def test_verificar_conexion_true_con_bd_disponible(monkeypatch):
    from sqlalchemy import create_engine

    monkeypatch.setattr(database, "engine", create_engine("sqlite://"))
    assert database.verificar_conexion() is True
