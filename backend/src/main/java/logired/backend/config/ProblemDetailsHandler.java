package logired.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ProblemDetailsHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException exception,
                                            HttpServletRequest request) {
        return problem(HttpStatus.BAD_GATEWAY, "Integración no disponible",
                exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception,
                                             HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Cuerpo JSON inválido",
                "El cuerpo de la solicitud no es JSON válido.", request);
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

    private ProblemDetail problem(HttpStatus status, String title,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://logired.local/problems/"
                + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
