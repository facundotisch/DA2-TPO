package logired.backend.mensajeria;

public record EventoLogDto(
    String suscriptor,
    String icono,
    String accion,
    String timestamp,
    String payload
) {}
