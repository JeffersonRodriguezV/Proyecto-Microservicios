package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func sembrar(t *testing.T) {
	t.Helper()
	guardarNotificacion(db, Notificacion{ID: "1", Tipo: "VACACIONES", Destinatario: "a@x.com", Mensaje: "m1", FechaEnvio: "2026-01-01T00:00:00Z", EmpleadoID: "E1"})
	guardarNotificacion(db, Notificacion{ID: "2", Tipo: "CUENTA", Destinatario: "b@x.com", Mensaje: "m2", FechaEnvio: "2026-01-02T00:00:00Z", EmpleadoID: "E2"})
	guardarNotificacion(db, Notificacion{ID: "3", Tipo: "SEGURIDAD", Destinatario: "a@x.com", Mensaje: "m3", FechaEnvio: "2026-01-03T00:00:00Z", EmpleadoID: "E1"})
}

func consultar(t *testing.T, ruta string) []Notificacion {
	t.Helper()
	rec := httptest.NewRecorder()
	construirMux().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, ruta, nil))
	if rec.Code != 200 {
		t.Fatalf("GET %s devolvió %d", ruta, rec.Code)
	}
	var lista []Notificacion
	if err := json.Unmarshal(rec.Body.Bytes(), &lista); err != nil {
		t.Fatalf("respuesta no es una lista JSON: %v", err)
	}
	return lista
}

func TestListarTodasOrdenadasPorFechaDescendente(t *testing.T) {
	prepararDB(t)
	sembrar(t)
	lista := consultar(t, "/notificaciones")
	if len(lista) != 3 || lista[0].ID != "3" || lista[2].ID != "1" {
		t.Errorf("orden o cantidad inesperados: %+v", lista)
	}
}

func TestListarPorEmpleado(t *testing.T) {
	prepararDB(t)
	sembrar(t)
	lista := consultar(t, "/notificaciones/E1")
	if len(lista) != 2 {
		t.Fatalf("esperaba 2 notificaciones de E1, obtuve %d", len(lista))
	}
	for _, n := range lista {
		if n.EmpleadoID != "E1" {
			t.Errorf("notificación de otro empleado: %+v", n)
		}
	}
}

func TestEmpleadoSinNotificacionesDevuelveListaVacia(t *testing.T) {
	prepararDB(t)
	lista := consultar(t, "/notificaciones/NADIE")
	if lista == nil || len(lista) != 0 {
		t.Errorf("esperaba lista vacía, obtuve %+v", lista)
	}
}

func TestErrorDeBaseDeDatosDevuelve500(t *testing.T) {
	prepararDB(t)
	db.Close()
	for _, ruta := range []string{"/notificaciones", "/notificaciones/E1"} {
		rec := httptest.NewRecorder()
		construirMux().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, ruta, nil))
		if rec.Code != http.StatusInternalServerError {
			t.Errorf("%s: esperaba 500, obtuve %d", ruta, rec.Code)
		}
	}
}
