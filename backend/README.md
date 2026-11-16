# Estado del backend LogiRed

## Implementado

- Stack: Java 17, Spring Boot 4.1.1, Spring MVC, Spring Data JPA y PostgreSQL.
- API REST de pedidos bajo `/api/pedido` con operaciones de consulta, alta, actualizacion y baja.
- API REST de inventario bajo `/api/inventario`: consulta, alta, edicion y baja de productos/stock, ademas de reserva y liberacion. El hold se mantiene en la sesion HTTP y vence con el timeout de cinco minutos.
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

## Guia de integracion para el frontend

Esta seccion es el contrato vigente que debe seguir el cliente React. Las rutas son relativas a `http://localhost:8080`; por ejemplo, `GET /api/productos` se consulta en `http://localhost:8080/api/productos`. El backend debe estar iniciado antes de ejecutar o probar el frontend.

### 1. Inicio de sesion y autenticacion

- Login: `POST /api/auth/login`, sin `Authorization`, con `Content-Type: application/json`:

  ```json
  { "username": "operador", "password": "operador123" }
  ```

- En exito responde HTTP 200: `{"mensaje":"Login exitoso","username":"operador","rol":"OPERADOR"}`. El rol puede ser `OPERADOR` o `ADMIN`. No se devuelve JWT.
- Registro: `POST /api/auth/register`, mismo formato de cuerpo. En exito responde HTTP 201 con `username` y `rol`; el registro publico siempre crea un usuario `OPERADOR` y nunca permite elegir `ADMIN`.
- Para toda solicitud protegida, el frontend debe enviar `Authorization: Basic <base64(username:password)>`. No inventar un token ni usar el usuario guardado en la interfaz como prueba de autenticacion: el backend valida las credenciales en cada solicitud protegida.
- El frontend puede guardar las credenciales durante la sesion del navegador y eliminarlas al cerrar sesion. Base64 no cifra la contrasena; usar HTTPS fuera del entorno local y nunca guardar credenciales en el codigo fuente.
- Credenciales de desarrollo sembradas: `operador/operador123` y `admin/admin123`. No usar estas claves en un despliegue real.
- Si login responde HTTP 401, mostrar que las credenciales son invalidas y no guardar una sesion local.

Las solicitudes protegidas deben manejar los codigos HTTP en lugar de asumir que toda respuesta es exitosa:

| HTTP | Significado para el frontend | Accion |
| --- | --- | --- |
| `400` | Datos invalidos, stock insuficiente u otra regla de negocio; normalmente el backend envia `{"error":"..."}` o `{"mensaje":"..."}`. | Mostrar el texto recibido; no mostrar confirmacion de exito. |
| `401` | Falta autenticacion o las credenciales son invalidas/expiraron. | Pedir iniciar sesion de nuevo. |
| `403` | El usuario esta autenticado pero su rol/cuenta no tiene permiso. | Mostrar acceso denegado; no reintentar con permisos locales. |
| `404` | El producto, pedido o fila de inventario no existe. | Informar que el registro ya no esta disponible y refrescar la lista. |
| `409` | Un producto no se puede borrar porque tiene unidades reservadas en pedidos activos. | Explicar que primero debe resolverse el pedido que reserva el stock. |

Las respuestas exitosas de borrado son HTTP 204 y no tienen cuerpo. El cliente no debe intentar parsear JSON en ese caso.

### 2. Permisos exactos

| Accion | `OPERADOR` | `ADMIN` con usuario `admin` |
| --- | --- | --- |
| Consultar catalogo, inventario y pedidos | Si | Si |
| Crear/editar productos o stock | Si | Si |
| Crear/editar pedidos | Si | Si |
| Borrar producto, fila de stock o pedido | No | Si |

El backend es la autoridad final de permisos. El frontend debe ocultar o deshabilitar los botones de borrar para cualquier cuenta que no tenga simultaneamente `rol === "ADMIN"` y `username === "admin"`, pero igualmente debe manejar HTTP 403 si la solicitud es rechazada. Tener rol `ADMIN` con otro nombre no habilita borrados.

### 3. Catalogo: usar `/api/productos`

Usar estas rutas para la pantalla de catalogo y su formulario de alta/edicion:

| Metodo y ruta | Uso | Respuesta esperada |
| --- | --- | --- |
| `GET /api/productos` | Listar productos vendibles. | HTTP 200 y array de productos. |
| `GET /api/productos/{id}` | Cargar el formulario de edicion o detalle. | HTTP 200 o 404. |
| `POST /api/productos` | Crear producto y su stock inicial en el deposito predeterminado. | HTTP 201 y producto creado. |
| `PUT /api/productos/{id}` | Editar datos y stock disponible agregado del producto. | HTTP 200 y producto actualizado, o 404. |
| `DELETE /api/productos/{id}` | Borrar el producto del inventario. | HTTP 204, 404 o 409 si tiene stock reservado. Solo cuenta admin. |

