package logired.backend.usuario.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import logired.backend.usuario.entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);
}
