# Estado del backend LogiRed

## Implementado

- Stack: Java 17, Spring Boot 4.1.1, Spring MVC, Spring Data JPA y PostgreSQL.
- API REST de pedidos bajo `/api/pedido` con operaciones de consulta, alta, actualizacion y baja.
- API REST de inventario bajo `/api/inventario`: consulta de stock, reserva y liberacion. El hold se mantiene en la sesion HTTP y vence con el timeout de cinco minutos.
- API REST de ruteo bajo `POST /api/ruteo/optimizar`, con seleccion de Strategy por `DISTANCIA_CORTA` o `TIEMPO_RAPIDO`.
- Registro/login bajo `/api/auth/register` y `/api/auth/login`. El registro publico asigna siempre rol `OPERADOR`; no permite elegir `ADMIN`.
- Usuarios iniciales de desarrollo: `admin/admin123` y `operador/operador123`. Cambiar estas credenciales antes de cualquier despliegue compartido o real.
- Deposito central de demostracion persistido en PostgreSQL, asociado automaticamente a pedidos sin deposito.
- `GET /api/deposito/default` devuelve nombre, direccion, latitud, longitud y `openStreetMapUrl`; las respuestas de pedidos incluyen el objeto `deposito`.
- Ubicacion demo: Av. 9 de Julio s/n, junto al Obelisco, CABA (`-34.6037`, `-58.3816`). Se puede cambiar antes del primer arranque con `LOGIRED_DEPOSITO_DEFAULT_NOMBRE`, `LOGIRED_DEPOSITO_DEFAULT_DIRECCION`, `LOGIRED_DEPOSITO_DEFAULT_LATITUD` y `LOGIRED_DEPOSITO_DEFAULT_LONGITUD`, que Spring mapea a las propiedades `logired.deposito.default.*`.
- Los pedidos e items de inventario persisten en PostgreSQL. La busqueda de stock reserva la fila con bloqueo pesimista para serializar reservas concurrentes.
- Sesiones HTTP expiran a los cinco minutos (`server.servlet.session.timeout=5m`).
- CORS se configura centralmente en `SecurityConfig` para frontend local en `localhost`/`127.0.0.1`.
- Los servicios de negocio usan repositorios DAO/JPA y delegan en controladores REST; Ruteo aplica Strategy y los servicios funcionan como Facade para sus controladores.
- Ruteo actualmente devuelve estimaciones por cantidad de paradas; no geocodifica direcciones, no calcula calles reales ni consulta trafico. Los nombres de estrategia no implican optimizacion geoespacial real todavia.

## Catalogo, vendedores y pedidos

El catalogo se modela en tablas separadas: un `Vendedor` publica muchos `Producto`; cada producto se identifica por SKU y puede tener stock en varios depositos; un `Pedido` contiene `PedidoItem` con producto, cantidad, deposito de despacho y precio unitario congelado. El total se calcula en el backend. El stock reservado se descuenta de disponible y se agrega a bloqueado; borrar el pedido libera esas cantidades.

Al iniciar, `CatalogoDataInitializer` enlaza los registros de inventario existentes con productos y vendedores de demostracion. Es idempotente: conserva las cantidades existentes y no duplica productos ni vendedores. Precios iniciales de demo: `IND-001` ARS 59.990, `IND-002` ARS 89.990, `ELEC-010` ARS 42.990; el resto de los productos demo ARS 15.990.

### API de catalogo

- `GET /api/productos`: listado de productos con precio ARS, vendedor y stock agregado/por deposito.
- `GET /api/productos/{id}`: detalle navegable del producto.
- `GET /api/vendedores` y `GET /api/vendedores/{id}`: vendedores del catalogo.
- `GET /api/inventario`: stock por SKU y deposito, incluyendo `producto` cuando ya fue enlazado.

El front puede usar `producto.id` como ruta (`/productos/{id}`) y consultar `GET /api/productos/{id}` para la pantalla de detalle.

### Crear pedido desde productos

`POST /api/pedido` mantiene el boton/flujo actual y acepta el nuevo formato. Solo se envia el ID y la cantidad; el cliente no define precios ni total.

```json
{
	"clienteNombre": "Cliente Demo",
	"direccionEntrega": "Av. Corrientes 1234, CABA",
	"items": [
		{ "productoId": 1, "cantidad": 2 },
		{ "productoId": 3, "cantidad": 1 }
	]
}
```

