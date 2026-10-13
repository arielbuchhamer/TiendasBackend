-- Integración con Correo Argentino (API MiCorreo): zonas cotizadas por el Correo y datos del envío en el pedido.

-- Una zona puede tener precio fijo (como hasta ahora) o cotizarse con Correo Argentino. En las de Correo, precio
-- es opcional: es el respaldo si el Correo no responde (sin precio, el costo queda a convenir).
ALTER TABLE zona_envio
    ADD COLUMN tipo             VARCHAR(20) NOT NULL DEFAULT 'PRECIO_FIJO',
    -- Qué servicios del Correo ofrece la zona
    ADD COLUMN correo_domicilio BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN correo_sucursal  BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN correo_expreso   BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE zona_envio DROP CONSTRAINT ck_zona_envio_precio;
ALTER TABLE zona_envio ADD CONSTRAINT ck_zona_envio_precio
    CHECK (tipo = 'CORREO_ARGENTINO' OR costo_a_convenir OR precio IS NOT NULL);

ALTER TABLE pedido
    -- Sucursal del Correo donde retira el cliente (entrega SUCURSAL), con su nombre y dirección al momento de comprar
    ADD COLUMN envio_sucursal         VARCHAR(10),
    ADD COLUMN envio_sucursal_detalle VARCHAR(300),
    -- Cuándo se dio de alta el envío en MiCorreo y el código de seguimiento del paquete
    ADD COLUMN correo_importado_en    TIMESTAMPTZ,
    ADD COLUMN envio_seguimiento      VARCHAR(60);
