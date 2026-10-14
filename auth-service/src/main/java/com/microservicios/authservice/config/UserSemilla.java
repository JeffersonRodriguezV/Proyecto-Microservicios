package com.microservicios.authservice.config;

import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.Rol;
import com.microservicios.authservice.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * Crea un usuario de rol USER "semilla" al arrancar, si no existe.
 * Existe para que las pruebas BDD de seguridad (Reto 6) tengan un USER con
 * credenciales conocidas sin depender del flujo completo de onboarding.
 */
@Component
public class UserSemilla implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSemilla.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String id;
    private final String email;
    private final String password;

    public UserSemilla(UsuarioRepository usuarios, PasswordEncoder encoder,
                       @Value("${seed.user.id:user-semilla}") String id,
                       @Value("${seed.user.email:}") String email,
                       @Value("${seed.user.password:}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.id = id;
        this.email = email;
        this.password = password;
    }

    /** Idempotente: reiniciar el contenedor no duplica ni falla. */
    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("SEED_USER_EMAIL / SEED_USER_PASSWORD no definidos: no se crea el usuario semilla");
            return;
        }
        if (usuarios.existsByEmail(email)) {
            log.info("El usuario semilla ya existe: {}", email);
            return;
        }
        Usuario user = new Usuario();
        user.setEmpleadoId(id);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setRol(Rol.USER);
        user.setEstado(EstadoCuenta.ACTIVA);
        user.setFechaCreacion(LocalDateTime.now());
        usuarios.save(user);
        log.info("Usuario semilla creado: {}", email);
    }
}