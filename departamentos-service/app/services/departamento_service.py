"""
Lógica de negocio de Departamento.
"""

from typing import List, Optional
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.departamento import Departamento
from app.repositories import departamento_repository as repository
from app.schemas.departamento_schema import DepartamentoCreate


def crear_departamento(db: Session, datos: DepartamentoCreate) -> Departamento:
    if repository.exists_by_id(db, datos.id):
        raise ValueError(f"El id '{datos.id}' ya está registrado")

    nuevo_departamento = Departamento(
        id=datos.id,
        nombre=datos.nombre,
        descripcion=datos.descripcion,
    )

    try:
        return repository.create(db, nuevo_departamento)
    except IntegrityError:
        db.rollback()
        raise ValueError(f"El id '{datos.id}' ya está registrado")


def obtener_departamento_por_id(db: Session, departamento_id: str) -> Optional[Departamento]:
    return repository.get_by_id(db, departamento_id)


def listar_departamentos(db: Session) -> List[Departamento]:
    return repository.get_all(db)