package logired.backend.common.exception;

/**
 * Un sistema externo no respondió o respondió con error. Se traduce a HTTP 503.
 */
public class IntegracionNoDisponibleException extends IllegalStateException {

    public IntegracionNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
