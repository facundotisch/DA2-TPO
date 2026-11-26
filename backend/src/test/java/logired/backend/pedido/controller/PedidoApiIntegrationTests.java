package logired.backend.pedido.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import logired.backend.common.exception.StockInsuficienteException;
import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;
import logired.backend.mensajeria.OutboxEvent;
import logired.backend.mensajeria.OutboxEventRepository;
import logired.backend.mensajeria.ProcessedMessageRepository;
import logired.backend.pedido.dto.ItemPedidoRequest;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.integracion.IdempotencyService;
import logired.backend.pedido.repository.PedidoRepository;
import logired.backend.pedido.service.PedidoService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de punta a punta de la API de pedidos sin mocks: servicio, base PostgreSQL y broker
 * reales (la base la levanta build.sh). Cubren los hallazgos de la auditoría:
 * SEC-02, SEC-03, ARQ-01, ARQ-02, ARQ-04, ARQ-05 y ARQ-06.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PedidoApiIntegrationTests {

    private static final String COLA_DEPOSITO = "Consumer.deposito.VirtualTopic.logired.envios.creados";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private InventarioRepository inventarioRepository;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    @Qualifier("queueJmsTemplate")
    private JmsTemplate queueJmsTemplate;

    // ---------- Patrón State y errores (ARQ-06) ----------

    @Test
    void transicionInvalidaRealDevuelve409EnProblemDetails() throws Exception {
        long pedidoId = crearPedido(crearProducto(5), 1, "cliente-" + UUID.randomUUID());

        mockMvc.perform(patch("/api/pedido/" + pedidoId + "/estado").param("accion", "ENTREGAR").with(operador()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Transición de estado inválida"));
    }

    @Test
    void cambiarEstadoDePedidoInexistenteDevuelve404YNo500() throws Exception {
        mockMvc.perform(patch("/api/pedido/987654321/estado").param("accion", "ASIGNAR").with(operador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void noSePuedeEditarUnPedidoQueYaFueAsignado() throws Exception {
        long pedidoId = crearPedido(crearProducto(5), 1, "cliente-" + UUID.randomUUID());
        mockMvc.perform(patch("/api/pedido/" + pedidoId + "/estado").param("accion", "ASIGNAR").with(operador()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/pedido/" + pedidoId).with(operador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Cambio tardío\"}"))
                .andExpect(status().isConflict());
    }

    // ---------- If-Match / ETag (ARQ-05) ----------

    @Test
    void ifMatchDesactualizadoDevuelve412() throws Exception {
        long pedidoId = crearPedido(crearProducto(5), 1, "cliente-" + UUID.randomUUID());
        String etagOriginal = mockMvc.perform(get("/api/pedido/" + pedidoId).with(operador()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        String etagNuevo = mockMvc.perform(patch("/api/pedido/" + pedidoId + "/estado")
                        .param("accion", "ASIGNAR").header("If-Match", etagOriginal).with(operador()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        assertNotEquals(etagOriginal, etagNuevo);

        mockMvc.perform(patch("/api/pedido/" + pedidoId + "/estado")
                        .param("accion", "EN_RUTA").header("If-Match", etagOriginal).with(operador()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.status").value(412));

        mockMvc.perform(patch("/api/pedido/" + pedidoId + "/estado")
                        .param("accion", "EN_RUTA").header("If-Match", etagNuevo).with(operador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_RUTA"));
    }

    // ---------- Idempotency-Key (ARQ-04) ----------

    @Test
    void postRepetidoConLaMismaClaveCreaUnSoloPedidoYDevuelveLaMismaRespuesta() throws Exception {
        long productoId = crearProducto(5);
        String cliente = "cliente-" + UUID.randomUUID();
        String clave = UUID.randomUUID().toString();
        String cuerpo = cuerpoPedido(productoId, 1, cliente);

        MvcResult primero = mockMvc.perform(post("/api/pedido").with(operador()).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn();
        MvcResult segundo = mockMvc.perform(post("/api/pedido").with(operador()).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        assertEquals(id(primero), id(segundo));
        assertEquals(1, pedidosDe(cliente));

        mockMvc.perform(post("/api/pedido").with(operador()).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoPedido(productoId, 2, cliente)))
                .andExpect(status().isConflict());
    }

    @Test
    void laClaveEstaAisladaPorUsuario() throws Exception {
        long productoId = crearProducto(5);
        String cliente = "cliente-" + UUID.randomUUID();
        String clave = UUID.randomUUID().toString();
        String cuerpo = cuerpoPedido(productoId, 1, cliente);

        MvcResult deOperador = mockMvc.perform(post("/api/pedido").with(operador()).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn();
        MvcResult deOtro = mockMvc.perform(post("/api/pedido").with(user("otro-operador").roles("OPERADOR"))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andReturn();

        assertNotEquals(id(deOperador), id(deOtro));
    }

    @Test
    void dosReservasSimultaneasDeLaMismaClaveSoloUnaGana() throws Exception {
        String clave = idempotencyService.claveConAlcance("operador", "POST", "/api/pedido", UUID.randomUUID().toString());
        List<IdempotencyService.Reserva> reservas = enParalelo(2, () -> idempotencyService.reservar(clave, "hash"));

        long nuevas = reservas.stream().filter(r -> r.estado() == IdempotencyService.Estado.NUEVA).count();
        assertEquals(1, nuevas);
        assertTrue(reservas.stream().anyMatch(r -> r.estado() == IdempotencyService.Estado.EN_CURSO));
    }

    // ---------- Concurrencia de stock (ARQ-01) ----------

    @Test
    void dosPedidosSimultaneosPorLaUltimaUnidadNoSobrevenden() throws Exception {
        long productoId = crearProducto(1);
        String sku = skuDe(productoId);

        List<Boolean> resultados = enParalelo(2, () -> {
            try {
                pedidoService.crearDesdeCatalogo(solicitud(productoId, 1, "cliente-" + UUID.randomUUID()));
                return true;
            } catch (StockInsuficienteException sinStock) {
                return false;
            }
        });

        assertEquals(1, resultados.stream().filter(Boolean::booleanValue).count());
        ItemInventario stock = inventarioRepository.findBySku(sku).get(0);
        assertEquals(0, stock.getStockDisponible());
        assertEquals(1, stock.getStockBloqueado());
    }

    // ---------- Mensajería: outbox, idempotencia y DLQ (ARQ-02, SEC-02) ----------

    @Test
    void crearPedidoDejaUnEventoSinDatosPersonalesQueTerminaProcesado() throws Exception {
        String cliente = "Cliente Privado " + UUID.randomUUID();
        long pedidoId = crearPedido(crearProducto(5), 1, cliente);

        OutboxEvent evento = esperar(() -> outboxEventRepository.findAll().stream()
                .filter(e -> pedidoIdDe(e) == pedidoId)
                .findFirst(), 5);
        assertFalse(evento.getPayload().contains(cliente));

        assertTrue(esperarQue(() -> processedMessageRepository
                .findByEventoIdAndConsumerName(evento.getEventoId(), "deposito-inventario").isPresent(), 30),
                "El consumidor de depósito no procesó el evento a tiempo");
    }

    @Test
    void mensajeInvalidoTerminaEnLaColaDeMensajesMuertos() throws Exception {
        String marca = "payload-invalido-" + UUID.randomUUID();
        queueJmsTemplate.convertAndSend(COLA_DEPOSITO, marca);

        assertTrue(esperarQue(() -> Boolean.TRUE.equals(queueJmsTemplate.browse("ActiveMQ.DLQ", (session, browser) -> {
            var mensajes = browser.getEnumeration();
            while (mensajes.hasMoreElements()) {
                Object mensaje = mensajes.nextElement();
                if (mensaje instanceof TextMessage texto && marca.equals(texto.getText())) {
                    return true;
                }
            }
            return false;
        })), 30), "El mensaje inválido no llegó a ActiveMQ.DLQ");
    }

    @Test
    void eventosDeMensajeriaSoloParaAdministradores() throws Exception {
        mockMvc.perform(get("/api/mensajeria/eventos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mensajeria/eventos").with(operador())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mensajeria/eventos").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    // ---------- Fuerza bruta por Basic Auth (SEC-03) ----------

    @Test
    void fuerzaBrutaPorBasicAuthEsBloqueadaCon429() throws Exception {
        String ip = "198.51.100." + (int) (Math.random() * 200 + 1);
        RequestPostProcessor desdeIp = request -> {
            request.setRemoteAddr(ip);
            return request;
        };
        for (int intento = 1; intento <= 5; intento++) {
            mockMvc.perform(get("/api/pedido").with(desdeIp).with(httpBasic("admin", "clave-incorrecta-" + intento)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/pedido").with(desdeIp).with(httpBasic("admin", "otra-clave")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // ---------- Utilidades ----------

    private RequestPostProcessor operador() {
        return user("operador").roles("OPERADOR");
    }

    private long crearProducto(int stock) throws Exception {
        String sku = "IT-" + UUID.randomUUID();
        String alta = """
                {"sku":"%s","nombre":"Producto de integración","descripcion":"Test",
                 "categoria":"Test","precio":1000,"moneda":"ARS",
                 "stockDisponible":%d,"stockBloqueado":0}
                """.formatted(sku, stock);
        MvcResult resultado = mockMvc.perform(post("/api/productos").with(operador())
                        .contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isCreated())
                .andReturn();
        return id(resultado);
    }

    private String skuDe(long productoId) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/productos/" + productoId).with(operador()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("sku").asText();
    }

    private long crearPedido(long productoId, int cantidad, String cliente) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/pedido").with(operador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPedido(productoId, cantidad, cliente)))
                .andExpect(status().isOk())
                .andReturn();
        return id(resultado);
    }

    private String cuerpoPedido(long productoId, int cantidad, String cliente) {
        return """
                {"clienteNombre":"%s","direccionEntrega":"Av. Corrientes 1234, CABA",
                 "items":[{"productoId":%d,"cantidad":%d}]}
                """.formatted(cliente, productoId, cantidad);
    }

    private PedidoCreacionRequest solicitud(long productoId, int cantidad, String cliente) {
        ItemPedidoRequest linea = new ItemPedidoRequest();
        linea.setProductoId(productoId);
        linea.setCantidad(cantidad);
        PedidoCreacionRequest request = new PedidoCreacionRequest();
        request.setClienteNombre(cliente);
        request.setDireccionEntrega("Av. Corrientes 1234, CABA");
        request.setItems(List.of(linea));
        return request;
    }

    private long id(MvcResult resultado) throws Exception {
        JsonNode json = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private long pedidoIdDe(OutboxEvent evento) {
        try {
            JsonNode pedidoId = objectMapper.readTree(evento.getPayload()).get("pedidoId");
            return pedidoId == null ? -1 : pedidoId.asLong();
        } catch (Exception payloadIlegible) {
            return -1;
        }
    }

    private long pedidosDe(String cliente) {
        return pedidoRepository.findAll().stream().filter(p -> cliente.equals(p.getClienteNombre())).count();
    }

    /** Lanza n tareas a la vez (todas esperan la misma señal de largada) y devuelve sus resultados. */
    private <T> List<T> enParalelo(int cantidad, Callable<T> tarea) throws Exception {
        ExecutorService ejecutor = Executors.newFixedThreadPool(cantidad);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < cantidad; i++) {
                futuros.add(ejecutor.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private <T> T esperar(Callable<Optional<T>> consulta, int segundos) throws Exception {
        long limite = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            Optional<T> valor = consulta.call();
            if (valor.isPresent()) {
                return valor.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No se obtuvo el valor esperado en " + segundos + " s");
    }

    private boolean esperarQue(BooleanSupplier condicion, int segundos) throws InterruptedException {
        long limite = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(250);
        }
        return false;
    }
}
