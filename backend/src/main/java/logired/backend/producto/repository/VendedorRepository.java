package logired.backend.producto.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import logired.backend.producto.entity.Vendedor;

public interface VendedorRepository extends JpaRepository<Vendedor, Long> {

    Optional<Vendedor> findByNombre(String nombre);
}