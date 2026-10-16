# CLAUDE.md

Guía para trabajar en este repositorio (para Claude Code y para cualquier desarrollador).

## Qué es

Backend base reutilizable para tiendas web (producto propio). **Una instancia + una base PostgreSQL por tienda**,
mismo código para todas. Lo que diferencia a una tienda de otra es **solo configuración** (variables de entorno),
nunca código. Si una tienda necesita algo distinto, se resuelve con configuración o generalizando la funcionalidad
para todas — jamás con `if (tienda == "X")`, forks ni ramas por cliente.

- Origen: `~/Dev/BackTecnophones` (MongoDB). **Es la producción actual de TecnoPhones: no modificarlo nunca.**
  Se puede leer como referencia.
- Clientes previstos: TecnoPhones (celulares), una maderera (venta por m², metro, pie) y futuros rubros.
- Deploy: Railway, un proyecto por tienda (API + PostgreSQL), o varias tiendas en un servidor propio con Coolify
  (Oracle Cloud Always Free, ver `docs/despliegue-oracle-coolify.md`). Cada tienda fija la versión que corre.

## Comandos

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run   # levanta PostgreSQL (compose.yaml) + app en :8080
./mvnw compile                                         # compilar
./mvnw package                                         # jar en target/
docker build -t tiendas-backend .                      # imagen de producción
docker compose up -d / docker compose stop             # solo la base local
```

Verificar que funciona: levantar con perfil `local` y probar con curl (`/actuator/health`, login
`admin`/`admin-local-123`). Para requests que modifican datos hay que mandar la cookie `XSRF-TOKEN` en el header
`X-XSRF-TOKEN` (ver ejemplos en README).

El proyecto **no tiene tests automatizados** por decisión del dueño. No agregar tests salvo que se pidan
explícitamente; validar los cambios ejecutando la app.

## Stack y versiones

Java 21 · Spring Boot 4.1 (Spring Framework 7, Security 7, Hibernate 7, **Jackson 3**) · PostgreSQL 18 · Flyway ·
Caffeine · Mercado Pago SDK 3.x · AWS SDK v2 (S3) · PDFBox 3 · Thumbnailator + TwelveMonkeys (WebP) · Lombok.

Ojo con Boot 4 / Jackson 3:
- `JsonNode`, `JsonMapper` están en `tools.jackson.databind` (no `com.fasterxml.jackson.databind`). Las
  **anotaciones** sí siguen en `com.fasterxml.jackson.annotation`. `JsonNode#asText()` → `asString()`.
- Excepciones de Jackson 3 son unchecked (`tools.jackson.core.JacksonException`).
- Starters modulares: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, etc.
- `HttpStatus.UNPROCESSABLE_CONTENT` (no `UNPROCESSABLE_ENTITY`).

## Arquitectura

Paquetes **por funcionalidad** (no por capa técnica), en `src/main/java/com/tiendas/`:

| Paquete | Contenido |
|---|---|
| `comun` | `EntidadBase` (id + auditoría), excepciones de negocio, `ManejadorErrores` (ProblemDetail) |
| `config` | `TiendaProperties` (config por tienda), seguridad, caché, web, `GET /tienda` |
| `usuarios` | Administradores, login por sesión, límite de intentos, admin inicial |
| `catalogo` | Rubro → Categoría → Producto → Variante, `UnidadVenta`, filtros |
| `archivos` | `Almacenamiento` (Local / S3), imágenes y sus tamaños |
| `pedidos` | Pedido, items, pagos, checkout, registro de pagos, vencimiento, `PasarelaPago` (interfaz) |
| `mercadopago` | Implementación de `PasarelaPago`, webhook y firma HMAC |
| `facturacion` | ARCA vía AFRelay; solo se activa con `facturacion.habilitada=true` (`@FacturacionHabilitada`) |
| `descuentos` | Cupones (códigos de descuento) y su cálculo en el checkout; módulo opcional (`descuentos.habilitada`) |
| `envios` | Zonas de envío por código postal, retiro y envío gratis desde un monto; módulo opcional (`envios.habilitada`) |
| `correo` | Correo Argentino (API MiCorreo): cotización, sucursales, alta de envíos y seguimiento; opcional (`correo-argentino.habilitada`) |
| `emails` | Emails de pedidos vía Resend; cola `email_pendiente` (outbox) con reintentos; `email.habilitada=true` (`@EmailHabilitado`) |
| `zipnova` | Zipnova (API de envíos v2, varios transportes): cotización, sucursales, alta, etiqueta y seguimiento; opcional (`zipnova.habilitada`) |
| `desarrollo` | Solo perfil `local`: `DatosEjemplo` (catálogo HA!Tablas) y `PasarelaSimulada` (pagos sin MP) |

