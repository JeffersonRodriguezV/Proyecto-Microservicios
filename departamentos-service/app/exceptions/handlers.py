"""
Manejadores de excepciones globales.
"""

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.exceptions.error_response import build_error_body


async def handle_validation_error(request: Request, exc: RequestValidationError):
    mensaje = ", ".join(
        f"{'.'.join(str(loc) for loc in error['loc'] if loc != 'body')}: {error['msg']}"
        for error in exc.errors()
    )
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=build_error_body(status.HTTP_400_BAD_REQUEST, mensaje),
    )


async def handle_value_error(request: Request, exc: ValueError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=build_error_body(status.HTTP_400_BAD_REQUEST, str(exc)),
    )


async def handle_http_exception(request: Request, exc: StarletteHTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content=build_error_body(exc.status_code, str(exc.detail)),
    )


async def handle_unexpected_error(request: Request, exc: Exception):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=build_error_body(
            status.HTTP_500_INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado"
        ),
    )


def registrar_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(RequestValidationError, handle_validation_error)
    app.add_exception_handler(ValueError, handle_value_error)
    app.add_exception_handler(StarletteHTTPException, handle_http_exception)
    app.add_exception_handler(Exception, handle_unexpected_error)


    