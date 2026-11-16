package logired.backend.pedido.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import logired.backend.pedido.exception.TransicionInvalidaException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.service.PedidoService;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PedidoService pedidoService;

    @Test
    void pedidoSinProductosSeleccionadosEsRechazado() throws Exception {
            when(pedidoService.crearDesdeCatalogo(any(PedidoCreacionRequest.class)))
                    .thenThrow(new IllegalArgumentException("Debe seleccionar al menos un producto para el pedido."));
            mockMvc.perform(post("/api/pedido")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clienteNombre\":\"Cliente Demo\",\"direccionEntrega\":\"Av. Corrientes 123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(
                            "Debe seleccionar al menos un producto para el pedido."));
    }

    @Test
    void operadorPuedeCrearYEditarCatalogoPeroSoloAdminPuedeEliminar() throws Exception {
        String sku = "MANUAL-" + UUID.randomUUID();
        String alta = """
                {"sku":"%s","nombre":"Auriculares nuevos","descripcion":"Bluetooth",
                 "categoria":"Audio","precio":85000,"moneda":"ARS",
                 "stockDisponible":5,"stockBloqueado":0}
                """.formatted(sku);

        MvcResult resultadoAlta = mockMvc.perform(post("/api/productos")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(resultadoAlta.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/productos/" + id)
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"sku":"%s","nombre":"Auriculares editados","descripcion":"Bluetooth",
                             "categoria":"Audio","precio":90000,"moneda":"ARS",
                             "stockDisponible":8,"stockBloqueado":0}
                            """.formatted(sku)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Auriculares editados"))
                .andExpect(jsonPath("$.stockDisponible").value(8));

        mockMvc.perform(delete("/api/productos/" + id)
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/productos/" + id)
                        .with(user("otro-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/productos/" + id)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

            @Test
            void operadorPuedeCrearPedidoConProductosYCantidades() throws Exception {
            Pedido pedido = new Pedido("Pedido de Cliente Demo", "CREADO", 119980.0);
            pedido.setClienteNombre("Cliente Demo");
            pedido.setDireccionEntrega("Av. Corrientes 123");
            var producto = productoRepository.findBySku("ZAP-NIKE-001").orElseThrow();
            pedido.agregarItem(new logired.backend.pedido.entity.PedidoItem(
                producto,
                pedido.getDeposito(), 2, new java.math.BigDecimal("129999.00")));
            when(pedidoService.crearDesdeCatalogo(any())).thenReturn(pedido);

            mockMvc.perform(post("/api/pedido")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clienteNombre\":\"Cliente Demo\",\"direccionEntrega\":\"Av. Corrientes 123\","
                            + "\"items\":[{\"productoId\":" + producto.getId() + ",\"cantidad\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(129999.0))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Demo"));
            }

    @Test
    void productoTieneDetalleDeVendedorYStockPorDeposito() throws Exception {
        Long productoId = productoRepository.findBySku("ZAP-NIKE-001").orElseThrow().getId();

        mockMvc.perform(get("/api/productos/" + productoId).with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("ZAP-NIKE-001"))
                .andExpect(jsonPath("$.precio").exists())
                .andExpect(jsonPath("$.vendedor.nombre").value("Tienda Urbana"))
                .andExpect(jsonPath("$.stockPorDeposito[0].deposito").exists())
                .andExpect(jsonPath("$.ubicacion.nombre").value("Deposito central (demo)"))
                .andExpect(jsonPath("$.ubicacion.direccion").value("Av. 9 de Julio s/n, junto al Obelisco, CABA"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(-34.6037))
                .andExpect(jsonPath("$.ubicacion.longitud").value(-58.3816))
                .andExpect(jsonPath("$.ubicacion.openStreetMapUrl").value(
                    org.hamcrest.Matchers.containsString("openstreetmap.org")));
    }

    @Test
    void catalogoDePedidosSoloMuestraProductosConStockDisponible() throws Exception {
        mockMvc.perform(get("/api/productos").with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].stockDisponible").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void operadorNoPuedeEliminarPedido() throws Exception {
        mockMvc.perform(delete("/api/pedido/1")
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeEliminarPedido() throws Exception {
        when(pedidoService.eliminar(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/pedido/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void operadorPuedeActualizarPedido() throws Exception {
            Pedido pedido = new Pedido("Cambio", "CREADO", 1200.0);
            when(pedidoService.actualizarDesdeCatalogo(
                    org.mockito.ArgumentMatchers.eq(1L), any(PedidoCreacionRequest.class)))
                    .thenReturn(Optional.of(pedido));
            mockMvc.perform(put("/api/pedido/1")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descripcion\":\"Cambio\",\"estado\":\"CREADO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.descripcion").value("Cambio"));
    }

            @Test
            void administradorPuedeActualizarPedidoExistente() throws Exception {
            Pedido pedido = new Pedido("Entrega local", "EN_RUTA", 1200.0);
            when(pedidoService.actualizarDesdeCatalogo(
                    org.mockito.ArgumentMatchers.eq(7L), any(PedidoCreacionRequest.class)))
                .thenReturn(Optional.of(pedido));

            mockMvc.perform(put("/api/pedido/7")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descripcion\":\"Entrega local\",\"estado\":\"EN_RUTA\",\"total\":1200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_RUTA"));
            }

            @Test
            void obtenerTodosDevuelvePedidos() throws Exception {
            Pedido primero = new Pedido("Paquete A", "CREADO", 500.0);
            primero.setDireccionEntrega("Av. Corrientes 1234, CABA");
            primero.setDeposito(new logired.backend.deposito.entity.Deposito(
                    "Central", "Av. 9 de Julio, CABA", -34.6037, -58.3816));
            Pedido segundo = new Pedido("Paquete B", "EN_RUTA", 800.0);
            segundo.setDeposito(new logired.backend.deposito.entity.Deposito(
                    "Central", "Av. 9 de Julio, CABA", -34.6037, -58.3816));
            when(pedidoService.obtenerTodos()).thenReturn(List.of(primero, segundo));

            mockMvc.perform(get("/api/pedido").with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Paquete A"))
                .andExpect(jsonPath("$[1].estado").value("EN_RUTA"))
                .andExpect(jsonPath("$[0].mapaEntregaUrl").value(
                        org.hamcrest.Matchers.containsString("openstreetmap.org/search")))
                .andExpect(jsonPath("$[1].mapaEntregaUrl").value(
                        org.hamcrest.Matchers.containsString("openstreetmap.org")));
            }

            @Test
            void obtenerPedidoInexistenteDevuelve404() throws Exception {
            when(pedidoService.obtenerPorId(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/pedido/999").with(user("operador").roles("OPERADOR")))
                .andExpect(status().isNotFound());
            }

            @Test
            void actualizarPedidoInexistenteDevuelve404() throws Exception {
            when(pedidoService.actualizarDesdeCatalogo(
                    org.mockito.ArgumentMatchers.eq(999L), any(PedidoCreacionRequest.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(put("/api/pedido/999")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descripcion\":\"No existe\",\"estado\":\"CREADO\",\"total\":100}"))
                .andExpect(status().isNotFound());
            }

            @Test
            void eliminarPedidoInexistenteDevuelve404() throws Exception {
            when(pedidoService.eliminar(999L)).thenReturn(false);

            mockMvc.perform(delete("/api/pedido/999").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
            verify(pedidoService).eliminar(999L);
            }

            @Test
            @WithMockUser(username = "admin", roles = {"ADMIN"})
            void transicionInvalidaDeEstadoDevuelve409Conflict() throws Exception {
                org.mockito.Mockito.doThrow(new TransicionInvalidaException("Transición inválida"))
                        .when(pedidoService).cambiarEstado(1L, "ENTREGAR");
                mockMvc.perform(patch("/api/pedido/1/estado")
                        .param("accion", "ENTREGAR"))
                        .andExpect(status().isConflict());
            }
}