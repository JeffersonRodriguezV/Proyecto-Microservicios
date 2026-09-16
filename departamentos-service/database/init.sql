CREATE TABLE IF NOT EXISTS departamentos (

                                             id VARCHAR(50) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,

    CONSTRAINT pk_departamentos
    PRIMARY KEY (id)

    );