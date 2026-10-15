package com.microservicios.authservice.service;

import com.microservicios.authservice.exception.PasswordInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Política: mínimo 8 caracteres, con mayúscula, minúscula y número. Lógica pura, sin mocks. */
class PoliticaPasswordTest {

    private final PoliticaPassword politica = new PoliticaPassword();

    @Test
    @DisplayName("acepta una contraseña que cumple todas las reglas")
    void aceptaPasswordValida() {
        assertDoesNotThrow(() -> politica.validar("Segura123"));
    }

    @Test
    @DisplayName("acepta exactamente 8 caracteres (límite)")
    void aceptaLimiteDeOchoCaracteres() {
        assertDoesNotThrow(() -> politica.validar("Abcdef12"));
    }

    @ParameterizedTest(name = "rechaza \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {
            "Abc1234",      // 7 caracteres: justo debajo del límite
            "segura123",    // sin mayúscula
            "SEGURA123",    // sin minúscula
            "SeguraSegura"  // sin número
    })
    @DisplayName("rechaza contraseñas que incumplen alguna regla")
    void rechazaPasswordsInvalidas(String password) {
        PasswordInvalidaException ex = assertThrows(PasswordInvalidaException.class,
                () -> politica.validar(password));
        assertEquals(PoliticaPassword.REGLA, ex.getMessage());
    }
}