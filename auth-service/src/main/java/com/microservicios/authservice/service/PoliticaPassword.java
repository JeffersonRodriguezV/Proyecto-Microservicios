package com.microservicios.authservice.service;

import com.microservicios.authservice.exception.PasswordInvalidaException;
import org.springframework.stereotype.Component;

/** Política de seguridad: al menos 8 caracteres, con mayúscula, minúscula y número. */
@Component
public class PoliticaPassword {

    public static final String REGLA =
            "La contraseña debe tener al menos 8 caracteres, con mayúscula, minúscula y número";

    public void validar(String password) {
        if (password == null
                || password.length() < 8
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")) {
            throw new PasswordInvalidaException(REGLA);
        }
    }
}