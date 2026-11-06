package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func llamarHealth(t *testing.T) (int, RespuestaHealth) {
	t.Helper()
	rec := httptest.NewRecorder()
	manejarHealth(rec, httptest.NewRequest(http.MethodGet, "/health", nil))
	var r RespuestaHealth
	if err := json.Unmarshal(rec.Body.Bytes(), &r); err != nil {
		t.Fatalf("respuesta no es JSON: %v", err)
	}
	return rec.Code, r
}

func TestHealthUpCuandoBdYBrokerEstanArriba(t *testing.T) {
	prepararDB(t)
	brokerConectado.Store(true)
	t.Cleanup(func() { brokerConectado.Store(false) })

	codigo, r := llamarHealth(t)
	if codigo != 200 || r.Status != "UP" || r.Service != "notificaciones-service" {
		t.Fatalf("esperaba 200/UP, obtuve %d/%s", codigo, r.Status)
	}
	if r.Checks["database"] != "UP" || r.Checks["messageBroker"] != "UP" {
		t.Errorf("checks inesperados: %v", r.Checks)
	}
}

func TestHealthDownSiElBrokerNoEstaConectado(t *testing.T) {
	prepararDB(t)
	brokerConectado.Store(false)

	codigo, r := llamarHealth(t)
	if codigo != 503 || r.Status != "DOWN" {
		t.Fatalf("esperaba 503/DOWN, obtuve %d/%s", codigo, r.Status)
	}
	if r.Checks["database"] != "UP" || r.Checks["messageBroker"] != "DOWN" {
		t.Errorf("checks inesperados: %v", r.Checks)
	}
}

func TestHealthDownSiLaBaseDeDatosNoResponde(t *testing.T) {
	prepararDB(t)
	brokerConectado.Store(true)
	t.Cleanup(func() { brokerConectado.Store(false) })
	db.Close() // simula caída de la BD

	codigo, r := llamarHealth(t)
	if codigo != 503 || r.Checks["database"] != "DOWN" {
		t.Fatalf("esperaba 503 con database DOWN, obtuve %d %v", codigo, r.Checks)
	}
}
