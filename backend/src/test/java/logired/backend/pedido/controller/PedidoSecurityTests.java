package logired.backend.pedido.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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
import logired.backend.producto.repository.ProductoRepository;
import logired.backend.pedido.dto.PedidoCreacionRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import logired.backend.pedido.entity.Pedido;
import logired.backend.pedido.service.PedidoService;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @MockitoBean
    private PedidoService pedidoService;

    @Test
    void operadorPuedeCrearPedido() throws Exception {
        when(pedidoService.crear(any(Pedido.class)))
                .thenReturn(new Pedido("Entrega local", "CREADO", 1200.0));

        mockMvc.perform(post("/api/pedido")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Entrega local\",\"total\":1200}"))
                .andExpect(status().isOk());
    }

            @Test
            void pedidoSinItemsSolicitadosRecibeUnProductoDeCatalogo() throws Exception {
            var producto = productoRepository.findBySku("IND-001").orElseThrow();
            Pedido pedido = new Pedido("Pedido aleatorio", "CREADO", 59990.0);
            pedido.setClienteNombre("Cliente Demo");
            pedido.setDireccionEntrega("Av. Corrientes 123");
            pedido.agregarItem(new logired.backend.pedido.entity.PedidoItem(
                producto, null, 1, producto.getPrecio()));
            when(pedidoService.crearDesdeCatalogo(any(PedidoCreacionRequest.class))).thenReturn(pedido);

            mockMvc.perform(post("/api/pedido")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clienteNombre\":\"Cliente Demo\",\"direccionEntrega\":\"Av. Corrientes 123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].producto.sku").value("IND-001"))
                .andExpect(jsonPath("$.items[0].cantidad").value(1));
            }

            @Test
            void operadorPuedeCrearPedidoConProductosYCantidades() throws Exception {
            Pedido pedido = new Pedido("Pedido de Cliente Demo", "CREADO", 119980.0);
            pedido.setClienteNombre("Cliente Demo");
            pedido.setDireccionEntrega("Av. Corrientes 123");
            pedido.agregarItem(new logired.backend.pedido.entity.PedidoItem(
                productoRepository.findBySku("IND-001").orElseThrow(),
                pedido.getDeposito(), 2, new java.math.BigDecimal("59990.00")));
            when(pedidoService.crearDesdeCatalogo(any())).thenReturn(pedido);

            mockMvc.perform(post("/api/pedido")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"clienteNombre":"Cliente Demo","direccionEntrega":"Av. Corrientes 123",
                         "items":[{"productoId":1,"cantidad":2}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(59990.0))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Demo"));
            }

    @Test
    void productoTieneDetalleDeVendedorYStockPorDeposito() throws Exception {
        Long productoId = productoRepository.findBySku("IND-001").orElseThrow().getId();

        mockMvc.perform(get("/api/productos/" + productoId).with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("IND-001"))
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
            void operadorNoPuedeActualizarPedido() throws Exception {
            mockMvc.perform(put("/api/pedido/1")
                    .with(user("operador").roles("OPERADOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descripcion\":\"Cambio\",\"estado\":\"EN_RUTA\",\"total\":1200}"))
                .andExpect(status().isForbidden());
            }

            @Test
            void administradorPuedeActualizarPedidoExistente() throws Exception {
            Pedido pedido = new Pedido("Entrega local", "EN_RUTA", 1200.0);
            when(pedidoService.actualizar(org.mockito.ArgumentMatchers.eq(7L), any(Pedido.class)))
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
            when(pedidoService.obtenerTodos()).thenReturn(List.of(
                new Pedido("Paquete A", "CREADO", 500.0),
                new Pedido("Paquete B", "EN_RUTA", 800.0)));

            mockMvc.perform(get("/api/pedido").with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Paquete A"))
                .andExpect(jsonPath("$[1].estado").value("EN_RUTA"));
            }

            @Test
            void obtenerPedidoInexistenteDevuelve404() throws Exception {
            when(pedidoService.obtenerPorId(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/pedido/999").with(user("operador").roles("OPERADOR")))
                .andExpect(status().isNotFound());
            }

            @Test
            void actualizarPedidoInexistenteDevuelve404() throws Exception {
            when(pedidoService.actualizar(org.mockito.ArgumentMatchers.eq(999L), any(Pedido.class)))
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
                mockMvc.perform(patch("/api/pedido/1/estado")
                        .param("accion", "ENTREGAR"))
                        .andExpect(status().isConflict());
            }
}