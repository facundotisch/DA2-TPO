# ADR-002: Implementación del Patrón Strategy en RuteoService para Algoritmos Intercambiables de Optimización Logística

* **Estado:** Aceptado e Implementado
* **Fecha:** 15 de Noviembre de 2026
* **Componente:** `logired.backend.ruteo` (Servicio de Ruteo, Estrategias y Controlador REST)
* **Autores:** Grupo 13 (Facundo Tisch, Lola Díaz, Nicolás Valenzuela)
* **Requisito Cátedra:** *"Patrones implementados en el código, cada uno con su ADR."*

---

## 1. Contexto y Planteamiento del Problema
En el servicio logístico metropolitano de **LogiRed**, la planificación de despachos puede requerir diferentes criterios según la urgencia del cliente y la congestión del tráfico urbano:
1. **Minimizar Distancia Recorrida (`DISTANCIA_CORTA`):** Prioriza el menor kilometraje acumulado entre paradas para ahorrar combustible y desgaste vehicular en flotas estándar.
2. **Minimizar Tiempo de Entrega (`TIEMPO_RAPIDO`):** Prioriza arterias rápidas y ordenamiento ágil para despachos express de última milla.

### El Problema Técnico:
Acoplar directamente el algoritmo de cálculo dentro del servicio de ruteo obligaría a modificar la clase principal cada vez que se agregue o ajuste una regla logística (violando el principio Open/Closed de SOLID). Asimismo, dificultaría la incorporación futura de algoritmos de optimización combinatorial (como Simulated Annealing, Algoritmos Genéticos o integración con APIs externas de mapas).

---

## 2. Decisión Arquitectónica
Se implementó el **Patrón Strategy (GoF)** para encapsular los algoritmos de optimización de paradas detrás de una interfaz contractual común:

```
                  ┌──────────────────────┐
                  │    <<interface>>     │
                  │   EstrategiaRuteo    │
                  ├──────────────────────┤
                  │ + optimizar(paradas) │
                  │ + getCriterio()      │
                  └──────────┬───────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
┌───────────────────────┐         ┌───────────────────────┐
│  RutaMasCortaStrategy │         │  RutaMasRapidaStrategy│
├───────────────────────┤         ├───────────────────────┤
│ Optimiza por distancia│         │ Optimiza por velocidad│
│ mínima acumulada      │         │ y tiempo estimado     │
└───────────────────────┘         └───────────────────────┘
```

### Detalle de Implementación:
1. **Contrato de Estrategia (`EstrategiaRuteo`):** Define el método de cálculo y expone el identificador del criterio que atiende (`DISTANCIA_CORTA` o `TIEMPO_RAPIDO`).
2. **Inyección Dinámica de Dependencias:** El componente `RuteoServiceImpl` recibe una lista de todas las estrategias disponibles registradas como Beans en el contenedor de Spring (`List<EstrategiaRuteo>`) y las mapea internamente por su criterio.
3. **Resolución en Tiempo de Ejecución:** Cuando el cliente web o un sistema externo envía la solicitud a `POST /api/ruteo/optimizar`, el servicio resuelve la estrategia solicitada sin alterar la lógica de orquestación.

---

## 3. Consecuencias

### Positivas:
* **Extensibilidad (Open/Closed Principle):** Se pueden agregar nuevas estrategias (por ejemplo, `RutaMenorHuellaCarbono` o `RutaOptimizadaTraficoReal`) creando una nueva clase con `@Component`, sin modificar una sola línea de `RuteoServiceImpl`.
* **Desacoplamiento:** El cliente y los controladores solo interactúan con el DTO de entrada y salida (`RutaOptimizadaDto`), desconociendo la complejidad matemática interna.
* **Testabilidad aislada:** Cada algoritmo se prueba de forma unitaria independiente de la capa web o de base de datos.

### Negativas / Trade-offs:
* Aumenta la cantidad de clases en el proyecto (una interfaz y una clase por cada algoritmo).

---

## 4. Evidencia en Código y Tests

### Clases Involucradas:
* **Interfaz de Estrategia:** [`logired.backend.ruteo.strategy.EstrategiaRuteo`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/ruteo/strategy/EstrategiaRuteo.java)
* **Estrategias Concretas:**
  * [`RutaMasCortaStrategy.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/ruteo/strategy/RutaMasCortaStrategy.java)
  * [`RutaMasRapidaStrategy.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/ruteo/strategy/RutaMasRapidaStrategy.java)
* **Contexto de Ejecución:** [`RuteoServiceImpl.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/ruteo/service/RuteoServiceImpl.java)
* **Controlador REST:** [`RuteoController.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/main/java/logired/backend/ruteo/controller/RuteoController.java)

### Verificación Automatizada:
Pruebas unitarias en [`RuteoServiceImplTests.java`](file:///c:/Users/facun/Documents/Facultad/Materias/Desarrollo%20de%20apps%202/TPO/backend/src/test/java/logired/backend/ruteo/service/RuteoServiceImplTests.java) validando la correcta delegación polimórfica según el enum recibido.
