package com.microservicios.authservice.integration;

import com.microservicios.authservice.Repository.EventoProcesadoRepository;
import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.exception.CredencialesInvalidasException;
import com.microservicios.authservice.exception.CuentaNoActivaException;
import com.microservicios.authservice.messaging.EventoPublisher;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.Rol;
import com.microservicios.authservice.model.Usuario;
import com.microservicios.authservice.service.AuthService;
import com.microservicios.authservice.service.CicloVidaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Integración de auth-service contra un MySQL REAL (Testcontainers).
 *
 * El esquema es el mismo database/init.sql de docker-compose y la app arranca con ddl-auto=validate,
 * así que estas pruebas también garantizan que las entidades JPA coinciden con las tablas reales.
 * Lo que más importa aquí y NO se puede comprobar con mocks: que la transacción, la deduplicación
 * por id de evento y el ciclo de vida completo funcionen de verdad sobre la base de datos.
 *
 * Solo se simula RabbitMQ (EventoPublisher): se captura lo que el servicio "publicaría".
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=secreto-de-pruebas-con-mas-de-32-caracteres!!",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class CuentaIntegrationTest {

    private static final String PASSWORD = "Segura123";

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("auth_db")
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
    private EventoPublisher publisher;

    @Autowired
    private CicloVidaService cicloVida;
    @Autowired
    private AuthService auth;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private EventoProcesadoRepository procesados;

    @BeforeEach
    void limpiar() {
        usuarios.deleteAll();
        procesados.deleteAll();
        reset(publisher);
    }

    private static Map<String, Object> datos(String empleadoId, String email) {
        return Map.of("empleadoId", empleadoId, "email", email);
    }

    /** Procesa empleado.creado y devuelve el token de activación que se habría enviado por correo. */
    @SuppressWarnings("unchecked")
    private String crearCuentaYObtenerTokenDeActivacion(String empleadoId, String email) {
        cicloVida.procesar("evt-creado-" + empleadoId, "empleado.creado", datos(empleadoId, email));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publicar(eq("usuario.creado"), captor.capture());
        return (String) captor.getValue().get("tokenActivacion");
    }

    @Test
    @DisplayName("empleado.creado deja en MySQL una cuenta PENDIENTE_ACTIVACION, rol USER y sin contraseña")
    void creaCuentaPendienteEnLaBaseDeDatos() {
        cicloVida.procesar("evt-1", "empleado.creado", datos("10", "ana@empresa.com"));

        Usuario enBd = usuarios.findByEmpleadoId("10").orElseThrow();
        assertEquals(EstadoCuenta.PENDIENTE_ACTIVACION, enBd.getEstado());
        assertEquals(Rol.USER, enBd.getRol());
        assertNull(enBd.getPasswordHash());
        assertTrue(procesados.existsById("evt-1"), "el id del evento queda registrado en la misma transacción");
        verify(publisher).publicar(eq("usuario.creado"), anyMap());
    }

    @Test
    @DisplayName("el MISMO evento entregado dos veces se aplica una sola vez (deduplicación real)")
    void eventoDuplicadoSeAplicaUnaVez() {
        cicloVida.procesar("evt-1", "empleado.creado", datos("10", "ana@empresa.com"));
        cicloVida.procesar("evt-1", "empleado.creado", datos("10", "ana@empresa.com"));

        assertEquals(1, usuarios.count());
        assertEquals(1, procesados.count());
        verify(publisher, times(1)).publicar(eq("usuario.creado"), anyMap());
    }

    @Test
    @DisplayName("restricción UNIQUE de email en MySQL real")
    void emailUnicoEnMySql() {
        usuarios.saveAndFlush(cuenta("10", "ana@empresa.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> usuarios.saveAndFlush(cuenta("11", "ana@empresa.com")));
    }

    @Test
    @DisplayName("restricción UNIQUE de empleado_id en MySQL real")
    void empleadoIdUnicoEnMySql() {
        usuarios.saveAndFlush(cuenta("10", "ana@empresa.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> usuarios.saveAndFlush(cuenta("10", "otra@empresa.com")));
    }

    @Test
    @DisplayName("flujo completo: crear -> activar con el token -> login OK -> retiro -> login rechazado")
    void flujoCompletoDeLaCuenta() {
        String token = crearCuentaYObtenerTokenDeActivacion("10", "ana@empresa.com");

        // Antes de activar: sin contraseña no puede entrar
        assertThrows(CredencialesInvalidasException.class, () -> auth.login("ana@empresa.com", PASSWORD));

        // Activación con el token recibido: guarda el hash, pasa a ACTIVA y publica cuenta.activada
        auth.restablecerPassword(token, PASSWORD);
        Usuario activa = usuarios.findByEmpleadoId("10").orElseThrow();
        assertEquals(EstadoCuenta.ACTIVA, activa.getEstado());
        assertNotEquals(PASSWORD, activa.getPasswordHash());
        verify(publisher).publicar(eq("cuenta.activada"), anyMap());

        // Ya puede iniciar sesión
        assertEquals("Bearer", auth.login("ana@empresa.com", PASSWORD).tokenType());

        // Retiro del empleado: baja permanente y login rechazado
        cicloVida.procesar("evt-retiro", "empleado.retirado", Map.of("empleadoId", "10"));
        assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, usuarios.findByEmpleadoId("10").orElseThrow().getEstado());
        assertThrows(CredencialesInvalidasException.class, () -> auth.login("ana@empresa.com", PASSWORD));
    }

    @Test
    @DisplayName("vacaciones suspenden y reactivan la cuenta, y la reactivación se refleja en el login")
    void vacacionesSuspendenYReactivan() {
        activarCuenta("10", "ana@empresa.com");

        cicloVida.procesar("evt-vac-1", "vacaciones.iniciadas", Map.of("empleadoId", "10"));
        assertEquals(EstadoCuenta.SUSPENDIDA_TEMPORAL, usuarios.findByEmpleadoId("10").orElseThrow().getEstado());
        assertThrows(CredencialesInvalidasException.class, () -> auth.login("ana@empresa.com", PASSWORD));

        cicloVida.procesar("evt-vac-2", "vacaciones.finalizadas", Map.of("empleadoId", "10"));
        assertEquals(EstadoCuenta.ACTIVA, usuarios.findByEmpleadoId("10").orElseThrow().getEstado());
        assertNotNull(auth.login("ana@empresa.com", PASSWORD).accessToken());
    }

    @Test
    @DisplayName("CASO BORDE: retirado durante las vacaciones NO se reactiva al finalizar (estado en BD)")
    void retiradoDuranteVacacionesNoSeReactiva() {
        activarCuenta("10", "ana@empresa.com");
        cicloVida.procesar("evt-vac-1", "vacaciones.iniciadas", Map.of("empleadoId", "10"));
        cicloVida.procesar("evt-retiro", "empleado.retirado", Map.of("empleadoId", "10"));

        cicloVida.procesar("evt-vac-2", "vacaciones.finalizadas", Map.of("empleadoId", "10"));

        assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, usuarios.findByEmpleadoId("10").orElseThrow().getEstado());
        verify(publisher, never()).publicar(eq("cuenta.activada"), anyMap());
        assertThrows(CredencialesInvalidasException.class, () -> auth.login("ana@empresa.com", PASSWORD));
    }

    @Test
    @DisplayName("a una cuenta retirada no se le puede restablecer la contraseña con un token antiguo")
    void retiradaNoRestableceConTokenAntiguo() {
        String token = crearCuentaYObtenerTokenDeActivacion("10", "ana@empresa.com");
        cicloVida.procesar("evt-retiro", "empleado.retirado", Map.of("empleadoId", "10"));

        assertThrows(CuentaNoActivaException.class, () -> auth.restablecerPassword(token, PASSWORD));

        assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, usuarios.findByEmpleadoId("10").orElseThrow().getEstado());
        assertNull(usuarios.findByEmpleadoId("10").orElseThrow().getPasswordHash());
    }

    // ---------- apoyo ----------

    private void activarCuenta(String empleadoId, String email) {
        String token = crearCuentaYObtenerTokenDeActivacion(empleadoId, email);
        auth.restablecerPassword(token, PASSWORD);
        reset(publisher); // los tests posteriores verifican solo lo que ocurre después de activar
    }

    private static Usuario cuenta(String empleadoId, String email) {
        Usuario u = new Usuario();
        u.setEmpleadoId(empleadoId);
        u.setEmail(email);
        u.setRol(Rol.USER);
        u.setEstado(EstadoCuenta.ACTIVA);
        u.setFechaCreacion(LocalDateTime.now());
        return u;
    }
}