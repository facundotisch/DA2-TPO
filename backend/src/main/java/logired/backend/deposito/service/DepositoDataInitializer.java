package logired.backend.deposito.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.repository.PedidoRepository;

@Component
@Order(2)
public class DepositoDataInitializer implements CommandLineRunner {

    private final DepositoRepository depositoRepository;
    private final PedidoRepository pedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final String nombre;
    private final String direccion;
    private final double latitud;
    private final double longitud;

    public DepositoDataInitializer(
            DepositoRepository depositoRepository,
            PedidoRepository pedidoRepository,
            InventarioRepository inventarioRepository,
            @Value("${logired.deposito.default.nombre:Deposito central (demo)}") String nombre,
            @Value("${logired.deposito.default.direccion:Av. 9 de Julio s/n, junto al Obelisco, CABA}") String direccion,
            @Value("${logired.deposito.default.latitud:-34.6037}") double latitud,
            @Value("${logired.deposito.default.longitud:-58.3816}") double longitud) {
        this.depositoRepository = depositoRepository;
        this.pedidoRepository = pedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.nombre = nombre;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    @Override
    public void run(String... args) {
        Deposito deposito = depositoRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> depositoRepository.save(
                        new Deposito(nombre, direccion, latitud, longitud)));

        List<Pedido> pedidosSinDeposito = pedidoRepository.buscarPedidosSinDeposito();
        pedidosSinDeposito.forEach(pedido -> pedido.setDeposito(deposito));
        pedidoRepository.saveAll(pedidosSinDeposito);

        List<String> nombresDepositosStock = inventarioRepository.findAll().stream()
                .map(item -> item.getDeposito())
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .collect(Collectors.toList());
        for (String nombreStock : nombresDepositosStock) {
            if (depositoRepository.findByNombre(nombreStock).isEmpty()) {
                boolean munro = nombreStock.toLowerCase().contains("munro");
                String direccionStock = munro ? "Av. Mitre 3200, Munro" : "Av. Iriarte 3500, CABA";
                double latitudStock = munro ? -34.5251 : -34.6462;
                double longitudStock = munro ? -58.5254 : -58.3858;
                depositoRepository.save(new Deposito(nombreStock, direccionStock, latitudStock, longitudStock));
            }
        }
    }
}