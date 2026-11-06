"""
Configuración común de las pruebas.

Las variables de entorno se fijan ANTES de importar la app (database.py las exige al
importarse). La base real (PostgreSQL) se reemplaza por SQLite en memoria mediante
dependency_overrides: así las pruebas no necesitan Docker ni red.
"""

import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/test")
os.environ.setdefault("DB_USERNAME", "test")
os.environ.setdefault("DB_PASSWORD", "test")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.models import departamento  # noqa: F401  (registra la tabla en Base.metadata)


@pytest.fixture()
def db_session():
    motor = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(motor)
    sesion = sessionmaker(bind=motor, autocommit=False, autoflush=False)()
    try:
        yield sesion
    finally:
        sesion.close()
        motor.dispose()


@pytest.fixture()
def client(db_session):
    def _get_db():
        yield db_session

    app.dependency_overrides[get_db] = _get_db
    # raise_server_exceptions=False: los errores inesperados deben verse como 500 JSON.
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()
