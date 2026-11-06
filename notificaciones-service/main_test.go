package main

import (
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

// prepararDB deja la variable global db apuntando a un SQLite temporal limpio.
func prepararDB(t *testing.T) {
	t.Helper()
	t.Setenv("DB_PATH", filepath.Join(t.TempDir(), "test.db"))
	inicializarDB()
	t.Cleanup(func() { db.Close() })
}

func TestObtenerEnv(t *testing.T) {
	t.Setenv("MI_VARIABLE", "valor")
	if got := obtenerEnv("MI_VARIABLE", "defecto"); got != "valor" {
		t.Errorf("esperaba 'valor', obtuve %q", got)
	}
	if got := obtenerEnv("NO_EXISTE_XYZ", "defecto"); got != "defecto" {
		t.Errorf("esperaba 'defecto', obtuve %q", got)
	}
}

func TestInicializarDBCreaEsquema(t *testing.T) {
	prepararDB(t)
	for _, tabla := range []string{"notificaciones", "eventos_procesados"} {
		var n int
		err := db.QueryRow("SELECT COUNT(*) FROM " + tabla).Scan(&n)
		if err != nil {
			t.Errorf("la tabla %s no existe: %v", tabla, err)
		}
	}
}

func TestConstruirMuxRegistraTodasLasRutas(t *testing.T) {
	prepararDB(t)
	mux := construirMux()
	for _, ruta := range []string{"/notificaciones", "/notificaciones/docs", "/notificaciones/openapi.json", "/health"} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, ruta, nil))
		if rec.Code == http.StatusNotFound {
			t.Errorf("la ruta %s no está registrada", ruta)
		}
	}
}

func TestDocsYOpenAPI(t *testing.T) {
	rec := httptest.NewRecorder()
	servirOpenAPI(rec, httptest.NewRequest(http.MethodGet, "/notificaciones/openapi.json", nil))
	if rec.Code != 200 || rec.Header().Get("Content-Type") != "application/json" || rec.Body.Len() == 0 {
		t.Errorf("openapi.json inválido: código %d", rec.Code)
	}
	rec = httptest.NewRecorder()
	servirSwaggerUI(rec, httptest.NewRequest(http.MethodGet, "/notificaciones/docs", nil))
	if rec.Code != 200 || rec.Body.Len() == 0 {
		t.Errorf("Swagger UI inválido: código %d", rec.Code)
	}
}


