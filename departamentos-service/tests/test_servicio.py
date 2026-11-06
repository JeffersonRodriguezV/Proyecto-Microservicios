"""Pruebas unitarias de la lógica de negocio (servicio + repositorio)."""

import pytest
from sqlalchemy.exc import IntegrityError

from app.repositories import departamento_repository as repo
from app.schemas.departamento_schema import DepartamentoCreate
from app.services import departamento_service as servicio


def datos(id_="IT", nombre="Tecnología", desc="Área de TI"):
    return DepartamentoCreate(id=id_, nombre=nombre, descripcion=desc)


def test_crear_departamento_lo_persiste(db_session):
    creado = servicio.crear_departamento(db_session, datos())
    assert creado.id == "IT"
    assert repo.exists_by_id(db_session, "IT") is True


def test_id_duplicado_se_rechaza_con_value_error(db_session):
    servicio.crear_departamento(db_session, datos())
    with pytest.raises(ValueError, match="ya está registrado"):
        servicio.crear_departamento(db_session, datos())


def test_integrity_error_se_traduce_a_value_error(db_session, monkeypatch):
    # Simula la carrera: la verificación previa dice "no existe" pero la BD rechaza el insert.
    monkeypatch.setattr(repo, "exists_by_id", lambda db, i: False)

    def falla(db, dep):
        raise IntegrityError("insert", {}, Exception("duplicado"))

    monkeypatch.setattr(repo, "create", falla)
    with pytest.raises(ValueError, match="ya está registrado"):
        servicio.crear_departamento(db_session, datos())


def test_obtener_por_id_existente_e_inexistente(db_session):
    servicio.crear_departamento(db_session, datos("RRHH", "Recursos Humanos", "Personas"))
    assert servicio.obtener_departamento_por_id(db_session, "RRHH").nombre == "Recursos Humanos"
    assert servicio.obtener_departamento_por_id(db_session, "NOPE") is None


def test_listar_devuelve_todos(db_session):
    assert servicio.listar_departamentos(db_session) == []
    servicio.crear_departamento(db_session, datos("A", "a", "d"))
    servicio.crear_departamento(db_session, datos("B", "b", "d"))
    assert {d.id for d in servicio.listar_departamentos(db_session)} == {"A", "B"}


def test_schema_rechaza_campos_vacios():
    with pytest.raises(ValueError):
        DepartamentoCreate(id="", nombre="x", descripcion="y")
