-- Personalización opcional de los productos (ej. grabado) con un precio único que el dueño edita en el panel,
-- y productos que son servicios (sin stock ni envío, ej. "Servicio de grabado" sobre piezas del cliente).

-- Un servicio puede no pertenecer a ninguna categoría: no aparece en los rubros del catálogo
ALTER TABLE producto
    ALTER COLUMN categoria_id DROP NOT NULL,
    ADD COLUMN servicio BOOLEAN NOT NULL DEFAULT FALSE;

-- Ajustes de la personalización (una sola fila por tienda)
CREATE TABLE ajustes_personalizacion (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Cómo se llama para el cliente (ej. "Grabado")
    nombre         VARCHAR(40)   NOT NULL,
    -- Se suma por unidad al precio del producto
    precio         NUMERIC(14,2) NOT NULL CHECK (precio >= 0),
    -- Qué le pasa al cliente después de elegirla (ej. "Después de comprar, nos pasás el diseño por WhatsApp")
    detalle        VARCHAR(300),
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Copia de la personalización al momento de la compra, como el resto de la línea
ALTER TABLE pedido_item
    ADD COLUMN personalizado           BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN personalizacion_nombre  VARCHAR(40),
    ADD COLUMN personalizacion_precio  NUMERIC(14,2),
    -- Lo que el cliente pidió (ej. el nombre a grabar)
    ADD COLUMN personalizacion_detalle VARCHAR(300);
