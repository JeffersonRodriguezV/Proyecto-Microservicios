const { DatabaseSync } = require('node:sqlite');

const db = new DatabaseSync(process.env.DB_PATH || './vacaciones.db');
db.exec(`
    CREATE TABLE IF NOT EXISTS vacaciones (
                                              numero INTEGER PRIMARY KEY AUTOINCREMENT,
                                              id TEXT UNIQUE,
                                              empleado_id TEXT NOT NULL,
                                              fecha_inicio TEXT NOT NULL,
                                              fecha_fin TEXT NOT NULL,
                                              estado TEXT NOT NULL,
                                              fecha_creacion TEXT NOT NULL,
                                              email TEXT
    );
`);

// Migración Reto 5: el scheduler publica eventos con el email del empleado (Catálogo 3.9 y 3.10),
// así que se guarda al programar el período. En bases creadas antes del Reto 5 la columna no existe:
// se agrega sin perder datos.
const columnas = db.prepare('PRAGMA table_info(vacaciones)').all().map((c) => c.name);
if (!columnas.includes('email')) {
    db.exec('ALTER TABLE vacaciones ADD COLUMN email TEXT');
}

module.exports = db;