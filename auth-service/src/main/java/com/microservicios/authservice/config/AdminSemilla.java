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

/** Crea el administrador "semilla" al arrancar, si no existe. */
@Component
public class AdminSemilla implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSemilla.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String id;
    private final String email;
    private final String password;

    /** Constructor para inyectar dependencias y valores de configuración. */
    public AdminSemilla(UsuarioRepository usuarios, PasswordEncoder encoder,
                        @Value("${admin.id:admin}") String id,
                        @Value("${admin.email:}") String email,
                        @Value("${admin.password:}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.id = id;
        this.email = email;
        this.password = password;
    }

    /**
     * Crea el administrador semilla si no existe y si los valores de configuración están definidos.
     * @param args incoming application arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD no definidos: no se crea el administrador semilla");
            return;
        }
        if (usuarios.existsByEmail(email)) {
            log.info("El administrador semilla ya existe: {}", email);
            return;
        }
        Usuario admin = new Usuario();
        admin.setEmpleadoId(id);
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRol(Rol.ADMIN);
        admin.setEstado(EstadoCuenta.ACTIVA);
        admin.setFechaCreacion(LocalDateTime.now());
        usuarios.save(admin);
        log.info("Administrador semilla creado: {}", email);
    }
}