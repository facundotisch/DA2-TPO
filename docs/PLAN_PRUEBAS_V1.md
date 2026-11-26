# Plan Oficial de Pruebas v1.0 - LogiRed

**Cátedra:** Desarrollo de Aplicaciones II — UADE  
**Grupo 13:** Facundo Tisch, Lola Díaz, Nicolás Valenzuela  
**Instancia:** 30/11 (3er Checkpoint y Evaluación Final Integrada)

---

## 1. Alcance y Objetivos de Calidad

El plan de pruebas valida la totalidad de los criterios funcionales, arquitecturales, de resiliencia y de seguridad exigidos para la entrega final:
* APIs REST: Pedidos, Inventario, Productos, Depósitos, Ruteo, Mensajería y Autenticación.
* Integraciones: SOAP 1.2 legado (Faults y protección XXE), REST moderno (timeout 5s y fallback), mensajería JMS asíncrona (ActiveMQ con Virtual Topics).
* Resiliencia: Transactional Outbox ante broker caído, consumidores idempotentes, Dead Letter Queue (DLQ), `Idempotency-Key` y concurrencia optimista (`ETag` / `If-Match`).
* Seguridad: Rate Limiting 429 ante fuerza bruta, RBAC (ADMIN vs OPERADOR), prevención BOLA/BFLA y anonimización de datos sensibles (PII).
* Ejes de Calidad (A a E): Cobertura $\ge 80\%$ líneas / $\ge 70\%$ ramas, Mutation score $\ge 60\%$, Rendimiento $p95 < 500\text{ ms}$, Lighthouse $\ge 90$.

---

## 2. Tipos de Prueba y Estrategia

1. **Pruebas Unitarias y de Dominio (JUnit 5 / AssertJ):**
   * Validación del ciclo de vida y matriz completa de transiciones de estado (`PedidoEstadoTransicionesTests`).
   * Servicios de negocio (`PedidoServiceTests`, `PedidoServiceBranchCoverageTests`, `InventarioCatalogoServiceTests`).
2. **Pruebas de Mutación (PITest):**
   * Evaluación de la solidez de la suite contra mutantes inyectados en el patrón State: umbral $\ge 60\%$ (obtenido: **100%**).
3. **Pruebas de Integración y Controladores (MockMvc / Spring Boot Test):**
   * Flujos de autenticación y bloqueo por fuerza bruta (`AuthFlowTests`, `LoginRateLimiterTests`).
   * Seguridad y permisos (`PedidoSecurityTests`).
   * Integración SOAP con rechazo de DTD y entidades externas (`LegacySoapControllerTests`).
   * Integración REST con timeout y fallback (`ModernRuteoRestClientTests`).
4. **Pruebas de Punta a Punta de Resiliencia y Concurrencia (`PedidoApiIntegrationTests`):**
   * Idempotencia con replay de cabeceras (`Idempotency-Replayed: true`).
   * Concurrencia de stock con bloqueo pesimista (`dosPedidosSimultaneosPorLaUltimaUnidadNoSobrevenden`).
   * Concurrencia optimista HTTP con ETag e `If-Match` (412 Precondition Failed).
   * Desvío a DLQ ante mensajes JMS inválidos (`mensajeInvalidoTerminaEnLaColaDeMensajesMuertos`).
   * Transactional Outbox ante desconexión del broker (`OutboxServiceTests`).
5. **Pruebas de Carga y Rendimiento (Script reproducible):**
   * Medición de latencia concurrente mediante `scripts/prueba_carga.py`.
   * Criterio de aceptación: latencia en percentil 95 (**p95**) inferior a **500 ms**.
6. **Pruebas de Accesibilidad y Front (Lighthouse):**
   * Medición de accesibilidad y diseño responsivo en Vite + React con umbral $\ge 90$.

---

## 3. Matriz de Casos de Prueba Prioritarios (PT)

| ID | Caso de Prueba | Tipo | Entrada / Acción | Resultado Esperado | Suite Automatizada |
|---|---|---|---|---|---|
| **PT-001** | Autenticación y Fuerza Bruta | Seguridad | 5 intentos erróneos en < 60s | HTTP 429 Too Many Requests con `Retry-After` | `AuthFlowTests`, `LoginRateLimiterTests` |
| **PT-002** | Autorización RBAC | Seguridad | Operador intenta ejecutar DELETE | HTTP 403 Forbidden | `PedidoSecurityTests` |
| **PT-003** | Stock y Concurrencia | Dominio | 2 pedidos simultáneos por 1 unidad | Solo 1 confirma, el otro 422 (sin sobreventa) | `PedidoApiIntegrationTests` |
| **PT-004** | SOAP Legado y XXE | Integración | Payload SOAP con DOCTYPE / entidades | SOAP Fault `soap:Client` con HTTP 400 | `LegacySoapControllerTests` |
| **PT-005** | REST Timeout y Fallback | Resiliencia | Endpoint externo demora > 5 s | Conmuta a algoritmo local con HTTP 200 | `ModernRuteoRestClientTests` |
| **PT-006** | Consumo Idempotente JMS | Mensajería | Mismo `eventoId` reenviado | Deduplicación en `processed_messages`, sin reprocesar | `ProcesadorEventosIdempotenteTests` |
| **PT-007** | Idempotency-Key REST | Resiliencia | Mismo POST con clave repetida | Replay del 200 con `Idempotency-Replayed: true` | `PedidoApiIntegrationTests` |
| **PT-008** | Concurrencia Optimista ETag | HTTP | PATCH con `If-Match` desactualizado | HTTP 412 Precondition Failed | `PedidoApiIntegrationTests` |
| **PT-009** | Resiliencia IA Gateway | Integración | Gateway sin conexión o timeout | Fallback estructurado `sinSugerencia()` con HTTP 200 | `AiGatewayTests` |
| **PT-010** | Matriz Transiciones State | Mutación | 5 estados x 4 acciones posibles | Solo transiciones válidas avanzan; inválidas 409 | `PedidoEstadoTransicionesTests` |
| **PT-011** | Dead Letter Queue (DLQ) | Resiliencia | Envío de poison pill no parseable | 3 reintentos con backoff y derivación a `ActiveMQ.DLQ` | `PedidoApiIntegrationTests` |
| **PT-012** | Outbox sin Broker | Resiliencia | Crear pedido con ActiveMQ caído | Evento queda en `outbox_events` (PENDIENTE) y se envía al volver | `OutboxServiceTests` |

---

## 4. Ejecución y Reproducibilidad

1. **Suite completa local:**
   ```bash
   cd backend
   ./mvnw clean test
   ```
   *Resultado: 140 tests en 22 clases, 0 fallos, 0 errores.*

2. **Prueba de Mutación (PITest):**
   ```bash
   cd backend
   ./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
   ```
   *Resultado: 100% de mutantes eliminados en el patrón State.*

3. **Prueba de Carga (p95 < 500 ms):**
   ```bash
   python scripts/prueba_carga.py
   ```

4. **Pipeline Automatizado (CI):**
   * Configurado en `.github/workflows/backend-ci.yml`. Ejecuta `build.sh`, reporte JaCoCo, SonarCloud, Gitleaks y Trivy en cada push.
