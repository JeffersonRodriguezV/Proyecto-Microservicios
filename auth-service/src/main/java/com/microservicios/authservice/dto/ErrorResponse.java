package com.microservicios.authservice.dto;

import java.time.LocalDateTime;

public record ErrorResponse(int status, String mensaje, LocalDateTime timestamp) {}