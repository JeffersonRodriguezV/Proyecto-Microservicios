"""
Configuración de la conexión a la base de datos PostgreSQL.
"""

import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

DB_URL = os.getenv("DB_URL")
DB_USERNAME = os.getenv("DB_USERNAME")
DB_PASSWORD = os.getenv("DB_PASSWORD")

if not all([DB_URL, DB_USERNAME, DB_PASSWORD]):
    raise RuntimeError(
        "Variables de entorno de base de datos incompletas: "
        "se requieren DB_URL, DB_USERNAME y DB_PASSWORD"
    )

SQLALCHEMY_DATABASE_URL = (
    f"postgresql+psycopg://{DB_USERNAME}:{DB_PASSWORD}@"
    f"{DB_URL.replace('postgresql://', '')}"
)

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    pool_pre_ping=True,
    connect_args={"connect_timeout": 5},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()