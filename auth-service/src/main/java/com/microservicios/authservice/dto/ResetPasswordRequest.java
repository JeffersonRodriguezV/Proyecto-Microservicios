package com.microservicios.authservice.dto;

import jakarta.validation.constraints.NotBlank;
// Registro para solicitar el restablecimiento de contraseña
public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}