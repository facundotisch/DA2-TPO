package logired.backend.deposito.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import logired.backend.deposito.entity.Deposito;

public interface DepositoRepository extends JpaRepository<Deposito, Long> {

    Optional<Deposito> findFirstByOrderByIdAsc();
}