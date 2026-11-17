"""Pruebas de las metricas basadas en la base de datos (SQLite en memoria)."""

from prometheus_client import CollectorRegistry
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool

from app.database import Base
from app.metricas_bd import registrar_metricas_bd
from app.models.departamento import Departamento


def _motor():
    motor = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(motor)
    return motor


def test_publica_total_y_disponibilidad():
    motor = _motor()
    with motor.begin() as c:
        c.execute(Departamento.__table__.insert(), [
            {"id": "TI", "nombre": "TI", "descripcion": "x"},
            {"id": "RH", "nombre": "RH", "descripcion": "y"},
        ])
    registro = CollectorRegistry()
    registrar_metricas_bd(motor, registro)
    assert registro.get_sample_value("departamentos_bd_disponible") == 1
    assert registro.get_sample_value("departamentos_bd_total") == 2


def test_base_caida_marca_no_disponible_sin_lanzar_error():
    motor = create_engine("sqlite://")  # sin tablas: la consulta falla
    registro = CollectorRegistry()
    registrar_metricas_bd(motor, registro)
    assert registro.get_sample_value("departamentos_bd_disponible") == 0
