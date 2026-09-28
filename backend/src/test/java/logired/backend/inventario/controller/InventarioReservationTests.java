package logired.backend.inventario.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import logired.backend.inventario.entity.ItemInventario;
import logired.backend.inventario.repository.InventarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
class InventarioReservationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventarioRepository inventarioRepository;

    @Test
    void reservaYLiberaStockDentroDeLaMismaSesion() throws Exception {
        String sku = "TEST-" + UUID.randomUUID();
        String deposito = "Deposito de prueba";
        inventarioRepository.save(new ItemInventario(sku, "Item de prueba", deposito, 5, 0));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/inventario/reservar")
                        .session(session)
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"deposito\":\"" + deposito
                                + "\",\"cantidad\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.stockDisponibleRestante").value(3))
                .andExpect(jsonPath("$.stockBloqueadoTotal").value(2));

        mockMvc.perform(post("/api/inventario/liberar")
                        .session(session)
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"deposito\":\"" + deposito
                                + "\",\"cantidad\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadReservada").value(1))
                .andExpect(jsonPath("$.stockDisponibleRestante").value(4))
                .andExpect(jsonPath("$.stockBloqueadoTotal").value(1));
    }

    @Test
    void noPermiteLiberarCantidadNoPositiva() throws Exception {
        mockMvc.perform(post("/api/inventario/liberar")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"IND-001\",\"deposito\":\"Depósito Central Barracas\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exitoso").value(false));
    }

    @Test
    void rolSinPermisoNoPuedeReservarStock() throws Exception {
        mockMvc.perform(post("/api/inventario/reservar")
                        .with(user("consulta").roles("CONSULTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"IND-001\",\"deposito\":\"Depósito Central Barracas\",\"cantidad\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservaStockInexistenteDevuelve400() throws Exception {
        mockMvc.perform(post("/api/inventario/reservar")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-NO-EXISTE\",\"deposito\":\"Deposito de prueba\",\"cantidad\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exitoso").value(false))
                .andExpect(jsonPath("$.mensaje").value("Item no encontrado en el depósito indicado."));
    }

    @Test
    void reservaSinStockSuficienteDevuelve400() throws Exception {
        String sku = "LOW-" + UUID.randomUUID();
        String deposito = "Deposito sin stock";
        inventarioRepository.save(new ItemInventario(sku, "Item limitado", deposito, 1, 0));

        mockMvc.perform(post("/api/inventario/reservar")
                        .session(new MockHttpSession())
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"deposito\":\"" + deposito
                                + "\",\"cantidad\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exitoso").value(false))
                .andExpect(jsonPath("$.stockDisponibleRestante").value(1));
    }

    @Test
    void noPermiteLiberarReservaDeOtraSesion() throws Exception {
        String sku = "SESSION-" + UUID.randomUUID();
        String deposito = "Deposito por sesion";
        inventarioRepository.save(new ItemInventario(sku, "Item de sesion", deposito, 5, 0));

        mockMvc.perform(post("/api/inventario/liberar")
                        .session(new MockHttpSession())
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"deposito\":\"" + deposito
                                + "\",\"cantidad\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exitoso").value(false));
    }
}