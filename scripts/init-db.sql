-- =============================================================================
-- init-db.sql: esquema consolidado de las bases relacionales del sistema.
-- ES UNA COPIA DE REFERENCIA: Docker Compose monta los init.sql de cada servicio
-- (<servicio>/database/init.sql), que son la fuente de verdad. Si cambia un esquema,
-- cambie ese archivo y regenere este.
-- Motores distintos: las secciones 1 y 2 son MySQL 8; la 3 es PostgreSQL.
-- perfiles-service (SQLite) y notificaciones-service (SQLite) crean su esquema al arrancar;
-- vacaciones-service (SQLite) también.
-- =============================================================================

-- ---------- 1. auth-service (MySQL) ----------
CREATE DATABASE IF NOT EXISTS auth_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auth_db;

CREATE TABLE IF NOT EXISTS usuarios (
    id INT NOT NULL AUTO_INCREMENT,
    empleado_id VARCHAR(50) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NULL,
    rol VARCHAR(10) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    fecha_creacion DATETIME NOT NULL,

    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_empleado UNIQUE (empleado_id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT chk_usuarios_rol CHECK (rol IN ('ADMIN', 'USER')),
    CONSTRAINT chk_usuarios_estado CHECK (
        estado IN ('PENDIENTE_ACTIVACION', 'ACTIVA',
                   'SUSPENDIDA_TEMPORAL', 'DESACTIVADA_PERMANENTE')
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eventos_procesados (
    id VARCHAR(100) NOT NULL,
    procesado_en DATETIME NOT NULL,
    CONSTRAINT pk_eventos_procesados PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- 2. empleados-service (MySQL) ----------
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

    departamento_validado BOOLEAN NOT NULL DEFAULT TRUE,

    fecha_retiro DATETIME NULL,

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

-- ---------- 3. departamentos-service (PostgreSQL) ----------
CREATE TABLE IF NOT EXISTS departamentos (

                                             id VARCHAR(50) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,

    CONSTRAINT pk_departamentos
    PRIMARY KEY (id)

    );

