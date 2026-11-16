package logired.backend.producto.dto;

public record UbicacionResponse(
        Long depositoId,
        String nombre,
        String direccion,
        double latitud,
        double longitud,
        String openStreetMapUrl) {
}