# TiendasBackend

Backend base para tiendas web. **Un mismo código, una instancia y una base de datos por tienda**: cada cliente
(TecnoPhones, Maderas, …) se despliega como un servicio independiente con su propia configuración, credenciales,
dominio y PostgreSQL. No hay forks ni copias del código por cliente.

```
                 TiendasBackend (este repo, versionado con tags vX.Y.Z)
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
   TecnoPhones (v1.2.0)    Maderas (v1.3.0)     Cliente 3 (v1.1.0)
   API + PostgreSQL        API + PostgreSQL     API + PostgreSQL
```

Nació a partir de `BackTecnophones` (MongoDB), conservando su historial de Git (reescrito para eliminar credenciales).

## Funcionalidades

- **Catálogo**: rubros → categorías → productos → variantes. Atributos libres por variante (`color`, `espesor`, `largo`…),
  unidades de venta con decimales (m², metro, pie, kg…), búsqueda por texto, filtros y paginación.
- **Imágenes**: se suben una vez y se generan 4 tamaños (miniatura, tarjeta, mediana, grande). Se sirven con caché
  inmutable de 1 año. Almacenamiento en S3-compatible (Cloudflare R2, AWS S3, MinIO) o disco en desarrollo.
- **Checkout con Mercado Pago** (Checkout Pro): precios, envío y totales calculados siempre en el servidor.
- **Webhook de Mercado Pago**: firma HMAC verificada, pago consultado a la API de MP, procesamiento idempotente,
  descuento de stock con bloqueo de filas (sin sobreventa) y revisión manual si se cobró sin stock o por otro monto.
- **Panel de administración**: login por sesión, usuarios, catálogo, pedidos (con contador de no vistos).
- **Facturación electrónica ARCA** (opcional, por tienda) vía AFRelay: Factura/Nota A, B, C, PDF con logo y color de marca.

## Stack

Java 21 · Spring Boot 4.1 · Spring Security 7 · Spring Data JPA / Hibernate 7 · PostgreSQL 18 · Flyway ·
Caffeine · Mercado Pago SDK · AWS SDK S3 · PDFBox · Docker · Railway.

## Ejecutar en local

Requisitos: **JDK 21** y **Docker** (para PostgreSQL).

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Con el perfil `local`:
- Spring Boot levanta PostgreSQL solo con `compose.yaml` y Flyway crea el esquema.
- API en `http://localhost:8080`, frontend esperado en `http://localhost:5173` (CORS y cookies configurados).
- Se crea el administrador `admin` / `admin-local-123`.
- Si la base está vacía, se carga un **catálogo de ejemplo** (rubros de HA!Tablas, productos con variantes e imágenes).
- Los **pagos se simulan**: el checkout aprueba el pedido al instante (descuenta stock) y redirige a `/exito`, sin
  Mercado Pago. Para usar el sandbox real: `MP_SIMULADO=false` y credenciales de prueba en `.env` (ver
  `.env.example`); el webhook necesita la API expuesta (ej. `ngrok http 8080` y esa URL en `TIENDA_API_URL`).
  La simulación solo funciona con el perfil `local`: en cualquier otro perfil la app no arranca si está activa.
- Imágenes y PDFs se guardan en `./datos/archivos` (ignorado por git).
- Para empezar de cero: `docker compose down -v` (borra la base local).

Comprobación rápida: `curl localhost:8080/actuator/health` → `{"status":"UP"}`.

### Conectar un frontend

Guía completa (endpoints, campos, adaptadores, checkout, CSRF): [`docs/integracion-frontend.html`](docs/integracion-frontend.html).
Resumen: `VITE_API_URL=http://localhost:8080/api/v1`, axios con `withCredentials` y `withXSRFToken` en `true`.

### Compilar / imagen Docker

```bash
./mvnw package                       # target/tiendas-backend-*.jar
docker build -t tiendas-backend .    # imagen de producción (usuario sin privilegios)
```

## Desplegar una tienda nueva en Railway

1. Crear un **proyecto** en Railway para la tienda y agregar un servicio **PostgreSQL**.
2. Agregar un servicio desde este repositorio de GitHub. Usa el `Dockerfile` y `railway.json`
   (healthcheck en `/actuator/health/readiness`).
3. En *Settings → Source* elegir qué desplegar: la rama `main` o, mejor, fijar un **tag** (`v1.3.0`) para
   controlar cuándo se actualiza cada cliente.
