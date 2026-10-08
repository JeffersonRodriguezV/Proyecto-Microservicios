package com.microservicios.authservice.service;

import com.microservicios.authservice.exception.TokenAccesoInvalidoException;
import com.microservicios.authservice.exception.TokenInvalidoException;
import com.microservicios.authservice.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Emite y valida JWT (HS256). La contraseña nunca va en un token. */
@Service
public class JwtService {

    public static final String TIPO_RESET = "RESET_PASSWORD";

    /** Un token recién emitido junto con su fecha de expiración. */
    public record TokenEmitido(String token, Instant expiraEn) {}

    private final SecretKey clave;
    private final long expiracionMinutos;
    private final long expiracionResetMinutos;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration-minutes}") long expiracionMinutos,
                      @Value("${jwt.reset-expiration-minutes}") long expiracionResetMinutos) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
        this.expiracionResetMinutos = expiracionResetMinutos;
    }

    /** Token de acceso: lleva sub (empleadoId) y role. */
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

    /**
     * Token de activación/recuperación. NO lleva role: un Gateway que exija role
     * lo rechaza como token de acceso. Lo usan tanto este servicio como el consumidor
     * de empleado.creado (tokenActivacion de usuario.creado).
     */
    public TokenEmitido generarTokenReset(String empleadoId, String email) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expiracionResetMinutos, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(empleadoId)
                .claim("type", TIPO_RESET)
                .claim("email", email)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        return new TokenEmitido(token, expira);
    }

    /** Valida firma, expiración y que sea un token de reset (no uno de acceso). */
    public Claims validarTokenReset(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build()
                    .parseSignedClaims(token).getPayload();
            if (!TIPO_RESET.equals(claims.get("type", String.class))) {
                throw new TokenInvalidoException();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException();
        }
    }

    /**
     * Valida un token de ACCESO: firma, expiración, que lleve role y que NO sea de reset.
     * Así un token de recuperación (que viaja por correo) no sirve para operar la cuenta.
     */
    public Claims validarTokenAcceso(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build()
                    .parseSignedClaims(token).getPayload();
            if (claims.get("role", String.class) == null || claims.get("type") != null) {
                throw new TokenAccesoInvalidoException();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenAccesoInvalidoException();
        }
    }

    public long getExpiracionSegundos() {
        return expiracionMinutos * 60;
    }
}
