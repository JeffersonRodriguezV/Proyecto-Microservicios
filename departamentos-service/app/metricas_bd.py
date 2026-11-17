"""
Metricas basadas en la BASE DE DATOS de departamentos-service (se publican en GET /metrics).

  departamentos_bd_disponible      1 si la base responde, 0 si no
  departamentos_bd_total           departamentos registrados
  departamentos_bd_pool_en_uso     conexiones del pool SQLAlchemy en uso (si el pool lo soporta)

Decisiones:
  - Colector personalizado de prometheus_client: la consulta se hace al hacer scrape, pero el resultado
    se guarda TTL segundos para que muchos scrapes seguidos no golpeen la base.
  - Si la base falla, collect() NO lanza excepcion: publica disponible=0 y el endpoint /metrics sigue vivo.
"""

import time

from prometheus_client import REGISTRY
from prometheus_client.core import GaugeMetricFamily
from sqlalchemy import func, select
from sqlalchemy.orm import sessionmaker

from app.models.departamento import Departamento

TTL_SEGUNDOS = 15


class ColectorBaseDatos:
    def __init__(self, motor, ttl: float = TTL_SEGUNDOS):
        self._motor = motor
        self._sesiones = sessionmaker(bind=motor)
        self._ttl = ttl
        self._ultimo = 0.0
        self._cache = (0, 0)  # (disponible, total)

    def _leer(self):
        ahora = time.monotonic()
        if ahora - self._ultimo < self._ttl:
            return self._cache
        try:
            with self._sesiones() as sesion:
                total = sesion.execute(select(func.count()).select_from(Departamento)).scalar_one()
            self._cache = (1, int(total))
        except Exception:  # noqa: BLE001  la base caida no debe tumbar /metrics
            self._cache = (0, self._cache[1])
        self._ultimo = ahora
        return self._cache

    def collect(self):
        disponible, total = self._leer()
        g = GaugeMetricFamily("departamentos_bd_disponible", "1 si la base de datos responde, 0 si no")
        g.add_metric([], disponible)
        yield g
        g = GaugeMetricFamily("departamentos_bd_total", "Departamentos registrados en la base de datos")
        g.add_metric([], total)
        yield g
        pool = getattr(self._motor, "pool", None)
        if pool is not None and hasattr(pool, "checkedout"):
            g = GaugeMetricFamily("departamentos_bd_pool_en_uso", "Conexiones del pool en uso")
            g.add_metric([], pool.checkedout())
            yield g


def registrar_metricas_bd(motor, registro=REGISTRY) -> ColectorBaseDatos:
    colector = ColectorBaseDatos(motor)
    registro.register(colector)
    return colector
