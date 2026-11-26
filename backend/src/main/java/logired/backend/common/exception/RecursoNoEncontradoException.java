package logired.backend.common.exception;

/**
 * El recurso pedido no existe. Se traduce a HTTP 404 en formato Problem Details.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
