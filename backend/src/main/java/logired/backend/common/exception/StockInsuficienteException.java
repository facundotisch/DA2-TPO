package logired.backend.common.exception;

/**
 * No hay stock suficiente para reservar las unidades pedidas. Se traduce a HTTP 409.
 */
public class StockInsuficienteException extends ConflictoException {

    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
