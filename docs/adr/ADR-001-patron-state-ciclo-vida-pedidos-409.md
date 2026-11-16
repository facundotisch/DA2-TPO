# ADR-001: Implementación del Patrón State en la Entidad Central Pedido y Retorno de HTTP 409 ante Transiciones Inválidas

* **Estado:** Aceptado e Implementado
* **Fecha:** 15 de Noviembre de 2026
* **Componente:** `logired.backend.pedido` (Entidad central `Pedido`, Estados y Controlador REST)
* **Autores:** Grupo 13 (Facundo Tisch, Lola Díaz, Nicolás Valenzuela)
* **Requisito Cátedra:** *"El patrón State en la entidad central tiene que devolver 409 ante una transición inválida."*

---

## 1. Contexto y Planteamiento del Problema
En el dominio logístico de **LogiRed**, el ciclo de vida de un envío sigue un flujo operacional estricto:
1. `CREADO`: El pedido fue registrado y sus ítems de inventario fueron reservados.
2. `ASIGNADO`: El pedido fue asignado a un depósito o repartidor para su preparación.
3. `EN_RUTA`: El repartidor retiró la carga y se encuentra en tránsito hacia el domicilio del comprador.
4. `ENTREGADO`: El paquete fue entregado de forma efectiva (estado final exitoso).
5. `FALLIDO`: Ocurrió una anomalía irrecuperable en el trayecto (estado final cancelado).

### El Problema Técnico:
Permitir que cualquier capa modifique directamente el campo de texto `pedido.setEstado("ENTREGADO")` genera inconsistencias severas:
* Se podrían marcar como "Entregados" pedidos que ni siquiera fueron asignados o despachados.
* Desde estados finales (`ENTREGADO` o `FALLIDO`), se podría reactivar arbitrariamente un pedido cerrado.
* Resolver esto mediante condicionales anidados (`if-else` / `switch`) en el servicio dispersa las reglas de negocio, viola el principio de Abierto/Cerrado (OCP) y dificulta el mantenimiento cuando se agreguen nuevos estados operativos (ej. `EN_SUCURSAL`, `REINTENTO`).

---

## 2. Decisión Arquitectónica
Se implementó el **Patrón State (GoF)** modelando cada estado del pedido como una clase concreta que implementa una interfaz polimórfica común:

```
                  ┌──────────────────────┐
                  │    <<interface>>     │
                  │     EstadoPedido     │
                  ├──────────────────────┤
                  │ + asignar(Pedido)    │
                  │ + ponerEnRuta(Pedido)│
                  │ + entregar(Pedido)   │
                  │ + fallar(Pedido)     │
                  │ + getNombreEstado()  │
                  └──────────┬───────────┘
                             │
     ┌───────────────┬───────┴───────┬───────────────┬───────────────┐
     ▼               ▼               ▼               ▼               ▼
┌──────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐
│Estado    │   │Estado     │   │Estado     │   │Estado     │   │Estado     │
│Creado    │   │Asignado   │   │EnRuta     │   │Entregado  │   │Fallido    │
└──────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘
 (Permite       (Permite        (Permite        (Estado         (Estado
  asignar/       en ruta/        entregar/       final:          final:
  fallar)        fallar)         fallar)         inmutable)      inmutable)
```

### Reglas de Transición y Manejo Defensivo:
1. **Entidad Contexto (`Pedido`):** Mantiene una referencia transitoria a su estado actual (`estadoInstancia`), mientras que el campo persistible `estado` (`String`) guarda el nombre en la tabla `pedidos` de PostgreSQL.
2. **Reconstrucción Automática:** Al cargarse desde base de datos, el método `reconstruirEstadoDesdeBD()` instancia polimórficamente la clase de estado correspondiente.
3. **Excepción de Negocio Tipada (`TransicionInvalidaException`):** Cada método de la interfaz define por defecto que la acción no está permitida para ese estado. Cuando una clase de estado no sobreescribe una acción, lanza `TransicionInvalidaException`.
4. **Mapeo a Código HTTP 409 Conflict:** La clase `TransicionInvalidaException` está anotada con `@ResponseStatus(HttpStatus.CONFLICT)`, garantizando que el framework Spring MVC capture la excepción y genere una respuesta HTTP con código **409 Conflict** y el mensaje explicativo de por qué fue rechazada.

---

## 3. Consecuencias

### Positivas:
* **Cumplimiento estricto de la consigna:** Todo intento de salto inválido es rechazado de inmediato con HTTP 409.
* **Separación de responsabilidades:** Cada clase de estado (`EstadoCreado`, `EstadoEnRuta`, etc.) concentra únicamente las reglas de las operaciones permitidas desde su etapa.
* **Trazabilidad y Auditoría:** La API devuelve mensajes descriptivos que explican por qué la acción no pudo ejecutarse (ej. *"Estado final: ya fue ENTREGADO"* o *"Desde CREADO solo se puede pasar a ASIGNADO o FALLIDO"*).
* **Alta cohesión:** La entidad `Pedido` delega su comportamiento al estado activo en lugar de depender de lógica procedural.

### Negativas / Trade-offs:
* Requiere sincronizar la instancia en memoria (`EstadoPedido`) con la columna persistida en PostgreSQL (`estado`). Esto se mitigó encapsulando la mutación en `setEstadoInstancia()`.

---

## 4. Evidencia en Código y Tests

### Clases Involucradas:
* **Interfaz de Estado:** [`logired.backend.pedido.state.EstadoPedido`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/pedido/state/EstadoPedido.java)
* **Estados Concretos:**
  * `EstadoCreado.java`
  * `EstadoAsignado.java`
  * `EstadoEnRuta.java`
  * `EstadoEntregado.java`
  * `EstadoFallido.java`
* **Excepción HTTP 409:** [`logired.backend.pedido.exception.TransicionInvalidaException`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/pedido/exception/TransicionInvalidaException.java)
* **Endpoint REST:** [`PedidoController.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/pedido/controller/PedidoController.java)
  ```java
  @PatchMapping("/{id}/estado")
  public ResponseEntity<Pedido> actualizarEstado(@PathVariable Long id, @RequestParam String accion) {
      Pedido actualizado = pedidoService.cambiarEstado(id, accion);
      return ResponseEntity.ok(actualizado);
  }
  ```

### Verificación Automatizada (JUnit 5 + MockMvc):
En [`PedidoSecurityTests.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/test/java/logired/backend/pedido/controller/PedidoSecurityTests.java):
```java
@Test
@WithMockUser(username = "admin", roles = {"ADMIN"})
void transicionInvalidaDeEstadoDevuelve409Conflict() throws Exception {
    when(pedidoService.cambiarEstado(1L, "ENTREGAR"))
            .thenThrow(new TransicionInvalidaException("Transición inválida"));

    mockMvc.perform(patch("/api/pedido/1/estado")
            .param("accion", "ENTREGAR"))
            .andExpect(status().isConflict()); // HTTP 409 Conflict verificado
}
```
* **Resultado del Test:** **PASSED (HTTP 409 Conflict recibido)**.
