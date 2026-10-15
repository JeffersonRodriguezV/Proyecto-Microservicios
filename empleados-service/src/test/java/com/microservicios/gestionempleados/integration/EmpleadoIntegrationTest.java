package com.microservicios.gestionempleados.integration;

import com.microservicios.gestionempleados.client.DepartamentoValidacionResultado;
import com.microservicios.gestionempleados.client.DepartamentoValidador;
import com.microservicios.gestionempleados.eventos.EventoPublisher;
import com.microservicios.gestionempleados.model.Empleado;
import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import com.microservicios.gestionempleados.service.EmpleadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integración de empleados-service contra un MySQL REAL levantado con Testcontainers.
 *
 * Por qué MySQL real y no H2:
 *  - El esquema sale del MISMO database/init.sql que usa docker-compose, y la aplicación arranca con
 *    spring.jpa.hibernate.ddl-auto=validate: si una entidad no coincide con las tablas reales, el
 *    contexto NO arranca y todos los tests fallan. H2 con ddl-auto=create jamás detectaría eso.
 *  - Las restricciones UNIQUE y CHECK son las del motor real, no una imitación.
 *
 * Se simulan solo los bordes que no son la base de datos: la validación de departamentos (HTTP) y el
 * publicador de eventos (RabbitMQ).
 */
