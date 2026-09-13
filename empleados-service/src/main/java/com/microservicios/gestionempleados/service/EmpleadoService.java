package com.microservicios.gestionempleados.service;

import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Contiene la lógica de negocio relacionada con los empleados.
 */
@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;

    /**
     * Constructor del servicio.
     *
     * @param empleadoRepository repositorio de empleados
     */
    public EmpleadoService(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }
    /**
     * Registra un nuevo empleado
     * Antes de guardar el empleado se valida que no exista
     * otro empleado con el mismo correo electrónico ni con
     * el mismo número empresarial
     * @param empleado empleado que se desea registrar
     * @return empleado guardado
     * @throws IllegalArgumentException si el email o el
     *  numeroEmpleado ya existen
     */
    public Empleado crearEmpleado(Empleado empleado) {
        /*
         * Verificamos si el correo ya está registrado.
         */
        if (empleadoRepository.existsByEmail(empleado.getEmail())) {
            throw new IllegalArgumentException(
                    "El email ya está registrado"
            );
        }

        /*
         * Verificamos si el número empresarial ya está registrado.
         */
        if (empleadoRepository.existsByNumeroEmpleado(
                empleado.getNumeroEmpleado())) {

            throw new IllegalArgumentException(
                    "El numeroEmpleado ya está registrado"
            );
        }
        /*
         *  los nuevos empleados comienzan
         * en estado ACTIVO
         */
        if (empleado.getEstado() == null) {
            empleado.setEstado(EstadoEmpleado.ACTIVO);
        }

        /*
         * Guardamos el empleado.
         *
         * El ID se genera automáticamente mediante JPA.
         */
        return empleadoRepository.save(empleado);
    }
    /**
     * Busca un empleado por su identificador técnico.
     *
     * @param id identificador del empleado
     * @return Optional que contiene el empleado si existe
     */
    public Optional <Empleado> obtenerEmpleadoPorId (int id) {
        return empleadoRepository.findById(id);
    }

}
