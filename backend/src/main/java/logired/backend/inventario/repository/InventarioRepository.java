package logired.backend.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import logired.backend.inventario.entity.ItemInventario;

/**
 * Patrón DAO / Repository (Spring Data JPA) para Inventario.
 */
public interface InventarioRepository extends JpaRepository<ItemInventario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ItemInventario> findBySkuAndDeposito(String sku, String deposito);

    List<ItemInventario> findBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ItemInventario item where item.sku = :sku")
    List<ItemInventario> findAllForUpdateBySku(@Param("sku") String sku);

    List<ItemInventario> findByDeposito(String deposito);
}
