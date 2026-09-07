# INFORME TÉCNICO DE ARQUITECTURA, COMPONENTES Y PATRONES
## Entrega Obligatoria N.º 1 — 1.º Parcial (Unidades I, II y III)
**Cátedra:** Desarrollo de Aplicaciones II • Cursada 2026 • Universidad Argentina de la Empresa (UADE)  
**Proyecto:** LogiRed — Plataforma Distribuida de Logística de Última Milla  
**Grupo:** N.º 13  
**Integrantes:** Facundo Tisch · Lola Díaz · Nicolás Valenzuela  
**Fecha de Entrega & Defensa:** 14 de Septiembre de 2026  
**Tecnologías Principales:** Java 17 (Adoptium) · Spring Boot 3.5.0 · PostgreSQL 18 · React 19 (Vite)

---

## 1. Introducción y Dominio del Sistema

**LogiRed** es una plataforma orientada a servicios (SOA) diseñada para coordinar las operaciones de logística urbana de última milla entre comercios minoristas, centros de almacenamiento/distribución (depósitos en Barracas y Munro) y una flota de repartidores independientes en el Área Metropolitana de Buenos Aires (AMBA).

El sistema resuelve problemáticas críticas del sector:
1. **Gestión integral de pedidos:** Recepción, tarifación y seguimiento de envíos con trazabilidad de estados.
2. **Optimización algorítmica de rutas:** Ruteo eficiente minimizando distancias o tiempos de congestión en CABA.
3. **Reserva preventiva de stock (Hold de sesión):** Prevención de sobreventa mediante reservas temporales con liberación automática si la operación caduca o se cancela.

Para cumplir rigurosamente con los objetivos pedagógicos de las Unidades I, II y III de la materia, se diseñó e implementó una arquitectura en tres capas, utilizando exclusivamente la base de datos relacional **PostgreSQL 18** (sin bases volátiles en memoria como H2), aplicando patrones de diseño de software reconocidos, gestión de ciclo de vida en el contenedor de inversión de control (IoC) y seguridad declarativa por roles (RBAC).

---

## 2. Arquitectura Global en Tres Capas

La arquitectura desacopla estrictamente las responsabilidades del sistema en tres capas físicas y lógicas:

| Capa | Tecnologías | Responsabilidad en LogiRed |
| :--- | :--- | :--- |
| **1. Capa de Presentación (Frontend)** | React 19 + Vite 8.2<br>CSS3 Modular, React Router 7 | Aplicación SPA que consume las APIs REST del Hub. Ofrece paneles de control (Dashboard), alta y edición de órdenes de envío, cotización en tiempo real, selector dinámico de estrategia de ruteo y consola interactiva de control de stock y depósitos. |
| **2. Capa de Negocio / Servicios (Backend)** | Spring Boot 3.5.0<br>Java 17 (Eclipse Adoptium) | Contenedor IoC/DI. Expone controladores REST delgados, orquesta las reglas de negocio, administra el estado de sesión HTTP conversacional (`@SessionScope`), ejecuta los algoritmos del patrón Strategy y aplica la seguridad declarativa RBAC. |
| **3. Capa de Persistencia (Datos)** | PostgreSQL 18 Relacional<br>Spring Data JPA / Hibernate | Motor de base de datos relacional transaccional (ACID). Garantiza la persistencia definitiva de órdenes (`pedidos`) y catálogo de ítems por depósito (`items_inventario`) con bloqueos de concurrencia y claves foráneas. |

---

## 3. Implementación y Despliegue de los 3 Componentes

En estricta consonancia con el catálogo oficial de 6 componentes presentado en el informe técnico inicial del Grupo 13, se implementaron completamente los siguientes 3 componentes:

### 3.1. Componente 1: `PedidoService` (Stateless · Singleton)
- **Naturaleza y Ámbito:** Stateless (sin estado conversacional entre peticiones). Gestionado en ámbito Singleton por el contenedor Spring Boot (`@Service`).
- **Responsabilidad:** Administrar el ciclo de vida de las órdenes de envío en LogiRed (alta, cotización inicial según peso y zona, consulta de estado, actualización y baja definitiva).
- **Arquitectura Interna en 3 Capas:**
  - *Capa Presentación:* `PedidoController.java` (`/api/pedido`) con CORS habilitado y autenticación básica HTTP.
  - *Capa Negocio:* `PedidoService.java` y su implementación `PedidoServiceImpl.java`.
  - *Capa Datos:* `PedidoRepository.java` (extiende `JpaRepository<Pedido, Long>`) y la entidad JPA `Pedido.java` mapeada a la tabla `pedidos` en PostgreSQL.

