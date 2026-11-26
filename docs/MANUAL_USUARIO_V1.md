# Manual de usuario LogiRed v1

## Inicio

1. Copiar `.env.example` a `.env` y completar las contraseñas.
2. Ejecutar `docker compose up --build` desde la raíz del repositorio (levanta base, broker, mock de ruteo y app).
3. Iniciar sesión con el usuario `operador` o `admin` y la contraseña definida en `.env`.

## Operaciones principales

- Consultar, crear y editar pedidos desde `/api/pedido`.
- Administrar stock desde `/api/inventario` y `/api/productos`.
- Calcular rutas desde `/api/ruteo/optimizar`.
- Consultar eventos JMS desde `/api/mensajeria/eventos` (solo `admin`).
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
Sin configuración válida, o si el proveedor no responde, devuelve HTTP 200 con `degradada: true` y un mensaje de respaldo.

## Cabeceras HTTP

- En mutaciones usar `Idempotency-Key`.
- En consultas usar `If-None-Match` si se obtuvo un `ETag`.
- En `PUT`/`PATCH` de pedidos enviar `If-Match` con el `ETag` para evitar pisar cambios de otro usuario (HTTP 412).
- Basic Auth es obligatorio salvo rutas públicas declaradas.
