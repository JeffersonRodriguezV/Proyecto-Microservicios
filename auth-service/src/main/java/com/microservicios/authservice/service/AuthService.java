package com.microservicios.authservice.service;

import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.dto.LoginResponse;
import com.microservicios.authservice.exception.CredencialesInvalidasException;
import com.microservicios.authservice.exception.CuentaNoActivaException;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse login(String email, String password) {
        Usuario usuario = usuarios.findByEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        // Sin contraseña (cuenta pendiente de activar) o contraseña incorrecta: mismo mensaje genérico
        if (usuario.getPasswordHash() == null
                || !encoder.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        // La contraseña es correcta, pero la cuenta no puede iniciar sesión
        if (usuario.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CuentaNoActivaException(usuario.getEstado());
        }

        return new LoginResponse(jwt.generarTokenAcceso(usuario), "Bearer", jwt.getExpiracionSegundos());
    }
}