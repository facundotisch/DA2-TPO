package logired.backend.ruteo.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;
import logired.backend.ruteo.strategy.EstrategiaRuteo;

/**
 * Implementación del Componente 5 (RuteoService).
 * Actúa como Contexto del patrón Strategy, seleccionando la estrategia adecuada
 * para calcular la ruta óptima de forma desacoplada y Stateless.
 */
@Service
public class RuteoServiceImpl implements RuteoService {

    private final Map<String, EstrategiaRuteo> estrategias;
    private final EstrategiaRuteo estrategiaPorDefecto;

    public RuteoServiceImpl(List<EstrategiaRuteo> listaEstrategias) {
        this.estrategias = listaEstrategias.stream()
                .collect(Collectors.toMap(EstrategiaRuteo::getIdentificador, e -> e));

        // Por defecto aplica distancia corta
        this.estrategiaPorDefecto = estrategias.getOrDefault("DISTANCIA_CORTA",
                listaEstrategias.isEmpty() ? null : listaEstrategias.get(0));
    }

    @Override
    public RutaResponse calcularRutaOptima(List<String> direcciones) {
        if (estrategiaPorDefecto == null) {
            throw new IllegalStateException("No hay estrategias de ruteo configuradas.");
        }
        return estrategiaPorDefecto.calcularRuta(direcciones);
    }

    @Override
    public RutaResponse calcularRutaConEstrategia(RutaRequest request) {
        if (request == null || request.getDirecciones() == null) {
            throw new IllegalArgumentException("La solicitud de ruteo no puede ser nula.");
        }

        String criterio = request.getCriterio();
        EstrategiaRuteo seleccionada = (criterio != null)
                ? estrategias.getOrDefault(criterio.toUpperCase(), estrategiaPorDefecto)
                : estrategiaPorDefecto;

        return seleccionada.calcularRuta(request.getDirecciones());
    }
}
