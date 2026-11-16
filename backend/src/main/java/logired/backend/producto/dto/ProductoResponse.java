package logired.backend.producto.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String descripcion,
        String categoria,
        BigDecimal precio,
        String moneda,
        VendedorResponse vendedor,
        int stockDisponible,
        int stockBloqueado,
        List<StockDepositoResponse> stockPorDeposito,
        UbicacionResponse ubicacion) {
}