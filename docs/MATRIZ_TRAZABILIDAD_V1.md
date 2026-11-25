# Matriz de trazabilidad v2

Análisis estático del código de `backend/` (85 tests en 17 clases). No incluye resultados de ejecución del build.

**Estados:** ✅ implementado y probado · 🟡 implementado, prueba parcial o indirecta · 🔴 implementado sin test automatizado · ⚪ pendiente / no existe

## 1. Matriz principal (requisito → código → prueba)

| ID | Requisito / funcionalidad | Evidencia / código | Endpoint o mecanismo | Prueba automatizada | Caso manual | Estado |
|---|---|---|---|---|---|---|
| **Seguridad y usuarios** | | | | | | |
| R-01 | Registro público, siempre rol `OPERADOR` | `AuthController`, `AuthService`, `Usuario`, `UsuarioRepository` | `POST /api/auth/register` | `AuthFlowTests` (alta, duplicado 400, campos faltantes 400), `AuthServiceTests` | PT-001 | ✅ |
| R-02 | Login con credenciales válidas e inválidas | `AuthController`, `AuthService` (`UserDetailsService`) | `POST /api/auth/login` | `AuthFlowTests` (200 / 401) | PT-001 | ✅ |
| R-03 | Rate limit de login: 5 fallos por IP cada 60 s, 429 con `Retry-After` | `LoginRateLimiter`, `AuthController` | `POST /api/auth/login` | `AuthFlowTests` (sexto intento), `LoginRateLimiterTests` | — | ✅ (contador local a la instancia) |
| R-04 | Usuarios semilla `admin` y `operador` | `DataInitializer` | `CommandLineRunner` | Solo `BackendApplicationTests` (carga de contexto) | — | 🟡 |
| R-05 | RBAC: operador crea y edita, solo `admin` borra | `SecurityConfig`, `@PreAuthorize("hasRole('ADMIN') and authentication.name == 'admin'")` en Pedido, Producto e Inventario | `DELETE /api/pedido\|productos\|inventario/{id}` | `PedidoSecurityTests`, `InventarioReservationTests` | PT-002 | ✅ |
| R-06 | Rutas protegidas con Basic Auth | `SecurityConfig` (`anyRequest().authenticated()`) | todas las rutas salvo `/api/auth/**`, `/api/mensajeria/**`, `/ws/**` | `RuteoControllerTests`, `DepositoLocationTests` (exigen autenticación) | — | ✅ |
| R-07 | CORS para `localhost` y `127.0.0.1` | `SecurityConfig.corsConfigurationSource` | cabeceras CORS | — | — | 🔴 |
| **Pedidos** | | | | | | |
| R-08 | CRUD de pedidos | `PedidoController`, `PedidoService`, `Pedido`, `PedidoItem`, `PedidoRepository` | `GET/POST/PUT/DELETE /api/pedido` | `PedidoServiceTests`, `PedidoSecurityTests` (404, 403, 204, 200) | PT-003 | ✅ |
| R-09 | Crear pedido desde catálogo: valida líneas, calcula total, reserva stock y reparte entre depósitos | `PedidoService.crearDesdeCatalogo`, `seleccionarReservas`, `reservarItemsEnPedido` | `POST /api/pedido` | `PedidoServiceTests`, `PedidoServiceBranchCoverageTests` | PT-003 | ✅ |
| R-10 | Liberar reservas al editar o eliminar un pedido | `PedidoService.liberarReservasDelPedido`, `liberarStockDesdeDescripcion` | `PUT/DELETE /api/pedido/{id}` | `PedidoServiceBranchCoverageTests` | — | ✅ |
| R-11 | Compatibilidad con pedidos legacy (`SKU:`) y completado de pedidos sin ítems al iniciar | `PedidoService.crear`, `completarPedidosSinItems`, `PedidoDataInitializer` | `CommandLineRunner` | `PedidoServiceTests`, `PedidoServiceBranchCoverageTests` | — | ✅ |
| R-12 | Ciclo de vida con patrón State: CREADO → ASIGNADO → EN_RUTA → ENTREGADO / FALLIDO | `state/Estado*`, `Pedido.avanzarA*`, `PedidoService.cambiarEstado` | `PATCH /api/pedido/{id}/estado?accion=` | `PedidoServiceBranchCoverageTests` (acciones y acción desconocida) | — | 🟡 (no hay test unitario por transición) |
| R-13 | Transición inválida responde 409 | `TransicionInvalidaException` (`@ResponseStatus`) | `PATCH …/estado` | `PedidoSecurityTests.transicionInvalida…409` (servicio mockeado) | — | 🟡 |
| R-14 | `Idempotency-Key` en POST, PUT, DELETE y PATCH (replay o 409) | `IdempotencyService`, `IdempotencyRecord` (`unique`), `PedidoController` | cabecera `Idempotency-Key` | **ninguna** | PT-007 | 🔴 |
| R-15 | `ETag` e `If-None-Match` (304) | `PedidoController.calcularEtag` (SHA-256 del JSON) | `GET /api/pedido[/{id}]` | **ninguna** | PT-008 | 🔴 |
| R-16 | Mapa de entrega y depósito en la respuesta del pedido | `Pedido.getMapaEntregaUrl`, `Deposito.getOpenStreetMapUrl` | respuesta de `/api/pedido` | `DepositoLocationTests.pedidoNuevoConProductoIncluyeDepositoYMapa` | — | ✅ |
| R-17 | Generación automática de pedidos demo (máx. 10, cada 2 min) | `PedidoDemoScheduler`, `@EnableScheduling` | `@Scheduled` | — | — | 🔴 |
| **Inventario y catálogo** | | | | | | |
| R-18 | ABM de stock por depósito | `InventarioController`, `InventarioCatalogoService`, `ItemInventario` | `GET/POST/PUT/DELETE /api/inventario` | `InventarioCatalogoServiceTests`, `InventarioReservationTests` | — | ✅ |
| R-19 | Hold de stock por sesión (`@SessionScope`, `@PostConstruct` / `@PreDestroy`, timeout 5 min) | `InventarioServiceImpl`, `server.servlet.session.timeout=5m` | `POST /api/inventario/reservar\|liberar`, `GET /sesion` | `InventarioReservationTests` (reservar, liberar, otra sesión, sin stock, inexistente, cantidad ≤ 0) | — | 🟡 (`@PreDestroy` y expiración sin test) |
| R-20 | Bloqueo pesimista en reservas | `InventarioRepository` (`@Lock PESSIMISTIC_WRITE`) | JPA | — (sin test de concurrencia) | — | 🔴 |
| R-21 | No borrar stock con unidades reservadas | `InventarioCatalogoService.eliminar` | `DELETE /api/inventario/{id}` | `InventarioCatalogoServiceTests` | — | ✅ |
| R-22 | Catálogo de productos (ABM, stock agregado por depósito, listado solo con stock) | `ProductoController`, `ProductoService`, `Producto`, `Vendedor` | `/api/productos` | `ProductoServiceTests`, `PedidoSecurityTests` | — | ✅ |
| R-23 | Consulta de vendedores | `VendedorController`, `VendedorService` | `GET /api/vendedores` | — | — | 🔴 |
| R-24 | Catálogo inicial y limpieza de datos de prueba | `CatalogoDataInitializer`, seed en `BackendApplication` | `CommandLineRunner` | — | — | 🔴 |
| R-25 | Depósito predeterminado con coordenadas | `DepositoController`, `DepositoService`, `DepositoDataInitializer`, `Deposito` | `GET /api/deposito/default` | `DepositoLocationTests` | — | ✅ |
| **Ruteo e integraciones** | | | | | | |
| R-26 | Ruteo con patrón Strategy (`DISTANCIA_CORTA` / `TIEMPO_RAPIDO`) | `RuteoController`, `RuteoServiceImpl`, `EstrategiaRuteo`, `RutaMasCortaStrategy`, `RutaMasRapidaStrategy` | `POST /api/ruteo/optimizar` | `RuteoControllerTests`, `RuteoServiceImplTests` | — | 🟡 (estimación por cantidad de paradas, DEF-001) |
| R-27 | SOAP legado: WSDL, Fault y rechazo de DTD/XXE | `LegacySoapController`, `legacy-ruteo.wsdl` | `GET /ws/legacy-ruteo.wsdl`, `POST /ws/legacy-ruteo` | `LegacySoapControllerTests` (DOCTYPE rechazado, solicitud válida) | PT-004 | 🟡 (Faults por tamaño o body inválido sin test) |
| R-28 | REST moderno con timeout de 5 s y fallback local | `ModernRuteoRestClient`, `IntegracionRestController` | `POST /api/integraciones/rest/ruteo` | `ModernRuteoRestClientTests` (sin URL, timeout) | PT-005 | ✅ |
| R-29 | Gateway de IA con error controlado | `AiGatewayClient`, `AiGatewayController` | `POST /api/ia/generar` | `AiGatewayTests` (sin URL, prompt vacío; a nivel de clase) | PT-009 | 🟡 (no valida HTTP 502 ni Problem Detail) |
| **Mensajería** | | | | | | |
| R-30 | Broker ActiveMQ externo o embebido | `JmsConfig.brokerService`, `docker-compose.yml` | `tcp://localhost:61616` | — | — | 🔴 |
| R-31 | Publicar `PEDIDO_CREADO` en tópico y cola | `PedidoService.publicarEventoPedidoCreado`, `PedidoCreadoEvent`, `JmsConfig` | `logired.envios.creados`, `logired.pedidos.creados` | — | — | 🔴 |
| R-32 | 3 suscriptores de tópico y 1 consumidor de cola | `PedidoEventListeners` | `@JmsListener` | cubiertos indirectamente por R-33 | — | 🟡 |
| R-33 | Consumidores idempotentes (`eventoId + consumerName`) | `ProcessedMessage`, `ProcessedMessageRepository` (`unique`) | tabla `processed_messages` | `PedidoEventListenersIdempotencyTests` (2) | PT-006 | ✅ |
| R-34 | Reintentos y DLQ | `JmsConfig` (solo `ErrorHandler` con log) | ActiveMQ | — | — | 🔴 (sin configuración explícita) |
| R-35 | Consulta de eventos recientes | `MensajeriaController` | `GET /api/mensajeria/eventos` (público) | — | — | 🔴 |
| **Transversales** | | | | | | |
| R-36 | Errores en formato Problem Details (RFC 7807) | `ProblemDetailsHandler`, `AuthController.tooManyRequests` | 400 / 429 / 502 | — | PT-001 / PT-009 | 🔴 |
| R-37 | Build reproducible, CI y cobertura | `build.sh`, `backend-ci.yml`, `docker-compose.build.yml`, JaCoCo en `pom.xml` | GitHub Actions | `BackendApplicationTests` | — | 🟡 (JaCoCo sin umbral mínimo) |
| R-38 | Persistencia en PostgreSQL con JPA | entidades, repositorios, `application.properties` | `ddl-auto=update` | tests de integración con Spring | — | ✅ |
| R-39 | Documentación | `docs/*`, `backend/README.md` | — | — | — | 🟡 (ver hallazgos) |
| **Pendientes** | | | | | | |
| R-40 | Componentes Comercios, Repartidores, PagosYCobranzas, Notificaciones e IntegracionTransportistas | no existen (README: "candidatos") | — | — | — | ⚪ |
| R-41 | Patrón Adapter | no existe | — | — | — | ⚪ |
| R-42 | Frontend React | no está en el repo | — | — | — | ⚪ |
| R-43 | Sesiones distribuidas (Redis / Spring Session) | no existe | — | — | — | ⚪ |

