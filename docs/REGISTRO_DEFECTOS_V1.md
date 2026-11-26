# Registro Oficial de Defectos v1.0 - LogiRed

Cumplimiento de Criterio de Evaluación: **0 Defectos Bloqueantes / Críticos abiertos, máx. 3 Mayores con Workaround documentado**.

| ID | Estado | Severidad | Descripción del Defecto | Evidencia / Ubicación | Workaround Documentado | Responsable |
|---|---|---|---|---|---|---|
| **DEF-001** | Abierto | Mayor | Ruteo externo puede sufrir latencia variable o intermitencia en redes lentas. | `ModernRuteoRestClient` | **Fallback algorítmico local automático**: Si el endpoint REST de ruteo supera el timeout de 5s o responde 5xx, el cliente conmuta de forma transparente a `RuteoServiceImpl` (estrategias Dijkstra/heurística local), garantizando continuidad operativa con HTTP 200. | Backend / Nicolás |
| **DEF-002** | Abierto | Mayor | Las reservas conversacionales de stock (`@SessionScope`) son locales a la instancia de la aplicación. | `InventarioServiceImpl` | **Persistencia transaccional previa**: El operador puede proceder a la confirmación del pedido directamente vía `/api/pedido` con reservas pesimistas (`PESSIMISTIC_WRITE`) a nivel de base de datos relacional, evitando depender de la memoria de sesión. | Backend / Lola |
| **DEF-003** | Abierto | Mayor | Si el broker ActiveMQ se apaga durante la creación de un pedido, el evento JMS no puede enviarse inmediatamente. | `OutboxService` | **Transactional Outbox Pattern**: El evento queda guardado transaccionalmente en la tabla `outbox_events` con estado `PENDIENTE`. Un scheduler reintenta automáticamente la publicación cada 2000 ms; al reanudar el broker, ningún mensaje se pierde ni se duplica. | DevOps / Facundo |
| **DEF-004** | Abierto | Menor | Gateway de IA requiere API Key externa opcional de proveedor LLM. | `AiGatewayClient` | **Respuesta segura por defecto**: Si no hay API Key o falla el proveedor, el gateway retorna una clasificación estructurada predeterminada (`sinSugerencia()`) con confianza 0.0 y estado controlado, sin lanzar 500 al cliente. | Arquitectura / Nico |

> **Resumen de Severidad:**
> - Bloqueantes: 0
> - Críticos: 0
> - Mayores: 3 (todos con workaround funcional verificado en tests)
> - Menores: 1
