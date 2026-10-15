package com.microservicios.authservice.service;

import com.microservicios.authservice.exception.TokenAccesoInvalidoException;
import com.microservicios.authservice.exception.TokenInvalidoException;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.Rol;
import com.microservicios.authservice.model.Usuario;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del JwtService con la librería real (jjwt): no hay nada que simular, es criptografía
 * determinista. Cubre la regla clave: un token de reset NO sirve como token de acceso y viceversa.
 */
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres!!";

    private final JwtService jwt = new JwtService(SECRETO, 60, 60);

    private static Usuario usuario() {
        Usuario u = new Usuario();
        u.setEmpleadoId("10");
        u.setEmail("ana@empresa.com");
        u.setRol(Rol.USER);
        u.setEstado(EstadoCuenta.ACTIVA);
        return u;
    }

    @Test
    @DisplayName("rechaza un secreto de menos de 32 caracteres")
    void rechazaSecretoCorto() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corto", 60, 60));
    }

    @Test
    @DisplayName("el token de acceso lleva sub=empleadoId y role, y valida correctamente")
    void tokenDeAccesoValido() {
        String token = jwt.generarTokenAcceso(usuario());

        Claims claims = jwt.validarTokenAcceso(token);

        assertEquals("10", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertNull(claims.get("type"));
    }

    @Test
    @DisplayName("expiración en segundos = minutos * 60")
    void expiracionEnSegundos() {
        assertEquals(3600, jwt.getExpiracionSegundos());
    }

    @Test
    @DisplayName("el token de reset lleva type pero NO role, y expira en el futuro")
    void tokenDeResetValido() {
        JwtService.TokenEmitido emitido = jwt.generarTokenReset("10", "ana@empresa.com");

        Claims claims = jwt.validarTokenReset(emitido.token());

        assertEquals("10", claims.getSubject());
        assertEquals(JwtService.TIPO_RESET, claims.get("type", String.class));
        assertNull(claims.get("role"));
        assertTrue(emitido.expiraEn().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("un token de ACCESO no se acepta como token de reset")
    void accesoNoSirveComoReset() {
        String acceso = jwt.generarTokenAcceso(usuario());

        assertThrows(TokenInvalidoException.class, () -> jwt.validarTokenReset(acceso));
    }

    @Test
    @DisplayName("un token de RESET (que viaja por correo) no se acepta como token de acceso")
    void resetNoSirveComoAcceso() {
        String reset = jwt.generarTokenReset("10", "ana@empresa.com").token();

        assertThrows(TokenAccesoInvalidoException.class, () -> jwt.validarTokenAcceso(reset));
    }

    @Test
    @DisplayName("un token de reset expirado se rechaza")
    void resetExpirado() {
        JwtService conExpiracionNegativa = new JwtService(SECRETO, 60, -1);
        String vencido = conExpiracionNegativa.generarTokenReset("10", "ana@empresa.com").token();

        assertThrows(TokenInvalidoException.class, () -> jwt.validarTokenReset(vencido));
    }

    @Test
    @DisplayName("un token de acceso expirado se rechaza")
    void accesoExpirado() {
        JwtService conExpiracionNegativa = new JwtService(SECRETO, -1, 60);
        String vencido = conExpiracionNegativa.generarTokenAcceso(usuario());

        assertThrows(TokenAccesoInvalidoException.class, () -> jwt.validarTokenAcceso(vencido));
    }

    @Test
    @DisplayName("un token firmado con OTRO secreto se rechaza")
    void firmaDeOtroSecreto() {
        JwtService ajeno = new JwtService("otro-secreto-distinto-de-mas-de-32-caracteres", 60, 60);
        String token = ajeno.generarTokenAcceso(usuario());

        assertThrows(TokenAccesoInvalidoException.class, () -> jwt.validarTokenAcceso(token));
    }

    @Test
    @DisplayName("basura que no es un JWT se rechaza sin romper")
    void tokenMalFormado() {
        assertThrows(TokenInvalidoException.class, () -> jwt.validarTokenReset("esto-no-es-un-jwt"));
        assertThrows(TokenAccesoInvalidoException.class, () -> jwt.validarTokenAcceso("esto-no-es-un-jwt"));
    }
}