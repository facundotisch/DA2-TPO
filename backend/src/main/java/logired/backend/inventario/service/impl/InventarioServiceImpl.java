package logired.backend.inventario.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.annotation.SessionScope;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import logired.backend.inventario.dto.ReservaStockResponse;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.inventario.service.InventarioService;

/**
 * COMPONENTE STATEFUL: InventarioService (Componente 3 de LogiRed)
 * - Scope: @SessionScope (mantiene estado conversacional del comercio durante la preparación del envío).
 * - Ciclo de vida gestionado por el contenedor Spring mediante @PostConstruct y @PreDestroy.
 */
@Service
@SessionScope
public class InventarioServiceImpl implements InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioServiceImpl.class);

    private final InventarioRepository inventarioRepository;

    // Estado conversacional mantenido durante la sesión HTTP del usuario (Stateful)
    private String idSesionConversacional;
    private LocalDateTime fechaInicioSesion;
    private final Map<String, Integer> reservasActivasEnSesion = new ConcurrentHashMap<>();

    public InventarioServiceImpl(InventarioRepository inventarioRepository) {
        this.inventarioRepository = inventarioRepository;
    }

    /**
     * CALLBACK DE CICLO DE VIDA (Inicialización por el Contenedor)
     */
    @PostConstruct
    public void inicializarSesion() {
        this.idSesionConversacional = "SESS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.fechaInicioSesion = LocalDateTime.now();

        log.info("=========================================================================");
        log.info("[CICLO DE VIDA CONTEXTO - STATEFUL] @PostConstruct invocado.");
        log.info("El contenedor Spring instanció un nuevo InventarioService en ámbito @SessionScope.");
        log.info("ID Sesión Conversacional: {} | Creado: {}", idSesionConversacional, fechaInicioSesion);
        log.info("=========================================================================");

        // Seed inicial si la base está vacía (datos iniciales en PostgreSQL)
        if (inventarioRepository.count() == 0) {
            inventarioRepository.save(new ItemInventario("IND-001", "Zapatillas Urbanas Running", "Depósito Central Barracas", 45, 0));
            inventarioRepository.save(new ItemInventario("IND-002", "Campera Impermeable Térmica", "Depósito Central Barracas", 20, 0));
            inventarioRepository.save(new ItemInventario("ELEC-010", "Auriculares Bluetooth Pro", "Depósito Norte Munro", 60, 0));
            log.info("[POSTGRESQL SEED] Se inicializaron 3 ítems en la tabla items_inventario.");
        }
    }

    /**
     * CALLBACK DE CICLO DE VIDA (Destrucción por el Contenedor)
     * Se dispara automáticamente cuando expira la sesión HTTP (ej. 5 min timeout)
     * o se invalida manualmente.
     */
    @PreDestroy
    public void finalizarSesion() {
        log.info("=========================================================================");
        log.info("[CICLO DE VIDA CONTEXTO - STATEFUL] @PreDestroy invocado por el contenedor.");
        log.info("Finalizando sesión: {}. Liberando reservas pendientes...", idSesionConversacional);

        // Liberación automática de reservas huérfanas al cerrar la sesión
        for (Map.Entry<String, Integer> reserva : reservasActivasEnSesion.entrySet()) {
            String clave = reserva.getKey();
            int cantidad = reserva.getValue();
            String[] partes = clave.split("@");
            if (partes.length == 2) {
                String sku = partes[0];
                String deposito = partes[1];
                revertirReservaEnBase(sku, deposito, cantidad);
            }
        }
        reservasActivasEnSesion.clear();
        log.info("Todas las reservas de la sesión {} fueron liberadas automáticamente.", idSesionConversacional);
        log.info("=========================================================================");
    }

    @Override
    @Transactional
    public ReservaStockResponse reservarStock(String sku, String deposito, int cantidad) {
        if (cantidad <= 0) {
            return new ReservaStockResponse(false, sku, deposito, 0, 0, 0,
                    "La cantidad a reservar debe ser mayor que cero.", idSesionConversacional, reservasActivasEnSesion);
        }

        Optional<ItemInventario> itemOpt = inventarioRepository.findBySkuAndDeposito(sku, deposito);
        if (itemOpt.isEmpty()) {
            return new ReservaStockResponse(false, sku, deposito, 0, 0, 0,
                    "Item no encontrado en el depósito indicado.", idSesionConversacional, reservasActivasEnSesion);
        }

        ItemInventario item = itemOpt.get();
        if (item.getStockDisponible() < cantidad) {
            return new ReservaStockResponse(false, sku, deposito, 0, item.getStockDisponible(), item.getStockBloqueado(),
                    String.format("Stock insuficiente. Disponible: %d, Solicitado: %d", item.getStockDisponible(), cantidad),
                    idSesionConversacional, reservasActivasEnSesion);
        }

        // 1. Descuenta disponible y suma a bloqueado en PostgreSQL
        item.setStockDisponible(item.getStockDisponible() - cantidad);
        item.setStockBloqueado(item.getStockBloqueado() + cantidad);
        inventarioRepository.save(item);

        // 2. Almacena la reserva en el estado conversacional de la sesión (Stateful)
        String claveReserva = sku + "@" + deposito;
        int acumulado = reservasActivasEnSesion.getOrDefault(claveReserva, 0) + cantidad;
        reservasActivasEnSesion.put(claveReserva, acumulado);

        log.info("[RESERVA STATEFUL EXITOSA] Sesión: {} | SKU: {} | Depósito: {} | Cantidad: {} (Total sesión: {})",
                idSesionConversacional, sku, deposito, cantidad, acumulado);

        return new ReservaStockResponse(
                true,
                sku,
                deposito,
                cantidad,
                item.getStockDisponible(),
                item.getStockBloqueado(),
                String.format("Reserva temporal exitosa (Hold de sesión): %d unidades retenidas.", cantidad),
                idSesionConversacional,
                Collections.unmodifiableMap(reservasActivasEnSesion)
        );
    }

    @Override
    @Transactional
    public ReservaStockResponse liberarStock(String sku, String deposito, int cantidad) {
        String claveReserva = sku + "@" + deposito;
        int cantidadActual = reservasActivasEnSesion.getOrDefault(claveReserva, 0);

        if (cantidadActual <= 0) {
            return new ReservaStockResponse(false, sku, deposito, 0, 0, 0,
                    "No existen reservas activas en esta sesión para ese ítem.", idSesionConversacional, reservasActivasEnSesion);
        }

        int aLiberar = Math.min(cantidad, cantidadActual);
        revertirReservaEnBase(sku, deposito, aLiberar);

        int restante = cantidadActual - aLiberar;
        if (restante > 0) {
            reservasActivasEnSesion.put(claveReserva, restante);
        } else {
            reservasActivasEnSesion.remove(claveReserva);
        }

        Optional<ItemInventario> itemOpt = inventarioRepository.findBySkuAndDeposito(sku, deposito);
        int disponible = itemOpt.map(ItemInventario::getStockDisponible).orElse(0);
        int bloqueado = itemOpt.map(ItemInventario::getStockBloqueado).orElse(0);

        return new ReservaStockResponse(
                true,
                sku,
                deposito,
                aLiberar,
                disponible,
                bloqueado,
                String.format("Liberación exitosa: %d unidades devueltas al stock disponible.", aLiberar),
                idSesionConversacional,
                Collections.unmodifiableMap(reservasActivasEnSesion)
        );
    }

    private void revertirReservaEnBase(String sku, String deposito, int cantidad) {
        inventarioRepository.findBySkuAndDeposito(sku, deposito).ifPresent(item -> {
            int nuevoBloqueado = Math.max(0, item.getStockBloqueado() - cantidad);
            item.setStockBloqueado(nuevoBloqueado);
            item.setStockDisponible(item.getStockDisponible() + cantidad);
            inventarioRepository.save(item);
        });
    }

    @Override
    public List<ItemInventario> obtenerTodos() {
        return inventarioRepository.findAll();
    }

    @Override
    public Map<String, Integer> obtenerReservasActivasSesion() {
        return Collections.unmodifiableMap(reservasActivasEnSesion);
    }

    @Override
    public String getIdSesionConversacional() {
        return idSesionConversacional;
    }
}
