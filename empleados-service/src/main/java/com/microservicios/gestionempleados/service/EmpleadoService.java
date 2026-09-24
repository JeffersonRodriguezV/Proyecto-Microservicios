package com.microservicios.gestionempleados.service;

import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import com.microservicios.gestionempleados.client.DepartamentoValidador;
import com.microservicios.gestionempleados.client.DepartamentoValidacionResultado;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Contiene la lógica de negocio relacionada con los empleados.
 */
@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final DepartamentoValidador departamentoValidador;

    /**
     * Constructor del servicio.
     *
     * @param empleadoRepository repositorio de empleados
     * @param departamentoValidador cliente hacia departamentos-service,
     *                               con retry + Circuit Breaker
     */
    public EmpleadoService(EmpleadoRepository empleadoRepository, DepartamentoValidador departamentoValidador) {
        this.empleadoRepository = empleadoRepository;
        this.departamentoValidador = departamentoValidador;
    }

    /**
     * Registra un nuevo empleado
     * Antes de guardar el empleado se valida que no exista
     * otro empleado con el mismo correo electrónico ni con
     * el mismo número empresarial, y que el departamento
     * indicado exista.
     * @param empleado empleado que se desea registrar
     * @return empleado guardado
     * @throws IllegalArgumentException si el email o el
     *  numeroEmpleado ya existen, o si el departamento no existe
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
         * Verificamos que el departamento exista, consultando a
         * departamentos-service a través del Circuit Breaker. Tres
         * resultados posibles:
         * - EXISTE: se confirma, el empleado se marca como validado.
         * - NO_EXISTE: respuesta definitiva (404 real) -> se rechaza con 400.
         * - INDETERMINADO: no se pudo determinar (reintentos agotados,
         *   o el circuito ya está abierto) -> se acepta el empleado,
         *   pendiente de validación.
         */
        DepartamentoValidacionResultado resultado =
                departamentoValidador.consultarExistencia(empleado.getDepartamentoId());

        switch (resultado) {
            case NO_EXISTE -> throw new IllegalArgumentException(
                    "El departamentoId '" + empleado.getDepartamentoId() + "' no existe"
            );
            case INDETERMINADO -> empleado.setDepartamentoValidado(false);
            case EXISTE -> empleado.setDepartamentoValidado(true);
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
    public Optional<Empleado> obtenerEmpleadoPorId(int id) {
        return empleadoRepository.findById(id);
    }

    /**
     * Lista todos los empleados registrados.
     *
     * @return lista completa de empleados
     */
    public List<Empleado> listarEmpleados() {
        return empleadoRepository.findAll();
    }

}