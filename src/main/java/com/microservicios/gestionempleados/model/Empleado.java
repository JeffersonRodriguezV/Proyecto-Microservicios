package com.microservicios.gestionempleados.model;

import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String numeroEmpleado;

    /**
     * Cargo que desempeña el empleado.
     */
    @Column(nullable = false)
    private String cargo;

    /**
     * Área a la que pertenece el empleado.
     */
    @Column(nullable = false)
    private String area;

    /**
     * Identificador del departamento al que pertenece el empleado.
     */
    @Column(name = "departamento_id", nullable = false)
    private String departamentoId;

    /**
     * Fecha en la que el empleado ingresó a la organización.
     */
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    /**
     * Estado actual del empleado.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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