Capas dentro de cada paquete: `Controller` (HTTP, sin lógica) → `Service` (reglas y transacciones) →
`Repository` (Spring Data JPA). Sin capas extra (no hexagonal, no mappers, no `GenericService`).

### Decisiones que hay que respetar

- **Sin DTOs** (decisión del dueño). Las entidades son el modelo de la API. Para que eso sea seguro:
  - Campos que maneja el servidor: `@JsonProperty(access = READ_ONLY)` (se devuelven pero se ignoran al recibir).
  - Datos internos: `@JsonIgnore` (hashes, claves de almacenamiento, back-references).
  - **Los services nunca persisten la entidad recibida en el body.** Siempre copian los campos permitidos sobre una
    entidad nueva o cargada de la base (ver `ProductoService#copiarDatos`). Esto evita mass assignment.
  - Solo se usan `record` para entradas/salidas que no son entidades (ej. `LoginRequest`, `InfoTienda`, `PagoInformado`,
    `CheckoutService.Cotizacion`).
- **Relaciones en JSON por id**: `categoria.rubroId`, `producto.categoriaId`, `item.varianteId` son columnas simples.
  Las asociaciones JPA (`@ManyToOne`) se usan solo donde hace falta navegar, y van `LAZY` + `@JsonIgnore`.
- **Colecciones LAZY + OSIV desactivado**: el service que devuelve una entidad inicializa sus colecciones dentro de
  la transacción (`Hibernate.initialize`, cargadas en lote por `default_batch_fetch_size`). Si se agrega una
  colección, inicializarla en el `inicializar(...)` del service; si no, el JSON falla con LazyInitializationException.
- **Esquema solo por Flyway** (`src/main/resources/db/migration`). `ddl-auto=validate`. Para cambiar el modelo:
  nueva migración `V{n}__descripcion.sql` + ajustar la entidad. **Nunca editar una migración ya publicada.**
- **El stock vive solo en `Variante`** y todo producto tiene ≥ 1 variante. `CHECK (stock >= 0)` en la base.
- **Montos** `NUMERIC(14,2)` / `BigDecimal`, cantidades `NUMERIC(14,3)`. Redondeo `HALF_UP` a 2 decimales.
- **Precios y totales se calculan siempre en el servidor** (`CheckoutService`). Nunca usar importes del cliente.
- **Llamadas HTTP externas fuera de transacciones** (Mercado Pago, AFRelay, S3, Resend): no retener conexiones de la base.
- **Emails por cola (outbox)**: nunca se envían dentro del flujo que los origina. Un listener de un evento de dominio
  (ej. `PedidoAprobado`, publicado dentro de la transacción) guarda el email ya armado en `email_pendiente`, y
  `EnvioEmails` lo envía después con reintentos. Para un email nuevo: publicar un evento y escucharlo en `emails`.
- **Concurrencia**:
  - Pagos: `SELECT ... FOR UPDATE` sobre el pedido y sobre las variantes (ordenadas por id). Idempotente por
    `UNIQUE (proveedor, id_externo)` en `pago`.
  - Edición desde el panel: `@Version` en `Producto` y `Variante`; el front reenvía la versión → 409 si cambió.
  - Facturación: lock en memoria (una instancia por tienda) para la numeración.
- **Caché**: Caffeine, una sola caché `catalogo` (`CacheConfig.CATALOGO`). Toda escritura del catálogo o del stock
  lleva `@CacheEvict(allEntries = true)`. El manager es transaccional: el evict se aplica al confirmar.
  No hace falta Redis: hay una instancia por tienda.

### Seguridad

- Lista blanca de rutas en `SecurityConfig`; todo lo no declarado se deniega. `/api/v1/admin/**` requiere `ADMIN`.
  Al agregar un endpoint público, declararlo explícitamente ahí.
