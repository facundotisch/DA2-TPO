# ADR-005: Implementación de los Patrones DAO / Repository y Service Layer (Facade) con Spring Data JPA

* **Estado:** Aceptado e Implementado
* **Fecha:** 15 de Noviembre de 2026
* **Componente:** Capas de Dominio, Persistencia y Negocio
* **Autores:** Grupo 13 (Facundo Tisch, Lola Díaz, Nicolás Valenzuela)
* **Requisito Cátedra:** *"Componentes en capas desplegados y un primer flujo de punta a punta funcionando."*

---

## 1. Contexto y Planteamiento del Problema
En el desarrollo de aplicaciones empresariales, mezclar la lógica de acceso a datos (consultas SQL / JDBC nativas) dentro de los controladores web o dentro de la lógica de negocio produce sistemas monolíticos frágiles, difíciles de testear y con alto riesgo de inyecciones SQL o fugas de conexiones a la base de datos.

### El Problema Técnico:
Se necesita garantizar:
1. Una arquitectura en capas estricta: `Presentación (Controllers)` $\rightarrow$ `Negocio (Services / Facades)` $\rightarrow$ `Persistencia (Repositories / DAO)` $\rightarrow$ `Base de Datos Relacional (PostgreSQL 18)`.
2. Aislamiento total de las operaciones de base de datos detrás de interfaces tipadas.
3. Demarcación transaccional declarativa (`@Transactional`) para garantizar atomicidad (ACID) en operaciones críticas como la creación de pedidos con reservas de inventario.

---

## 2. Decisión Arquitectónica
Se implementó la combinación de dos patrones fundamentales de Martin Fowler y GoF:
1. **Patrón Data Access Object (DAO) / Repository:** A través de Spring Data JPA (`JpaRepository<T, ID>`), abstrayendo todo el acceso a la base de datos relacional PostgreSQL.
2. **Patrón Service Layer / Facade:** A través de clases de servicio (`PedidoService`, `InventarioService`, `CatalogoService`, `AuthService`) que encapsulan los casos de uso del dominio, coordinan la lógica transaccional y exponen métodos de alto nivel a los controladores.

```
┌────────────────────────────────┐
│   Capa de Presentación (REST)  │  --> PedidoController, InventarioController, AuthController
└───────────────┬────────────────┘
                │ Inyecta servicios (DTOs de entrada y salida)
                ▼
┌────────────────────────────────┐
│   Capa de Negocio (Service)    │  --> PedidoService, InventarioService, AuthService
│   Demarcación @Transactional   │      (Aplica reglas de negocio, State, Strategy)
└───────────────┬────────────────┘
                │ Inyecta repositorios
                ▼
┌────────────────────────────────┐
│  Capa de Persistencia (DAO)    │  --> PedidoRepository, InventarioRepository, ProductoRepository
│  Spring Data JPA + Hibernate   │      (PreparedStatements, bloqueos pesimistas, ORM)
└───────────────┬────────────────┘
                │ Conexión JDBC (HikariCP)
                ▼
┌────────────────────────────────┐
│   PostgreSQL 18.6 (Database)   │  --> Tablas: pedidos, pedido_items, productos, usuarios, etc.
└────────────────────────────────┘
```

---

## 3. Consecuencias

### Positivas:
* **Seguridad contra SQL Injection:** Toda consulta ejecutada por Spring Data JPA y Hibernate utiliza `PreparedStatement` parametrizado internamente, descartando concatenaciones vulnerables (cumple OWASP Top 10 A05).
* **Gestión transaccional segura:** La anotación `@Transactional` asegura que si falla la creación de un ítem del pedido, se efectúe un `rollback` automático de todas las operaciones previas.
* **Sustitución y Mocking en Tests:** Los controladores pueden probarse de forma aislada mockeando la capa de servicio con `@MockitoBean`, sin necesidad de levantar la base de datos completa.

---

## 4. Evidencia en Código y Tests

### Clases Involucradas:
* **Repositorios:**
  * [`PedidoRepository.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/pedido/repository/PedidoRepository.java)
  * [`InventarioRepository.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/inventario/repository/InventarioRepository.java)
  * [`ProductoRepository.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/producto/repository/ProductoRepository.java)
* **Servicios de Negocio:**
  * `PedidoService.java`, `InventarioServiceImpl.java`, `AuthService.java`
* **Tests de Persistencia y Servicio:**
  * `PedidoServiceTests.java` y `AuthFlowTests.java`.
