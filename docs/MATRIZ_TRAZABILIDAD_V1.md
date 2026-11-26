# Matriz de Trazabilidad v1.0 Final - LogiRed

Sistema LogiRed - Entrega Final / 3er Checkpoint Integrado (30/11)  
Grupo 13: Facundo Tisch, Lola Díaz, Nicolás Valenzuela  
Estado de la Suite: **140 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (100% éxito)**.  
Cobertura de Negocio (Service + State): **90.04% líneas / 74.92% ramas** (supera umbral 80%/70%).  
Mutation Score PITest (Patrón State): **100%** (supera umbral 60%).

---

## 1. Matriz de Requisitos vs Código vs Pruebas Automatizadas

| ID | Requisito / Rubro | Implementación en Código | Mecanismo / Endpoint | Prueba Automatizada | Estado |
|---|---|---|---|---|---|
| **R-01** | Registro público de operadores | `AuthController`, `AuthService`, `Usuario` | `POST /api/auth/register` | `AuthFlowTests` | ✅ Cumplido |
| **R-02** | Login con credenciales válidas e inválidas | `AuthController`, `AuthService` | `POST /api/auth/login` | `AuthFlowTests` | ✅ Cumplido |
| **R-03** | Protección 429 Fuerza Bruta (Rate Limit) | `LoginRateLimiter`, `BasicAuthRateLimitFilter` | 5 intentos erróneos / 60s | `LoginRateLimiterTests`, `AuthFlowTests` | ✅ Cumplido |
| **R-04** | Usuarios semilla seguros | `DataInitializer`, variables `${LOGIRED_*}` | DB initialization | `BackendApplicationTests` | ✅ Cumplido |
| **R-05** | RBAC / Control de Acceso (BFLA/IDOR) | `SecurityConfig`, `@PreAuthorize` | `DELETE` solo ADMIN | `PedidoSecurityTests` | ✅ Cumplido |
| **R-06** | Autenticación HTTP Basic estándar | `SecurityConfig` | Todas las rutas privadas | `PedidoSecurityTests`, `AuthFlowTests` | ✅ Cumplido |
| **R-07** | CRUD y Dominio de Pedidos | `PedidoController`, `PedidoService`, `Pedido` | `/api/pedido` | `PedidoServiceTests`, `PedidoServiceBranchCoverageTests` | ✅ Cumplido |
| **R-08** | Reserva atómica y distribución de stock | `PedidoService.crearDesdeCatalogo` | `POST /api/pedido` | `PedidoServiceTests`, `PedidoServiceBranchCoverageTests` | ✅ Cumplido |
| **R-09** | Concurrencia de stock sin sobreventa | `InventarioRepository` (`@Lock PESSIMISTIC_WRITE`) | Concurrencia en BD | `PedidoApiIntegrationTests.dosPedidosSimultaneosPorLaUltimaUnidadNoSobrevenden` | ✅ Cumplido |
| **R-10** | Patrón State (CREADO, ASIGNADO, EN_RUTA, ENTREGADO, FALLIDO) | `logired.backend.pedido.state.*` | Entidad `Pedido` | `PedidoEstadoTransicionesTests` (matriz 5x4) | ✅ Cumplido |
| **R-11** | Transición inválida de estado -> HTTP 409 | `TransicionInvalidaException`, `ProblemDetailsHandler` | `PATCH /api/pedido/{id}/estado` | `PedidoApiIntegrationTests.transicionInvalidaRealDevuelve409EnProblemDetails` | ✅ Cumplido |
| **R-12** | Integridad de Edición según Estado | `Pedido.avanzarA*`, `PedidoService` | `PUT /api/pedido/{id}` | `PedidoApiIntegrationTests.noSePuedeEditarUnPedidoQueYaFueAsignado` | ✅ Cumplido |
| **R-13** | ETag y Concurrencia Optimista (`If-Match`) | `PedidoController` (SHA-256 ETag) | `If-Match` / HTTP 412 | `PedidoApiIntegrationTests.ifMatchDesactualizadoDevuelve412` | ✅ Cumplido |
| **R-14** | `Idempotency-Key` en API REST | `IdempotencyService`, `IdempotencyRecord` | Cabecera `Idempotency-Key` | `PedidoApiIntegrationTests.postRepetidoConLaMismaClaveCreaUnSoloPedidoYDevuelveLaMismaRespuesta` | ✅ Cumplido |
| **R-15** | Aislamiento de clave idempotente por usuario | `IdempotencyService.claveConAlcance` | Scope por Username | `PedidoApiIntegrationTests.laClaveEstaAisladaPorUsuario` | ✅ Cumplido |
| **R-16** | Control de carreras en idempotencia concurrente | `IdempotencyService.reservar` | Bloqueo en BD | `PedidoApiIntegrationTests.dosReservasSimultaneasDeLaMismaClaveSoloUnaGana` | ✅ Cumplido |
| **R-17** | Mensajería ActiveMQ + Transactional Outbox | `OutboxService`, `outbox_events` table | Virtual Topics JMS | `OutboxServiceTests.publicarReintentaSiFallaBroker` | ✅ Cumplido |
| **R-18** | Protección de datos personales (PII) en eventos | `PedidoService.publicarEventoPedidoCreado` | Payload sin PII | `PedidoApiIntegrationTests.crearPedidoDejaUnEventoSinDatosPersonalesQueTerminaProcesado` | ✅ Cumplido |
| **R-19** | Consumidores Idempotentes en JMS | `ProcesadorEventosIdempotente` | `processed_messages` | `ProcesadorEventosIdempotenteTests` | ✅ Cumplido |
| **R-20** | Dead Letter Queue (DLQ) para poison pills | `JmsConfig`, ActiveMQ Virtual Topics | `ActiveMQ.DLQ` | `PedidoApiIntegrationTests.mensajeInvalidoTerminaEnLaColaDeMensajesMuertos` | ✅ Cumplido |
| **R-21** | SOAP Legado: WSDL, Faults y Protección XXE | `LegacySoapController`, `legacy-ruteo.wsdl` | `/soap/ws/ruteo` | `LegacySoapControllerTests` (4 tests: DTD, XXE, Fault, OK) | ✅ Cumplido |
| **R-22** | REST Moderno con Timeout 5s y Fallback | `ModernRuteoRestClient`, `RuteoServiceImpl` | `/api/integraciones/rest/ruteo` | `ModernRuteoRestClientTests` (3 tests: timeout, 503, 200) | ✅ Cumplido |
| **R-23** | IA Gateway con JSON Schema y Fallback | `AiGatewayClient`, `AiGatewayController` | `/api/ia/generar` | `AiGatewayTests` (3 tests) | ✅ Cumplido |
| **R-24** | Errores en RFC 7807 Problem Details | `ProblemDetailsHandler` | `application/problem+json` | `PedidoApiIntegrationTests`, `ProblemDetailsHandler` | ✅ Cumplido |
| **R-25** | Patrón Strategy en Ruteo | `EstrategiaRuteo`, `RutaMasCortaStrategy`, `RutaMasRapidaStrategy` | `/api/ruteo/optimizar` | `RuteoControllerTests`, `RuteoServiceImplTests` | ✅ Cumplido |
| **R-26** | Inventario Stateful por Sesión HTTP | `InventarioServiceImpl` (`@SessionScope`) | `/api/inventario/reservar` | `InventarioReservationTests`, `InventarioCatalogoServiceTests` | ✅ Cumplido |
| **R-27** | Catálogo de Productos y Depósitos | `ProductoService`, `DepositoService` | `/api/productos`, `/api/deposito` | `ProductoServiceTests`, `DepositoLocationTests` | ✅ Cumplido |

