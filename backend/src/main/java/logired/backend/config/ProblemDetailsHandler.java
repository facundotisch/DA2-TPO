package logired.backend.config;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import logired.backend.common.exception.ConflictoException;
import logired.backend.common.exception.IntegracionNoDisponibleException;
import logired.backend.common.exception.PrecondicionFallidaException;
import logired.backend.common.exception.RecursoNoEncontradoException;
import logired.backend.common.exception.StockInsuficienteException;
import logired.backend.pedido.exception.TransicionInvalidaException;

/**
 * Traduce todas las excepciones de la API REST a Problem Details (RFC 9457).
 *
 * Cada respuesta incluye además la propiedad "error" con el mismo texto que "detail",
 * para que el frontend que leía {"error": "..."} siga funcionando sin cambios.
 * Los errores inesperados devuelven 500 con un mensaje genérico: nunca se envían
 * stack traces ni mensajes internos al cliente.
 */
@RestControllerAdvice
public class ProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception,
                                             HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud inválido",
                "El cuerpo no es JSON válido o incluye campos que no se pueden modificar.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception,
                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Uno o más campos de la solicitud no son válidos.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception,
                                                HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Parámetro obligatorio",
                "Falta el parámetro: " + exception.getParameterName(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail handleNotFound(RecursoNoEncontradoException exception,
                                        HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Recurso inexistente",
                exception.getMessage(), request);
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail handleTransicionInvalida(TransicionInvalidaException exception,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Transición de estado inválida",
                exception.getMessage(), request);
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ProblemDetail handleStockInsuficiente(StockInsuficienteException exception,
                                                 HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Stock insuficiente",
                exception.getMessage(), request);
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail handleConflicto(ConflictoException exception,
                                         HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflicto con el estado actual",
                exception.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrencia(OptimisticLockingFailureException exception,
                                            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Modificación concurrente",
                "El recurso fue modificado por otra solicitud. Volvé a consultarlo y reintentá.", request);
    }

    @ExceptionHandler(PrecondicionFallidaException.class)
    public ProblemDetail handlePrecondicion(PrecondicionFallidaException exception,
                                            HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Precondición fallida",
                exception.getMessage(), request);
    }

    @ExceptionHandler(IntegracionNoDisponibleException.class)
    public ProblemDetail handleIntegracion(IntegracionNoDisponibleException exception,
                                           HttpServletRequest request) {
        log.warn("Integración no disponible en {}: {}", request.getRequestURI(), exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Integración no disponible",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                           HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado",
                "Tu usuario no tiene permiso para esta operación.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception,
                                              HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "No autenticado",
                "Las credenciales faltan o no son válidas.", request);
    }

    /**
     * Excepciones propias de Spring MVC (método no permitido, ruta inexistente, etc.)
     * ya traen su propio ProblemDetail. El resto es un error interno: se registra
     * completo en el log y al cliente solo le llega un mensaje genérico.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            ProblemDetail body = errorResponse.getBody();
            body.setProperty("error", body.getDetail());
            return body;
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Si persiste, contactá al equipo de soporte.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://logired.local/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("error", detail);
        return problem;
    }
}
