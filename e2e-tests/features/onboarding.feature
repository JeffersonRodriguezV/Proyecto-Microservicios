# language: es
Característica: Onboarding de un empleado nuevo
  Registrar un empleado dispara eventos que crean su perfil, su cuenta y su correo de bienvenida.

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que estoy autenticado como "ADMIN"

  Escenario: Registrar un empleado genera perfil y notificación de bienvenida
    Dado que se registra un empleado nuevo con datos únicos
    Entonces el empleado debe quedar registrado con estado "ACTIVO"
    Y eventualmente debe existir el perfil del empleado
    Y eventualmente el empleado debe tener una notificación que contenga "Para establecer su contraseña"

  Escenario: Antes de activar la cuenta no se puede iniciar sesión
    Dado que se registra un empleado nuevo con datos únicos
    Entonces eventualmente el empleado debe tener una notificación de tipo "SEGURIDAD"
    Y el empleado no debe poder iniciar sesión

  Escenario: Activar la cuenta con el token recibido permite iniciar sesión
    Dado que se registra un empleado nuevo con datos únicos
    Cuando activo la cuenta con el token recibido por notificación
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado debe poder iniciar sesión
    Y eventualmente el empleado debe tener una notificación que contenga "Ya puede iniciar sesión"

  Esquema del escenario: No se registra un empleado con datos inválidos
    Cuando intento registrar un empleado con <campo> "<valor>"
    Entonces la respuesta debe tener código 400

    Ejemplos:
      | campo  | valor        |
      | email  | no-es-email  |
      | nombre |              |