package logired.backend.usuario.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.usuario.entity.Usuario;
import logired.backend.usuario.service.AuthService;
import logired.backend.usuario.service.LoginRateLimiter;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.loginRateLimiter = loginRateLimiter;
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String remoteAddress = httpRequest.getRemoteAddr();
        long retryAfter = loginRateLimiter.retryAfterSeconds(remoteAddress);
        if (retryAfter > 0) {
            return tooManyRequests(retryAfter);
        }

        if (request == null || request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Credenciales inválidas"));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            loginRateLimiter.clear(remoteAddress);
            Usuario usuario = authService.buscarPorUsername(request.username());
            return ResponseEntity.ok(new LoginResponse("Login exitoso", usuario.getUsername(), usuario.getRol()));
        } catch (AuthenticationException exception) {
            retryAfter = loginRateLimiter.recordFailure(remoteAddress);
            if (retryAfter > 0) {
                return tooManyRequests(retryAfter);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Credenciales inválidas"));
        }
    }

    private ResponseEntity<ProblemDetail> tooManyRequests(long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Se alcanzó el límite de intentos de inicio de sesión. Intente nuevamente más tarde.");
        problem.setTitle("Demasiados intentos de inicio de sesión");
        problem.setType(URI.create("https://logired.local/problems/429"));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
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
