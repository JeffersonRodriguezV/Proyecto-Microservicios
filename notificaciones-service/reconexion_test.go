package main

import (
	"testing"
	"time"
)

func TestSiguienteEsperaDuplicaHastaElMaximo(t *testing.T) {
	espera := time.Second
	for _, esperado := range []time.Duration{2, 4, 8, 16, 32} {
		espera = siguienteEspera(espera, time.Second)
		if espera != esperado*time.Second {
			t.Fatalf("esperaba %v y fue %v", esperado*time.Second, espera)
		}
	}
	// ya en el tope (32 s > 30 s), no crece más
	if got := siguienteEspera(espera, time.Second); got != espera {
		t.Fatalf("no debe crecer sobre el tope: %v", got)
	}
}

func TestSiguienteEsperaSeReiniciaTrasConexionEstable(t *testing.T) {
	if got := siguienteEspera(16*time.Second, 2*time.Minute); got != time.Second {
		t.Fatalf("esperaba 1s tras conexión estable y fue %v", got)
	}
}

func TestConsumirUnaVezSinBrokerDevuelveError(t *testing.T) {
	t.Setenv("RABBITMQ_HOST", "127.0.0.1")
	t.Setenv("RABBITMQ_PORT", "1") // puerto cerrado
	t.Setenv("RABBITMQ_PASSWORD", "x")
	if err := consumirUnaVez(); err == nil {
		t.Fatal("debía devolver error cuando el broker no está disponible")
	}
}
