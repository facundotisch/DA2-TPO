package logired.backend.usuario.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import logired.backend.usuario.entity.Usuario;
import logired.backend.usuario.repository.UsuarioRepository;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner inicializarAdmin(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!usuarioRepository.existsByUsername("admin")) {
                usuarioRepository.save(new Usuario(
                        "admin",
                        passwordEncoder.encode("admin123"),
                        "ADMIN"));
            }
        };
    }
}
