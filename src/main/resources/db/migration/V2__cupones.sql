-- Módulo de descuentos: cupones (códigos de descuento) y su aplicación en los pedidos.

CREATE TABLE cupon (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Siempre en mayúsculas: el cliente puede escribirlo como quiera
    codigo           VARCHAR(40)   NOT NULL,
    descripcion      VARCHAR(200),
    tipo             VARCHAR(20)   NOT NULL,
    -- Porcentaje (1 a 100) o monto fijo según el tipo; NULL en envío gratis
    valor            NUMERIC(14,2) CHECK (valor > 0),
    -- Descuento máximo en pesos para los cupones de porcentaje
    tope             NUMERIC(14,2) CHECK (tope > 0),
    minimo_compra    NUMERIC(14,2) CHECK (minimo_compra > 0),
    desde            TIMESTAMPTZ,
    hasta            TIMESTAMPTZ,
    usos_maximos     INT           CHECK (usos_maximos > 0),
    usos_por_cliente INT           CHECK (usos_por_cliente > 0),
    solo_primera_compra BOOLEAN    NOT NULL DEFAULT FALSE,
    activo           BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_cupon_codigo UNIQUE (codigo),
    CONSTRAINT ck_cupon_vigencia CHECK (desde IS NULL OR hasta IS NULL OR desde < hasta)
);

-- Alcance del cupón. Sin filas en ninguna de las tres tablas, aplica a toda la tienda.
-- Sin FK a rubro/categoría/producto a propósito: borrar un producto no debe fallar porque un cupón lo
-- nombra, y un id que ya no existe simplemente no coincide con nada (el cupón no pasa a ser general).
CREATE TABLE cupon_rubro (
    cupon_id BIGINT NOT NULL REFERENCES cupon (id) ON DELETE CASCADE,
    rubro_id BIGINT NOT NULL,
    PRIMARY KEY (cupon_id, rubro_id)
);

CREATE TABLE cupon_categoria (
    cupon_id     BIGINT NOT NULL REFERENCES cupon (id) ON DELETE CASCADE,
    categoria_id BIGINT NOT NULL,
    PRIMARY KEY (cupon_id, categoria_id)
);

CREATE TABLE cupon_producto (
    cupon_id    BIGINT NOT NULL REFERENCES cupon (id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL,
    PRIMARY KEY (cupon_id, producto_id)
);

-- El pedido guarda el cupón usado y el descuento calculado al momento de la compra
ALTER TABLE pedido
    ADD COLUMN cupon_id     BIGINT        REFERENCES cupon (id),
    ADD COLUMN cupon_codigo VARCHAR(40),
    ADD COLUMN descuento    NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0);
-- Usos y métricas por cupón
CREATE INDEX ix_pedido_cupon ON pedido (cupon_id, estado) WHERE cupon_id IS NOT NULL;
-- "Solo primera compra" y "usos por cliente" buscan pedidos por email
CREATE INDEX ix_pedido_cliente_email ON pedido (lower(cliente_email));
