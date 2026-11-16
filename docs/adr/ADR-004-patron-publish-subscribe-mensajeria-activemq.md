# ADR-004: Implementación del Patrón Asincrónico Publish-Subscribe (Pub/Sub) con Apache ActiveMQ Classic

* **Estado:** Aceptado e Implementado
* **Fecha:** 15 de Noviembre de 2026
* **Componente:** `logired.backend.mensajeria` (Broker ActiveMQ, JmsConfig, Productor y Listeners)
* **Autores:** Grupo 13 (Facundo Tisch, Lola Díaz, Nicolás Valenzuela)
* **Requisito Cátedra:** *"Mensajería funcionando en al menos un proceso."*

---

## 1. Contexto y Planteamiento del Problema
Cuando un cliente o comercio confirma un nuevo pedido en **LogiRed**, el sistema debe disparar múltiples actividades periféricas obligatorias:
1. Ordenar al depósito central que inicie el proceso de preparación física (*Picking y Packing*).
2. Notificar a logística para que recalcule rutas y pre-asigne chofer de última milla.
3. Enviar una confirmación con enlace de seguimiento al comprador por canales externos (WhatsApp / Email).

### El Problema Técnico:
Ejecutar estas operaciones de forma sincrónica dentro del hilo HTTP del controlador `POST /api/pedido`:
* Degrada la latencia percibida por el usuario (la respuesta tardaría varios segundos esperando a los otros sistemas).
* Acopla fuertemente el subsistema de Pedidos con Depósito, Ruteo y Comunicaciones (si el servidor de notificaciones o el depósito falla, la creación del pedido fallaría por efecto dominó).
* Viola la autonomía de servicios en una arquitectura orientada a servicios (SOA).

---

## 2. Decisión Arquitectónica
Se implementó un esquema de **Mensajería Asincrónica Basada en Eventos (EDA)** utilizando el patrón **Publish-Subscribe (Pub/Sub) 1:N** sobre un broker de mensajes real: **Apache ActiveMQ Classic** (`tcp://localhost:61616`).

```
[ Cliente HTTP ] ──(POST /api/pedido)──> [ PedidoService ] ──> [ PostgreSQL (INSERT) ]
                                                │
                                                ▼  (Publicación Fire-and-Forget)
                                      [ Tópico ActiveMQ JMS ]
                                      "logired.envios.creados"
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    ▼                           ▼                           ▼
        [ @JmsListener Depósito ]   [ @JmsListener Ruteo ]    [ @JmsListener Notificaciones ]
        (Inicia preparación física) (Calcula recorrido)       (Emite aviso al comprador)
```

### Principales Definiciones de Diseño:
1. **Modelo de Entrega Pub/Sub (Tópico JMS):** Se configuró el contenedor de Spring JMS con `setPubSubDomain(true)` para operar sobre el tópico `logired.envios.creados`, asegurando que cada suscriptor reciba una copia independiente y concurrente del mensaje (modelo Fan-Out).
2. **Payload Tipado y Seguro:** Se utiliza un DTO estricto serializado en JSON (`PedidoCreadoEvent`) transportado dentro de un `TextMessage` estándar, descartando `ObjectMessage` por razones de seguridad defensiva (OWASP A08 - Prevención de deserialización insegura).
3. **Resiliencia de Conexión (Broker Embebido vs Externo):** La clase `JmsConfig` detecta automáticamente si existe un broker ActiveMQ externo corriendo en el puerto TCP `61616`. Si no está disponible, levanta un broker embebido en memoria de forma transparente para no detener la ejecución local ni los tests continuos.
4. **Endpoint de Auditoría y Observabilidad:** Los listeners acumulan los eventos procesados en una estructura thread-safe en memoria expuesta bajo `GET /api/mensajeria/eventos` para consumo del frontend.

---

## 3. Consecuencias

### Positivas:
* **Desacoplamiento temporal y espacial:** `PedidoService` persiste el pedido en base de datos, emite el evento al broker y responde al cliente en menos de 50 ms.
* **Aislamiento de fallas:** Si el servicio de notificaciones o el servicio de ruteo experimenta una caída, el pedido ya está seguro en la base de datos y en el broker.
* **Escalabilidad independiente:** Es posible agregar nuevos consumidores (ej. módulo de Facturación o Auditoría Impositiva) sin tocar una sola línea del emisor `PedidoService`.

### Negativas / Trade-offs:
* Requiere infraestructura de broker activa (ActiveMQ) o dependencia de ejecución embebida.
* Consistencia eventual: El cliente recibe confirmación de que el pedido fue creado, pero la preparación y la notificación ocurren asincrónicamente unos milisegundos después.

---

## 4. Evidencia en Código y Tests

### Clases Involucradas:
* **Configuración del Broker y Contenedor JMS:** [`logired.backend.mensajeria.JmsConfig`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/mensajeria/JmsConfig.java)
* **Productor del Evento:** [`logired.backend.pedido.service.PedidoService.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/pedido/service/PedidoService.java) (método `publicarEventoPedidoCreado`).
* **Suscriptores Concurrentes:** [`logired.backend.mensajeria.PedidoEventListeners`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/mensajeria/PedidoEventListeners.java)
* **DTO del Evento:** [`logired.backend.mensajeria.PedidoCreadoEvent`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/mensajeria/PedidoCreadoEvent.java)
* **Controlador REST:** [`logired.backend.mensajeria.MensajeriaController`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/mensajeria/MensajeriaController.java)
