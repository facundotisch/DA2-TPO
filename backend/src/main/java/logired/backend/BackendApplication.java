package logired.backend;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;

import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	@Order(1)
	public CommandLineRunner inicializarInventario(InventarioRepository inventarioRepository) {
		return args -> {
			if (inventarioRepository.count() == 0) {
				List<ItemInventario> items = List.of(
						new ItemInventario("IND-001", "Zapatillas Urbanas Running", "Depósito Central Barracas", 450, 0),
						new ItemInventario("IND-002", "Campera Impermeable Térmica", "Depósito Central Barracas", 200, 0),
						new ItemInventario("ELEC-010", "Auriculares Bluetooth Pro", "Depósito Norte Munro", 600, 0),
						new ItemInventario("TEST-01e4973d-6dc4-4194-b3f8-b8e11e882c53", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("TEST-bac4c0bd-06c5-47e9-b6c3-fbc3c3f1cc62", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("TEST-9605e638-7cba-4f40-b3ab-75628be7f3ce", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("TEST-1e64fcd4-d250-4591-88bb-dd17ba902b2f", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("TEST-968806ae-47bb-44e6-8720-b457c413a62e", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("TEST-6770feaa-2694-403b-b44f-e63981002651", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("LOW-6a41d3c8-5c68-470d-9ed8-abbea6a36e48", "Item limitado", "Deposito sin stock", 10, 0),
						new ItemInventario("SESSION-a54d95d0-915f-4a3b-aa85-8101e9b18c77", "Item de sesion", "Deposito por sesion", 50, 0),
						new ItemInventario("TEST-84e5a6b1-b650-42eb-a6fa-e951da326176", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("LOW-2dcbac5f-a2c4-49e6-a70e-81ef069bdf9d", "Item limitado", "Deposito sin stock", 10, 0),
						new ItemInventario("SESSION-786ef36b-6fce-4d09-81e2-90fd96551610", "Item de sesion", "Deposito por sesion", 50, 0),
						new ItemInventario("TEST-1373b28c-7ae2-4d33-b8c1-7c1c8959c000", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("LOW-89d44b95-c529-4bc9-b708-241f7a4b52c2", "Item limitado", "Deposito sin stock", 10, 0),
						new ItemInventario("SESSION-287f8a67-9ebc-4bd3-8308-0911fffe0b5d", "Item de sesion", "Deposito por sesion", 50, 0),
						new ItemInventario("TEST-a02471ae-0d09-474d-9362-5b8194ad3e3f", "Item de prueba", "Deposito de prueba", 41, 0),
						new ItemInventario("LOW-1533e8ed-edb6-457d-a745-ce06a1132e09", "Item limitado", "Deposito sin stock", 10, 0),
						new ItemInventario("SESSION-80065215-9b84-4b68-92f0-3e1b82e9d9af", "Item de sesion", "Deposito por sesion", 50, 0),
						new ItemInventario("TEST-38d107fa-27b4-41b9-b0c1-bd8baeca770d", "Item de prueba", "Deposito de prueba", 41, 0)
				);
				inventarioRepository.saveAll(items);
				System.out.println("[POSTGRESQL SEED] Se inicializaron exitosamente los 21 ítems del catálogo LogiRed en PostgreSQL.");
			}
		};
	}

}
