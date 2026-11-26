package logired.backend.legado;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySoapControllerTests {

    @Test
    void rechazaSoapConDoctypeProtegiendoContraXxe() throws Exception {
        RuteoService ruteoService = mock(RuteoService.class);
        LegacySoapController controller = new LegacySoapController(ruteoService);
        HttpServletRequest request = soapRequest("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><x:CalcularRutaRequest xmlns:x="http://logired/backend/legacy"/></soap:Body>
                </soap:Envelope>
                """);

        var respuesta = controller.recibirSolicitud(request);
        String response = new String(respuesta.getBody(), StandardCharsets.UTF_8);

        // SOAP 1.1: todo Fault viaja con HTTP 500; soap:Client indica error de quien llama.
        assertEquals(500, respuesta.getStatusCode().value());
        assertTrue(response.contains("Fault"));
        assertTrue(response.contains("soap:Client"));
        assertTrue(response.contains("entidades"));
        verify(ruteoService, never()).calcularRutaConEstrategia(any(RutaRequest.class));
    }

    @Test
    void procesaSolicitudSoapValida() throws Exception {
        RuteoService ruteoService = mock(RuteoService.class);
        when(ruteoService.calcularRutaConEstrategia(any(RutaRequest.class)))
                .thenReturn(new RutaResponse(
                        java.util.List.of("Central", "Destino"),
                        3.5, 12, "DISTANCIA_CORTA", "Ruta calculada"));
        LegacySoapController controller = new LegacySoapController(ruteoService);
        HttpServletRequest request = soapRequest("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:lg="http://logired/backend/legacy">
                  <soap:Body>
                    <lg:CalcularRutaRequest>
                      <lg:direccion>Central</lg:direccion>
                      <lg:direccion>Destino</lg:direccion>
                      <lg:criterio>DISTANCIA_CORTA</lg:criterio>
                    </lg:CalcularRutaRequest>
                  </soap:Body>
                </soap:Envelope>
                """);

        var respuesta = controller.recibirSolicitud(request);
        String response = new String(respuesta.getBody(), StandardCharsets.UTF_8);

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(response.contains("CalcularRutaResponse"));
        assertTrue(response.contains("Central"));
        assertTrue(response.contains("Destino"));
        verify(ruteoService).calcularRutaConEstrategia(any(RutaRequest.class));
    }

    @Test
    void faultTecnicoNoFiltraElMensajeInterno() throws Exception {
        RuteoService ruteoService = mock(RuteoService.class);
        when(ruteoService.calcularRutaConEstrategia(any(RutaRequest.class)))
                .thenThrow(new IllegalStateException("detalle interno: conexión a base caída"));
        LegacySoapController controller = new LegacySoapController(ruteoService);

        var respuesta = controller.recibirSolicitud(soapRequest(solicitudValida()));
        String response = new String(respuesta.getBody(), StandardCharsets.UTF_8);

        assertEquals(500, respuesta.getStatusCode().value());
        assertTrue(response.contains("soap:Server"));
        assertFalse(response.contains("detalle interno"));
    }

    @Test
    void faultDeNegocioCuandoFaltanDirecciones() throws Exception {
        RuteoService ruteoService = mock(RuteoService.class);
        LegacySoapController controller = new LegacySoapController(ruteoService);

        var respuesta = controller.recibirSolicitud(soapRequest("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:lg="http://logired/backend/legacy">
                  <soap:Body><lg:CalcularRutaRequest><lg:criterio>DISTANCIA_CORTA</lg:criterio></lg:CalcularRutaRequest></soap:Body>
                </soap:Envelope>
                """));
        String response = new String(respuesta.getBody(), StandardCharsets.UTF_8);

        assertEquals(500, respuesta.getStatusCode().value());
        assertTrue(response.contains("soap:Client"));
        assertTrue(response.contains("al menos una dirección"));
        verify(ruteoService, never()).calcularRutaConEstrategia(any(RutaRequest.class));
    }

    private String solicitudValida() {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:lg="http://logired/backend/legacy">
                  <soap:Body>
                    <lg:CalcularRutaRequest>
                      <lg:direccion>Central</lg:direccion>
                      <lg:criterio>DISTANCIA_CORTA</lg:criterio>
                    </lg:CalcularRutaRequest>
                  </soap:Body>
                </soap:Envelope>
                """;
    }

    private HttpServletRequest soapRequest(String xml) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        });
        return request;
    }
}
