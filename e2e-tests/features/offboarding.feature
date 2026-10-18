# language: es
Característica: Offboarding de un empleado
  Retirar a un empleado desactiva su cuenta, notifica y lo deja en la auditoría.

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que estoy autenticado como "ADMIN"
    Y que existe un empleado con la cuenta activada

  Escenario: Desvinculación completa genera la notificación
    Cuando retiro al empleado
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado debe tener una notificación de tipo "DESVINCULACION"
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada de forma permanente"

  Escenario: El empleado retirado no puede iniciar sesión
    Cuando retiro al empleado
    Entonces el empleado no debe poder iniciar sesión
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada de forma permanente"

  Escenario: La recuperación de contraseña no funciona para un empleado retirado
    Cuando retiro al empleado
    Y eventualmente el empleado debe tener una notificación que contenga "desactivada de forma permanente"
    Y solicito recuperar la contraseña del empleado
    Entonces no debe llegar ninguna notificación con enlace de restablecimiento

  Escenario: El empleado retirado aparece en la auditoría
    Cuando retiro al empleado
    Y consulto la auditoría de empleados retirados
    Entonces la respuesta debe tener código 200
    Y la auditoría debe incluir al empleado con su fecha de retiro