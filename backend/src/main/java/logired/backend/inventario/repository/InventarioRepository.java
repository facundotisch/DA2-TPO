package logired.backend.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import logired.backend.inventario.entity.ItemInventario;

/**
 * Patrón DAO / Repository (Spring Data JPA) para Inventario.
 */
@Repository
public interface InventarioRepository extends JpaRepository<ItemInventario, Long> {

    Optional<ItemInventario> findBySkuAndDeposito(String sku, String deposito);

    List<ItemInventario> findByDeposito(String deposito);
}
