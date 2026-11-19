package logired.backend.integraciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGatewayTests {

    @Test
    void sinGatewayConfiguradoDevuelveErrorControlado() {
        AiGatewayClient client =
                new AiGatewayClient("", "", "logired-gateway", 5000);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.generarRespuesta("Probar gateway"));

        assertTrue(exception.getMessage().contains("gateway-url"));
    }

    @Test
    void requierePromptNoVacio() {
        AiGatewayClient client =
                new AiGatewayClient("", "", "logired-gateway", 5000);
        AiGatewayController controller = new AiGatewayController(client);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.generar(
                        new AiGatewayController.PromptRequest("")));

        assertTrue(exception.getMessage().contains("prompt"));
    }
}
