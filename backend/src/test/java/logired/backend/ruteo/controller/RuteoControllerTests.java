package logired.backend.ruteo.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;

@SpringBootTest
@AutoConfigureMockMvc
class RuteoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuteoService ruteoService;

    @Test
    void optimizarDevuelveLaRutaCalculada() throws Exception {
        when(ruteoService.calcularRutaConEstrategia(any(RutaRequest.class)))
                .thenReturn(new RutaResponse(List.of("Barracas", "Microcentro"), 6.4, 24,
                        "DISTANCIA_CORTA", "Ruta estimada"));

        mockMvc.perform(post("/api/ruteo/optimizar")
                        .with(user("operador").roles("OPERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direcciones\":[\"Barracas\",\"Microcentro\"],\"criterio\":\"DISTANCIA_CORTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradasOrdenadas[0]").value("Barracas"))
                .andExpect(jsonPath("$.distanciaTotalKm").value(6.4))
                .andExpect(jsonPath("$.tiempoEstimadoMinutos").value(24))
                .andExpect(jsonPath("$.estrategiaAplicada").value("DISTANCIA_CORTA"));
    }

    @Test
    void ruteoRequiereAutenticacion() throws Exception {
        mockMvc.perform(post("/api/ruteo/optimizar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direcciones\":[],\"criterio\":\"DISTANCIA_CORTA\"}"))
                .andExpect(status().isUnauthorized());
    }
}