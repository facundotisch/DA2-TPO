package logired.backend.ruteo.service;

import java.util.List;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;

/**
 * Interfaz de Servicio SOA Reutilizable: Componente 5 de LogiRed.
 * - Contrato explícito e independiente de la implementación (Principio SOA 1).
 * - Reutilizado en creación de despacho (Frontend), hoja de ruta (RepartidorService)
 *   y decisión de derivación externa (IntegracionTransportistasService).
 */
public interface RuteoService {

    /**
     * Operación oficial definida en el Informe Técnico (Sección 2, Componente 5).
     */
    RutaResponse calcularRutaOptima(List<String> direcciones);

    /**
     * Operación parametrizada que aplica el patrón Strategy (corta vs rápida).
     */
    RutaResponse calcularRutaConEstrategia(RutaRequest request);
}
