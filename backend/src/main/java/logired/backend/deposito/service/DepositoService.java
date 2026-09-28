package logired.backend.deposito.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.repository.DepositoRepository;

@Service
public class DepositoService {

    private final DepositoRepository depositoRepository;

    public DepositoService(DepositoRepository depositoRepository) {
        this.depositoRepository = depositoRepository;
    }

    @Transactional(readOnly = true)
    public Deposito obtenerPredeterminado() {
        return depositoRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No hay un deposito configurado"));
    }
}