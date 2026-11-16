# Registro de Decisiones de Arquitectura (ADR)
## Sistema LogiRed · Grupo 13

Este directorio contiene los **Architecture Decision Records (ADR)** correspondientes a los patrones de diseño y decisiones estructurales implementados en el núcleo del sistema **LogiRed**, dando cumplimiento a los requisitos de la **2.ª Entrega Parcial (16/11)** de la cátedra de Desarrollo de Aplicaciones II.

---

### Índice de Decisiones Arquitectónicas

| Código | Título del Patrón / Decisión | Estado | Componente Afectado | Requisito de Cátedra |
| :---: | :--- | :---: | :--- | :--- |
| [**ADR-001**](./ADR-001-patron-state-ciclo-vida-pedidos-409.md) | **Patrón State en Entidad Central `Pedido` (Transiciones y HTTP 409)** | **Aceptado / Implementado** | `logired.backend.pedido.state` | Obligatorio: State con HTTP 409 ante transición inválida |
| [**ADR-002**](./ADR-002-patron-strategy-optimizacion-ruteo.md) | **Patrón Strategy en `RuteoService` para Algoritmos de Despacho** | **Aceptado / Implementado** | `logired.backend.ruteo.service` | Algoritmos intercambiables de optimización logística |
| [**ADR-003**](./ADR-003-patron-stateful-hold-inventario.md) | **Patrón Stateful Session con Hold de Stock en `InventarioService`** | **Aceptado / Implementado** | `logired.backend.inventario.service` | Manejo de estado conversacional y bloqueo pesimista |
| [**ADR-004**](./ADR-004-patron-publish-subscribe-mensajeria-activemq.md) | **Patrón Publish-Subscribe asincrónico con Apache ActiveMQ** | **Aceptado / Implementado** | `logired.backend.mensajeria` | Proceso asincrónico punta a punta con broker real |
| [**ADR-005**](./ADR-005-patron-repository-service-layer-jpa.md) | **Patrones DAO / Repository y Service Layer (Facade)** | **Aceptado / Implementado** | Capas de Persistencia y Negocio | Arquitectura en capas limpia y desacoplada |

---

### Estructura Estándar de los Registros
Cada ADR sigue el formato formal de Michael Nygard:
1. **Contexto:** Problema técnico y de negocio que motiva la decisión.
2. **Decisión:** Patrón adoptado y detalles de la implementación en código.
3. **Consecuencias:** Beneficios obtenidos y trade-offs evaluados.
4. **Evidencia en Código y Tests:** Clases, endpoints y tests unitarios automatizados que verifican la decisión.
