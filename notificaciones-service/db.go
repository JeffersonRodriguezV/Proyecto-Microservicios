package main

import (
	"database/sql"
	"log"

	_ "modernc.org/sqlite"
)

var db *sql.DB

func inicializarDB() {
	var err error
	db, err = sql.Open("sqlite", obtenerEnv("DB_PATH", "./notificaciones.db"))
	if err != nil {
		log.Fatal("No se pudo abrir la base de datos:", err)
	}

	esquema := `
	CREATE TABLE IF NOT EXISTS notificaciones (
		id TEXT PRIMARY KEY,
		tipo TEXT NOT NULL,
		destinatario TEXT NOT NULL,
		mensaje TEXT NOT NULL,
		fecha_envio TEXT NOT NULL,
		empleado_id TEXT NOT NULL
	);

	CREATE TABLE IF NOT EXISTS eventos_procesados (
		id TEXT PRIMARY KEY,
		procesado_en TEXT NOT NULL
	);
	`

	if _, err := db.Exec(esquema); err != nil {
		log.Fatal("No se pudo crear el esquema:", err)
	}

	log.Println("Base de datos SQLite lista")
}