### 3.2. Componente 5: `RuteoService` (Stateless · Servicio SOA Reutilizable con Patrón Strategy)
- **Naturaleza y Ámbito:** Stateless. Cada invocación es idempotente y autocontenida: recibe un conjunto de direcciones o paradas y devuelve la secuencia ordenada más óptima.
- **Responsabilidad:** Proporcionar al Hub logístico y a los repartidores la secuencia más eficiente para el despacho y entrega de paquetes.
- **Aplicación del Patrón Strategy:** Desacopla la lógica algorítmica de optimización en dos estrategias intercambiables en tiempo de ejecución:
  - `RutaMasCortaStrategy`: Algoritmo heurístico que minimiza la distancia total en kilómetros (ideal para transportistas de carga pesada).
  - `RutaMasRapidaStrategy`: Algoritmo que pondera coeficientes de velocidad y congestión urbana en CABA según la franja horaria (ideal para motomensajería urgente).
- **Contrato de Servicio SOA:** Expone el endpoint `POST /api/ruteo/optimizar` mediante DTOs fuertemente tipados (`RutaRequest` y `RutaResponse`).

### 3.3. Componente 3: `InventarioService` (Stateful · Ámbito `@SessionScope`)
- **Naturaleza y Ámbito:** Stateful (`@SessionScope`). Cada cliente u operador que inicia un flujo de preparación de despacho mantiene una instancia propia y aislada durante su sesión HTTP.
- **Justificación de Negocio (Hold de Stock de 5 min):** En logística urbana con stock limitado en depósitos de consolidación (Barracas y Munro), si dos operadores cargan pedidos simultáneamente sin bloquear el inventario, se produce sobreventa involuntaria. `InventarioService` implementa un hold temporal: al reservar stock, este se descuenta del disponible y se transfiere a bloqueado en PostgreSQL, registrando la reserva en la sesión del usuario.
- **Gestión del Ciclo de Vida por el Contenedor:** Utiliza anotaciones Jakarta EE / Spring (`@PostConstruct` y `@PreDestroy`) para inicializar la sesión conversacional y garantizar la liberación automática de stock si la sesión expira o el usuario se desconecta.

---

## 4. Gestión de Estado y Ciclo de Vida: Stateful vs. Stateless

### 4.1. Cuadro Comparativo Técnico

| Criterio de Diseño | Componentes Stateless (`PedidoService` / `RuteoService`) | Componente Stateful (`InventarioService`) |
| :--- | :--- | :--- |
| **Anotación de Ámbito** | `@Service` (Singleton por defecto del contenedor) | `@Service` + `@SessionScope` (Un bean por sesión HTTP) |
| **Almacenamiento de Estado** | Ninguno en la memoria del servidor; se delega en PostgreSQL. | Mantiene en memoria del bean el `idSesionConversacional` y el mapa `reservasActivasEnSesion`. |
| **Idempotencia / Concurrencia** | Totalmente idempotente y thread-safe. Cualquier hilo puede reutilizar la misma instancia. | Estado protegido por sesión. Cada cliente interactúa con su propio contexto conversacional. |
| **Escalabilidad Horizontal** | Máxima (*Share Nothing*). Cualquier nodo de un clúster puede atender cualquier request. | Requiere afinidad de sesión (*Sticky Sessions*) o externalización de sesión a una caché distribuida (*Redis*). |
| **Ciclo de Vida** | Nace al iniciar el contenedor; muere al apagar el servidor. | Nace al iniciar la sesión del usuario (`@PostConstruct`); muere al expirar el timeout o cerrar sesión (`@PreDestroy`). |

### 4.2. Evidencia de Callbacks de Ciclo de Vida en el Contenedor

En `InventarioServiceImpl.java` se evidencian los callbacks de ciclo de vida:

```java
@PostConstruct
public void inicializarSesion() {
    this.idSesionConversacional = "SESS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    this.fechaInicioSesion = LocalDateTime.now();
    log.info("[CICLO DE VIDA] @PostConstruct: Nueva sesión conversacional {} creada.", idSesionConversacional);
    
    // Inicialización del catálogo en PostgreSQL si la tabla está vacía
    if (inventarioRepository.count() == 0) {
        inventarioRepository.save(new ItemInventario("IND-001", "Zapatillas Urbanas Running", "Depósito Central Barracas", 45, 0));
        inventarioRepository.save(new ItemInventario("IND-002", "Campera Impermeable Térmica", "Depósito Central Barracas", 20, 0));
        inventarioRepository.save(new ItemInventario("ELEC-010", "Auriculares Bluetooth Pro", "Depósito Norte Munro", 60, 0));
    }
}

@PreDestroy
public void finalizarSesion() {
    log.info("[CICLO DE VIDA] @PreDestroy: Finalizando sesión {}. Revertiendo reservas pendientes...", idSesionConversacional);
    
    // Liberación automática de reservas huérfanas al vencer la sesión HTTP
    for (Map.Entry<String, Integer> reserva : reservasActivasEnSesion.entrySet()) {
        String[] partes = reserva.getKey().split("@");
        revertirReservaEnBase(partes[0], partes[1], reserva.getValue());
    }
    reservasActivasEnSesion.clear();
    log.info("Todas las reservas huérfanas de la sesión {} fueron reintegradas a PostgreSQL.", idSesionConversacional);
}
```

> **Justificación para la Defensa Oral — Liberación de Stock Huérfano:**  
> Si un comercio reserva stock para un envío y abandona intempestivamente la aplicación o pierde conexión, el contenedor Spring detecta el vencimiento de la sesión HTTP e invoca automáticamente `@PreDestroy`. El método recorre las unidades retenidas en esa sesión y las reincorpora al stock disponible en PostgreSQL, evitando bloqueos permanentes de mercadería sin requerir procesos cron o scripts manuales.

---

## 5. Patrones de Diseño Aplicados y Justificados

El sistema incorpora tres patrones de diseño distintos, resolviendo problemáticas específicas de arquitectura:

### 5.1. Patrón DAO / Repository (Acceso a Datos)
- **Implementación:** `PedidoRepository` e `InventarioRepository` (`org.springframework.data.jpa.repository.JpaRepository`).
- **Justificación:** Aislar la capa de negocio de la complejidad de las sentencias SQL y la tecnología de almacenamiento. Permite ejecutar operaciones transaccionales CRUD, búsquedas personalizadas (`findBySkuAndDeposito`) y paginación mediante abstracciones fuertemente tipadas. Cumple con el Principio de Inversión de Dependencias (DIP) y el Principio de Responsabilidad Única (SRP).

### 5.2. Patrón Strategy (Comportamiento)
- **Implementación:** Interfaz `EstrategiaRuteo` y sus implementaciones concretas `RutaMasCortaStrategy` y `RutaMasRapidaStrategy`, orquestadas por `RuteoServiceImpl`.
- **Justificación:** El cálculo de rutas logísticas requiere diferentes criterios según el tipo de transporte (furgón vs. motocicleta) y las condiciones horarias. El patrón Strategy encapsula cada familia de algoritmos dentro de una clase independiente que cumple una interfaz común. Esto respeta el Principio Abierto/Cerrado (OCP), posibilitando la incorporación de nuevas estrategias (ej. *RutaEcoFriendly* o *RutaMinimosPeajes*) sin alterar el código del servicio que las consume ni el contrato de la API.

### 5.3. Patrón Facade / Service Layer (Estructural y de Arquitectura)
- **Implementación:** `PedidoServiceImpl`, `RuteoServiceImpl` e `InventarioServiceImpl`.
- **Justificación:** Proporcionar una interfaz simplificada y de alto nivel a los controladores REST. Cada servicio actúa como una fachada que oculta la orquestación interna: validaciones de reglas de negocio, transaccionalidad declarativa (`@Transactional`), control de concurrencia, selección de estrategias algorítmicas, auditoría de eventos y persistencia. De este modo, los controladores actúan como *Skinny Controllers*, limitándose a transformar solicitudes HTTP en invocaciones a la fachada.

---

## 6. Seguridad Declarativa: Autenticación y Autorización por Rol (RBAC)

