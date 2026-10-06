package com.microservicios.authservice.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}