Ejemplo para `POST /api/productos`:

```json
{
  "sku": "ZAP-001",
  "nombre": "Zapatillas urbanas",
  "descripcion": "Calzado deportivo",
  "categoria": "Calzado",
  "precio": 85000,
  "moneda": "ARS",
  "stockDisponible": 10,
  "stockBloqueado": 0
}
```

`sku`, `nombre`, `precio` y `stockDisponible` son obligatorios; el precio debe ser mayor que cero y las cantidades no pueden ser negativas. `moneda` se omite o debe ser `ARS`. El SKU debe ser unico al crear y no se puede cambiar al editar. Para crear, `stockBloqueado` debe ser cero; en la interfaz es un campo de solo lectura y el backend calcula las unidades bloqueadas a partir de pedidos activos.

En `/api/productos`, `stockDisponible` es la suma de las unidades disponibles del producto en todos los depositos. Al editarlo, el backend ajusta ese total sin modificar las unidades bloqueadas; si se aumenta, agrega la diferencia al deposito predeterminado. No enviar un total de stock calculado en el cliente ni permitir editar `stockBloqueado`. El listado `GET /api/productos` omite productos con cero stock disponible; un producto puede seguir existiendo y consultarse por ID aunque no aparezca en el listado.

Cada producto de respuesta incluye `id`, `sku`, `nombre`, `descripcion`, `categoria`, `precio`, `moneda`, `vendedor`, `stockDisponible`, `stockBloqueado`, `stockPorDeposito` y `ubicacion`. `ubicacion` incluye `id`, `nombre`, `direccion`, `latitud`, `longitud` y `openStreetMapUrl`.

### 4. Stock por deposito: usar `/api/inventario` solo si se necesita editar una fila puntual

El formulario normal del catalogo debe usar `/api/productos`: esa ruta crea/edita el producto y mantiene su stock agregado. Usar `/api/inventario` solamente cuando la interfaz necesite administrar una existencia concreta en un deposito.

| Metodo y ruta | Uso |
| --- | --- |
| `GET /api/inventario` | Leer filas con `id`, `sku`, `nombre`, `deposito`, `stockDisponible`, `stockBloqueado` y, si esta enlazado, `producto`. |
| `POST /api/inventario` | Crear stock para un SKU en un deposito. |
| `PUT /api/inventario/{id}` | Editar stock de una fila existente. |
| `DELETE /api/inventario/{id}` | Borrar una fila; solo cuenta admin y solo si no tiene unidades reservadas. |

Ejemplo de cuerpo para crear una fila:

```json
{
  "sku": "AUR-001",
  "nombre": "Auriculares inalambricos",
  "descripcion": "Auriculares Bluetooth",
  "categoria": "Audio",
  "precio": 42000,
  "cantidad": 10,
  "deposito": "nombre exacto devuelto por /api/deposito/default"
}
```

Para obtener el nombre valido del deposito, llamar primero a `GET /api/deposito/default` y pasar `respuesta.nombre` como `deposito`; no enviar el ID ni inventar un nombre. `cantidad` debe ser un entero no negativo. En un `PUT`, `cantidad` representa el total deseado de unidades (disponibles mas bloqueadas); no puede ser menor que `stockBloqueado`. No se puede cambiar el SKU de una fila existente ni crear dos filas con el mismo SKU en el mismo deposito. Si hay stock reservado, el borrado se rechaza.

### 5. Pedidos: seleccionar productos del catalogo

Usar estas rutas:

| Metodo y ruta | Uso | Respuesta esperada |
| --- | --- | --- |
| `GET /api/pedido` | Listar pedidos. | HTTP 200 y array. |
| `GET /api/pedido/{id}` | Cargar detalle o formulario de edicion. | HTTP 200 o 404. |
| `POST /api/pedido` | Crear pedido. | HTTP 200 y pedido creado. |
| `PUT /api/pedido/{id}` | Reemplazar los datos y las lineas deseadas del pedido. | HTTP 200 y pedido actualizado, o 404. |
| `DELETE /api/pedido/{id}` | Borrar pedido y liberar sus unidades reservadas. | HTTP 204 o 404. Solo cuenta admin. |

