# Ciclo de vida de la cuenta (Reto 5)

La cuenta de acceso de un empleado la gobiernan los eventos del ecosistema. El `auth-service` consume cuatro
(`empleado.creado`, `empleado.retirado`, `vacaciones.iniciadas`, `vacaciones.finalizadas`) y publica los que
consume `notificaciones-service`.

## Estados de la cuenta

```mermaid
stateDiagram-v2
    [*] --> PENDIENTE_ACTIVACION: empleado.creado
    PENDIENTE_ACTIVACION --> ACTIVA: reset-password con el token de activacion
    ACTIVA --> SUSPENDIDA_TEMPORAL: vacaciones.iniciadas
    SUSPENDIDA_TEMPORAL --> ACTIVA: vacaciones.finalizadas
    PENDIENTE_ACTIVACION --> DESACTIVADA_PERMANENTE: empleado.retirado
    ACTIVA --> DESACTIVADA_PERMANENTE: empleado.retirado
    SUSPENDIDA_TEMPORAL --> DESACTIVADA_PERMANENTE: empleado.retirado
    DESACTIVADA_PERMANENTE --> [*]
```

`DESACTIVADA_PERMANENTE` es terminal: ningún evento posterior la reactiva. Por eso se modela como estado propio y no como un booleano.

## 1. Alta y activación

```mermaid
sequenceDiagram
    autonumber
    actor RRHH
    actor Emp as Empleado
    participant EMP as empleados-service
    participant MQ as RabbitMQ
    participant AUTH as auth-service
    participant NOT as notificaciones-service

    RRHH->>EMP: POST /empleados
    EMP->>MQ: empleado.creado
    MQ->>AUTH: empleado.creado
    AUTH->>AUTH: crea la cuenta PENDIENTE_ACTIVACION sin contrasena
    AUTH->>MQ: usuario.creado con email y tokenActivacion
    MQ->>NOT: usuario.creado
    NOT-->>Emp: correo de bienvenida con el token (simulado en log)
    Emp->>AUTH: POST /auth/reset-password con token y nueva contrasena
    AUTH->>AUTH: PENDIENTE_ACTIVACION pasa a ACTIVA
    AUTH->>MQ: cuenta.activada con motivo ACTIVACION_INICIAL
    MQ->>NOT: cuenta.activada
```

## 2. Vacaciones: suspensión y reactivación

```mermaid
sequenceDiagram
    autonumber
    actor RRHH
    participant VAC as vacaciones-service
    participant MQ as RabbitMQ
    participant AUTH as auth-service
    participant NOT as notificaciones-service

    RRHH->>VAC: POST /vacaciones
    VAC->>MQ: vacaciones.programadas
    MQ->>NOT: vacaciones.programadas
    Note over VAC: el scheduler revisa cada minuto
    VAC->>VAC: llega la fechaInicio, PROGRAMADA pasa a EN_CURSO
    VAC->>MQ: vacaciones.iniciadas
    MQ->>AUTH: vacaciones.iniciadas
    AUTH->>AUTH: ACTIVA pasa a SUSPENDIDA_TEMPORAL
    AUTH->>MQ: cuenta.desactivada con VACACIONES y permanente false
    MQ->>NOT: cuenta.desactivada
    Note over AUTH: el login falla mientras dure la suspension
    VAC->>VAC: pasa la fechaFin, EN_CURSO pasa a FINALIZADA
    VAC->>MQ: vacaciones.finalizadas
    MQ->>AUTH: vacaciones.finalizadas
    AUTH->>AUTH: SUSPENDIDA_TEMPORAL vuelve a ACTIVA
    AUTH->>MQ: cuenta.activada con motivo FIN_VACACIONES
    MQ->>NOT: cuenta.activada
```

## 3. Caso borde: retiro durante las vacaciones

Si el empleado es retirado mientras está de vacaciones, al llegar la `fechaFin` el evento `vacaciones.finalizadas`
**no** debe reactivar la cuenta.

```mermaid
sequenceDiagram
    autonumber
    participant EMP as empleados-service
    participant VAC as vacaciones-service
    participant MQ as RabbitMQ
    participant AUTH as auth-service

    VAC->>MQ: vacaciones.iniciadas
    MQ->>AUTH: vacaciones.iniciadas
    AUTH->>AUTH: ACTIVA pasa a SUSPENDIDA_TEMPORAL
    EMP->>MQ: empleado.retirado
    MQ->>AUTH: empleado.retirado
    AUTH->>AUTH: pasa a DESACTIVADA_PERMANENTE
    AUTH->>MQ: cuenta.desactivada con RETIRO y permanente true
    VAC->>MQ: vacaciones.finalizadas
    MQ->>AUTH: vacaciones.finalizadas
    Note over AUTH: la cuenta es DESACTIVADA_PERMANENTE, no se reactiva y queda registrado en el log
```

