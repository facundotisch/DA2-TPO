# Manual de usuario LogiRed v1

## Inicio

1. Iniciar PostgreSQL y ActiveMQ.
2. Ejecutar `cd backend && ./mvnw spring-boot:run`.
3. Iniciar sesión con usuario/contraseña.

## Operaciones principales

- Consultar, crear y editar pedidos desde `/api/pedido`.
- Administrar stock desde `/api/inventario` y `/api/productos`.
- Calcular rutas desde `/api/ruteo/optimizar`.
- Consultar eventos JMS desde `/api/mensajeria/eventos`.
- Consultar WSDL SOAP desde `/ws/legacy-ruteo.wsdl`.

## Integración REST

Configurar `logired.integraciones.rest.ruteo-url` y, opcionalmente,
`logired.integraciones.rest.timeout-millis`.

## Gateway de IA

Configurar como variables de entorno las propiedades:

- `LOGIRED_INTEGRACIONES_IA_GATEWAY_URL`
- `LOGIRED_INTEGRACIONES_IA_API_KEY`
- `LOGIRED_INTEGRACIONES_IA_MODEL`

Invocar `POST /api/ia/generar` con `{ "prompt": "texto" }`.
Sin configuración válida devuelve una Problem Detail HTTP 502.

## Cabeceras HTTP

- En mutaciones usar `Idempotency-Key`.
- En consultas usar `If-None-Match` si se obtuvo un `ETag`.
- Basic Auth es obligatorio salvo rutas públicas declaradas.
