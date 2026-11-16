# ADR-003: Implementación del Patrón Stateful Session con Hold de Stock en InventarioService

* **Estado:** Aceptado e Implementado
* **Fecha:** 15 de Noviembre de 2026
* **Componente:** `logired.backend.inventario` (Servicio de Inventario, Entidades y Bloqueo Pesimista)
* **Autores:** Grupo 13 (Facundo Tisch, Lola Díaz, Nicolás Valenzuela)
* **Requisito Cátedra:** *"Patrones implementados en el código, cada uno con su ADR."*

---

## 1. Contexto y Planteamiento del Problema
En plataformas logísticas de alta concurrencia, múltiples operadores o compradores pueden intentar reservar unidades del mismo producto simultáneamente. 
* Si la reserva se procesa de forma no transaccional o sin estado conversacional, se produce **sobreventa (overselling)** por condiciones de carrera (*race conditions*).
* Si una unidad se descuenta de forma definitiva antes de confirmar el pago o completar el pedido, y el usuario abandona la sesión, el stock quedaría perdido.

### El Problema Técnico:
Se necesita un mecanismo que:
1. Congele temporalmente el stock (*Hold*) mientras dura el proceso de compra/despacho.
2. Mantenga el estado conversacional aislado por usuario.
3. Libere automáticamente las unidades si la sesión HTTP expira o se cierra inesperadamente.
4. Serialice el acceso concurrente a la base de datos para no vender más unidades de las disponibles.

---

## 2. Decisión Arquitectónica
Se diseñó un componente con estado conversacional utilizando **Spring `@SessionScope`** combinado con **Bloqueo Pesimista en Base de Datos (`PESSIMISTIC_WRITE`)**:

```
[ Cliente HTTP ] ──(JSESSIONID)──> [ InventarioService (@SessionScope) ]
                                            │
                                            ├── Guarda reservas activas en memoria
                                            │   (Mapa: SKU + Depósito -> Cantidad)
                                            │
                                            ├── Timeout automático: 5 minutos
                                            │   (@PreDestroy -> Libera reservas)
                                            ▼
                               [ PostgreSQL 18: items_inventario ]
                                (SELECT ... FOR UPDATE con bloqueo pesimista)
```

### Mecanismos Clave:
1. **Alcance de Sesión (`@SessionScope`):** Cada usuario tiene una instancia separada de `InventarioServiceImpl` vinculada a su sesión web. En memoria se registra qué productos y cantidades tiene retenidas ese operador.
2. **Ciclo de Vida y Liberación Segura:**
   * `@PostConstruct`: Inicializa las estructuras de retención de la sesión.
   * `@PreDestroy`: Cuando la sesión expira (configurada en `server.servlet.session.timeout=5m`), Spring destruye el bean y ejecuta la liberación automática de las cantidades retenidas, reintegrándolas al stock disponible en PostgreSQL.
3. **Control de Concurrencia con Bloqueo Pesimista:**
   En `InventarioRepository`, el método de búsqueda de stock utiliza `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
   ```java
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT i FROM ItemInventario i WHERE i.sku = :sku AND i.deposito = :deposito")
   Optional<ItemInventario> findBySkuAndDepositoWithLock(String sku, String deposito);
   ```
   Esto emite una cláusula SQL nativa `SELECT ... FOR UPDATE` en PostgreSQL 18, bloqueando la fila hasta que termine la transacción y evitando colisiones entre operadores simultáneos.

---

## 3. Consecuencias

### Positivas:
* **Garantía estricta contra sobreventa:** Ninguna operación concurrente puede sobrepasar el stock físico disponible.
* **Experiencia de usuario protegida:** El operador tiene garantizadas sus unidades durante 5 minutos para completar el checkout.
* **Limpieza pasiva y autónoma:** Si el usuario cierra el navegador o se corta la conexión, el contenedor libera el stock sin intervención humana.

### Negativas / Trade-offs:
* **Escalabilidad horizontal en memoria:** Al residir las sesiones en la memoria JVM local del servidor, el despliegue con múltiples réplicas detrás de un balanceador de carga requeriría *Sticky Sessions* o la externalización del estado de sesión hacia una base distribuida en memoria (ej. Spring Session con Redis). Esta limitación está formalmente documentada para futuras entregas.

---

## 4. Evidencia en Código y Tests

### Clases Involucradas:
* **Servicio Stateful:** [`logired.backend.inventario.service.InventarioServiceImpl`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/inventario/service/InventarioServiceImpl.java)
* **Repositorio con Bloqueo:** [`logired.backend.inventario.repository.InventarioRepository`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/inventario/repository/InventarioRepository.java)
* **Configuración de Timeout:** `application.properties` (`server.servlet.session.timeout=5m`).

### Verificación Automatizada:
Tests unitarios en [`InventarioReservationTests.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/test/java/logired/backend/inventario/service/InventarioReservationTests.java) validando la retención de unidades y la imposibilidad de reservar cantidades por encima del saldo real.
