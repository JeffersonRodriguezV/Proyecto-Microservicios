package com.microservicios.gestionempleados.controller;

import com.microservicios.gestionempleados.exception.ErrorResponse;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.service.EmpleadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/empleados")
@Tag(name = "Empleados", description = "Registro y consulta de empleados")
public class EmpleadoController {
    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @Operation(
            summary = "Registrar un nuevo empleado",
            description = "Valida unicidad de email y numeroEmpleado, y la existencia " +
                    "del departamentoId contra departamentos-service antes de registrar."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado registrado correctamente",
                    content = @Content(schema = @Schema(implementation = Empleado.class))),
            @ApiResponse(responseCode = "400", description = "Email/numeroEmpleado duplicado, " +
                    "departamento inexistente, o datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarEmpleado(@PathVariable int id, @Valid @RequestBody Empleado empleado) {
        try {
            Empleado actualizado = empleadoService.actualizarEmpleado(id, empleado);
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException exception) {
            HttpStatus status = exception.getMessage().contains("no existe")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            ErrorResponse error = new ErrorResponse(status.value(), exception.getMessage());
            return ResponseEntity.status(status).body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> retirarEmpleado(
            @PathVariable int id,
            @RequestParam(required = false, defaultValue = "RENUNCIA") String motivo
    ) {
        try {
            Empleado retirado = empleadoService.retirarEmpleado(id, motivo);
            return ResponseEntity.ok(retirado);
        } catch (IllegalArgumentException exception) {
            ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @Operation(summary = "Consultar un empleado por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado encontrado",
                    content = @Content(schema = @Schema(implementation = Empleado.class))),
            @ApiResponse(responseCode = "404", description = "No existe un empleado con ese id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    @Operation(summary = "Listar todos los empleados registrados")
    @ApiResponse(responseCode = "200", description = "Lista de empleados (puede estar vacía)")
    @GetMapping
    public ResponseEntity<?> listarEmpleados(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        if (estado != null) {
            return ResponseEntity.ok(empleadoService.listarPorEstado(estado, desde, hasta));
        }
        return ResponseEntity.ok(empleadoService.listarEmpleados());
    }
}