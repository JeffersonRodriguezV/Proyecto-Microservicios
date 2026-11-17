package com.microservicios.gestionempleados.repository;

import com.microservicios.gestionempleados.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import java.time.LocalDateTime;

import java.util.List;

@Repository
public interface EmpleadoRepository extends JpaRepository <Empleado, Integer >{
    /**
     * Comprueba si existe un empleado con el correo registrado
     * @param email correo para comprobar
     * @return si ya existe, retorna gfalso
     */
    boolean existsByEmail(String email);

    /**
     * Comprobar si el empleado existe con un numero empresarial indicado
     * @param numeroEmplado numero empresarial a comprobar
     * @return true si ya existe un empleado con ese numero.
     */
    boolean existsByNumeroEmpleado(String numeroEmplado);

    /**
     * Empleados cuyo departamento quedó sin confirmar (INDETERMINADO en
     * su momento). Se usa al arrancar el servicio para reintentar la
     * validación.
     * @return lista de empleados pendientes de validación
     */
    List<Empleado> findByDepartamentoValidadoFalse();

    List<Empleado> findByEstado(EstadoEmpleado estado);

    /** Cuenta empleados por estado (metricas de base de datos). */
    long countByEstado(EstadoEmpleado estado);

    /** Cuenta empleados con departamento sin confirmar (metricas de base de datos). */
    long countByDepartamentoValidadoFalse();

    /** Empleados vigentes (no RETIRADO) agrupados por departamento (metricas de base de datos). */
    @org.springframework.data.jpa.repository.Query(
            "select new com.microservicios.gestionempleados.repository.ConteoPorDepartamento("
                    + "e.departamentoId, count(e)) from Empleado e "
                    + "where e.estado <> com.microservicios.gestionempleados.model.enume.EstadoEmpleado.RETIRADO "
                    + "group by e.departamentoId")
    List<ConteoPorDepartamento> contarVigentesPorDepartamento();

    List<Empleado> findByEstadoAndFechaRetiroBetween(
            EstadoEmpleado estado, LocalDateTime desde, LocalDateTime hasta);

}
