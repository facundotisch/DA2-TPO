package logired.backend.deposito.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DepositoLocationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointDevuelveUbicacionPredeterminadaEnCaba() throws Exception {
        mockMvc.perform(get("/api/deposito/default")
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Av. 9 de Julio s/n, junto al Obelisco, CABA"))
                .andExpect(jsonPath("$.latitud").value(-34.6037))
                .andExpect(jsonPath("$.longitud").value(-58.3816))
                .andExpect(jsonPath("$.openStreetMapUrl").value(org.hamcrest.Matchers.containsString(
                        "openstreetmap.org/?mlat=-34.603700&mlon=-58.381600")));
    }

        @Test
        void ubicacionDeDepositoRequiereAutenticacion() throws Exception {
                mockMvc.perform(get("/api/deposito/default"))
                                .andExpect(status().isUnauthorized());
        }

    @Test
    void pedidoNuevoIncluyeElDepositoPredeterminado() throws Exception {
        mockMvc.perform(post("/api/pedido")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Entrega de prueba\",\"total\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deposito.direccion")
                        .value("Av. 9 de Julio s/n, junto al Obelisco, CABA"))
                .andExpect(jsonPath("$.deposito.latitud").value(-34.6037))
                .andExpect(jsonPath("$.deposito.longitud").value(-58.3816));
    }
}