- Sesión por cookie HttpOnly (`SESION`), CSRF con cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` (excepto checkout,
  `checkout/cupon` y webhook: son anónimos).
- Login: BCrypt(12), cambio de id de sesión, límite de intentos por usuario e IP.
- Webhook de MP: firma HMAC obligatoria + consulta del pago a la API de MP (nunca confiar en el body).
- Errores: nunca exponer mensajes internos; `ReglaNegocioException` (422) para mensajes pensados para el usuario.
- **Secretos solo en variables de entorno.** Nada de tokens, contraseñas, CUITs ni datos de clientes en el código,
  en `application*.yml` ni en commits. `.env` está en `.gitignore`. Las propiedades obligatorias no tienen default
  para que la app no arranque si faltan.
- Imágenes: se reencodean (elimina EXIF), límite de 50 MP y 10 MB. Archivos en bucket privado, servidos por la API.

### Performance

- Índices pensados para las consultas reales (ver comentarios en `V1__esquema_inicial.sql`): listados por
  categoría/fecha, índice parcial de activos, trigram para búsqueda `LIKE '%texto%'`, pedidos por estado/fecha.
  Al agregar una consulta nueva, revisar con `EXPLAIN ANALYZE` y agregar el índice en una migración.
- Filtros dinámicos con `Specification` (solo se agregan al SQL los filtros presentes).
- Ordenamiento con lista blanca (`ProductoService.ORDENES_PERMITIDOS`) y desempate por id.
- Imágenes pre-generadas al subir + `Cache-Control: immutable` de 1 año + ETag/304 + caché en memoria.
- Hilos virtuales, compresión gzip de JSON, pool de Hikari configurable (`DB_POOL_MAX`).

## Configuración por tienda

Todo en `application.yml` toma valores de variables de entorno; la lista completa y comentada está en
`.env.example`. Los records `*Properties` (`TiendaProperties`, `MercadoPagoProperties`, `AlmacenamientoProperties`,
`FacturacionProperties`) se validan al arrancar. Para agregar un parámetro de tienda: campo en el record + entrada
en `application.yml` con `${VARIABLE}` + documentarlo en `.env.example`.
En el `.env` local los acentos van escapados (`\u00e9`): Spring lee ese archivo como `.properties` (ISO-8859-1).

### Módulos opcionales

Una funcionalidad opcional (hoy: facturación, descuentos, envíos, Correo Argentino, Zipnova y emails) se activa por
instancia con una sola propiedad (`FACTURACION_HABILITADA`, `DESCUENTOS_HABILITADA`, `ENVIOS_HABILITADA`,
`CORREO_ARGENTINO_HABILITADA`, `ZIPNOVA_HABILITADA`, `EMAIL_HABILITADA`). Esa misma propiedad:
1. crea o no los beans y endpoints del módulo (anotación condicional, ej. `@FacturacionHabilitada`), y
2. se informa al frontend en `GET /api/v1/tienda` → `modulos`, para que el panel muestre u oculte la sección.

No crear flags de "visibilidad" separados del flag que activa el módulo: podrían quedar desincronizados.
Para un módulo nuevo: propiedad `xxx.habilitada`, anotación condicional propia y campo en `TiendaController.Modulos`.

Excepción en descuentos y envíos: `CuponService`, `EnvioService` y sus entidades existen siempre (el checkout los necesita y la tabla
siempre está); con el módulo apagado `CuponService#aplicar` rechaza cualquier código y solo el ABM del panel
(`CuponController`, `@DescuentosHabilitada`) deja de existir.

Perfil `local` (`application-local.yml`): valores de desarrollo, admin `admin`/`admin-local-123`, cookies sin `secure`,
catálogo de ejemplo y pagos simulados (`MP_SIMULADO`, por defecto `true`).

**Pagos simulados — regla de seguridad:** `PasarelaSimulada` exige `@Profile("local")` **y**
`mercadopago.simulado=true`; las clases reales de MP llevan `@MercadoPagoReal` (se apagan con esa propiedad). Si la
propiedad se activa fuera de `local`, no queda ninguna `PasarelaPago` y la app no arranca. No romper ese doble candado:
todo código del paquete `desarrollo` debe llevar `@Profile("local")`.

### Descuentos (cupones) y Mercado Pago

Paquete `descuentos`. El dueño crea cupones desde el panel (`/api/v1/admin/cupones`) y el cliente escribe el código
en el checkout.

- **Tipos** (`TipoCupon`): `PORCENTAJE` (con `tope` opcional en pesos), `MONTO_FIJO` (nunca más que lo alcanzado) y
  `ENVIO_GRATIS` (solo con entrega a domicilio; descuenta el costo de envío que cobra la web).