El servidor valida productos/cantidades, elige stock disponible y guarda el precio de cada producto al momento de comprar. Si se envia `depositoId`, las lineas deben poder salir de ese deposito; si se omite, cada linea puede asignarse al deposito con stock suficiente. La respuesta incluye `items[].producto`, `items[].cantidad`, `items[].precioUnitario`, `items[].subtotal` e `items[].deposito`, ademas del total. Productos sin stock o IDs invalidos devuelven HTTP 400; las consultas y el alta siguen sujetas a autenticacion/roles existentes.

El scheduler de demostracion usa el mismo checkout del catalogo, por lo que los pedidos automaticos tambien tienen lineas, cantidades, precios y holds reales. Las solicitudes antiguas basadas en `descripcion` siguen aceptandose temporalmente para no romper clientes existentes.

## Componentes candidatos e interfaces

Estos contratos se basan en el dominio LogiRed. Pedidos, Inventario y Ruteo estan implementados; los demas siguen siendo candidatos.

| Componente | Operaciones principales | Responsabilidad |
| --- | --- | --- |
| Comercios | registrar, actualizar, consultar | Gestionar comercios que originan pedidos. |
| Pedidos | crear, consultar, actualizar estado, cancelar | Mantener el ciclo de vida del pedido. |
| Inventario | consultar disponibilidad, reservar, confirmar, liberar | Controlar stock por deposito y evitar sobreventa. |
| Ruteo | calcular ruta, reasignar recorrido | Proponer recorridos segun pedidos y capacidad. |
| Repartidores | registrar, cambiar disponibilidad, asignar pedido | Administrar repartidores y asignaciones. |
| PagosYCobranzas | cobrar, registrar pago contra entrega, conciliar | Registrar cobros y su estado. |
| Notificaciones | publicar aviso, consultar estado de envio | Informar cambios de estado a los actores. |
| IntegracionTransportistas | cotizar, solicitar envio, consultar seguimiento | Adaptar los contratos de transportistas externos. |

Para la arquitectura en capas, cada componente implementado debera exponer su contrato desde presentacion, delegar reglas a negocio y acceder a persistencia mediante repositorios. Las integraciones externas pertenecen a adaptadores de infraestructura, no a los controladores.

## Estado y ciclo de vida

- Pedidos es stateless en memoria: el servicio no conserva estado de una solicitud a otra; el estado durable del pedido vive en PostgreSQL.
- Ruteo es stateless: cada solicitud contiene sus paradas y criterio.
- Inventario es stateful con `@SessionScope`; conserva las reservas activas por sesion mientras PostgreSQL persiste el stock disponible/bloqueado.
- `@PostConstruct` prepara el contexto de sesion y carga datos de demostracion si el catalogo esta vacio; `@PreDestroy` intenta liberar las reservas pendientes.
- Limitacion: las reservas conversacionales viven en memoria de una instancia. Un cierre abrupto del proceso puede dejar unidades bloqueadas; no hay almacenamiento distribuido de sesiones ni proceso de recuperacion tras reinicio.

## Patrones seleccionados

1. **DAO/Repository:** `PedidoRepository` e `InventarioRepository` desacoplan persistencia JPA de los servicios.
2. **Strategy:** `EstrategiaRuteo` permite seleccionar entre `RutaMasCortaStrategy` y `RutaMasRapidaStrategy` sin cambiar el servicio consumidor.
3. **Facade / Service Layer:** `PedidoService`, `InventarioServiceImpl` y `RuteoServiceImpl` presentan operaciones de negocio de alto nivel a sus controladores.

Adapter sigue pendiente hasta implementar una integracion real con uno de los transportistas externos.

## Requisitos generales aun pendientes

- Publicar un servicio SOAP con WSDL para un sistema legado y consumir un servicio REST de partner; no estan implementados.
- Implementar un flujo asincronico con cola punto a punto y otro con topico pub/sub usando un broker real.
- Implementar transacciones declarativas en un flujo critico que cruce varios componentes.
- Completar los otros componentes del catalogo (Comercios, Repartidores, PagosYCobranzas, Notificaciones e IntegracionTransportistas).
- Mejorar el ruteo para trabajar con coordenadas/calles reales y un proveedor de rutas si se requiere navegacion precisa.
- Externalizar sesiones/holds para recuperacion y escalado horizontal; documentar la limitacion actual del stateful en memoria.
- Ejecutar los componentes y el broker en contenedores reales y documentar la arquitectura completa.
- Mantener un historial Git incremental con participacion de todo el grupo.

El branch `origin/entrega-1` fue integrado en `main`; su informe esta en `docs/ENTREGA_PARCIAL_1_LOGIRED.md` y el entregable original en `docs/Entrega_Obligatoria_1_Grupo13_LogiRed.docx`.