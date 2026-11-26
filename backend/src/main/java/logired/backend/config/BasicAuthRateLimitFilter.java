package logired.backend.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import logired.backend.usuario.service.LoginRateLimiter;

/**
 * Aplica el mismo límite de intentos del login a las credenciales Basic Auth.
 *
 * Antes solo /api/auth/login contaba fallos, pero cualquier endpoint protegido valida
 * usuario y contraseña con Basic Auth, así que se podía probar contraseñas sin límite
 * contra, por ejemplo, GET /api/pedido. Este filtro corre antes de la autenticación Basic:
 * si la IP superó el límite responde 429 sin validar nada, y si la autenticación termina
 * en 401 registra el fallo. El contador es el mismo que usa el login.
 *
 * No se registra como @Component para que Spring Boot no lo agregue también como filtro
 * global del servlet; SecurityConfig lo inserta en la cadena de seguridad.
 */
public class BasicAuthRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter rateLimiter;

    public BasicAuthRateLimitFilter(LoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String direccion = request.getRemoteAddr();
        long retryAfter = rateLimiter.retryAfterSeconds(direccion);
        if (retryAfter > 0) {
            responderDemasiadosIntentos(request, response, retryAfter);
            return;
        }

        filterChain.doFilter(request, response);

        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            rateLimiter.recordFailure(direccion);
        }
    }

    private void responderDemasiadosIntentos(HttpServletRequest request, HttpServletResponse response,
                                             long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        String detalle = "Se alcanzó el límite de intentos de autenticación. Intente nuevamente más tarde.";
        response.getWriter().write("{\"type\":\"https://logired.local/problems/429\","
                + "\"title\":\"Demasiados intentos de autenticación\","
                + "\"status\":429,"
                + "\"detail\":\"" + detalle + "\","
                + "\"error\":\"" + detalle + "\","
                + "\"instance\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }
}
