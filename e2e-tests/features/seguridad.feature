# language: es
Característica: Seguridad y control de acceso
  Como sistema de autenticación
  Quiero controlar el acceso a los recursos
  Para garantizar que solo los usuarios autorizados realicen operaciones

  Antecedentes:
    Dado que el sistema está desplegado y operativo

  # --- Sin credenciales o con credenciales inválidas: 401 ---

  Escenario: Acceso denegado sin token de autenticación
    Cuando consulto la lista de empleados sin token de autenticación
    Entonces la respuesta debe tener código 401

  Esquema del escenario: Acceso denegado con un token inválido o malformado
    Cuando consulto la lista de empleados con el token "<token>"
    Entonces la respuesta debe tener código 401

    Ejemplos:
      | token                                   |
      | abc                                     |
      | esto.no.es-un-jwt                       |
      | eyJhbGciOiJIUzI1NiJ9.e30.firma-invalida |

  Escenario: Acceso denegado con un token real cuya firma fue alterada
    Dado que estoy autenticado como "ADMIN"
    Y altero la firma de mi token
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 401

  # --- Rol USER: puede consultar, no puede modificar (403) ---

  Escenario: Un usuario USER puede consultar recursos
    Dado que estoy autenticado como "USER"
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 200

  Esquema del escenario: Un usuario USER no puede modificar recursos
    Dado que estoy autenticado como "USER"
    Cuando envío una solicitud <metodo> a "<ruta>"
    Entonces la respuesta debe tener código 403

    Ejemplos:
      | metodo | ruta                |
      | POST   | /empleados          |
      | PUT    | /empleados/999999   |
      | DELETE | /empleados/999999   |
      | POST   | /departamentos      |
      | POST   | /vacaciones         |

  Escenario: Un usuario USER solo puede editar su propio perfil
    Dado que estoy autenticado como "USER"
    Cuando intento actualizar el perfil de otro empleado
    Entonces la respuesta debe tener código 403
    Cuando intento actualizar mi propio perfil
    Entonces la solicitud no debe ser rechazada por seguridad

  # --- Rol ADMIN: puede realizar todas las operaciones ---

  Escenario: Un usuario ADMIN puede crear y retirar un empleado
    Dado que estoy autenticado como "ADMIN"
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 200
    Cuando creo un departamento con datos únicos
    Entonces la respuesta debe tener código 201
    Cuando creo un empleado en ese departamento
    Entonces la respuesta debe tener código 200
    Cuando retiro a ese empleado
    Entonces la respuesta debe tener código 200

  Esquema del escenario: Un usuario ADMIN no es rechazado por seguridad en operaciones de escritura
    Dado que estoy autenticado como "ADMIN"
    Cuando envío una solicitud <metodo> a "<ruta>"
    Entonces la solicitud no debe ser rechazada por seguridad

    Ejemplos:
      | metodo | ruta                |
      | PUT    | /empleados/999999   |
      | DELETE | /empleados/999999   |
      | POST   | /vacaciones         |
