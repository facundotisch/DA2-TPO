package logired.backend.usuario.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registroCreaOperadorQuePuedeIniciarSesion() throws Exception {
        String username = "test-" + UUID.randomUUID();
        String request = "{\"username\":\"" + username + "\",\"password\":\"ClaveSegura123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("OPERADOR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void registroDuplicadoDevuelve400() throws Exception {
        String username = "duplicate-" + UUID.randomUUID();
        String request = "{\"username\":\"" + username + "\",\"password\":\"ClaveSegura123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El username ya existe"));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-existe\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
    }

    @Test
    void bloqueaElSextoIntentoFallidoCon429YRetryAfter() throws Exception {
        for (int intento = 1; intento <= 5; intento++) {
            var result = mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.25");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"no-existe\",\"password\":\"incorrecta\"}"))
                    .andReturn();

            if (intento < 5) {
                org.junit.jupiter.api.Assertions.assertEquals(
                        401, result.getResponse().getStatus());
            } else {
                org.junit.jupiter.api.Assertions.assertEquals(
                        429, result.getResponse().getStatus());
                org.junit.jupiter.api.Assertions.assertNotNull(
                        result.getResponse().getHeader("Retry-After"));
                org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentAsString().contains("Demasiados intentos"));
            }
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.25");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operador\",\"password\":\"operador123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void registroSinCamposObligatoriosDevuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Username y password son obligatorios"));
    }
}