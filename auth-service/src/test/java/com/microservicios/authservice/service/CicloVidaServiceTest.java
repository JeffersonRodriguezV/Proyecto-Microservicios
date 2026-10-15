package com.microservicios.authservice.service;

import com.microservicios.authservice.Repository.EventoProcesadoRepository;
import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.messaging.EventoPublisher;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.EventoProcesado;
import com.microservicios.authservice.model.Rol;
import com.microservicios.authservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ciclo de vida de la cuenta dirigido por eventos (Catálogo de Eventos).
 * Sin transacción activa en el test, CicloVidaService publica de inmediato, por lo que se
 * puede verificar el evento saliente directamente en el publicador simulado.
 */
@ExtendWith(MockitoExtension.class)
class CicloVidaServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres!!";

    @Mock
    private EventoProcesadoRepository procesados;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private EventoPublisher publisher;

    private JwtService jwt; // real: el token de activación debe ser verificable
    private CicloVidaService servicio;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRETO, 60, 60);
        servicio = new CicloVidaService(procesados, usuarios, jwt, publisher);
    }

    private static Map<String, Object> datos(String empleadoId, String email) {
        Map<String, Object> m = new HashMap<>();
        if (empleadoId != null) m.put("empleadoId", empleadoId);
        if (email != null) m.put("email", email);
        return m;
    }

    private static Usuario cuenta(EstadoCuenta estado) {
        Usuario u = new Usuario();
        u.setEmpleadoId("10");
        u.setEmail("ana@empresa.com");
        u.setRol(Rol.USER);
        u.setEstado(estado);
        return u;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturarEvento(String tipo) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publicar(eq(tipo), captor.capture());
        return captor.getValue();
    }

    // ================= deduplicación =================

    @Nested
    @DisplayName("deduplicación e idempotencia")
    class Deduplicacion {

        @Test
        @DisplayName("un evento ya procesado se descarta sin ningún efecto")
        void eventoDuplicado() {
            when(procesados.existsById("evt-1")).thenReturn(true);

            servicio.procesar("evt-1", "empleado.creado", datos("10", "ana@empresa.com"));

            verifyNoInteractions(usuarios, publisher);
            verify(procesados, never()).save(any());
        }

        @Test
        @DisplayName("un evento nuevo queda registrado como procesado")
        void eventoNuevoSeRegistra() {
            when(procesados.existsById("evt-2")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.empty());
            when(usuarios.existsByEmail("ana@empresa.com")).thenReturn(false);

            servicio.procesar("evt-2", "empleado.creado", datos("10", "ana@empresa.com"));

            ArgumentCaptor<EventoProcesado> captor = ArgumentCaptor.forClass(EventoProcesado.class);
            verify(procesados).save(captor.capture());
            assertEquals("evt-2", captor.getValue().getId());
        }

        @Test
        @DisplayName("un tipo de evento desconocido se ignora y NO se marca como procesado")
        void eventoDesconocido() {
            when(procesados.existsById("evt-3")).thenReturn(false);

            servicio.procesar("evt-3", "algo.raro", Map.of());

            verify(procesados, never()).save(any());
            verifyNoInteractions(usuarios, publisher);
        }
    }

    // ================= empleado.creado =================

    @Nested
    @DisplayName("empleado.creado")
    class EmpleadoCreado {

        @Test
        @DisplayName("crea la cuenta PENDIENTE_ACTIVACION, rol USER, SIN contraseña")
        void creaCuentaPendiente() {
            when(procesados.existsById("e1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.empty());
            when(usuarios.existsByEmail("ana@empresa.com")).thenReturn(false);

            servicio.procesar("e1", "empleado.creado", datos("10", "ana@empresa.com"));

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).save(captor.capture());
            Usuario creada = captor.getValue();
            assertEquals("10", creada.getEmpleadoId());
            assertEquals("ana@empresa.com", creada.getEmail());
            assertEquals(EstadoCuenta.PENDIENTE_ACTIVACION, creada.getEstado());
            assertEquals(Rol.USER, creada.getRol());
            assertNull(creada.getPasswordHash());
            assertNotNull(creada.getFechaCreacion());
        }

        @Test
        @DisplayName("publica usuario.creado con un token de activación válido y sin contraseña")
        void publicaUsuarioCreado() {
            when(procesados.existsById("e1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.empty());
            when(usuarios.existsByEmail("ana@empresa.com")).thenReturn(false);

            servicio.procesar("e1", "empleado.creado", datos("10", "ana@empresa.com"));

            Map<String, Object> data = capturarEvento("usuario.creado");
            assertEquals("10", data.get("empleadoId"));
            assertEquals("ana@empresa.com", data.get("email"));
            assertEquals("10", jwt.validarTokenReset((String) data.get("tokenActivacion")).getSubject());
            assertNotNull(data.get("expiraEn"));
            assertFalse(data.containsKey("password"));
            assertFalse(data.containsKey("passwordHash"));
        }

        @Test
        @DisplayName("si ya existe una cuenta para ese empleado, no se duplica")
        void noDuplicaPorEmpleadoId() {
            when(procesados.existsById("e1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

            servicio.procesar("e1", "empleado.creado", datos("10", "ana@empresa.com"));

            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("si el email ya está en uso por otra cuenta, no se crea")
        void noDuplicaPorEmail() {
            when(procesados.existsById("e1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.empty());
            when(usuarios.existsByEmail("ana@empresa.com")).thenReturn(true);

            servicio.procesar("e1", "empleado.creado", datos("10", "ana@empresa.com"));

            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("evento sin empleadoId o sin email se descarta")
        void camposFaltantes() {
            when(procesados.existsById(any())).thenReturn(false);

            servicio.procesar("e1", "empleado.creado", datos(null, "ana@empresa.com"));
            servicio.procesar("e2", "empleado.creado", datos("10", null));
            servicio.procesar("e3", "empleado.creado", datos("  ", "ana@empresa.com"));

            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }
    }

    // ================= empleado.retirado =================

    @Nested
    @DisplayName("empleado.retirado")
    class EmpleadoRetirado {

        @ParameterizedTest(name = "desde {0}")
        @EnumSource(value = EstadoCuenta.class, names = {"PENDIENTE_ACTIVACION", "ACTIVA", "SUSPENDIDA_TEMPORAL"})
        @DisplayName("desactiva de forma PERMANENTE desde cualquier estado no terminal")
        void desactivaPermanente(EstadoCuenta origen) {
            Usuario u = cuenta(origen);
            when(procesados.existsById("r1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("r1", "empleado.retirado", datos("10", null));

            assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, u.getEstado());
            verify(usuarios).save(u);
            Map<String, Object> data = capturarEvento("cuenta.desactivada");
            assertEquals("RETIRO", data.get("motivo"));
            assertEquals(true, data.get("permanente"));
        }

        @Test
        @DisplayName("si ya estaba desactivada, no se vuelve a guardar ni a publicar")
        void yaDesactivada() {
            when(procesados.existsById("r1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(cuenta(EstadoCuenta.DESACTIVADA_PERMANENTE)));

            servicio.procesar("r1", "empleado.retirado", datos("10", null));

            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("empleado sin cuenta (anterior a este servicio): se ignora sin fallar")
        void sinCuenta() {
            when(procesados.existsById("r1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("99")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> servicio.procesar("r1", "empleado.retirado", datos("99", null)));

            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }
    }

    // ================= vacaciones =================

    @Nested
    @DisplayName("vacaciones")
    class Vacaciones {

        @Test
        @DisplayName("vacaciones.iniciadas suspende SOLO una cuenta ACTIVA")
        void iniciadasSuspendeActiva() {
            Usuario u = cuenta(EstadoCuenta.ACTIVA);
            when(procesados.existsById("v1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("v1", "vacaciones.iniciadas", datos("10", null));

            assertEquals(EstadoCuenta.SUSPENDIDA_TEMPORAL, u.getEstado());
            Map<String, Object> data = capturarEvento("cuenta.desactivada");
            assertEquals("VACACIONES", data.get("motivo"));
            assertEquals(false, data.get("permanente"));
        }

        @ParameterizedTest(name = "estado {0}")
        @EnumSource(value = EstadoCuenta.class, names = {"PENDIENTE_ACTIVACION", "SUSPENDIDA_TEMPORAL", "DESACTIVADA_PERMANENTE"})
        @DisplayName("vacaciones.iniciadas NO altera una cuenta que no está ACTIVA")
        void iniciadasIgnoraNoActivas(EstadoCuenta estado) {
            Usuario u = cuenta(estado);
            when(procesados.existsById("v1")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("v1", "vacaciones.iniciadas", datos("10", null));

            assertEquals(estado, u.getEstado());
            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("vacaciones.finalizadas reactiva una cuenta SUSPENDIDA_TEMPORAL y publica cuenta.activada")
        void finalizadasReactiva() {
            Usuario u = cuenta(EstadoCuenta.SUSPENDIDA_TEMPORAL);
            when(procesados.existsById("v2")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("v2", "vacaciones.finalizadas", datos("10", null));

            assertEquals(EstadoCuenta.ACTIVA, u.getEstado());
            verify(usuarios).save(u);
            Map<String, Object> data = capturarEvento("cuenta.activada");
            assertEquals("FIN_VACACIONES", data.get("motivo"));
            assertEquals("ana@empresa.com", data.get("email"));
        }

        /** CASO BORDE OBLIGATORIO: retirado durante las vacaciones => NO se reactiva al terminar. */
        @Test
        @DisplayName("si fue RETIRADO durante las vacaciones, vacaciones.finalizadas NO la reactiva")
        void retiradoDuranteVacacionesNoSeReactiva() {
            Usuario u = cuenta(EstadoCuenta.SUSPENDIDA_TEMPORAL);
            when(procesados.existsById(any())).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("r1", "empleado.retirado", datos("10", null));
            assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, u.getEstado());

            servicio.procesar("v2", "vacaciones.finalizadas", datos("10", null));

            assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, u.getEstado(),
                    "una baja permanente jamás se revierte por un evento posterior");
            verify(publisher, never()).publicar(eq("cuenta.activada"), anyMap());
        }

        @ParameterizedTest(name = "estado {0}")
        @EnumSource(value = EstadoCuenta.class, names = {"PENDIENTE_ACTIVACION", "ACTIVA"})
        @DisplayName("vacaciones.finalizadas ignora cuentas que no estaban suspendidas")
        void finalizadasIgnoraOtrosEstados(EstadoCuenta estado) {
            Usuario u = cuenta(estado);
            when(procesados.existsById("v2")).thenReturn(false);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(u));

            servicio.procesar("v2", "vacaciones.finalizadas", datos("10", null));

            assertEquals(estado, u.getEstado());
            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("evento de vacaciones sin empleadoId se descarta")
        void sinEmpleadoId() {
            when(procesados.existsById("v3")).thenReturn(false);

            servicio.procesar("v3", "vacaciones.iniciadas", Map.of());

            verifyNoInteractions(usuarios, publisher);
        }
    }
}