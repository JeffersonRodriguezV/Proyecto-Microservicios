"""
Acceso a datos de Departamento.
"""

from typing import List, Optional
from sqlalchemy.orm import Session
from app.models.departamento import Departamento


def get_by_id(db: Session, departamento_id: str) -> Optional[Departamento]:
    """Equivalente a JpaRepository.findById(id)."""
    return db.query(Departamento).filter(Departamento.id == departamento_id).first()


def exists_by_id(db: Session, departamento_id: str) -> bool:
    return get_by_id(db, departamento_id) is not None


def get_all(db: Session) -> List[Departamento]:
    """Equivalente a JpaRepository.findAll()."""
    return db.query(Departamento).all()


def create(db: Session, departamento: Departamento) -> Departamento:
    db.add(departamento)
    db.commit()
    db.refresh(departamento)
    return departamento