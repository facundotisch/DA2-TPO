package logired.backend.producto.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import logired.backend.producto.dto.VendedorResponse;
import logired.backend.producto.entity.Vendedor;
import logired.backend.producto.repository.VendedorRepository;

@Service
@Transactional(readOnly = true)
public class VendedorService {

    private final VendedorRepository vendedorRepository;

    public VendedorService(VendedorRepository vendedorRepository) {
        this.vendedorRepository = vendedorRepository;
    }

    public List<VendedorResponse> obtenerTodos() {
        return vendedorRepository.findAll().stream().map(this::convertir).toList();
    }

    public Optional<VendedorResponse> obtenerPorId(Long id) {
        return vendedorRepository.findById(id).map(this::convertir);
    }

    private VendedorResponse convertir(Vendedor vendedor) {
        return new VendedorResponse(vendedor.getId(), vendedor.getNombre(), vendedor.getEmail(), vendedor.getDescripcion());
    }
}