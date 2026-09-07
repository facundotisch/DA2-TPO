package logired.backend.inventario.service;

import java.util.List;
import java.util.Map;

import logired.backend.inventario.dto.ReservaStockRequest;
import logired.backend.inventario.dto.ReservaStockResponse;
import logired.backend.inventario.entity.ItemInventario;

/**
 * Interfaz para el Componente 3 de LogiRed: InventarioService (Stateful).
 */
public interface InventarioService {

    /**
     * Operación oficial definida en el Informe Técnico.
     */
    ReservaStockResponse reservarStock(String sku, String deposito, int cantidad);

    ReservaStockResponse liberarStock(String sku, String deposito, int cantidad);

    List<ItemInventario> obtenerTodos();

    Map<String, Integer> obtenerReservasActivasSesion();

    String getIdSesionConversacional();
}
