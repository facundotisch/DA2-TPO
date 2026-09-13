package logired.backend.usuario.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.usuario.entity.Usuario;
import logired.backend.usuario.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request == null || request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username y password son obligatorios"));
        }

        try {
            Usuario usuario = authService.registrar(request.username(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegisterResponse(usuario.getUsername(), usuario.getRol()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Credenciales inválidas"));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            Usuario usuario = authService.buscarPorUsername(request.username());
            return ResponseEntity.ok(new LoginResponse("Login exitoso", usuario.getUsername(), usuario.getRol()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Credenciales inválidas"));
        }
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterResponse(String username, String rol) {
    }

    public record LoginResponse(String mensaje, String username, String rol) {
    }

    public record ErrorResponse(String mensaje) {
    }
}
