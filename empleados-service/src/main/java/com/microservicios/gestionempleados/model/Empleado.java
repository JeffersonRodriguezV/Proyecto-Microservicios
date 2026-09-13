package com.microservicios.gestionempleados.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener un formato válido")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "El numeroEmpleado es obligatorio")
    @Column(name = "numero_empleado", nullable = false, unique = true, length = 50)
    private String numeroEmpleado;

    @NotBlank(message = "El cargo es obligatorio")
    @Column(name = "cargo", nullable = false, length = 100)
    private String cargo;

    @NotBlank(message = "El area es obligatoria")
    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @NotBlank(message = "El departamentoId es obligatorio")
    @Column(name = "departamento_id", nullable = false, length = 50)
    private String departamentoId;

    @NotNull(message = "La fechaIngreso es obligatoria")
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoEmpleado estado;

    /**
     * Constructor completo.
     */
    public Empleado(
            Integer id,
            String nombre,
            String apellido,
            String email,
            String numeroEmpleado,
            String cargo,
            String area,
            String departamentoId,
            LocalDate fechaIngreso,
            EstadoEmpleado estado
    ) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.numeroEmpleado = numeroEmpleado;
        this.cargo = cargo;
        this.area = area;
        this.departamentoId = departamentoId;
        this.fechaIngreso = fechaIngreso;
        this.estado = estado;
    }

    /**
     * Constructor vacío requerido por JPA.
     */
    public Empleado() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNumeroEmpleado() {
        return numeroEmpleado;
    }

    public void setNumeroEmpleado(String numeroEmpleado) {
        this.numeroEmpleado = numeroEmpleado;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getDepartamentoId() {
        return departamentoId;
    }

    public void setDepartamentoId(String departamentoId) {
        this.departamentoId = departamentoId;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public EstadoEmpleado getEstado() {
        return estado;
    }

    public void setEstado(EstadoEmpleado estado) {
        this.estado = estado;
    }
}
