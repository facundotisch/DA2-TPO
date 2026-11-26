package logired.backend.usuario.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import logired.backend.usuario.entity.Usuario;
import logired.backend.usuario.repository.UsuarioRepository;

/**
 * Crea los usuarios iniciales "admin" y "operador" con contraseñas tomadas de variables
 * de entorno (LOGIRED_ADMIN_PASSWORD y LOGIRED_OPERADOR_PASSWORD).
 *
 * Antes las contraseñas estaban escritas en el código y se creaban en cualquier entorno.
 * Si la variable no está definida, el usuario no se crea y se deja un aviso en el log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner inicializarUsuarios(UsuarioRepository usuarioRepository,
                                                 PasswordEncoder passwordEncoder,
                                                 @Value("${logired.seed.admin-password:}") String adminPassword,
                                                 @Value("${logired.seed.operador-password:}") String operadorPassword) {
        return args -> {
            crearSiFalta(usuarioRepository, passwordEncoder, "admin", adminPassword, "ADMIN");
            crearSiFalta(usuarioRepository, passwordEncoder, "operador", operadorPassword, "OPERADOR");
        };
    }

    private void crearSiFalta(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                              String username, String password, String rol) {
        if (usuarioRepository.existsByUsername(username)) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No se creó el usuario '{}': falta su contraseña en las variables de entorno.", username);
            return;
        }
        usuarioRepository.save(new Usuario(username, passwordEncoder.encode(password), rol));
        log.info("Usuario inicial '{}' creado con rol {}.", username, rol);
    }
}
