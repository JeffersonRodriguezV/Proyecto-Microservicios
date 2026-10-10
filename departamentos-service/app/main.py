"""
Punto de entrada de la aplicación.

Nota de diseño: no se llama a Base.metadata.create_all() aquí. El
esquema de la base de datos se crea mediante database/init.sql, montado
por Docker Compose (misma estrategia que empleados-service, que usa
spring.jpa.hibernate.ddl-auto=validate en vez de update).

Seguridad (Reto 5): el JWT lo valida el API Gateway. Aquí solo se DOCUMENTA el
esquema BearerAuth en OpenAPI; no se exige en el servicio porque empleados-service
lo consulta directamente (sin token) para validar el departamentoId.
"""

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

from app.exceptions.handlers import registrar_exception_handlers
from app.routers import departamentos

app = FastAPI(
    title="Gestión de Departamentos",
    description=(
        "Microservicio de gestión de departamentos. Parte del sistema de "
        "onboarding/offboarding de empleados basado en microservicios. "
        "Requiere JWT: obténgalo en POST /auth/login y use el botón Authorize."
    ),
    version="0.1.0",
    # Documentación bajo el prefijo /departamentos para que el Gateway la enrute.
    docs_url="/departamentos/docs",
    openapi_url="/departamentos/openapi.json",
    redoc_url=None,
)


def openapi_con_bearer():
    """Agrega el esquema BearerAuth y lo aplica a todas las operaciones."""
    if app.openapi_schema:
        return app.openapi_schema
    esquema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
    )
    esquema.setdefault("components", {}).setdefault("securitySchemes", {})["BearerAuth"] = {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "Pegue el accessToken devuelto por /auth/login (sin el prefijo 'Bearer ').",
    }
    esquema["security"] = [{"BearerAuth": []}]
    app.openapi_schema = esquema
    return esquema


app.openapi = openapi_con_bearer

registrar_exception_handlers(app)
app.include_router(departamentos.router)


@app.get("/", include_in_schema=False)
def root():
    """Endpoint raíz, útil como target de healthcheck en Docker Compose."""
    return {"servicio": "departamentos-service", "estado": "activo"}