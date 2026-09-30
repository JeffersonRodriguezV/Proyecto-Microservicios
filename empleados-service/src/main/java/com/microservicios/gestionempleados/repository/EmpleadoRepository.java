package com.microservicios.gestionempleados.repository;

import com.microservicios.gestionempleados.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}