- **Condiciones** (opcionales): vigencia `desde`/`hasta` (`hasta` es exclusivo), compra mínima, usos totales, usos por
  cliente (por email), solo primera compra, `activo`. **Alcance**: rubros, categorías o productos
  (`cupon_rubro`/`cupon_categoria`/`cupon_producto`); sin ninguno, toda la tienda. Esas tablas no tienen FK a
  catálogo a propósito: borrar un producto no debe fallar ni convertir el cupón en general.
- **El front solo manda `cuponCodigo`** en `POST /checkout`. `CheckoutService#armarPedido` valida y calcula con
  `CuponService#aplicar`: `total = subtotal + costoEnvio - descuento`. El pedido guarda `cuponId`, `cuponCodigo`
  (normalizado en mayúsculas) y `descuento` (READ_ONLY). Un total ≤ 0 se rechaza (MP no cobra $0).
- **`POST /api/v1/checkout/cupon`** (público): mismo cálculo sin guardar nada, para mostrar el descuento antes de
  pagar. El email es opcional; las condiciones por cliente se vuelven a validar al confirmar. Los códigos inexistentes
  cuentan para un límite por IP (`LimitadorIntentosCupon`, 15 cada 15 min → 429) para que no se puedan adivinar.
- **Usos**: cuentan solo pedidos pagados (`APROBADO` o `REQUIERE_REVISION`); pendientes, vencidos o rechazados no
  gastan el cupón. No hay bloqueo: si dos clientes pagan a la vez el último uso, se aprueban ambos (excedente
  aceptado a propósito). Un cupón con pedidos no se borra, se pausa (las métricas salen de esos pedidos).
- **Envío gratis sin costo en la web**: hay tiendas que cobran $0 de envío y lo coordinan aparte (ej. HA!Tablas).
  Ahí el descuento es 0 y el pedido solo queda marcado con el cupón. Invariante: un pedido con cupón y
  `descuento = 0` siempre es de envío gratis (`aplicar` rechaza porcentaje/monto que no descuenten nada); el panel
  se apoya en eso para mostrar "Envío gratis".

**Al tocar Mercado Pago, tener en cuenta:**
- MP no acepta ítems con precio negativo. Si `pedido.descuento > 0`, `MercadoPagoPasarela#items` manda **un solo
  ítem** (`id = "PEDIDO"`, título "Compra en {tienda} (cupón X)", descripción con el detalle) por `pedido.total`,
  que ya incluye envío y descuento. Sin descuento se mandan los ítems y el envío como siempre.
- `PedidoService#montoCubreElPedido` compara el pago contra `pedido.total` (ya descontado). Si se cambia cómo se
  arma la preferencia, la suma de los ítems tiene que seguir dando exactamente `pedido.total`.
- `PasarelaSimulada` (local) aprueba por `pedido.total`, así que en local el descuento ya se prueba completo.
  **Pendiente: probar el ítem consolidado contra el sandbox real de MP** (nunca se probó con MP real).

### Envíos por zona

Paquete `envios`. Con el módulo apagado, el checkout usa el costo único de `tienda.envio` (como antes).

- **Zonas** (`ZonaEnvio`): nombre, `codigosPostales` (lista de códigos de 4 dígitos o rangos: `"3164, 3100-3299"`),
  `provincias` (códigos de una letra del Correo separados por coma, `"E,S,X"`, ver `Provincias`), `precio` o
  `costoAConvenir` (no se cobra en la web), plazo `diasMin`/`diasMax`, `activa`, `orden`. Sin códigos ni provincias
  = resto del país. Elección (`zonaPara`): primero la zona activa (por `orden`) que incluye el CP, después la que
  incluye la provincia del cliente y si no, la del resto del país. Los CP se comparan por sus 4 dígitos (acepta CPA,
  ej. "A3164ABC"); los CP de 4 dígitos se repiten entre provincias, por eso las zonas grandes van por provincia.
  El cotizar y el checkout reciben `provincia` (nombre o código); en SUCURSAL sale de la primera letra del código
  de la sucursal.
- **Ajustes** (`AjustesEnvio`, una fila): retiro habilitado y su texto, `envioGratisDesde` (sobre el subtotal; no
  aplica a zonas a convenir).
