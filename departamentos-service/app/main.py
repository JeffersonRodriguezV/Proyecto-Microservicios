"""
Punto de entrada de la aplicación.

Nota de diseño: no se llama a Base.metadata.create_all() aquí. El
esquema de la base de datos se crea mediante database/init.sql, montado
por Docker Compose (misma estrategia que empleados-service, que usa
spring.jpa.hibernate.ddl-auto=validate en vez de update).
"""

from fastapi import FastAPI

from app.exceptions.handlers import registrar_exception_handlers
from app.routers import departamentos

app = FastAPI(
    title="Gestión de Departamentos",
    description=(
        "Microservicio de gestión de departamentos. Parte del sistema de "
        "onboarding/offboarding de empleados basado en microservicios."
    ),
    version="0.1.0",
)

registrar_exception_handlers(app)
app.include_router(departamentos.router)


@app.get("/", include_in_schema=False)
def root():
    """Endpoint raíz, útil como target de healthcheck en Docker Compose."""
    return {"servicio": "departamentos-service", "estado": "activo"}