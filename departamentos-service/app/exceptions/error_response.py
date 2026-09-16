"""
Formato uniforme de respuesta de error.
"""

from datetime import datetime


def build_error_body(status: int, mensaje: str) -> dict:
    return {
        "status": status,
        "mensaje": mensaje,
        "timestamp": datetime.now().isoformat(timespec="seconds"),
    }


