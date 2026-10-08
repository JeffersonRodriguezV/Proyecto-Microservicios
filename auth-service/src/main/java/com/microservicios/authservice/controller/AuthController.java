package com.microservicios.authservice.controller;

import com.microservicios.authservice.dto.ChangePasswordRequest;
import com.microservicios.authservice.dto.ErrorResponse;
import com.microservicios.authservice.dto.LoginRequest;
import com.microservicios.authservice.dto.LoginResponse;
import com.microservicios.authservice.dto.MensajeResponse;
import com.microservicios.authservice.dto.RecoverPasswordRequest;
import com.microservicios.authservice.dto.ResetPasswordRequest;
import com.microservicios.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Login, recuperación y cambio de contraseña")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión", description = "Devuelve un JWT de acceso (claims: sub, role, email, iat, exp).")
    @ApiResponse(responseCode = "200", description = "Credenciales válidas")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Cuenta no activa (pendiente, suspendida o desactivada)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest peticion) {
        return authService.login(peticion.email(), peticion.password());
    }

    @Operation(summary = "Solicitar recuperación de contraseña",
            description = "Siempre responde 200 para no revelar si el correo existe. "
                    + "El token de recuperación se publica como evento usuario.recuperacion.")
    @ApiResponse(responseCode = "200", description = "Solicitud recibida")
    @PostMapping("/recover-password")
    public MensajeResponse recover(@Valid @RequestBody RecoverPasswordRequest peticion) {
        authService.recuperarPassword(peticion.email());
        return new MensajeResponse("Si el correo está registrado, recibirá instrucciones para restablecer su contraseña");
    }

    @Operation(summary = "Restablecer / activar contraseña",
            description = "Usa el token de recuperación. Si la cuenta estaba PENDIENTE_ACTIVACION pasa a ACTIVA.")
    @ApiResponse(responseCode = "200", description = "Contraseña establecida")
    @ApiResponse(responseCode = "400", description = "Token inválido/expirado o contraseña que no cumple la política",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Cuenta desactivada permanentemente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/reset-password")
    public MensajeResponse reset(@Valid @RequestBody ResetPasswordRequest peticion) {
        authService.restablecerPassword(peticion.token(), peticion.newPassword());
        return new MensajeResponse("Contraseña establecida correctamente");
    }

    @Operation(summary = "Cambiar contraseña (usuario autenticado)",
            description = "Requiere el JWT de acceso y la contraseña actual. Un token de recuperación no es válido aquí.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva contraseña débil",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o de recuperación",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Cuenta no activa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/change-password")
    public MensajeResponse change(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest peticion) {
        authService.cambiarPassword(authorization, peticion.currentPassword(), peticion.newPassword());
        return new MensajeResponse("Contraseña actualizada correctamente");
    }
}
