package logired.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner inicializarInventario(InventarioRepository inventarioRepository) {
		return args -> {
			if (inventarioRepository.count() == 0) {
				inventarioRepository.save(new ItemInventario("IND-001", "Zapatillas Urbanas Running", "Depósito Central Barracas", 45, 0));
				inventarioRepository.save(new ItemInventario("IND-002", "Campera Impermeable Térmica", "Depósito Central Barracas", 20, 0));
				inventarioRepository.save(new ItemInventario("ELEC-010", "Auriculares Bluetooth Pro", "Depósito Norte Munro", 60, 0));
				System.out.println("[POSTGRESQL SEED] Se inicializaron exitosamente los 3 ítems de depósitos en PostgreSQL.");
			}
		};
	}

}
