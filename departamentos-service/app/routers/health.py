"""
Endpoint de salud (requisito transversal del proyecto).

Formato común a todos los microservicios:
    {"status": "UP", "service": "departamentos-service", "checks": {"database": "UP"}}
HTTP 200 si todo está UP y 503 si alguna dependencia está DOWN.
Este servicio no usa message broker (solo REST), por eso solo verifica la base de datos.
"""

from fastapi import APIRouter, Response, status

from app import database

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    summary="Estado de salud del servicio y de sus dependencias",
    responses={503: {"description": "Alguna dependencia (base de datos) no responde"}},
)
def health(response: Response):
    base_datos = "UP" if database.verificar_conexion() else "DOWN"
    estado = "UP" if base_datos == "UP" else "DOWN"
    if estado == "DOWN":
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    return {
        "status": estado,
        "service": "departamentos-service",
        "checks": {"database": base_datos},
    }
