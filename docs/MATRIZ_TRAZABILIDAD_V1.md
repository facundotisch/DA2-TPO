# Matriz de trazabilidad v1

| Requisito | Evidencia/código | Prueba |
|---|---|---|
| SOAP legado con XXE/Fault | `LegacySoapController`, `legacy-ruteo.wsdl` | `LegacySoapControllerTests` |
| REST moderno con timeout/fallback | `ModernRuteoRestClient`, integración REST | `ModernRuteoRestClientTests` |
| Mensajería tópico/cola y DLQ | `JmsConfig`, `PedidoEventListeners` | `PedidoEventListenersIdempotencyTests` |
| Consumidores idempotentes | `ProcessedMessage`, repositorio JPA | Caso PT-006 |
| Idempotency-Key | `IdempotencyService`, `PedidoController` | PT-007 |
| ETag | `PedidoController` | PT-008 |
| Problem Details | `ProblemDetailsHandler` | PT-001/PT-009 |
| Gateway de IA | `AiGatewayClient`, `AiGatewayController` | PT-009 |
| Seguridad RBAC | `SecurityConfig`, `@PreAuthorize` | `PedidoSecurityTests`, `AuthFlowTests` |

Cobertura inicial: parcial. La métrica se obtiene en `backend/target/site/jacoco/index.html` y como artefacto del pipeline.
