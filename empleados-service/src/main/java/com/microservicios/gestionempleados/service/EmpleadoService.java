package com.microservicios.gestionempleados.service;

import com.microservicios.gestionempleados.client.DepartamentoValidador;
import com.microservicios.gestionempleados.client.DepartamentoValidacionResultado;
import com.microservicios.gestionempleados.eventos.EventoPublisher;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contiene la lógica de negocio relacionada con los empleados.
 */
@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final DepartamentoValidador departamentoValidador;
    private final EventoPublisher eventoPublisher;

    public EmpleadoService(
            EmpleadoRepository empleadoRepository,
            DepartamentoValidador departamentoValidador,
            EventoPublisher eventoPublisher
    ) {
        this.empleadoRepository = empleadoRepository;
        this.departamentoValidador = departamentoValidador;
        this.eventoPublisher = eventoPublisher;
    }

    /**
     * Registra un nuevo empleado. Publica empleado.creado tras
     * persistir exitosamente.
     */
    public Empleado crearEmpleado(Empleado empleado) {
        if (empleadoRepository.existsByEmail(empleado.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        if (empleadoRepository.existsByNumeroEmpleado(empleado.getNumeroEmpleado())) {
            throw new IllegalArgumentException("El numeroEmpleado ya está registrado");
        }

        DepartamentoValidacionResultado resultado =
                departamentoValidador.consultarExistencia(empleado.getDepartamentoId());

        switch (resultado) {
            case NO_EXISTE -> throw new IllegalArgumentException(
                    "El departamentoId '" + empleado.getDepartamentoId() + "' no existe"
            );
            case INDETERMINADO -> empleado.setDepartamentoValidado(false);
            case EXISTE -> empleado.setDepartamentoValidado(true);
        }

        if (empleado.getEstado() == null) {
            empleado.setEstado(EstadoEmpleado.ACTIVO);
        }

        Empleado guardado = empleadoRepository.save(empleado);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", String.valueOf(guardado.getId()));
        data.put("nombre", guardado.getNombre());
        data.put("apellido", guardado.getApellido());
        data.put("email", guardado.getEmail());
        data.put("numeroEmpleado", guardado.getNumeroEmpleado());
        data.put("cargo", guardado.getCargo());
        data.put("area", guardado.getArea());
        data.put("departamentoId", guardado.getDepartamentoId());
        data.put("fechaIngreso", guardado.getFechaIngreso().toString());
        data.put("estado", guardado.getEstado().toString());
        eventoPublisher.publicar("empleado.creado", data);

        return guardado;
    }

    /**
     * Actualiza los datos editables de un empleado existente.
     * Publica empleado.actualizado tras guardar exitosamente.
     */
    public Empleado actualizarEmpleado(int id, Empleado datosActualizados) {
        Empleado empleadoExistente = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El empleado con id " + id + " no existe"
                ));

        boolean departamentoCambio = !empleadoExistente.getDepartamentoId()
                .equals(datosActualizados.getDepartamentoId());

        if (departamentoCambio) {
            DepartamentoValidacionResultado resultado =
                    departamentoValidador.consultarExistencia(datosActualizados.getDepartamentoId());

            switch (resultado) {
                case NO_EXISTE -> throw new IllegalArgumentException(
                        "El departamentoId '" + datosActualizados.getDepartamentoId() + "' no existe"
                );
                case INDETERMINADO -> empleadoExistente.setDepartamentoValidado(false);
                case EXISTE -> empleadoExistente.setDepartamentoValidado(true);
            }
        }

        empleadoExistente.setNombre(datosActualizados.getNombre());
        empleadoExistente.setApellido(datosActualizados.getApellido());
        empleadoExistente.setCargo(datosActualizados.getCargo());
        empleadoExistente.setArea(datosActualizados.getArea());
        empleadoExistente.setDepartamentoId(datosActualizados.getDepartamentoId());
        empleadoExistente.setFechaIngreso(datosActualizados.getFechaIngreso());

        Empleado actualizado = empleadoRepository.save(empleadoExistente);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", String.valueOf(actualizado.getId()));
        data.put("nombre", actualizado.getNombre());
        data.put("apellido", actualizado.getApellido());
        data.put("email", actualizado.getEmail());
        data.put("cargo", actualizado.getCargo());
        data.put("area", actualizado.getArea());
        data.put("departamentoId", actualizado.getDepartamentoId());
        eventoPublisher.publicar("empleado.actualizado", data);

        return actualizado;
    }

    /**
     * Da de baja a un empleado (baja lógica, nunca se borra).
     * Publica empleado.retirado tras guardar exitosamente.
     */
    public Empleado retirarEmpleado(int id, String motivo) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El empleado con id " + id + " no existe"
                ));

        empleado.setEstado(EstadoEmpleado.RETIRADO);
        empleado.setFechaRetiro(LocalDateTime.now());

        Empleado retirado = empleadoRepository.save(empleado);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", String.valueOf(retirado.getId()));
        data.put("email", retirado.getEmail());
        data.put("fechaRetiro", retirado.getFechaRetiro().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString() + "Z");
        data.put("motivo", motivo);
        eventoPublisher.publicar("empleado.retirado", data);

        return retirado;
    }

    public Optional<Empleado> obtenerEmpleadoPorId(int id) {
        return empleadoRepository.findById(id);
    }

    public List<Empleado> listarEmpleados() {
        return empleadoRepository.findAll();
    }

    public List<Empleado> listarPorEstado(String estado, LocalDate desde, LocalDate hasta) {
        EstadoEmpleado estadoEnum = EstadoEmpleado.valueOf(estado.toUpperCase());

        if (desde != null && hasta != null) {
            return empleadoRepository.findByEstadoAndFechaRetiroBetween(
                    estadoEnum, desde.atStartOfDay(), hasta.atTime(23, 59, 59));
        }

        return empleadoRepository.findByEstado(estadoEnum);
    }

    public void reconciliarDepartamentosPendientes() {
        List<Empleado> pendientes = empleadoRepository.findByDepartamentoValidadoFalse();

        for (Empleado empleado : pendientes) {
            DepartamentoValidacionResultado resultado =
                    departamentoValidador.consultarExistencia(empleado.getDepartamentoId());

            if (resultado == DepartamentoValidacionResultado.EXISTE) {
                empleado.setDepartamentoValidado(true);
                empleadoRepository.save(empleado);
            }
        }
    }
}