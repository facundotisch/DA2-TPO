package logired.backend.usuario.service;

import logired.backend.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void rechazaContrasenaEnBlancoAntesDeConsultarDuplicados() {
        AuthService service = new AuthService(usuarioRepository, passwordEncoder);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("usuario-valido", " "));

        verify(usuarioRepository, never()).existsByUsername(anyString());
    }

    @Test
    void rechazaContrasenasDeMenosDeOchoCaracteres() {
        AuthService service = new AuthService(usuarioRepository, passwordEncoder);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("usuario-valido", "1234567"));

        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
