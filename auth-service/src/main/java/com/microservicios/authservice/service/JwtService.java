package com.microservicios.authservice.service;

import com.microservicios.authservice.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Emite el JWT de acceso (HS256). La contraseña nunca va en el token. */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMinutos;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration-minutes}") long expiracionMinutos) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
    }

    public String generarTokenAcceso(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmpleadoId())
                .claim("role", usuario.getRol().name())
                .claim("email", usuario.getEmail())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracionMinutos, ChronoUnit.MINUTES)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    public long getExpiracionSegundos() {
        return expiracionMinutos * 60;
    }
}