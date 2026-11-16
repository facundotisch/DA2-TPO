package logired.backend.inventario.dto;

import java.math.BigDecimal;

public record ProductoStockRequest(
        String sku,
        String nombre,
        String descripcion,
        String categoria,
        BigDecimal precio,
        Integer cantidad,
        String deposito) {
}