@Testcontainers
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class EmpleadoIntegrationTest {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("empleados_db")
            // Mismo script que docker-compose monta en /docker-entrypoint-initdb.d
            .withCopyFileToContainer(
                    MountableFile.forHostPath("database/init.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");

    @DynamicPropertySource
    static void propiedadesDeBaseDeDatos(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @MockitoBean
    private DepartamentoValidador departamentoValidador;
    @MockitoBean
    private EventoPublisher eventoPublisher;

    @Autowired
    private EmpleadoService servicio;
    @Autowired
    private EmpleadoRepository repositorio;

    @BeforeEach
    void limpiarTabla() {
        repositorio.deleteAll();
        when(departamentoValidador.consultarExistencia(anyString()))
                .thenReturn(DepartamentoValidacionResultado.EXISTE);
    }

    private static Empleado empleado(String email, String numero) {
        Empleado e = new Empleado();
        e.setNombre("Ana");
        e.setApellido("Pérez");
        e.setEmail(email);
        e.setNumeroEmpleado(numero);
        e.setCargo("Desarrolladora");
        e.setArea("TI");
        e.setDepartamentoId("DEP-1");
        e.setFechaIngreso(LocalDate.of(2024, 1, 15));
        return e;
    }

    @Test
    @DisplayName("crear persiste en MySQL: id autogenerado, estado ACTIVO y departamento validado")
    void creaYRecuperaDeLaBaseDeDatos() {
        Empleado creado = servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));

        assertNotNull(creado.getId());
        Empleado enBd = repositorio.findById(creado.getId()).orElseThrow();
        assertEquals("ana@empresa.com", enBd.getEmail());
        assertEquals("Pérez", enBd.getApellido(), "los acentos deben sobrevivir (utf8mb4)");
        assertEquals(EstadoEmpleado.ACTIVO, enBd.getEstado());
        assertTrue(enBd.getDepartamentoValidado());
        assertEquals(LocalDate.of(2024, 1, 15), enBd.getFechaIngreso());
    }

    @Test
    @DisplayName("el servicio rechaza un email repetido y no deja un segundo registro")
    void rechazaEmailDuplicadoEnElServicio() {
        servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-002")));

        assertEquals(1, repositorio.count());
    }

    @Test
    @DisplayName("la restricción UNIQUE de la BD real protege el email aunque se salte el servicio")
    void restriccionUniqueDeEmailEnMySql() {
        repositorio.saveAndFlush(conEstado(empleado("ana@empresa.com", "EMP-001")));

        assertThrows(DataIntegrityViolationException.class,
                () -> repositorio.saveAndFlush(conEstado(empleado("ana@empresa.com", "EMP-002"))));
    }

    @Test
    @DisplayName("la restricción UNIQUE de la BD real protege el numeroEmpleado")
    void restriccionUniqueDeNumeroEnMySql() {
        repositorio.saveAndFlush(conEstado(empleado("ana@empresa.com", "EMP-001")));

        assertThrows(DataIntegrityViolationException.class,
                () -> repositorio.saveAndFlush(conEstado(empleado("otra@empresa.com", "EMP-001"))));
    }

    @Test
    @DisplayName("departamento INDETERMINADO: se guarda como pendiente (departamento_validado = false)")
    void guardaPendienteSiElDepartamentoEsIndeterminado() {
        when(departamentoValidador.consultarExistencia("DEP-1"))
                .thenReturn(DepartamentoValidacionResultado.INDETERMINADO);

        Empleado creado = servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));

        assertFalse(repositorio.findById(creado.getId()).orElseThrow().getDepartamentoValidado());
        assertEquals(1, repositorio.findByDepartamentoValidadoFalse().size());
    }

    @Test
    @DisplayName("la reconciliación marca como validados en la BD a los pendientes cuyo departamento ya existe")
    void reconciliaContraLaBaseDeDatos() {
        when(departamentoValidador.consultarExistencia("DEP-1"))
                .thenReturn(DepartamentoValidacionResultado.INDETERMINADO);
        servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));
        assertEquals(1, repositorio.findByDepartamentoValidadoFalse().size());

        when(departamentoValidador.consultarExistencia("DEP-1"))
                .thenReturn(DepartamentoValidacionResultado.EXISTE);
        servicio.reconciliarDepartamentosPendientes();

        assertTrue(repositorio.findByDepartamentoValidadoFalse().isEmpty());
    }

    @Test
    @DisplayName("retirar persiste RETIRADO + fecha_retiro y NO borra la fila (baja lógica)")
    void retiroEsBajaLogica() {
        Empleado creado = servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));

        servicio.retirarEmpleado(creado.getId(), "RENUNCIA");

        Empleado enBd = repositorio.findById(creado.getId()).orElseThrow();
        assertEquals(EstadoEmpleado.RETIRADO, enBd.getEstado());
        assertNotNull(enBd.getFechaRetiro());
        assertEquals(1, repositorio.count());
    }

    @Test
    @DisplayName("retirar a un empleado ya retirado se rechaza y la fecha de retiro original no cambia")
    void noSePuedeRetirarDosVeces() {
        Empleado creado = servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));
        servicio.retirarEmpleado(creado.getId(), "RENUNCIA");
        var fechaOriginal = repositorio.findById(creado.getId()).orElseThrow().getFechaRetiro();

        assertThrows(IllegalArgumentException.class,
                () -> servicio.retirarEmpleado(creado.getId(), "DESPIDO"));

        assertEquals(fechaOriginal, repositorio.findById(creado.getId()).orElseThrow().getFechaRetiro());
    }

    @Test
    @DisplayName("listarPorEstado filtra por estado y por rango de fechas de retiro contra MySQL real")
    void listaPorEstadoYRango() {
        Empleado retirado = servicio.crearEmpleado(empleado("ana@empresa.com", "EMP-001"));
        servicio.crearEmpleado(empleado("luis@empresa.com", "EMP-002")); // sigue ACTIVO
        servicio.retirarEmpleado(retirado.getId(), "RENUNCIA");

        LocalDate hoy = LocalDate.now();

        List<Empleado> soloRetirados = servicio.listarPorEstado("RETIRADO", null, null);
        List<Empleado> retiradosHoy = servicio.listarPorEstado("RETIRADO", hoy, hoy);
        List<Empleado> retiradosAyerYAntes = servicio.listarPorEstado("RETIRADO",
                hoy.minusDays(10), hoy.minusDays(1));
        List<Empleado> activos = servicio.listarPorEstado("ACTIVO", null, null);

        assertEquals(1, soloRetirados.size());
        assertEquals(1, retiradosHoy.size());
        assertTrue(retiradosAyerYAntes.isEmpty());
        assertEquals(1, activos.size());
        assertEquals("luis@empresa.com", activos.get(0).getEmail());
    }

    /** Para saltarse el servicio y probar las restricciones de la BD: estado y validado son NOT NULL. */
    private static Empleado conEstado(Empleado e) {
        e.setEstado(EstadoEmpleado.ACTIVO);
        e.setDepartamentoValidado(true);
        return e;
    }
}