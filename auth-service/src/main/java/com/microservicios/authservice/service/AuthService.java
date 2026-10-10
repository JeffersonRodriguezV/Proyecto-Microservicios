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
import com.microservicios.authservice.model.Usuario;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EventoPublisher publisher;
    private final PoliticaPassword politica;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt,
                       EventoPublisher publisher, PoliticaPassword politica) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.publisher = publisher;
        this.politica = politica;
    }

    public LoginResponse login(String email, String password) {
        Usuario usuario = usuarios.findByEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        if (usuario.getPasswordHash() == null
                || !encoder.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        // Reto 5 (pruebas 13, 15, 17): una cuenta suspendida o desactivada falla con 401.
        // Mismo error que una credencial incorrecta: no revela que el correo existe.
        if (usuario.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CredencialesInvalidasException();
        }

        return new LoginResponse(jwt.generarTokenAcceso(usuario), "Bearer", jwt.getExpiracionSegundos());
    }

    /**
     * Genera un token de recuperación y publica usuario.recuperacion (Catálogo 3.5).
     * No revela si el correo existe: el controlador responde igual en ambos casos.
     * A una cuenta desactivada de forma permanente no se le emite token.
     */
    public void recuperarPassword(String email) {
        usuarios.findByEmail(email)
                .filter(u -> u.getEstado() != EstadoCuenta.DESACTIVADA_PERMANENTE)
                .ifPresent(u -> {
                    JwtService.TokenEmitido t = jwt.generarTokenReset(u.getEmpleadoId(), u.getEmail());
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("email", u.getEmail());
                    data.put("tokenRecuperacion", t.token());
                    data.put("expiraEn", t.expiraEn().truncatedTo(ChronoUnit.SECONDS).toString());
                    publisher.publicar("usuario.recuperacion", data);
                });
    }

    /**
     * Establece la contraseña con un token de reset. Solo una cuenta PENDIENTE_ACTIVACION
     * pasa a ACTIVA (activación inicial, publica cuenta.activada). Una suspendida cambia la
     * contraseña pero sigue suspendida; una desactivada de forma permanente se rechaza.
     */
    public void restablecerPassword(String token, String nuevaPassword) {
        Claims claims = jwt.validarTokenReset(token);
        Usuario usuario = usuarios.findByEmpleadoId(claims.getSubject())
                .orElseThrow(TokenInvalidoException::new);

        if (usuario.getEstado() == EstadoCuenta.DESACTIVADA_PERMANENTE) {
            throw new CuentaNoActivaException(usuario.getEstado());
        }

        politica.validar(nuevaPassword);
        usuario.setPasswordHash(encoder.encode(nuevaPassword));

        boolean activacionInicial = usuario.getEstado() == EstadoCuenta.PENDIENTE_ACTIVACION;
        if (activacionInicial) {
            usuario.setEstado(EstadoCuenta.ACTIVA);
        }
        usuarios.save(usuario);

        if (activacionInicial) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("empleadoId", usuario.getEmpleadoId());
            data.put("email", usuario.getEmail());
            data.put("motivo", "ACTIVACION_INICIAL");
            publisher.publicar("cuenta.activada", data);
        }
    }

    /**
     * Cambia la contraseña del usuario autenticado. El Gateway reenvía la cabecera
     * Authorization; aquí se vuelve a validar el token y se identifica al usuario por su sub.
     * Exige la contraseña actual y que la cuenta esté ACTIVA.
     */
    public void cambiarPassword(String authorization, String passwordActual, String passwordNueva) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new TokenAccesoInvalidoException();
        }
        Claims claims = jwt.validarTokenAcceso(authorization.substring(7).trim());
        Usuario usuario = usuarios.findByEmpleadoId(claims.getSubject())
                .orElseThrow(TokenAccesoInvalidoException::new);

        if (usuario.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CuentaNoActivaException(usuario.getEstado());
        }
        if (usuario.getPasswordHash() == null
                || !encoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new PasswordInvalidaException("La contraseña actual es incorrecta");
        }

        politica.validar(passwordNueva);
        usuario.setPasswordHash(encoder.encode(passwordNueva));
        usuarios.save(usuario);
    }
}
