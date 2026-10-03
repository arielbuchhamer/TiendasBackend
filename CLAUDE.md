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
- Deploy: Railway, un proyecto por tienda (API + PostgreSQL). Cada tienda fija el tag de versión que corre.

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
| `desarrollo` | Solo perfil `local`: `DatosEjemplo` (catálogo HA!Tablas) y `PasarelaSimulada` (pagos sin MP) |

Capas dentro de cada paquete: `Controller` (HTTP, sin lógica) → `Service` (reglas y transacciones) →
`Repository` (Spring Data JPA). Sin capas extra (no hexagonal, no mappers, no `GenericService`).

### Decisiones que hay que respetar

- **Sin DTOs** (decisión del dueño). Las entidades son el modelo de la API. Para que eso sea seguro:
  - Campos que maneja el servidor: `@JsonProperty(access = READ_ONLY)` (se devuelven pero se ignoran al recibir).
  - Datos internos: `@JsonIgnore` (hashes, claves de almacenamiento, back-references).
  - **Los services nunca persisten la entidad recibida en el body.** Siempre copian los campos permitidos sobre una
    entidad nueva o cargada de la base (ver `ProductoService#copiarDatos`). Esto evita mass assignment.
  - Solo se usan `record` para entradas/salidas que no son entidades (ej. `LoginRequest`, `InfoTienda`, `PagoInformado`).
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
- **Llamadas HTTP externas fuera de transacciones** (Mercado Pago, AFRelay, S3): no retener conexiones de la base.
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
- Sesión por cookie HttpOnly (`SESION`), CSRF con cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` (excepto checkout y webhook).
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

### Módulos opcionales

Una funcionalidad opcional (hoy: facturación) se activa por instancia con una sola propiedad
(`FACTURACION_HABILITADA`). Esa misma propiedad:
1. crea o no los beans y endpoints del módulo (anotación condicional, ej. `@FacturacionHabilitada`), y
2. se informa al frontend en `GET /api/v1/tienda` → `modulos`, para que el panel muestre u oculte la sección.

No crear flags de "visibilidad" separados del flag que activa el módulo: podrían quedar desincronizados.
Para un módulo nuevo: propiedad `xxx.habilitada`, anotación condicional propia y campo en `TiendaController.Modulos`.

Perfil `local` (`application-local.yml`): valores de desarrollo, admin `admin`/`admin-local-123`, cookies sin `secure`,
catálogo de ejemplo y pagos simulados (`MP_SIMULADO`, por defecto `true`).

**Pagos simulados — regla de seguridad:** `PasarelaSimulada` exige `@Profile("local")` **y**
`mercadopago.simulado=true`; las clases reales de MP llevan `@MercadoPagoReal` (se apagan con esa propiedad). Si la
propiedad se activa fuera de `local`, no queda ninguna `PasarelaPago` y la app no arranca. No romper ese doble candado:
todo código del paquete `desarrollo` debe llevar `@Profile("local")`.

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

- Ramas: `main` (estable), `develop` (integración), `feature/*`. Las ramas no son clientes.
- Releases con tags semánticos `vX.Y.Z` desde `main`. Cada tienda despliega un tag.
- Commits en español, descriptivos, en imperativo ("Agrega…", "Corrige…").

### Historial lineal: nunca commits de merge

El historial de `main` y `develop` es una línea recta. **No se hacen merges que generen commit de merge**;
se integra con rebase, avance rápido (`--ff-only`) o cherry-pick. La única excepción es el commit inicial que
unió el historial importado de BackTecnophones.

```bash
# 1. Nueva funcionalidad
git checkout develop && git pull
git checkout -b feature/mi-cambio
# ...commits...

# 2. Integrar a develop: primero rebase sobre develop actualizado, después avance rápido
git fetch origin
git rebase origin/develop                # resolver conflictos acá, en la feature
git checkout develop && git pull
git merge --ff-only feature/mi-cambio    # solo mueve el puntero; si no es posible, falla (rebasear de nuevo)
git push origin develop
git branch -d feature/mi-cambio

# 3. Publicar una versión: main avanza hasta develop
git checkout main && git pull
git merge --ff-only develop
git tag v1.2.0 && git push origin main v1.2.0

# 4. Hotfix urgente sobre main: se corrige en main y se lleva a develop con cherry-pick
git checkout main && git checkout -b hotfix/descripcion
# ...commit...
git checkout main && git merge --ff-only hotfix/descripcion && git push origin main
git checkout develop && git cherry-pick <sha-del-hotfix> && git push origin develop
```

Configuración local recomendada (ya aplicada en este clon):
`git config pull.rebase true`, `git config merge.ff only`, `git config rebase.autoStash true`.
Con `merge.ff only`, un `git merge` que necesitaría commit de merge falla en lugar de crearlo.

- No reescribir historia ya publicada de `main`. En `develop` solo con `git push --force-with-lease` y avisando.
- En GitHub: proteger `main` y `develop` con "Require linear history" y permitir solo "Rebase and merge" en los PR.

## Pendientes conocidos

- Migración de datos de TecnoPhones desde MongoDB (script ETL único) antes de mover esa tienda a este backend.
- QR fiscal en el PDF de factura (RG 4892) y factura automática desde un pedido aprobado.
- Estados de despacho del pedido (en preparación, enviado, entregado).
- Limpieza de imágenes huérfanas en el almacenamiento.