- **Checkout**: el front manda `entrega`, la dirección (con CP) y opcionalmente `envioOpcion` (`RETIRO` o
  `ZONA-{id}`). `EnvioService#elegir` valida la opción contra el CP y devuelve el precio; el pedido guarda
  `envioOpcion`, `envioDescripcion` (lo que vio el cliente) y `envioAConvenir`. El envío se calcula antes que el
  cupón (el de envío gratis descuenta ese costo).
- **Endpoints**: `GET /api/v1/envios` y `POST /api/v1/envios/cotizar` (públicos, sin CSRF) para el checkout del
  front (sin carrito cotiza un paquete por defecto: lo usa el probador del panel); ABM en `/api/v1/admin/envios/zonas` y `/api/v1/admin/envios/ajustes`.
- **Peso y medidas** (`Medidas`, embebido en `Producto`, `Categoria` y `AjustesEnvio.paquetePorDefecto`): se cotiza
  con los del producto, si no los de su categoría y si no el paquete por defecto de la tienda (los cuatro datos
  completos). Solo los usan las zonas cotizadas (Correo, Zipnova).
- Nombres con "AConvenir": la estrategia de nombres de Hibernate no separa dos mayúsculas seguidas, por eso esos
  campos llevan `@Column(name = ...)` explícito.

### Correo Argentino (API MiCorreo)

Paquete `correo`, requiere el módulo de envíos. Sigue el manual "API MiCorreo" (versión 2026-05): base
`https://api.correoargentino.com.ar/micorreo/v1` (TEST: `apitest…`), credenciales de la API (una por ambiente, se
piden al Correo) por HTTP Basic en `POST /token` → JWT, que `MiCorreoApi` reutiliza 50 minutos o hasta un 401.

- **Interfaz `CorreoArgentino`** con dos implementaciones, elegidas en `CorreoArgentinoConfig`: `MiCorreoApi` (real)
  y `MiCorreoSimulado` (precios, sucursales y seguimiento inventados). El simulado exige el perfil `local`: fuera de
  local la app no arranca si se pide (mismo doble candado que los pagos simulados).
- **Zonas** (`ZonaEnvio.tipo`): `PRECIO_FIJO` o `CORREO_ARGENTINO`, con `correoDomicilio`, `correoSucursal` y
  `correoExpreso`. Una zona del Correo cotiza con `POST /rates/packages` (un bulto por línea del carrito; el Correo
  apila) y devuelve opciones `CORREO-{zona}-{D|S}-{CP|EP}`. Si el Correo falla o a un producto le faltan peso o
  medidas (`Medidas` del producto o de su categoría, las cuatro completas), se usa `precio` de la zona como
  respaldo o el costo queda a convenir: nunca se corta la venta. Cotizaciones en caché 30 min, sucursales 12 h.
- **Retiro en sucursal** (`TipoEntrega.SUCURSAL`): el cliente manda `envioSucursal` (código del Correo, ej. "E0101";
  la primera letra es la provincia). `EnvioService#elegir` valida la sucursal con `GET /agencies` (activas y con
  `pickupAvailability`) y cotiza con **el código postal de la sucursal**; el pedido guarda la sucursal y su detalle.
- **Alta del envío** (`CorreoEnvios`, `POST /api/v1/admin/pedidos/{id}/correo`): `POST /shipping/import` con
  `extOrderId` = código del pedido, un solo bulto consolidado (suma de pesos, largo y ancho máximos, alturas
  apiladas), valor declarado = subtotal y la provincia traducida a su código de una letra (`Provincias`). Sin
  `sender`: el Correo usa el domicilio de remitente del perfil de MiCorreo (sin domicilio cargado, rechaza). El
  rótulo **no** sale por API: se paga y se imprime en MiCorreo ("Mis envíos"); el PDF automático es de Paq.ar y
  requiere acuerdo comercial.
- **Seguimiento**: el vendedor guarda el código del rótulo (`PUT …/seguimiento`) y `GET …/seguimiento` consulta
  `/shipping/tracking` (un GET con el id en el cuerpo, como dice el manual: por eso `JdkClientHttpRequestFactory`).
- Configuración: `CORREO_ARGENTINO_*` (URL, usuario y clave de la API, número de cliente de MiCorreo y código postal
  de origen = el del domicilio de remitente). `GET /tienda` informa `modulos.correoArgentino`.
- Pendiente: probar contra la API de TEST cuando el Correo entregue las credenciales.

### Zipnova (API de envíos v2)

