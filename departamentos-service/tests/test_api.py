"""Pruebas de integración de la API (camino feliz de todas las operaciones + errores)."""

DEP = {"id": "IT", "nombre": "Tecnología", "descripcion": "Área de TI"}


def test_crear_departamento_responde_201(client):
    r = client.post("/departamentos", json=DEP)
    assert r.status_code == 201
    assert r.json() == DEP


def test_crear_duplicado_responde_400(client):
    client.post("/departamentos", json=DEP)
    r = client.post("/departamentos", json=DEP)
    assert r.status_code == 400
    assert "ya está registrado" in r.json()["mensaje"]


def test_datos_invalidos_responden_400_con_formato_uniforme(client):
    r = client.post("/departamentos", json={"id": "", "nombre": "x"})
    assert r.status_code == 400
    cuerpo = r.json()
    assert cuerpo["status"] == 400 and "timestamp" in cuerpo


def test_obtener_departamento_existente(client):
    client.post("/departamentos", json=DEP)
    r = client.get("/departamentos/IT")
    assert r.status_code == 200
    assert r.json()["nombre"] == "Tecnología"


def test_obtener_inexistente_responde_404(client):
    r = client.get("/departamentos/NOPE")
    assert r.status_code == 404
    assert r.json()["status"] == 404


def test_listar_departamentos(client):
    client.post("/departamentos", json=DEP)
    client.post("/departamentos", json={"id": "RRHH", "nombre": "RRHH", "descripcion": "Personas"})
    r = client.get("/departamentos")
    assert r.status_code == 200
    assert {d["id"] for d in r.json()} == {"IT", "RRHH"}


def test_error_inesperado_responde_500_json(client, monkeypatch):
    from app.services import departamento_service as servicio

    def explota(db):
        raise RuntimeError("boom")

    monkeypatch.setattr(servicio, "listar_departamentos", explota)
    r = client.get("/departamentos")
    assert r.status_code == 500
    assert r.json()["status"] == 500


def test_openapi_documenta_bearer_y_rutas(client):
    r = client.get("/departamentos/openapi.json")
    assert r.status_code == 200
    esquema = r.json()
    assert "BearerAuth" in esquema["components"]["securitySchemes"]
    assert "/departamentos" in esquema["paths"] and "/health" in esquema["paths"]
    # segunda llamada: usa el esquema cacheado
    assert client.get("/departamentos/openapi.json").status_code == 200


def test_swagger_ui_disponible(client):
    assert client.get("/departamentos/docs").status_code == 200


def test_raiz_responde_up(client):
    r = client.get("/")
    assert r.status_code == 200 and r.json()["status"] == "UP"
