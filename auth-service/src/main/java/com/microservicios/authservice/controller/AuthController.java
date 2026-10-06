package com.microservicios.authservice.controller;

import com.microservicios.authservice.dto.LoginRequest;
import com.microservicios.authservice.dto.LoginResponse;
import com.microservicios.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Controlador para manejar las solicitudes de autenticación.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    /**
     * Maneja la solicitud de inicio de sesión.
     *
     * @param peticion La solicitud de inicio de sesión que contiene el correo electrónico y la contraseña.
     * @return La respuesta de inicio de sesión que contiene el token de acceso, el tipo de token y el tiempo de expiración.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest peticion) {
        return authService.login(peticion.email(), peticion.password());
    }
}