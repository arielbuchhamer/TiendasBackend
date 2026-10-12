-- Módulo de envíos: zonas con precio por código postal, ajustes de entrega y medidas para cotizar con correos.

-- Peso y medidas del paquete. En la categoría son el valor por defecto de sus productos.
-- Hoy no se usan para el precio (las zonas tienen precio fijo); quedan listas para cotizar con un correo.
ALTER TABLE producto
    ADD COLUMN peso_gramos INT CHECK (peso_gramos > 0),
    ADD COLUMN largo_cm    INT CHECK (largo_cm > 0),
    ADD COLUMN ancho_cm    INT CHECK (ancho_cm > 0),
    ADD COLUMN alto_cm     INT CHECK (alto_cm > 0);

ALTER TABLE categoria
    ADD COLUMN peso_gramos INT CHECK (peso_gramos > 0),
    ADD COLUMN largo_cm    INT CHECK (largo_cm > 0),
    ADD COLUMN ancho_cm    INT CHECK (ancho_cm > 0),
    ADD COLUMN alto_cm     INT CHECK (alto_cm > 0);

-- Zona de envío: a qué códigos postales llega, cuánto cuesta y cuánto tarda.
-- codigos_postales es una lista como "3164, 3100-3299"; vacía = el resto del país (se evalúa al final).
CREATE TABLE zona_envio (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(80)   NOT NULL,
    codigos_postales VARCHAR(500),
    -- NULL solo si el costo es a convenir (se arregla por fuera de la web)
    precio           NUMERIC(14,2) CHECK (precio >= 0),
    costo_a_convenir BOOLEAN       NOT NULL DEFAULT FALSE,
    dias_min         INT           CHECK (dias_min >= 0),
    dias_max         INT           CHECK (dias_max >= 0),
    activa           BOOLEAN       NOT NULL DEFAULT TRUE,
    orden            INT           NOT NULL DEFAULT 0,
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_zona_envio_precio CHECK (costo_a_convenir OR precio IS NOT NULL),
    CONSTRAINT ck_zona_envio_dias CHECK (dias_min IS NULL OR dias_max IS NULL OR dias_min <= dias_max)
);

-- Ajustes generales de entrega (una sola fila por tienda)
CREATE TABLE ajustes_envio (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    retiro_habilitado  BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Dónde y cómo se retira (ej. "En Gral. Ramírez. Te escribimos para coordinar.")
    retiro_detalle     VARCHAR(300),
    envio_gratis_desde NUMERIC(14,2) CHECK (envio_gratis_desde > 0),
    creado_en          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Opción de envío que eligió el cliente y su descripción al momento de la compra
ALTER TABLE pedido
    ADD COLUMN envio_opcion      VARCHAR(40),
    ADD COLUMN envio_descripcion VARCHAR(200),
    ADD COLUMN envio_a_convenir  BOOLEAN NOT NULL DEFAULT FALSE;
