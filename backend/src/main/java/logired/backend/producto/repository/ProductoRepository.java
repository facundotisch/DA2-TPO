package logired.backend.producto.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import logired.backend.producto.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findBySku(String sku);
}