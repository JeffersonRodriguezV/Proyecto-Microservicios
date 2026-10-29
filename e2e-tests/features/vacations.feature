# language: es
Característica: Vacaciones de un empleado
  Programar vacaciones, sus validaciones y su efecto sobre la cuenta del empleado.

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que estoy autenticado como "ADMIN"

  Escenario: Programar vacaciones válidas
    Dado que se registra un empleado nuevo con datos únicos
    Cuando programo vacaciones del día 10 al día 15 contando desde hoy
    Entonces la respuesta debe tener código 201
    Y el período de vacaciones debe estar en estado "PROGRAMADA"
    Y eventualmente el empleado debe tener una notificación que contenga "falso"

  Esquema del escenario: Se rechazan las fechas inválidas
    Dado que se registra un empleado nuevo con datos únicos
    Cuando programo vacaciones del día <desde> al día <hasta> contando desde hoy
    Entonces la respuesta debe tener código 400
    Y el mensaje de error debe contener "<mensaje>"

    Ejemplos:
      | desde | hasta | mensaje                                  |
      | 10    | 5     | fechaFin no puede ser anterior           |
      | -3    | 5     | fechaInicio no puede ser una fecha pasada |

  Escenario: Se rechaza un período que se cruza con otro del mismo empleado
    Dado que se registra un empleado nuevo con datos únicos
    Y que el empleado tiene vacaciones programadas del día 10 al día 15 contando desde hoy
    Cuando programo vacaciones del día 15 al día 20 contando desde hoy
    Entonces la respuesta debe tener código 400
    Y el mensaje de error debe contener "se cruza"

  Escenario: Se rechaza programar vacaciones de un empleado que no existe
    Cuando programo vacaciones para un empleado que no existe
    Entonces la respuesta debe tener código 400
    Y el mensaje de error debe contener "no existe"

  Escenario: Ciclo completo: las vacaciones suspenden y reactivan la cuenta
    Dado que existe un empleado con la cuenta activada
    Y que el empleado tiene vacaciones programadas del día 10 al día 15 contando desde hoy
    Cuando se fuerza el inicio de las vacaciones
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada temporalmente"
    Y el empleado no debe poder iniciar sesión
    Cuando se fuerza el fin de las vacaciones
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado debe tener una notificación que contenga "Bienvenido de regreso"
    Y eventualmente el empleado debe poder iniciar sesión

  Escenario: Un empleado retirado durante sus vacaciones no se reactiva al terminar
    Dado que existe un empleado con la cuenta activada
    Y que el empleado tiene vacaciones programadas del día 10 al día 15 contando desde hoy
    Cuando se fuerza el inicio de las vacaciones
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada temporalmente"
    Y retiro al empleado
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada de forma permanente"
    Y se fuerza el fin de las vacaciones
    Entonces el empleado no debe poder iniciar sesión durante unos segundos
    Y eventualmente el empleado no debe tener una notificación que contenga "Bienvenido de regreso"