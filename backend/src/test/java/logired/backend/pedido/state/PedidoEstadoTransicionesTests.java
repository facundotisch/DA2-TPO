package logired.backend.pedido.state;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.exception.TransicionInvalidaException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Recorre la matriz completa estado x acción del patrón State sobre la entidad real
 * (sin mocks): 5 estados x 4 acciones = 20 casos. Si se invierte o borra una transición,
 * falla al menos un caso; por eso este test también sirve para la prueba de mutación.
 */
class PedidoEstadoTransicionesTests {

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @MethodSource("transicionesValidas")
    void transicionValidaCambiaElEstado(String estadoInicial, String accion, String estadoEsperado) {
        Pedido pedido = new Pedido("Pedido de prueba", estadoInicial, 100.0);

        aplicar(accion).accept(pedido);

        assertEquals(estadoEsperado, pedido.getEstado());
    }

    @ParameterizedTest(name = "{0} --{1}--> rechazado")
    @MethodSource("transicionesInvalidas")
    void transicionInvalidaLanzaExcepcionYNoCambiaElEstado(String estadoInicial, String accion) {
        Pedido pedido = new Pedido("Pedido de prueba", estadoInicial, 100.0);

        assertThrows(TransicionInvalidaException.class, () -> aplicar(accion).accept(pedido));
        assertEquals(estadoInicial, pedido.getEstado());
    }

    @Test
    void unPedidoNuevoNaceEnCreado() {
        assertEquals("CREADO", new Pedido().getEstado());
    }

    @Test
    void noSePuedeConstruirUnPedidoConUnEstadoInexistente() {
        assertThrows(IllegalArgumentException.class, () -> new Pedido("x", "HACKEADO", 1.0));
    }

    @Test
    void recorridoCompletoHastaEntregado() {
        Pedido pedido = new Pedido();
        pedido.avanzarAAsignado();
        pedido.avanzarAEnRuta();
        pedido.avanzarAEntregado();
        assertEquals("ENTREGADO", pedido.getEstado());
    }

    static Stream<Arguments> transicionesValidas() {
        return Stream.of(
                Arguments.of("CREADO", "ASIGNAR", "ASIGNADO"),
                Arguments.of("ASIGNADO", "EN_RUTA", "EN_RUTA"),
                Arguments.of("EN_RUTA", "ENTREGAR", "ENTREGADO"),
                Arguments.of("EN_RUTA", "FALLAR", "FALLIDO"));
    }

    static Stream<Arguments> transicionesInvalidas() {
        return Stream.of(
                Arguments.of("CREADO", "EN_RUTA"),
                Arguments.of("CREADO", "ENTREGAR"),
                Arguments.of("CREADO", "FALLAR"),
                Arguments.of("ASIGNADO", "ASIGNAR"),
                Arguments.of("ASIGNADO", "ENTREGAR"),
                Arguments.of("ASIGNADO", "FALLAR"),
                Arguments.of("EN_RUTA", "ASIGNAR"),
                Arguments.of("EN_RUTA", "EN_RUTA"),
                Arguments.of("ENTREGADO", "ASIGNAR"),
                Arguments.of("ENTREGADO", "EN_RUTA"),
                Arguments.of("ENTREGADO", "ENTREGAR"),
                Arguments.of("ENTREGADO", "FALLAR"),
                Arguments.of("FALLIDO", "ASIGNAR"),
                Arguments.of("FALLIDO", "EN_RUTA"),
                Arguments.of("FALLIDO", "ENTREGAR"),
                Arguments.of("FALLIDO", "FALLAR"));
    }

    private static Consumer<Pedido> aplicar(String accion) {
        return switch (accion) {
            case "ASIGNAR" -> Pedido::avanzarAAsignado;
            case "EN_RUTA" -> Pedido::avanzarAEnRuta;
            case "ENTREGAR" -> Pedido::avanzarAEntregado;
            case "FALLAR" -> Pedido::reportarFalla;
            default -> throw new IllegalArgumentException(accion);
        };
    }
}
