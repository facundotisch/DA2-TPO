package logired.backend.common.exception;

/**
 * El cliente envió un If-Match que no coincide con el ETag actual. Se traduce a HTTP 412.
 */
public class PrecondicionFallidaException extends RuntimeException {

    public PrecondicionFallidaException(String mensaje) {
        super(mensaje);
    }
}
