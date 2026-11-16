package logired.backend.producto.dto;

import java.math.BigDecimal;

public record ProductoCatalogoRequest(
        String sku,
        String nombre,
        String descripcion,
        String categoria,
        BigDecimal precio,
        String moneda,
        Integer stockDisponible,
        Integer stockBloqueado) {
}
