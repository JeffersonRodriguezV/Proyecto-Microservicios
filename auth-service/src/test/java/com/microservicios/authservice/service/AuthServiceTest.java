package com.microservicios.authservice.service;

import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.dto.LoginResponse;
import com.microservicios.authservice.exception.CredencialesInvalidasException;
import com.microservicios.authservice.exception.CuentaNoActivaException;
import com.microservicios.authservice.exception.PasswordInvalidaException;
import com.microservicios.authservice.exception.TokenAccesoInvalidoException;
import com.microservicios.authservice.exception.TokenInvalidoException;
import com.microservicios.authservice.messaging.EventoPublisher;
import com.microservicios.authservice.model.EstadoCuenta;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
 * Reglas de autenticación. Se simulan solo los bordes (repositorio y publicador); el
 * PasswordEncoder (BCrypt), el JwtService y la PoliticaPassword son REALES porque son
 * deterministas y rápidos: así se prueba que el hash realmente se genera y que los tokens
 * realmente se validan, en lugar de comprobar llamadas a mocks.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres!!";
    private static final String PASSWORD = "Segura123";

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private EventoPublisher publisher;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // costo bajo: tests rápidos
    private JwtService jwt;
    private AuthService servicio;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRETO, 60, 60);
        servicio = new AuthService(usuarios, encoder, jwt, publisher, new PoliticaPassword());
    }

    private Usuario cuenta(EstadoCuenta estado, String passwordPlano) {
        Usuario u = new Usuario();
        u.setEmpleadoId("10");
        u.setEmail("ana@empresa.com");
        u.setRol(Rol.USER);
        u.setEstado(estado);
        u.setPasswordHash(passwordPlano == null ? null : encoder.encode(passwordPlano));
        return u;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturarEvento(String tipo) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publicar(eq(tipo), captor.capture());
        return captor.getValue();
    }

    // ================= login =================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("credenciales correctas en cuenta ACTIVA devuelven un JWT Bearer verificable")
        void loginCorrecto() {
            when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA, PASSWORD)));

            LoginResponse r = servicio.login("ana@empresa.com", PASSWORD);

            assertEquals("Bearer", r.tokenType());
            assertEquals("10", jwt.validarTokenAcceso(r.accessToken()).getSubject());
            assertEquals(3600, r.expiresIn());
        }

        @Test
        @DisplayName("contraseña incorrecta -> CredencialesInvalidas")
        void passwordIncorrecta() {
            when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA, PASSWORD)));

            assertThrows(CredencialesInvalidasException.class, () -> servicio.login("ana@empresa.com", "Otra12345"));
        }

        @Test
        @DisplayName("correo inexistente -> el MISMO error (no revela si el correo existe)")
        void correoInexistente() {
            when(usuarios.findByEmail("nadie@empresa.com")).thenReturn(Optional.empty());

            assertThrows(CredencialesInvalidasException.class, () -> servicio.login("nadie@empresa.com", PASSWORD));
        }

        @Test
        @DisplayName("cuenta sin contraseña (pendiente de activar) no puede iniciar sesión")
        void sinPassword() {
            when(usuarios.findByEmail("ana@empresa.com"))
                    .thenReturn(Optional.of(cuenta(EstadoCuenta.PENDIENTE_ACTIVACION, null)));

            assertThrows(CredencialesInvalidasException.class, () -> servicio.login("ana@empresa.com", PASSWORD));
        }

        @ParameterizedTest(name = "estado {0}")
        @EnumSource(value = EstadoCuenta.class, names = {"PENDIENTE_ACTIVACION", "SUSPENDIDA_TEMPORAL", "DESACTIVADA_PERMANENTE"})
        @DisplayName("con la contraseña CORRECTA pero cuenta no activa -> rechazado igual")
        void cuentaNoActiva(EstadoCuenta estado) {
            when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(cuenta(estado, PASSWORD)));

            assertThrows(CredencialesInvalidasException.class, () -> servicio.login("ana@empresa.com", PASSWORD));
        }
    }

    // ================= recuperarPassword =================

    @Nested
    @DisplayName("recuperarPassword")
    class Recuperar {

        @Test
        @DisplayName("correo existente: publica usuario.recuperacion con un token de reset válido")
        void publicaRecuperacion() {
            when(usuarios.findByEmail("ana@empresa.com")).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA, PASSWORD)));

            servicio.recuperarPassword("ana@empresa.com");

            Map<String, Object> data = capturarEvento("usuario.recuperacion");
            assertEquals("ana@empresa.com", data.get("email"));
            assertEquals("10", jwt.validarTokenReset((String) data.get("tokenRecuperacion")).getSubject());
            assertNotNull(data.get("expiraEn"));
            assertFalse(data.containsKey("password"), "nunca debe viajar una contraseña");
        }

        @Test
        @DisplayName("correo inexistente: no lanza error ni publica (no revela si existe)")
        void correoInexistenteNoRevelaNada() {
            when(usuarios.findByEmail("nadie@empresa.com")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> servicio.recuperarPassword("nadie@empresa.com"));
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("cuenta DESACTIVADA_PERMANENTE: no se emite token de recuperación")
        void desactivadaNoRecibeToken() {
            when(usuarios.findByEmail("ana@empresa.com"))
                    .thenReturn(Optional.of(cuenta(EstadoCuenta.DESACTIVADA_PERMANENTE, PASSWORD)));

            servicio.recuperarPassword("ana@empresa.com");

            verifyNoInteractions(publisher);
        }
    }

    // ================= restablecerPassword =================

    @Nested
    @DisplayName("restablecerPassword")
    class Restablecer {

        private String tokenReset() {
            return jwt.generarTokenReset("10", "ana@empresa.com").token();
        }

        @Test
        @DisplayName("activación inicial: guarda el HASH (no el texto plano), pasa a ACTIVA y publica cuenta.activada")
        void activacionInicial() {
            Usuario pendiente = cuenta(EstadoCuenta.PENDIENTE_ACTIVACION, null);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(pendiente));

            servicio.restablecerPassword(tokenReset(), PASSWORD);

            assertEquals(EstadoCuenta.ACTIVA, pendiente.getEstado());
            assertNotEquals(PASSWORD, pendiente.getPasswordHash(), "jamás se guarda la contraseña en claro");
            assertTrue(encoder.matches(PASSWORD, pendiente.getPasswordHash()));
            verify(usuarios).save(pendiente);
            Map<String, Object> data = capturarEvento("cuenta.activada");
            assertEquals("ACTIVACION_INICIAL", data.get("motivo"));
        }

        @Test
        @DisplayName("cuenta ACTIVA: cambia la contraseña sin publicar cuenta.activada")
        void recuperacionDeCuentaActiva() {
            Usuario activa = cuenta(EstadoCuenta.ACTIVA, "Vieja12345");
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(activa));

            servicio.restablecerPassword(tokenReset(), PASSWORD);

            assertTrue(encoder.matches(PASSWORD, activa.getPasswordHash()));
            assertEquals(EstadoCuenta.ACTIVA, activa.getEstado());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("cuenta SUSPENDIDA: cambia la contraseña pero SIGUE suspendida")
        void suspendidaSigueSuspendida() {
            Usuario suspendida = cuenta(EstadoCuenta.SUSPENDIDA_TEMPORAL, "Vieja12345");
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(suspendida));

            servicio.restablecerPassword(tokenReset(), PASSWORD);

            assertEquals(EstadoCuenta.SUSPENDIDA_TEMPORAL, suspendida.getEstado());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("cuenta DESACTIVADA_PERMANENTE: se rechaza y no se toca nada (no se reactiva a un retirado)")
        void desactivadaSeRechaza() {
            Usuario retirada = cuenta(EstadoCuenta.DESACTIVADA_PERMANENTE, "Vieja12345");
            String hashAntes = retirada.getPasswordHash();
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(retirada));

            assertThrows(CuentaNoActivaException.class, () -> servicio.restablecerPassword(tokenReset(), PASSWORD));

            assertEquals(EstadoCuenta.DESACTIVADA_PERMANENTE, retirada.getEstado());
            assertEquals(hashAntes, retirada.getPasswordHash());
            verify(usuarios, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("contraseña débil: se rechaza y la cuenta pendiente NO se activa")
        void passwordDebilNoActiva() {
            Usuario pendiente = cuenta(EstadoCuenta.PENDIENTE_ACTIVACION, null);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(pendiente));

            assertThrows(PasswordInvalidaException.class, () -> servicio.restablecerPassword(tokenReset(), "debil"));

            assertEquals(EstadoCuenta.PENDIENTE_ACTIVACION, pendiente.getEstado());
            assertNull(pendiente.getPasswordHash());
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("token expirado: se rechaza sin consultar la base de datos")
        void tokenExpirado() {
            String vencido = new JwtService(SECRETO, 60, -1).generarTokenReset("10", "ana@empresa.com").token();

            assertThrows(TokenInvalidoException.class, () -> servicio.restablecerPassword(vencido, PASSWORD));

            verifyNoInteractions(usuarios, publisher);
        }

        @Test
        @DisplayName("un token de ACCESO no sirve para restablecer la contraseña")
        void tokenDeAccesoNoSirve() {
            String acceso = jwt.generarTokenAcceso(cuenta(EstadoCuenta.ACTIVA, PASSWORD));

            assertThrows(TokenInvalidoException.class, () -> servicio.restablecerPassword(acceso, PASSWORD));

            verifyNoInteractions(usuarios);
        }

        @Test
        @DisplayName("token válido de un empleado que ya no tiene cuenta -> TokenInvalido")
        void cuentaInexistente() {
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.empty());

            assertThrows(TokenInvalidoException.class, () -> servicio.restablecerPassword(tokenReset(), PASSWORD));
        }
    }

    // ================= cambiarPassword =================

    @Nested
    @DisplayName("cambiarPassword")
    class Cambiar {

        private String bearer(Usuario u) {
            return "Bearer " + jwt.generarTokenAcceso(u);
        }

        @Test
        @DisplayName("sin cabecera Authorization o sin prefijo Bearer -> TokenAccesoInvalido")
        void sinBearer() {
            assertThrows(TokenAccesoInvalidoException.class, () -> servicio.cambiarPassword(null, PASSWORD, "Nueva12345"));
            assertThrows(TokenAccesoInvalidoException.class, () -> servicio.cambiarPassword("Basic abc", PASSWORD, "Nueva12345"));
            verifyNoInteractions(usuarios);
        }

        @Test
        @DisplayName("un token de reset no sirve para cambiar la contraseña")
        void resetNoSirve() {
            String reset = "Bearer " + jwt.generarTokenReset("10", "ana@empresa.com").token();

            assertThrows(TokenAccesoInvalidoException.class, () -> servicio.cambiarPassword(reset, PASSWORD, "Nueva12345"));
        }

        @Test
        @DisplayName("contraseña actual incorrecta -> se rechaza y no se cambia el hash")
        void passwordActualIncorrecta() {
            Usuario activa = cuenta(EstadoCuenta.ACTIVA, PASSWORD);
            String hashAntes = activa.getPasswordHash();
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(activa));

            PasswordInvalidaException ex = assertThrows(PasswordInvalidaException.class,
                    () -> servicio.cambiarPassword(bearer(activa), "Equivocada1", "Nueva12345"));

            assertEquals("La contraseña actual es incorrecta", ex.getMessage());
            assertEquals(hashAntes, activa.getPasswordHash());
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("cuenta no ACTIVA (suspendida) no puede cambiar la contraseña")
        void cuentaNoActiva() {
            Usuario suspendida = cuenta(EstadoCuenta.SUSPENDIDA_TEMPORAL, PASSWORD);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(suspendida));

            assertThrows(CuentaNoActivaException.class,
                    () -> servicio.cambiarPassword(bearer(suspendida), PASSWORD, "Nueva12345"));
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("la nueva contraseña debe cumplir la política")
        void nuevaPasswordDebil() {
            Usuario activa = cuenta(EstadoCuenta.ACTIVA, PASSWORD);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(activa));

            assertThrows(PasswordInvalidaException.class,
                    () -> servicio.cambiarPassword(bearer(activa), PASSWORD, "corta"));
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("cambio correcto: se guarda el hash de la nueva contraseña")
        void cambioCorrecto() {
            Usuario activa = cuenta(EstadoCuenta.ACTIVA, PASSWORD);
            when(usuarios.findByEmpleadoId("10")).thenReturn(Optional.of(activa));

            servicio.cambiarPassword(bearer(activa), PASSWORD, "Nueva12345");

            assertTrue(encoder.matches("Nueva12345", activa.getPasswordHash()));
            assertFalse(encoder.matches(PASSWORD, activa.getPasswordHash()));
            verify(usuarios).save(activa);
            verify(publisher, never()).publicar(any(), anyMap());
        }
    }
}