### 6.1. Justificación de Seguridad Declarativa vs. Programática
- **Seguridad Declarativa (`@PreAuthorize`, `@EnableMethodSecurity`):** Desacopla las políticas de acceso de la lógica de negocio. Las reglas se especifican como metadatos sobre los métodos o rutas. Permite auditorías rápidas, mantenimiento centralizado y garantiza que las modificaciones de seguridad no requieran recompilar o modificar los algoritmos del servicio.
- **Inconveniente de la Seguridad Programática:** Requeriría ensuciar cada método con validaciones manuales (`if (!SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(...))`), resultando en código repetitivo, difícil de testear y altamente vulnerable a descuidos.

### 6.2. Implementación de la Operación Sensible Protegida
En `SecurityConfig.java` se definieron los roles y usuarios con codificación `BCryptPasswordEncoder`:
- **Usuario Operador:** `operador` / `operador123` con rol `ROLE_OPERADOR`.
- **Usuario Administrador:** `admin` / `admin123` con rol `ROLE_ADMIN`.

En `PedidoController.java` se protegió la operación sensible de eliminación y baja física de pedidos:

```java
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> eliminar(@PathVariable Long id) {
    pedidoService.eliminar(id);
    return ResponseEntity.noContent().build();
}
```

**Comportamiento Verificado:**
- Al invocar `DELETE /api/pedido/{id}` con credenciales de `operador`, Spring Security intercepta la petición por AOP antes de ejecutar el controlador y devuelve **HTTP 403 Forbidden**.
- Al invocar la misma petición con credenciales de `admin`, el sistema autoriza la operación y elimina el registro de PostgreSQL, devolviendo **HTTP 204 No Content**.

---

## 7. Matriz de Preguntas Clave para la Defensa Oral

| Pregunta Típica de la Cátedra | Respuesta Técnica del Grupo 13 |
| :--- | :--- |
| **1. ¿Por qué un componente es stateful o stateless y qué impacto tiene en la escalabilidad?** | `PedidoService` y `RuteoService` son stateless porque cada solicitud contiene toda la información necesaria para completarse sin depender de llamadas anteriores. Esto permite escalabilidad horizontal lineal mediante un balanceador de carga simple.<br>`InventarioService` es stateful porque implementa un hold temporal de stock durante el proceso de armado del pedido. El impacto es que introduce afinidad de sesión, requiriendo sticky sessions o memoria distribuida para escalar. |
| **2. ¿Qué ocurre con el estado conversacional si la aplicación escala a múltiples servidores backend?** | Si se utiliza balanceo de carga sin configuración adicional, una segunda petición del mismo usuario podría llegar a un nodo diferente que no posee la sesión en su memoria local. Para resolver esto en producción se externaliza el estado de la sesión utilizando **Spring Session** respaldado por una base en memoria distribuida como **Redis**, permitiendo que cualquier nodo acceda al estado conversacional en milisegundos. |
| **3. ¿Cómo interactúan los tres patrones de diseño implementados?** | El cliente invoca el controlador REST, el cual se comunica con la **Fachada** (`RuteoServiceImpl` / `PedidoServiceImpl`). Para resolver la optimización, la fachada delega el algoritmo en una **Estrategia** concreta (`RutaMasCortaStrategy` o `RutaMasRapidaStrategy`). Finalmente, la fachada persiste el resultado en PostgreSQL delegando en el **DAO / Repository** (`PedidoRepository`). |
| **4. ¿Por qué eligieron seguridad declarativa y qué ventaja ofrece frente a la programática?** | Porque respeta el principio de separación de incumbencias (*Separation of Concerns*). La seguridad declarativa intercepta las llamadas mediante proxies dinámicos / AOP antes de entrar a la lógica de negocio. Si las reglas de negocio cambian (por ejemplo, permitir también el rol `SUPERVISOR`), solo se modifica la anotación `@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")` sin alterar ni una línea del código de negocio. |

---

## 8. Conclusiones

La Entrega Obligatoria N.º 1 del proyecto **LogiRed** demuestra la integración funcional y arquitectónica de los conceptos nucleares de las Unidades I, II y III:
- Arquitectura desacoplada en tres capas reales (React, Spring Boot, PostgreSQL).
- Componentes reales del catálogo (`PedidoService`, `RuteoService`, `InventarioService`).
- Ciclo de vida administrado por el contenedor IoC con `@PostConstruct` y `@PreDestroy`.
- Patrones de diseño aplicados con justificación de ingeniería (DAO, Strategy, Facade).
- Seguridad declarativa RBAC con protección de operaciones sensibles.
- Despliegue sobre base de datos relacional PostgreSQL 18.
