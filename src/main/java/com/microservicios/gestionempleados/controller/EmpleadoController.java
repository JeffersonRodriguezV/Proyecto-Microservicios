package com.microservicios.gestionempleados.controller;

import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.service.EmpleadoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST encargado de exponer las operaciones
 * HTTP relacionadas con los empleados
 * a implementar: POST y GET
 */
@RestController
@RequestMapping("/empleados")
public class EmpleadoController {
    private final EmpleadoService empleadoService;
    /**
     * Constructor del controldor
     */
    public EmpleadoController (EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    /**
     * Registrar un nuevo empleado
     * ENDPOINT: POST/ empleados
     * Si el empleado se registra de forma correcta se devuelve un HTTP 200 OK junto con el empleado creado,
     * si el correo o numeroEmpleado ya existe se devuelve un HTTP 400 bad request
     * @param empleado info del empleado recibida en el cuerpo de la peticion
     * @return respuesta HTTP con el empleado creado o un mensaje de error
     */
    @PostMapping
    public ResponseEntity <?> crearEmpleado (@RequestBody Empleado empleado) {
        try {
            Empleado empleadoCreado = empleadoService.crearEmpleado(empleado);
            //200 para registro exitoso
            return ResponseEntity.ok(empleadoCreado);
        } catch (IllegalArgumentException exception) {
            //400 bad request cuando hay algun correo o numero empleado duplicado
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
        }
    }

    /**
     * Obtiene un empleado por su identificador.
     * Endpoint:
     * GET /empleados/{id}
     * Si el empleado existe se devuelve HTTP 200 OK.
     * Si no existe se devuelve HTTP 404 Not Found
     * @param id identificador del empleado
     * @return empleado encontrado o mensaje de error
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerEmpleado(
            @PathVariable int id) {
        return empleadoService
                .obtenerEmpleadoPorId(id)
                .map(empleado -> ResponseEntity
                        .status(HttpStatus.OK)
                        .body((Object) empleado))
                .orElseGet(() ->
                        ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body((Object) ("El empleado con id " + id + " no existe"))
                );
    }

}

