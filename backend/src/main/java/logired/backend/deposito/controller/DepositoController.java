package logired.backend.deposito.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.deposito.entity.Deposito;
import logired.backend.deposito.service.DepositoService;

@RestController
@RequestMapping("/api/deposito")
public class DepositoController {

    private final DepositoService depositoService;

    public DepositoController(DepositoService depositoService) {
        this.depositoService = depositoService;
    }

    @GetMapping("/default")
    public ResponseEntity<Deposito> obtenerPredeterminado() {
        return ResponseEntity.ok(depositoService.obtenerPredeterminado());
    }
}