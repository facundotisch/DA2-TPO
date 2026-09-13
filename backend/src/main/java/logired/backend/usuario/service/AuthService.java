package logired.backend.usuario.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import logired.backend.usuario.entity.Usuario;
import logired.backend.usuario.repository.UsuarioRepository;

@Service
public class AuthService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario registrar(String username, String password) {
        String usernameNormalizado = username.trim();

        if (usernameNormalizado.isEmpty() || password.isBlank()) {
            throw new IllegalArgumentException("Username y password son obligatorios");
        }

        if (usuarioRepository.existsByUsername(usernameNormalizado)) {
            throw new IllegalArgumentException("El username ya existe");
        }

        Usuario usuario = new Usuario(
                usernameNormalizado,
                passwordEncoder.encode(password),
                "OPERADOR");

        return usuarioRepository.save(usuario);
    }

    public Usuario buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = buscarPorUsername(username);

        return User.withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRol())
                .build();
    }
}
