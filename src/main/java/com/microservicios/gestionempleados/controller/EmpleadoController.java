package com.microservicios.gestionempleados.controller;

import com.microservicios.gestionempleados.exception.ErrorResponse;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.service.EmpleadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empleados")
public class EmpleadoController {
    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @PostMapping
    public ResponseEntity<?> crearEmpleado(@Valid @RequestBody Empleado empleado) {
        try {
            Empleado empleadoCreado = empleadoService.crearEmpleado(empleado);
            return ResponseEntity.ok(empleadoCreado);
        } catch (IllegalArgumentException exception) {
            ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerEmpleado(@PathVariable int id) {
        return empleadoService
                .obtenerEmpleadoPorId(id)
                .map(empleado -> ResponseEntity.status(HttpStatus.OK).body((Object) empleado))
                .orElseGet(() -> {
                    ErrorResponse error = new ErrorResponse(
                            HttpStatus.NOT_FOUND.value(),
                            "El empleado con id " + id + " no existe"
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body((Object) error);
                });
    }
}
