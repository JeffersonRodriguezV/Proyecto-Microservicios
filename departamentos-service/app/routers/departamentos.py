"""
Endpoints HTTP de Departamento.
"""

from typing import List

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.schemas.departamento_schema import DepartamentoCreate, DepartamentoResponse
from app.services import departamento_service as service

router = APIRouter(prefix="/departamentos", tags=["Departamentos"])


@router.post(
    "",
    response_model=DepartamentoResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Registrar un nuevo departamento",
    responses={
        400: {"description": "El id ya está registrado, o los datos son inválidos"},
    },
)
def crear_departamento(datos: DepartamentoCreate, db: Session = Depends(get_db)):
    """
    Registra un departamento nuevo.

    - **id**: código de negocio único (ej. "IT"), provisto por el cliente.
    - **nombre**, **descripcion**: obligatorios, no vacíos.
    """
    return service.crear_departamento(db, datos)


@router.get(
    "/{departamento_id}",
    response_model=DepartamentoResponse,
    summary="Consultar un departamento por id",
    responses={
        404: {"description": "No existe un departamento con ese id"},
    },
)
def obtener_departamento(departamento_id: str, db: Session = Depends(get_db)):
    departamento = service.obtener_departamento_por_id(db, departamento_id)
    if departamento is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"El departamento con id {departamento_id} no existe",
        )
    return departamento


@router.get(
    "",
    response_model=List[DepartamentoResponse],
    summary="Listar todos los departamentos",
)
def listar_departamentos(db: Session = Depends(get_db)):
    return service.listar_departamentos(db)