Antes de crear/editar, cargar las opciones con `GET /api/productos` y permitir seleccionar productos con stock disponible. Enviar el ID de producto y una cantidad entera positiva por linea. El cuerpo para crear y editar tiene el mismo formato:

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

Debe haber al menos una linea. `depositoId` es opcional: omitirlo para que el backend asigne cada linea a un deposito con stock suficiente; enviarlo solo si todas las lineas deben salir de ese deposito. No enviar precio, subtotal ni total como datos autoritativos: el backend obtiene el precio vigente, calcula importes y reserva stock. Si falta stock o el producto no existe, la solicitud falla con HTTP 400 y un mensaje de error.

Para editar, enviar el conjunto completo de lineas que debe quedar en el pedido, no solo las lineas modificadas. El `GET /api/productos` ya refleja el stock disponible despues de las reservas. Por eso, al validar una edicion en el navegador, sumar temporalmente al disponible las cantidades que ese mismo pedido ya tiene reservadas; tambien conservar sus productos en el selector aunque no aparezcan en el listado por tener cero stock libre. El backend vuelve a validar el stock y actualiza/libera las reservas al guardar.

La respuesta del pedido incluye `id`, `clienteNombre`, `direccionEntrega`, `estado`, `total`, `deposito`, `items` y `mapaEntregaUrl`. Cada linea incluye `producto`, `cantidad`, `precioUnitario`, `subtotal` y `deposito`; mostrar importes devueltos por el backend despues de guardar.

### 6. Mapas de pedidos

- Para el marcador Leaflet, usar `pedido.deposito.latitud` y `pedido.deposito.longitud`, con `pedido.deposito.nombre` y `pedido.deposito.direccion` como texto. No sustituir las coordenadas del pedido por las del deposito predeterminado si el pedido ya informa otro deposito.
- `pedido.deposito.openStreetMapUrl` abre el mapa del deposito.
- `pedido.mapaEntregaUrl` es un enlace de busqueda de OpenStreetMap para la direccion de entrega; abrirlo en una pestana nueva. No contiene coordenadas de entrega y no es una ruta de navegacion.
- Si falta `pedido.deposito` o alguna coordenada, no dibujar un marcador en una ubicacion supuesta. Se puede consultar `GET /api/deposito/default` como alternativa solo cuando corresponda al deposito del pedido.
- La direccion de entrega se guarda como texto. El backend no geocodifica direcciones ni calcula rutas reales.

### 7. Configuracion local y comprobacion de punta a punta

- URL base actual del frontend: `http://localhost:8080/api`. Usar el mismo host (`localhost`, no mezclar con `127.0.0.1`) durante la sesion.
- CORS permite origenes locales `http://localhost:<puerto>` y `http://127.0.0.1:<puerto>`, metodos GET/POST/PUT/DELETE/OPTIONS/HEAD y headers de solicitud. No incluye por defecto otros equipos o dominios.
- Para operaciones normales, enviar Basic Auth. Para las operaciones de sesion de inventario (`POST /api/inventario/reservar`, `POST /api/inventario/liberar` y `GET /api/inventario/sesion`), mantener tambien las cookies del navegador con `fetch(..., { credentials: "include" })`. El checkout del pedido ya reserva stock; no llamar a reservar manualmente antes de crear el pedido.
- Comprobar en este orden: iniciar sesion como operador; listar y crear un producto; editarlo; crear un pedido con ese producto; verificar en la respuesta los items/precios/total y que bajo el stock; editar el pedido comprobando que sus propias reservas no bloqueen el formulario; confirmar que operador recibe HTTP 403 al borrar; iniciar sesion como `admin` y comprobar que el borrado responde HTTP 204; finalmente revisar el marcador del deposito y abrir el enlace de entrega.

Al iniciar, `CatalogoDataInitializer` elimina filas de inventario cuyo SKU o nombre contiene marcas usuales de datos de prueba (test, prueba, demo, mock, fixture, dummy o fake), enlaza filas existentes con productos y agrega el catalogo inicial solo si corresponde. Incluye zapatillas, libros, auriculares, microfono, notebook, monitor, mouse y tablet. Los cambios manuales de productos/stock y las bajas se conservan entre reinicios. Los pedidos historicos sin lineas se completan al iniciar si hay stock. Las solicitudes antiguas basadas solo en `descripcion` se aceptan temporalmente; el frontend nuevo debe enviar `items`.

El scheduler de demostracion usa el mismo checkout del catalogo, por lo que los pedidos automaticos tambien tienen lineas, cantidades, precios y reservas reales.

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
- `@PostConstruct` prepara el contexto de sesion; `@PreDestroy` intenta liberar las reservas pendientes.
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