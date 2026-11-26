package logired.backend.integraciones;

/**
 * Respuesta del asistente de IA.
 *
 * @param respuesta texto para mostrar al usuario
 * @param fuente    "proveedor" si respondió el modelo, "fallback" si se usó la respuesta de respaldo
 * @param degradada true cuando el proveedor no estuvo disponible y se respondió con el respaldo
 */
public record RespuestaIa(String respuesta, String fuente, boolean degradada) {

    static RespuestaIa delProveedor(String texto) {
        return new RespuestaIa(texto, "proveedor", false);
    }

    static RespuestaIa respaldo(String texto) {
        return new RespuestaIa(texto, "fallback", true);
    }
}
