# Plan de pruebas v1

## Alcance

- APIs REST de autenticación, pedidos, inventario, productos, ruteo y mensajería.
- Integraciones SOAP/REST/JMS.
- Idempotencia, ETags, timeout/fallback, protección XXE y formato RFC 7807.

## Tipos de prueba

- Unitarias con JUnit 5 y Mockito.
- Controladores REST con MockMvc.
- Contexto Spring y persistencia JPA.
- Integración con broker ActiveMQ embebido/externo.
- Seguridad RBAC con Spring Security Test.

## Casos prioritarios

| ID | Caso | Resultado esperado |
|---|---|---|
| PT-001 | Login válido e inválido | 200 o 401 |
| PT-002 | Operador intenta borrar | 403 |
| PT-003 | Crear pedido con stock | Reserva y devuelve pedido |
| PT-004 | SOAP con DTD/entidades | SOAP Fault |
| PT-005 | REST excede 5 s | Fallback local |
| PT-006 | Repetir evento JMS | No reprocesa |
| PT-007 | Repetir Idempotency-Key | Respuesta original o 409 |
| PT-008 | GET con If-None-Match | 304 si no cambió |
| PT-009 | Gateway de IA sin URL | Problem Detail 502 |

## Automatización

Ejecutar `cd backend && ./mvnw test`. El pipeline ejecuta `verify` y publica el reporte Jacoco.
