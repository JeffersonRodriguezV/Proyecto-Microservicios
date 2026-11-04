package com.microservicios.gestionempleados.service;
import com.microservicios.gestionempleados.client.DepartamentoValidacionResultado;
import com.microservicios.gestionempleados.client.DepartamentoValidador;
import com.microservicios.gestionempleados.eventos.EventoPublisher;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la lógica de negocio de empleados.
 *
 * Se aíslan TODAS las dependencias con Mockito (repositorio, validador de
 * departamentos y publicador de eventos): no hay base de datos, ni red, ni broker.
 * Cada prueba verifica una regla de negocio, no un getter ni una anotación.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private DepartamentoValidador departamentoValidador;
    @Mock
    private EventoPublisher eventoPublisher;

    @InjectMocks
    private EmpleadoService servicio;

    // ---------- Datos de apoyo ----------

    private static Empleado nuevoEmpleado() {
        Empleado e = new Empleado();
        e.setNombre("Ana");
        e.setApellido("Pérez");
        e.setEmail("ana@empresa.com");
        e.setNumeroEmpleado("EMP-001");
        e.setCargo("Desarrolladora");
        e.setArea("TI");
        e.setDepartamentoId("DEP-1");
        e.setFechaIngreso(LocalDate.of(2024, 1, 15));
        return e;
    }

    private static Empleado empleadoPersistido(int id) {
        Empleado e = nuevoEmpleado();
        e.setId(id);
        e.setEstado(EstadoEmpleado.ACTIVO);
        e.setDepartamentoValidado(true);
        return e;
    }

    /** El repositorio "guarda" devolviendo la misma entidad, asignándole id si no lo tiene. */
    private void repositorioGuardaTalCual() {
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> {
            Empleado e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1);
            }
            return e;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturarEvento(String tipoEsperado) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(eventoPublisher).publicar(eq(tipoEsperado), captor.capture());
        return captor.getValue();
    }

    // ================= crearEmpleado =================

    @Nested
    @DisplayName("crearEmpleado")
    class CrearEmpleado {

        @Test
        @DisplayName("rechaza un email que ya está registrado y no guarda ni publica nada")
        void rechazaEmailDuplicado() {
            when(empleadoRepository.existsByEmail("ana@empresa.com")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.crearEmpleado(nuevoEmpleado()));

            assertEquals("El email ya está registrado", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher, departamentoValidador);
        }

        @Test
        @DisplayName("rechaza un numeroEmpleado que ya está registrado")
        void rechazaNumeroEmpleadoDuplicado() {
            when(empleadoRepository.existsByEmail(anyString())).thenReturn(false);
            when(empleadoRepository.existsByNumeroEmpleado("EMP-001")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.crearEmpleado(nuevoEmpleado()));

            assertEquals("El numeroEmpleado ya está registrado", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher, departamentoValidador);
        }

        @Test
        @DisplayName("rechaza un departamento que NO existe (404 confirmado) y no publica evento")
        void rechazaDepartamentoInexistente() {
            when(departamentoValidador.consultarExistencia("DEP-1"))
                    .thenReturn(DepartamentoValidacionResultado.NO_EXISTE);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.crearEmpleado(nuevoEmpleado()));

            assertEquals("El departamentoId 'DEP-1' no existe", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        @DisplayName("departamento EXISTE: se guarda como validado, estado ACTIVO por defecto")
        void creaConDepartamentoExistente() {
            when(departamentoValidador.consultarExistencia("DEP-1"))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            repositorioGuardaTalCual();

            Empleado creado = servicio.crearEmpleado(nuevoEmpleado());

            assertTrue(creado.getDepartamentoValidado());
            assertEquals(EstadoEmpleado.ACTIVO, creado.getEstado());
        }

        @Test
        @DisplayName("departamento INDETERMINADO (servicio caído): se acepta pero queda pendiente de validar")
        void creaConDepartamentoIndeterminado() {
            when(departamentoValidador.consultarExistencia("DEP-1"))
                    .thenReturn(DepartamentoValidacionResultado.INDETERMINADO);
            repositorioGuardaTalCual();

            Empleado creado = servicio.crearEmpleado(nuevoEmpleado());

            assertFalse(creado.getDepartamentoValidado());
            assertEquals(EstadoEmpleado.ACTIVO, creado.getEstado());
        }

        @Test
        @DisplayName("respeta el estado si ya viene informado")
        void noPisaElEstadoInformado() {
            when(departamentoValidador.consultarExistencia(anyString()))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            repositorioGuardaTalCual();
            Empleado entrada = nuevoEmpleado();
            entrada.setEstado(EstadoEmpleado.RETIRADO);

            Empleado creado = servicio.crearEmpleado(entrada);

            assertEquals(EstadoEmpleado.RETIRADO, creado.getEstado());
        }

        @Test
        @DisplayName("publica empleado.creado con el contrato del catálogo de eventos")
        void publicaEventoCreado() {
            when(departamentoValidador.consultarExistencia("DEP-1"))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            repositorioGuardaTalCual();

            servicio.crearEmpleado(nuevoEmpleado());

            Map<String, Object> data = capturarEvento("empleado.creado");
            assertEquals("1", data.get("empleadoId"));
            assertEquals("Ana", data.get("nombre"));
            assertEquals("Pérez", data.get("apellido"));
            assertEquals("ana@empresa.com", data.get("email"));
            assertEquals("EMP-001", data.get("numeroEmpleado"));
            assertEquals("Desarrolladora", data.get("cargo"));
            assertEquals("TI", data.get("area"));
            assertEquals("DEP-1", data.get("departamentoId"));
            assertEquals("2024-01-15", data.get("fechaIngreso"));
            assertEquals("ACTIVO", data.get("estado"));
        }

        @Test
        @DisplayName("el evento se publica DESPUÉS de persistir (nunca antes del save)")
        void publicaDespuesDePersistir() {
            when(departamentoValidador.consultarExistencia(anyString()))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            repositorioGuardaTalCual();

            servicio.crearEmpleado(nuevoEmpleado());

            org.mockito.InOrder orden = org.mockito.Mockito.inOrder(empleadoRepository, eventoPublisher);
            orden.verify(empleadoRepository).save(any(Empleado.class));
            orden.verify(eventoPublisher).publicar(eq("empleado.creado"), anyMap());
        }
    }

    // ================= actualizarEmpleado =================

    @Nested
    @DisplayName("actualizarEmpleado")
    class ActualizarEmpleado {

        @Test
        @DisplayName("error si el empleado no existe")
        void errorSiNoExiste() {
            when(empleadoRepository.findById(99)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.actualizarEmpleado(99, nuevoEmpleado()));

            assertEquals("El empleado con id 99 no existe", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        @DisplayName("si el departamento NO cambia, no se consulta al servicio de departamentos")
        void noValidaSiElDepartamentoNoCambia() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            repositorioGuardaTalCual();
            Empleado cambios = nuevoEmpleado();
            cambios.setCargo("Líder técnica");

            Empleado actualizado = servicio.actualizarEmpleado(1, cambios);

            assertEquals("Líder técnica", actualizado.getCargo());
            verifyNoInteractions(departamentoValidador);
        }

        @Test
        @DisplayName("si el departamento cambia a uno inexistente, rechaza y no guarda")
        void rechazaCambioADepartamentoInexistente() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            when(departamentoValidador.consultarExistencia("DEP-X"))
                    .thenReturn(DepartamentoValidacionResultado.NO_EXISTE);
            Empleado cambios = nuevoEmpleado();
            cambios.setDepartamentoId("DEP-X");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.actualizarEmpleado(1, cambios));

            assertEquals("El departamentoId 'DEP-X' no existe", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        @DisplayName("si el departamento cambia y la validación es INDETERMINADA, queda pendiente")
        void cambioDeDepartamentoIndeterminado() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            when(departamentoValidador.consultarExistencia("DEP-2"))
                    .thenReturn(DepartamentoValidacionResultado.INDETERMINADO);
            repositorioGuardaTalCual();
            Empleado cambios = nuevoEmpleado();
            cambios.setDepartamentoId("DEP-2");

            Empleado actualizado = servicio.actualizarEmpleado(1, cambios);

            assertEquals("DEP-2", actualizado.getDepartamentoId());
            assertFalse(actualizado.getDepartamentoValidado());
        }

        @Test
        @DisplayName("publica empleado.actualizado con los datos nuevos")
        void publicaEventoActualizado() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            when(departamentoValidador.consultarExistencia("DEP-2"))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            repositorioGuardaTalCual();
            Empleado cambios = nuevoEmpleado();
            cambios.setDepartamentoId("DEP-2");
            cambios.setNombre("Ana María");

            servicio.actualizarEmpleado(1, cambios);

            Map<String, Object> data = capturarEvento("empleado.actualizado");
            assertEquals("1", data.get("empleadoId"));
            assertEquals("Ana María", data.get("nombre"));
            assertEquals("DEP-2", data.get("departamentoId"));
        }

        @Test
        @DisplayName("no modifica email ni numeroEmpleado (identificadores inmutables)")
        void noCambiaIdentificadores() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            repositorioGuardaTalCual();
            Empleado cambios = nuevoEmpleado();
            cambios.setEmail("otro@empresa.com");
            cambios.setNumeroEmpleado("EMP-999");

            Empleado actualizado = servicio.actualizarEmpleado(1, cambios);

            assertEquals("ana@empresa.com", actualizado.getEmail());
            assertEquals("EMP-001", actualizado.getNumeroEmpleado());
        }
    }

    // ================= retirarEmpleado =================

    @Nested
    @DisplayName("retirarEmpleado")
    class RetirarEmpleado {

        @Test
        @DisplayName("error si el empleado no existe")
        void errorSiNoExiste() {
            when(empleadoRepository.findById(99)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.retirarEmpleado(99, "RENUNCIA"));

            assertEquals("El empleado con id 99 no existe", ex.getMessage());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        @DisplayName("pasa a RETIRADO, registra fechaRetiro y es baja lógica (nunca borra)")
        void bajaLogica() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            repositorioGuardaTalCual();
            LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

            Empleado retirado = servicio.retirarEmpleado(1, "RENUNCIA");

            assertEquals(EstadoEmpleado.RETIRADO, retirado.getEstado());
            assertNotNull(retirado.getFechaRetiro());
            assertTrue(retirado.getFechaRetiro().isAfter(antes));
            verify(empleadoRepository, never()).delete(any());
            verify(empleadoRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("publica empleado.retirado con email, motivo y fechaRetiro en formato ISO-8601 UTC")
        void publicaEventoRetirado() {
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(empleadoPersistido(1)));
            repositorioGuardaTalCual();

            servicio.retirarEmpleado(1, "DESPIDO");

            Map<String, Object> data = capturarEvento("empleado.retirado");
            assertEquals("1", data.get("empleadoId"));
            assertEquals("ana@empresa.com", data.get("email"));
            assertEquals("DESPIDO", data.get("motivo"));
            assertTrue(((String) data.get("fechaRetiro")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"),
                    "fechaRetiro debe ser ISO-8601 sin fracciones y con sufijo Z, pero fue: "
                            + data.get("fechaRetiro"));
        }

        /** Transición inválida RETIRADO -> RETIRADO: se rechaza y no se altera nada. */
        @Test
        @DisplayName("rechaza retirar a un empleado que ya está RETIRADO (conserva su fecha original)")
        void rechazaRetirarDosVeces() {
            Empleado yaRetirado = empleadoPersistido(1);
            yaRetirado.setEstado(EstadoEmpleado.RETIRADO);
            LocalDateTime fechaOriginal = LocalDateTime.of(2024, 6, 1, 10, 0);
            yaRetirado.setFechaRetiro(fechaOriginal);
            when(empleadoRepository.findById(1)).thenReturn(Optional.of(yaRetirado));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.retirarEmpleado(1, "RENUNCIA"));

            assertEquals("El empleado con id 1 ya está retirado", ex.getMessage());
            assertEquals(fechaOriginal, yaRetirado.getFechaRetiro());
            verify(empleadoRepository, never()).save(any());
            verifyNoInteractions(eventoPublisher);
        }
    }

    // ================= consultas =================

    @Nested
    @DisplayName("consultas")
    class Consultas {

        @Test
        @DisplayName("obtenerEmpleadoPorId delega en el repositorio")
        void obtenerPorId() {
            Empleado e = empleadoPersistido(5);
            when(empleadoRepository.findById(5)).thenReturn(Optional.of(e));

            assertSame(e, servicio.obtenerEmpleadoPorId(5).orElseThrow());
        }

        @Test
        @DisplayName("obtenerEmpleadoPorId devuelve vacío si no existe")
        void obtenerPorIdInexistente() {
            when(empleadoRepository.findById(5)).thenReturn(Optional.empty());

            assertTrue(servicio.obtenerEmpleadoPorId(5).isEmpty());
        }

        @Test
        @DisplayName("listarEmpleados devuelve todos")
        void listarTodos() {
            when(empleadoRepository.findAll()).thenReturn(List.of(empleadoPersistido(1), empleadoPersistido(2)));

            assertEquals(2, servicio.listarEmpleados().size());
        }

        @Test
        @DisplayName("listarPorEstado sin fechas filtra solo por estado (acepta minúsculas)")
        void listarPorEstadoSinFechas() {
            when(empleadoRepository.findByEstado(EstadoEmpleado.RETIRADO))
                    .thenReturn(List.of(empleadoPersistido(1)));

            List<Empleado> r = servicio.listarPorEstado("retirado", null, null);

            assertEquals(1, r.size());
            verify(empleadoRepository, never()).findByEstadoAndFechaRetiroBetween(any(), any(), any());
        }

        @Test
        @DisplayName("listarPorEstado con rango usa inicio del día 'desde' y 23:59:59 de 'hasta'")
        void listarPorEstadoConRango() {
            LocalDate desde = LocalDate.of(2024, 1, 1);
            LocalDate hasta = LocalDate.of(2024, 1, 31);
            when(empleadoRepository.findByEstadoAndFechaRetiroBetween(
                    EstadoEmpleado.RETIRADO,
                    LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2024, 1, 31, 23, 59, 59)))
                    .thenReturn(List.of(empleadoPersistido(1)));

            List<Empleado> r = servicio.listarPorEstado("RETIRADO", desde, hasta);

            assertEquals(1, r.size());
        }

        @Test
        @DisplayName("listarPorEstado con solo UNA fecha ignora el rango")
        void listarPorEstadoConUnaSolaFecha() {
            when(empleadoRepository.findByEstado(EstadoEmpleado.ACTIVO)).thenReturn(List.of());

            servicio.listarPorEstado("ACTIVO", LocalDate.of(2024, 1, 1), null);

            verify(empleadoRepository).findByEstado(EstadoEmpleado.ACTIVO);
            verify(empleadoRepository, never()).findByEstadoAndFechaRetiroBetween(any(), any(), any());
        }

        @Test
        @DisplayName("listarPorEstado con un estado inválido lanza IllegalArgumentException")
        void listarPorEstadoInvalido() {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.listarPorEstado("INEXISTENTE", null, null));
            verifyNoInteractions(empleadoRepository);
        }
    }

    // ================= reconciliación =================

    @Nested
    @DisplayName("reconciliarDepartamentosPendientes")
    class Reconciliar {

        @Test
        @DisplayName("marca como validados solo los que ahora EXISTEN; el resto sigue pendiente")
        void reconciliaSoloLosConfirmados() {
            Empleado existe = empleadoPersistido(1);
            existe.setDepartamentoId("DEP-OK");
            existe.setDepartamentoValidado(false);
            Empleado sigueCaido = empleadoPersistido(2);
            sigueCaido.setDepartamentoId("DEP-CAIDO");
            sigueCaido.setDepartamentoValidado(false);
            Empleado noExiste = empleadoPersistido(3);
            noExiste.setDepartamentoId("DEP-NO");
            noExiste.setDepartamentoValidado(false);

            when(empleadoRepository.findByDepartamentoValidadoFalse())
                    .thenReturn(List.of(existe, sigueCaido, noExiste));
            when(departamentoValidador.consultarExistencia("DEP-OK"))
                    .thenReturn(DepartamentoValidacionResultado.EXISTE);
            when(departamentoValidador.consultarExistencia("DEP-CAIDO"))
                    .thenReturn(DepartamentoValidacionResultado.INDETERMINADO);
            when(departamentoValidador.consultarExistencia("DEP-NO"))
                    .thenReturn(DepartamentoValidacionResultado.NO_EXISTE);

            servicio.reconciliarDepartamentosPendientes();

            assertTrue(existe.getDepartamentoValidado());
            assertFalse(sigueCaido.getDepartamentoValidado());
            assertFalse(noExiste.getDepartamentoValidado());
            verify(empleadoRepository).save(existe);
            verify(empleadoRepository, never()).save(sigueCaido);
            verify(empleadoRepository, never()).save(noExiste);
        }

        @Test
        @DisplayName("sin pendientes no consulta ni guarda nada")
        void sinPendientes() {
            when(empleadoRepository.findByDepartamentoValidadoFalse()).thenReturn(List.of());

            servicio.reconciliarDepartamentosPendientes();

            verifyNoInteractions(departamentoValidador);
            verify(empleadoRepository, never()).save(any());
        }
    }
}