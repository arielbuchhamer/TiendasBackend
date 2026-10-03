-- Esquema inicial de TiendasBackend.
-- Convenciones: tablas en singular, PK bigint identity, montos NUMERIC(14,2), cantidades NUMERIC(14,3),
-- fechas TIMESTAMPTZ. Las FK no usan ON DELETE CASCADE salvo en tablas hijas que no existen sin su padre.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─── Usuarios del panel de administración ───────────────────────────────
CREATE TABLE usuario (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR(60)  NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_username UNIQUE (username)
);

-- ─── Catálogo ───────────────────────────────────────────────────────────
CREATE TABLE rubro (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(80) NOT NULL,
    orden          INT         NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rubro_nombre UNIQUE (nombre)
);

CREATE TABLE categoria (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rubro_id       BIGINT      NOT NULL REFERENCES rubro (id),
    nombre         VARCHAR(80) NOT NULL,
    orden          INT         NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- El índice de esta restricción también sirve para filtrar categorías por rubro
    CONSTRAINT uq_categoria_rubro_nombre UNIQUE (rubro_id, nombre)
);

-- Metadatos de las imágenes; los archivos (en varios tamaños) viven en el almacenamiento (S3 o disco)
CREATE TABLE imagen (
    id           UUID PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    ancho        INT         NOT NULL,
    alto         INT         NOT NULL,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE producto (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    categoria_id   BIGINT        NOT NULL REFERENCES categoria (id),
    nombre         VARCHAR(200)  NOT NULL,
    descripcion    TEXT,
    precio         NUMERIC(14,2) NOT NULL CHECK (precio >= 0),
    unidad_venta   VARCHAR(20)   NOT NULL,
    activo         BOOLEAN       NOT NULL DEFAULT TRUE,
    destacado      BOOLEAN       NOT NULL DEFAULT FALSE,
    version        BIGINT        NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Listado por categoría ordenado por más nuevos
CREATE INDEX ix_producto_categoria_id ON producto (categoria_id, id DESC);
-- Listado público general (solo activos), ordenado por más nuevos
CREATE INDEX ix_producto_activos ON producto (id DESC) WHERE activo;
-- Búsqueda por texto con LIKE '%texto%'
CREATE INDEX ix_producto_nombre_trgm ON producto USING gin (lower(nombre) gin_trgm_ops);

CREATE TABLE producto_imagen (
    producto_id BIGINT NOT NULL REFERENCES producto (id) ON DELETE CASCADE,
    posicion    INT    NOT NULL,
    imagen_id   UUID   NOT NULL REFERENCES imagen (id),
    PRIMARY KEY (producto_id, posicion)
);
CREATE INDEX ix_producto_imagen_imagen ON producto_imagen (imagen_id);

-- Todo producto tiene al menos una variante: el stock vive solo acá
CREATE TABLE variante (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id    BIGINT        NOT NULL REFERENCES producto (id) ON DELETE CASCADE,
    posicion       INT           NOT NULL DEFAULT 0,
    sku            VARCHAR(60)   NOT NULL,
    atributos      JSONB         NOT NULL DEFAULT '{}',
    precio         NUMERIC(14,2) CHECK (precio >= 0),
    -- El CHECK garantiza a nivel base de datos que nunca se venda stock que no existe
    stock          NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (stock >= 0),
    imagen_id      UUID          REFERENCES imagen (id),
    activo         BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_variante_sku UNIQUE (sku)
);
CREATE INDEX ix_variante_producto ON variante (producto_id);
CREATE INDEX ix_variante_imagen ON variante (imagen_id) WHERE imagen_id IS NOT NULL;

-- ─── Pedidos y pagos ────────────────────────────────────────────────────
CREATE TABLE pedido (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Identificador público (external_reference en Mercado Pago); el id interno nunca se expone afuera del panel
    codigo              UUID          NOT NULL,
    estado              VARCHAR(30)   NOT NULL,
    cliente_nombre      VARCHAR(100)  NOT NULL,
    cliente_apellido    VARCHAR(100)  NOT NULL,
    cliente_documento   VARCHAR(20)   NOT NULL,
    cliente_email       VARCHAR(160)  NOT NULL,
    cliente_telefono    VARCHAR(40),
    entrega             VARCHAR(20)   NOT NULL,
    envio_calle         VARCHAR(120),
    envio_numero        VARCHAR(20),
    envio_piso          VARCHAR(10),
    envio_departamento  VARCHAR(10),
    envio_ciudad        VARCHAR(80),
    envio_provincia     VARCHAR(80),
    envio_codigo_postal VARCHAR(12),
    subtotal            NUMERIC(14,2) NOT NULL,
    costo_envio         NUMERIC(14,2) NOT NULL,
    total               NUMERIC(14,2) NOT NULL,
    moneda              VARCHAR(3)    NOT NULL,
    mp_preference_id    VARCHAR(100),
    checkout_url        VARCHAR(500),
    visto               BOOLEAN       NOT NULL DEFAULT FALSE,
    aprobado_en         TIMESTAMPTZ,
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_pedido_codigo UNIQUE (codigo)
);
-- Listado del panel: por estado y más recientes primero
CREATE INDEX ix_pedido_estado_creado ON pedido (estado, creado_en DESC);
CREATE INDEX ix_pedido_creado ON pedido (creado_en DESC);
-- Contador de pedidos aprobados sin ver
CREATE INDEX ix_pedido_no_vistos ON pedido (estado) WHERE NOT visto;

CREATE TABLE pedido_item (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id            BIGINT        NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    posicion             INT           NOT NULL,
    variante_id          BIGINT        NOT NULL REFERENCES variante (id),
    -- Copia de los datos al momento de la compra: el producto puede cambiar o borrarse después
    producto_nombre      VARCHAR(200)  NOT NULL,
    variante_descripcion VARCHAR(200),
    sku                  VARCHAR(60)   NOT NULL,
    unidad_venta         VARCHAR(20)   NOT NULL,
    imagen_id            UUID,
    cantidad             NUMERIC(14,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario      NUMERIC(14,2) NOT NULL,
    subtotal             NUMERIC(14,2) NOT NULL
);
CREATE INDEX ix_pedido_item_pedido ON pedido_item (pedido_id);
CREATE INDEX ix_pedido_item_variante ON pedido_item (variante_id);

-- Un pedido puede tener varios intentos de pago (ej. tarjeta rechazada y luego aprobada)
CREATE TABLE pago (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id      BIGINT        NOT NULL REFERENCES pedido (id),
    proveedor      VARCHAR(30)   NOT NULL,
    id_externo     VARCHAR(60)   NOT NULL,
    estado         VARCHAR(30)   NOT NULL,
    estado_detalle VARCHAR(80),
    monto          NUMERIC(14,2),
    moneda         VARCHAR(3),
    medio_pago     VARCHAR(40),
    aprobado_en    TIMESTAMPTZ,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Garantiza idempotencia: un mismo pago del proveedor nunca se registra dos veces
    CONSTRAINT uq_pago_proveedor_id_externo UNIQUE (proveedor, id_externo)
);
CREATE INDEX ix_pago_pedido ON pago (pedido_id);

-- ─── Facturación electrónica (ARCA) ─────────────────────────────────────
CREATE TABLE comprobante (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Identificador público para compartir el PDF con el comprador
    codigo          UUID          NOT NULL,
    idempotency_key VARCHAR(100)  NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    tipo            VARCHAR(20)   NOT NULL,
    punto_venta     INT           NOT NULL,
    numero          BIGINT,
    total           NUMERIC(14,2) NOT NULL,
    solicitud       JSONB         NOT NULL,
    cae             VARCHAR(20),
    cae_vencimiento DATE,
    resultado       VARCHAR(5),
    afrelay_request  JSONB,
    afrelay_response JSONB,
    pdf_clave       VARCHAR(200),
    error           TEXT,
    autorizado_en   TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_comprobante_codigo UNIQUE (codigo),
    CONSTRAINT uq_comprobante_idempotency_key UNIQUE (idempotency_key)
);
-- Un número de comprobante autorizado no puede repetirse
CREATE UNIQUE INDEX uq_comprobante_numero ON comprobante (tipo, punto_venta, numero) WHERE estado = 'AUTORIZADO';
CREATE INDEX ix_comprobante_creado ON comprobante (creado_en DESC);
