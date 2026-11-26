package logired.backend.common.exception;

/**
 * La operación choca con el estado actual del recurso (por ejemplo, borrar stock reservado).
 * Se traduce a HTTP 409. Extiende IllegalStateException para no romper a quienes ya la capturaban.
 */
public class ConflictoException extends IllegalStateException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
