package com.microservicios.gestionempleados.repository;

import com.microservicios.gestionempleados.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
