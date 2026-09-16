"""
Entidad Departamento, mapeada a la tabla 'departamentos'.

Decisión de diseño (a diferencia de Empleado en el Reto 1):
El 'id' aquí NO se autogenera. Es un código de negocio provisto por el
cliente (ej: "IT", "RRHH"), porque empleados-service lo usa como clave
foránea externa vía HTTP (departamentoId). Si lo autogenerábamos, el
flujo de pruebas oficial del Reto 2 (que crea "IT" y luego referencia
"IT" desde empleados) se rompería.

Al ser String + Primary Key, la propia base de datos ya garantiza la
unicidad del id a nivel de esquema.
"""

from sqlalchemy import Column, String
from app.database import Base


class Departamento(Base):
    __tablename__ = "departamentos"

    id = Column(String(50), primary_key=True, index=True)
    nombre = Column(String(100), nullable=False)
    descripcion = Column(String(255), nullable=False)


