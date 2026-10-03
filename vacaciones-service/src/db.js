const { DatabaseSync } = require('node:sqlite');

const db = new DatabaseSync('./vacaciones.db');

db.exec(`
  CREATE TABLE IF NOT EXISTS vacaciones (
    numero INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT UNIQUE,
    empleado_id TEXT NOT NULL,
    fecha_inicio TEXT NOT NULL,
    fecha_fin TEXT NOT NULL,
    estado TEXT NOT NULL,
    fecha_creacion TEXT NOT NULL
  );
`);

module.exports = db;