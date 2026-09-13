CREATE DATABASE IF NOT EXISTS empleados_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE empleados_db;

CREATE TABLE IF NOT EXISTS empleados (

    id INT NOT NULL AUTO_INCREMENT,

    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,

    email VARCHAR(150) NOT NULL,
    numero_empleado VARCHAR(50) NOT NULL,

    cargo VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,

    departamento_id VARCHAR(50) NOT NULL,

    fecha_ingreso DATE NOT NULL,

    estado VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',

    CONSTRAINT pk_empleados
        PRIMARY KEY (id),

    CONSTRAINT uk_empleados_email
        UNIQUE (email),

    CONSTRAINT uk_empleados_numero
        UNIQUE (numero_empleado),

    CONSTRAINT chk_empleados_estado
        CHECK (
            estado IN (
                'ACTIVO',
                'EN_VACACIONES',
                'RETIRADO'
            )
        )

) ENGINE=InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_empleados_departamento
    ON empleados(departamento_id);

CREATE INDEX idx_empleados_estado
    ON empleados(estado);
