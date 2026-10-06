package com.microservicios.authservice.Repository;
import com.microservicios.authservice.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmpleadoId(String empleadoId);
    boolean existsByEmail(String email);
}