4. Cargar las variables (ver `.env.example`):
   - Base de datos: `PGHOST=${{Postgres.PGHOST}}`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` (referencias al servicio Postgres).
   - Tienda: `TIENDA_NOMBRE`, `TIENDA_FRONTEND_URL`, `TIENDA_API_URL`, `TIENDA_CORS_ORIGENES`, `TIENDA_COOKIES_DOMINIO`.
   - Mercado Pago: `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`.
   - Archivos: `ALMACENAMIENTO_TIPO=S3` + `ALMACENAMIENTO_S3_*` (un bucket **privado** por tienda).
   - Primer deploy: `TIENDA_ADMIN_INICIAL_USUARIO` / `TIENDA_ADMIN_INICIAL_CLAVE` (borrarlas después).
   - Módulos opcionales: `FACTURACION_HABILITADA=true` + `FACTURACION_*` activa la facturación y muestra su
     tarjeta en el panel; sin definir (o `false`), la tienda no tiene facturación y el panel no la muestra.
5. Dominio: usar un subdominio del dominio de la tienda para la API (ej. `api.mitienda.com.ar`). Front y API bajo
   el mismo dominio permiten cookies `SameSite=Lax` (funcionan en Safari) y que el front lea la cookie CSRF.
   Recomendado: Cloudflare delante de la API para cachear las imágenes en el borde.
6. En Mercado Pago → Webhooks: URL `https://api.mitienda.com.ar/api/v1/webhooks/mercadopago`, evento *Pagos*;
   copiar la clave secreta en `MP_WEBHOOK_SECRET`.

Si falta una variable obligatoria, la aplicación **no arranca** y el log indica cuál.

## API (`/api/v1`)

Los errores siguen RFC 9457 (`application/problem+json`): `{ "status", "title", "detail", "errores"? }`.
Listados paginados: `?page=0&size=24&sort=precio,asc` → `{ "content": [...], "page": {...} }`.

| Público | |
|---|---|
| `GET /tienda` | Nombre, moneda, opciones de envío y módulos activos (`modulos.facturacion`) |
| `GET /rubros`, `GET /categorias?rubroId=` | Clasificación del catálogo |
| `GET /productos?rubroId=&categoriaId=&texto=&destacados=` | Listado paginado (solo activos) |
| `GET /productos/{id}`, `GET /productos/aleatorios?cantidad=12` | Detalle / selección para la home |
| `GET /imagenes/{id}/{miniatura\|tarjeta\|mediana\|grande}` | Imagen redimensionada |
| `POST /checkout` | Crea el pedido y devuelve `checkoutUrl` de Mercado Pago |
| `POST /webhooks/mercadopago` | Notificaciones de Mercado Pago (firmadas) |
| `GET /comprobantes/{codigo}/pdf` | PDF de factura (si la facturación está habilitada) |
| `POST /auth/login`, `POST /auth/logout`, `GET /auth/yo` | Sesión del panel |

| Panel (`/admin/**`, rol ADMIN) | |
|---|---|
| `rubros`, `categorias`, `productos` | ABM del catálogo (`GET` de productos incluye inactivos) |
| `POST /admin/imagenes` | Subida multipart (`archivos`, hasta 10) |
| `GET /admin/pedidos?estado=`, `GET /admin/pedidos/{id}`, `PATCH /admin/pedidos/{id}/visto`, `GET /admin/pedidos/no-vistos` | Pedidos |
| `usuarios` | ABM de administradores |
| `comprobantes`, `comprobantes/manual`, `facturacion/*` | Facturación ARCA |

### Para el frontend

- Enviar las requests con credenciales (`credentials: 'include'` / `withCredentials: true`).
- Todo `POST/PUT/PATCH/DELETE` (incluido el login) debe reenviar la cookie `XSRF-TOKEN` en el header
  `X-XSRF-TOKEN` (axios: `withXSRFToken: true`). La cookie llega en cualquier respuesta, por ejemplo en `GET /tienda`.
  Excepciones: `POST /checkout` (anónimo) y el webhook.
- Productos: el precio de una variante es `variante.precio ?? producto.precio`; al editar, reenviar `version`
  del producto y de cada variante (si cambió por una venta en el medio, la API responde 409).
- Imágenes: `${API}/api/v1/imagenes/${id}/tarjeta`.
- Panel: mostrar u ocultar las secciones opcionales según `GET /tienda` → `modulos` (ej. la tarjeta de
  Facturación solo si `modulos.facturacion` es `true`). Se controla por instancia con `FACTURACION_HABILITADA`.
- Mercado Pago vuelve a `${TIENDA_FRONTEND_URL}/exito|pendiente|fallo` (configurable con `MERCADOPAGO_RUTA_EXITO`, etc.) con `external_reference` = código del pedido.

## Versionado y ramas

- `main`: estable, lo que se despliega. `develop`: integración. `feature/*`: desarrollo. Las ramas **no** representan clientes.
- Versiones con tags semánticos (`v1.0.0`, `v1.1.0`, …). Cada tienda en Railway apunta al tag que corresponda y se
  actualiza de forma controlada.
- Los cambios de esquema son migraciones Flyway nuevas (`V2__...sql`) que se aplican solas al desplegar; nunca se
  editan migraciones ya publicadas.

Ver [CLAUDE.md](CLAUDE.md) para la arquitectura y las convenciones de desarrollo.