---

## 2. Casos del Plan de Pruebas (PT)

| Caso | Descripción | Suite de Prueba | Resultado |
|---|---|---|---|
| **PT-001** | Autenticación y Rate Limiting 429 | `AuthFlowTests`, `LoginRateLimiterTests` | ✅ 100% OK |
| **PT-002** | Autorización RBAC (Admin vs Operador) | `PedidoSecurityTests` | ✅ 100% OK |
| **PT-003** | Ciclo de Pedido con Stock y Concurrencia | `PedidoServiceTests`, `PedidoApiIntegrationTests` | ✅ 100% OK |
| **PT-004** | Integración SOAP Legada con Fault y Anti-XXE | `LegacySoapControllerTests` | ✅ 100% OK |
| **PT-005** | Integración REST con Timeout 5s y Fallback | `ModernRuteoRestClientTests` | ✅ 100% OK |
| **PT-006** | Consumo Idempotente de Mensajería JMS | `ProcesadorEventosIdempotenteTests` | ✅ 100% OK |
| **PT-007** | Idempotencia REST (`Idempotency-Key` / Replay) | `PedidoApiIntegrationTests` | ✅ 100% OK |
| **PT-008** | Concurrencia Optimista HTTP (`ETag` / `If-Match`) | `PedidoApiIntegrationTests` | ✅ 100% OK |
| **PT-009** | Resiliencia IA Gateway y Validación Schema | `AiGatewayTests` | ✅ 100% OK |
| **PT-010** | Matriz de Transiciones Patrón State | `PedidoEstadoTransicionesTests` | ✅ 100% OK |
| **PT-011** | Dead Letter Queue (DLQ) ante Mensaje Malformado | `PedidoApiIntegrationTests` | ✅ 100% OK |
| **PT-012** | Resiliencia Transactional Outbox sin Broker | `OutboxServiceTests` | ✅ 100% OK |

---

## 3. Resumen Cuantitativo de la Auditoría

- **Total de pruebas unitarias e integración:** 140 tests automatizados.
- **Tasa de éxito:** 100% (0 failures, 0 errors).
- **Cobertura Capa de Negocio:**
  - Líneas: **90.04%** (Umbral cátedra: $\ge 80\%$) -> **SUPERADO**.
  - Ramas: **74.92%** (Umbral cátedra: $\ge 70\%$) -> **SUPERADO**.
- **Mutation Testing (PITest en Patrón State):** **100%** (Umbral cátedra: $\ge 60\%$) -> **SUPERADO**.