Paquete `zipnova`, requiere el módulo de envíos. Zipnova es un agregador: con una sola cuenta cotiza y despacha con
Andreani, Correo Argentino, OCA y otros transportes con sus propias tarifas (sin contrato con cada uno). Se agregó
porque el Correo no dio acceso a la API de MiCorreo. Documentación: https://docs.zipnova.com/envios (las páginas
existen con `.md` al final; el índice `llms.txt` no las lista).

- **API**: base `https://api.zipnova.com.ar/v2`, HTTP Basic con el token y el secret de la cuenta (panel de Zipnova >
  Configuración > Integraciones > Gestionar credenciales y webhooks). `cuenta-id` y `origen-id` son opcionales: sin
  ellos `ZipnovaApi` usa la única cuenta de las credenciales (`GET /accounts`) y su origen por defecto
  (`GET /accounts/{id}/settings` → `default_forward_address_id`).
- **Interfaz `Zipnova`** con `ZipnovaApi` (real) y `ZipnovaSimulado` (precios, sucursales, alta, etiqueta PDF mínima y
  seguimiento inventados); el simulado exige el perfil `local` (mismo doble candado que el Correo y los pagos).
- **Zonas** de tipo `ZIPNOVA`: reusan `correoDomicilio`, `correoSucursal` y `correoExpreso` (domicilio, retiro en
  sucursal y express). A diferencia del Correo, **nunca quedan "a convenir"**: si Zipnova no cotiza (no responde,
  falta la localidad o no hay medidas) se usa el precio de la zona como respaldo y, sin precio, no se ofrece envío
  (`EnvioService#sinCotizacion`). `EnvioService#opcionesZipnova`
  cotiza con `POST /shipments/quote` mandando **unidades** (`items`, una por unidad del carrito con el peso y las
  medidas del producto o de su categoría; Zipnova arma los paquetes, `type_packaging=dynamic`) y el destino como
  código postal + provincia (nombre) + **localidad**: Zipnova identifica el destino por localidad y **sin ella
  responde 400** (probado), así que sin localidad la zona usa el respaldo sin llamar; el front la pide en el
  checkout cuando `modulos.zipnova` es true. Acepta acentos y nombres como "Ciudad Autónoma de
  Buenos Aires" (los resuelve: "Palermo / Capital Federal"). Usa `all_results` (cada forma de entrega con
  cada transporte habilitado en la cuenta; `results` solo trae la mejor de cada una) filtrando las seleccionables,
  con `estimated_delivery`; `Zipnova.Cotizacion` devuelve además el destino que entendió Zipnova (identifica la
  localidad por nombre y, si no la reconoce, por código postal), y el checkout se lo muestra al cliente. El precio es
  `price_incl_tax` (con IVA y seguro). El plazo sale
  de `delivery_time.times.total` ("P4DT3H14M": viaje + horas de preparación; el día empezado cuenta entero, como los
  `min`/`max` viejos que Zipnova va a dejar de mandar). Cotizaciones en caché 30 min.
- **Despacho**: las opciones llegan como `carrier_dropoff`: el vendedor lleva el paquete a un punto del transporte
  (con `include_dropoff_points=1` la cotización lista los más cercanos al origen).
- **Firewall**: la API está detrás de AWS WAF y a conexiones de fuera de Argentina les responde 202 sin cuerpo con
  `x-amzn-waf-action: challenge`. `ZipnovaApi` lo detecta y falla con un mensaje claro. El servidor de producción
  tiene que salir por una IP que Zipnova acepte (Argentina, o pedirles que la habiliten).
- **Opciones**: id `ZIPNOVA-{zona}-{transporte}-{servicio}` (máx. 40, la columna de `pedido.envio_opcion`;
  `EnvioService#servicioZipnova` lo vuelve a separar para el alta). `OpcionEnvio` trae `transportista`, `logo` y, en
  el retiro en sucursal (`pickup_point`), `puntos`: hasta 5 sucursales cercanas al destino. El checkout manda
  `envioSucursal` = id del punto y, aunque la entrega sea SUCURSAL, la `direccion` solo con localidad, provincia y CP:
  `EnvioService#elegirPuntoZipnova` vuelve a cotizar ese destino y exige que el punto sea uno de los de la opción. Por
  eso `DireccionEnvio.calle`/`numero` ya no son `@NotBlank`: el checkout los exige solo en DOMICILIO.
