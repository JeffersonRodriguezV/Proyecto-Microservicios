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