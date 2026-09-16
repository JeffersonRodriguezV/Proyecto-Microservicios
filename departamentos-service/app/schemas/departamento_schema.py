"""
Schemas Pydantic: contratos de entrada (request) y salida (response) de la API.
"""

from pydantic import BaseModel, Field, ConfigDict


class DepartamentoCreate(BaseModel):
    """Cuerpo esperado en POST /departamentos."""

    id: str = Field(
        ...,
        min_length=1,
        max_length=50,
        description="Código de negocio del departamento, ej: 'IT'",
    )
    nombre: str = Field(..., min_length=1, max_length=100)
    descripcion: str = Field(..., min_length=1, max_length=255)


class DepartamentoResponse(BaseModel):
    """Forma en que se devuelve un Departamento en las respuestas."""

    id: str
    nombre: str
    descripcion: str

    model_config = ConfigDict(from_attributes=True)