- **Checkout**: guarda en el pedido la forma de despacho de la opción (`envio_logistica`, Zipnova pide usar la de la
  cotización al crear) y la fecha estimada (`envio_entrega_estimada`); exige teléfono (Zipnova lo pide). El domicilio
  acepta `indicaciones` (entre calles, timbre), que van con piso y depto en `street_extras` (máx. 190).
- **Alta** (`ZipnovaEnvios`, `POST /api/v1/admin/pedidos/{id}/zipnova`): solo ventas APROBADAS con opción de Zipnova;
  `POST /shipments` con `external_id` = `P{id}-{8 del código}`, el transporte, el servicio y la logística elegidos,
  las unidades y el destinatario (Zipnova exige DNI y teléfono). Lo cobra Zipnova
  del saldo de la cuenta. Guarda `envioExternoId`, `envioAltaEn`, `envioTransportista`, `envioEstado`,
  `envioSeguimiento` (código del transporte) y `envioSeguimientoUrl` (página pública).
- **Guía de despacho**: `GET …/zipnova/guia` → PDF (algunos transportes no usan guía: Zipnova responde 400 y se
  avisa que alcanza con la etiqueta). **Dónde despachar**: `GET …/zipnova/despacho` → puntos del transporte cerca del
  CP de la cuenta (`/shipments/{id}/dropoff_locations`). **Cancelar**: `POST …/zipnova/cancelar` (si no se despachó, la
  venta vuelve a poder crear el envío; si ya se despachó, Zipnova pide el rescate al transporte; su 401 = "no se puede
  cancelar en este estado"). **Saldo**: `GET /api/v1/admin/zipnova/saldo` (`/accounts/{id}/billing`).
- **Webhook** (`ZipnovaWebhookController`): `POST /api/v1/webhooks/zipnova/{secreto}` (público, sin CSRF), tópicos
  `status` y `shipment`. Zipnova no firma los avisos: la URL lleva `ZIPNOVA_WEBHOOK_SECRETO` (≥ 32 caracteres; sin él
  responde 404) y el cuerpo solo dice qué envío consultar a la API. Responde siempre 200 a un aviso válido (si no,
  Zipnova reintenta cada hora 12 h). Se configura en Zipnova > Integraciones > Crear webhook con la URL pública.
- **Etiqueta**: `GET …/zipnova/etiqueta` → PDF. Zipnova responde `{"format","body"}` con el archivo en base64 (409 =
  todavía la está generando). **Seguimiento**: `POST …/zipnova/seguimiento` consulta el envío y su historial
  (`/shipments/{id}/tracking`) y actualiza el estado guardado.
- Zipnova le manda al comprador los mails de seguimiento (configuración de la cuenta); la tienda no los manda.
- Probado contra la API real (2026-10-16): cuentas, ajustes, saldo y cotizaciones. Pendiente: probar el alta, la etiqueta, la guía y la cancelación (requieren saldo en la cuenta) y
  el webhook (requiere el backend publicado).

### Frontends

Los frontends viven en sus propios repos (ej. FrontHaTablas). Las guías de integración no se guardan en este
repositorio. Para conectar uno: `VITE_API_URL=<api>/api/v1` y axios con `withCredentials` y `withXSRFToken` en `true`
(ver "Para el frontend" en README).

## Convenciones de código

- Código, nombres y mensajes **en español** (dominio: pedido, variante, rubro…); términos técnicos en inglés cuando
  es lo habitual (`Controller`, `Repository`, `sku`).
- Inyección por constructor. Lombok solo `@Getter/@Setter` en entidades (nunca `@Data` en entidades JPA) y `@Data`
  en clases de valor de facturación.
- Tablas en singular y snake_case; PK `bigint identity`; `UUID` para identificadores públicos no enumerables
  (`pedido.codigo`, `comprobante.codigo`, `imagen.id`).
- Excepciones: `NoEncontradoException` (404), `ReglaNegocioException` (422), `ConflictoException` (409),
  `ServicioExternoException` (502). No lanzar `ResponseStatusException` desde services.
- Comentarios para explicar el **por qué** (decisiones, riesgos), no el qué.
- Nada específico de un cliente en el código (nombres, dominios, CUIT, logos, categorías de MP).

## Git y versionado

- Ramas: `main` (estable), `develop` (integración compartida) y ramas de trabajo: personales (ej. `juan`) o por
  funcionalidad (`feature/*`), según convenga. Las ramas no son clientes.
- Releases con tags semánticos `vX.Y.Z` desde `main`. Cada tienda despliega un tag.
- Commits en español, descriptivos, en imperativo ("Agrega…", "Corrige…"). Un commit = un cambio lógico: así se
  pueden integrar por separado con cherry-pick.

### Integración: historial lineal, sin commits de merge

El historial de `main` y `develop` es una línea recta. **Nunca `git merge` hacia `develop`** (ni commits "Merge
branch…"): se integra con rebase y cherry-pick. La única excepción es el commit inicial que unió el historial
importado de BackTecnophones. **`develop` nunca se reescribe ni se fuerza el push.**

1. **Trabajar en la rama propia** y commitear ahí.
2. **Actualizar la rama propia con rebase** sobre `develop`, nunca con merge:
   ```bash
   git fetch origin
   git rebase origin/develop
   ```
   Si hay conflictos: resolverlos, `git add` y `git rebase --continue`. Los commits que ya estaban integrados en
   `develop` por cherry-pick git los descarta solo durante el rebase. Después, la rama propia se sube con
   `git push --force-with-lease` (solo la rama propia, nunca `develop` ni `main`).
3. **Integrar a `develop` con cherry-pick** de los commits que estén listos:
   ```bash
   git switch develop
   git pull --ff-only origin develop
   git cherry-pick <sha1> <sha2> ...      # en orden cronológico; o un rango: <primero>^..<último>
   ./mvnw compile                          # verificar que no se rompió nada (y levantar la app si hace falta)
   git push origin develop                 # push normal, sin --force
   git switch <rama-propia>
   ```
   Qué commits de la rama propia todavía no están en `develop`:
   `git log --oneline --cherry-pick --right-only origin/develop...HEAD`.
   Si el push de `develop` es rechazado porque el otro integró antes: `git pull --rebase origin develop` (solo
   rebasea los commits locales todavía no pusheados) y volver a pushear. Nunca `--force`.
4. Después de integrar, volver a la rama propia y `git rebase origin/develop` para que quede alineada.

**Migraciones de Flyway al integrar:** si dos ramas agregaron una migración con el mismo número, la que se integra
segunda se renumera (ej. `V2__…` → `V6__…`) **antes** del cherry-pick a `develop`. Una vez en `develop`, una
migración no se toca más.

### Versiones y hotfix

```bash
# Publicar una versión: main avanza hasta develop
git switch main && git pull --ff-only
git merge --ff-only develop               # solo mueve el puntero; si no es posible, falla
git tag v1.2.0 && git push origin main v1.2.0

# Hotfix urgente: se corrige sobre main y se lleva a develop con cherry-pick
git switch main && git switch -c hotfix/descripcion
# ...commit...
git switch main && git merge --ff-only hotfix/descripcion && git push origin main
git switch develop && git cherry-pick <sha-del-hotfix> && git push origin develop
```

Configuración local recomendada: `git config pull.rebase true`, `git config merge.ff only`,
`git config rebase.autoStash true`. Con `merge.ff only`, un `git merge` que necesitaría commit de merge falla en
lugar de crearlo. En GitHub: proteger `main` y `develop` con "Require linear history".

### Reglas para Claude Code

- No commitear, pushear, hacer rebase ni cherry-pick sin que el usuario lo pida.
- Prohibido `git push --force` / `--force-with-lease` sobre `develop` o `main`, y prohibido `git merge` hacia
  `develop` (usar cherry-pick).
- No reescribir ni pushear la rama del compañero.
- Si un cherry-pick o rebase tiene conflictos, mostrarlos y resolverlos con el usuario; no descartar cambios del
  otro con `--theirs`/`--ours` sin confirmar.

## Pendientes conocidos

- Migración de datos de TecnoPhones desde MongoDB (script ETL único) antes de mover esa tienda a este backend.
- QR fiscal en el PDF de factura (RG 4892) y factura automática desde un pedido aprobado.
- Estados de despacho del pedido (en preparación, enviado, entregado).
- Limpieza de imágenes huérfanas en el almacenamiento.
- Descuentos: probar con el sandbox de MP el ítem único que se manda cuando hay cupón (ver "Descuentos").
- Correo Argentino: probar contra la API de TEST (credenciales pedidas al Correo) y ajustar lo que difiera del manual.
- Zipnova: probar el alta y la etiqueta con un envío real; ver en qué región puede correr el servidor (firewall).
- Descuentos (etapa 2, si se pide): promociones automáticas sin código (ej. 2x1, descuento por cantidad).