## 2. Casos del plan de pruebas → test automatizado

| Caso | Descripción | Test que lo cubre | Resultado |
|---|---|---|---|
| PT-001 | Login válido e inválido | `AuthFlowTests` | ✅ |
| PT-002 | Operador intenta borrar → 403 | `PedidoSecurityTests.operadorNoPuedeEliminarPedido` | ✅ |
| PT-003 | Crear pedido con stock | `PedidoServiceTests`, `PedidoSecurityTests` | ✅ |
| PT-004 | SOAP con DTD → Fault | `LegacySoapControllerTests.rechazaSoapConDoctype…` | ✅ |
| PT-005 | REST excede 5 s → fallback | `ModernRuteoRestClientTests.timeoutDeCinco…` | ✅ |
| PT-006 | Repetir evento JMS | `PedidoEventListenersIdempotencyTests` | ✅ |
| PT-007 | Repetir `Idempotency-Key` | — | 🔴 sin test |
| PT-008 | `If-None-Match` → 304 | — | 🔴 sin test |
| PT-009 | Gateway de IA sin URL → 502 | `AiGatewayTests` | 🟡 no verifica el 502 |

## 3. Hallazgos

1. **Sin tests:** `Idempotency-Key`, `ETag`, Problem Details, DLQ, publicación JMS y `@PreDestroy`.
2. **DLQ:** no hay `RedeliveryPolicy` explícita; se depende de los valores por defecto de ActiveMQ y de un log de error.
3. **Bypass del State:** `PUT /api/pedido/{id}` acepta `estado` y lo asigna con `setEstado`, salteando las reglas del patrón.
4. **Stock sin ciclo completo:** entregar o fallar un pedido no confirma ni libera el stock bloqueado; solo se libera al editar o eliminar.
5. **`@Transactional` sin efecto:** está sobre un método `protected` invocado desde la misma clase (`procesarIdempotente`), por lo que Spring no lo intercepta. La restricción `unique` en BD sigue evitando duplicados.
6. **Fallback REST:** la URL por defecto apunta a `/api/ruteo/optimizar` del mismo servidor, que exige autenticación, y el cliente no envía credenciales. Probablemente siempre cae al fallback.
7. **Errores inconsistentes:** se mezclan `{"error": ...}`, `{"mensaje": ...}` y Problem Details. `cambiarEstado` lanza `RuntimeException` si el pedido no existe (500 en lugar de 404).
8. **Seed de prueba:** `BackendApplication` siembra 21 ítems, 18 de ellos `TEST-`, `LOW-` y `SESSION-`; luego `CatalogoDataInitializer` los elimina.
9. **Documentación desactualizada:** `ENTREGA_PARCIAL_1` menciona Spring Boot 3.5.0 (el `pom` usa 4.1.1), `PedidoServiceImpl` (no existe), usuarios en `SecurityConfig` (hoy están en BD) y un seed en `@PostConstruct` que ya no está. El README lista como pendiente la transacción declarativa multi-componente, que `crearDesdeCatalogo` ya usa.
10. **Credenciales por defecto** en `application.properties` y `DataInitializer`: válidas para desarrollo, cambiarlas antes de cualquier despliegue.

## 4. Totales

- 85 tests en 17 clases.
- 43 requisitos: 17 ✅, 10 🟡, 12 🔴 y 4 ⚪.
- Cobertura JaCoCo: se genera en `backend/target/site/jacoco/index.html` y como artefacto del pipeline; no hay umbral mínimo configurado.