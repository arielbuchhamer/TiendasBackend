-- Integración con Zipnova: zonas cotizadas por Zipnova (con Andreani, Correo Argentino, OCA y otros transportes) y
-- los datos del envío que se da de alta desde la venta.

-- Las zonas de Zipnova, como las del Correo, tienen el precio como respaldo opcional (sin precio, a convenir)
ALTER TABLE zona_envio DROP CONSTRAINT ck_zona_envio_precio;
ALTER TABLE zona_envio ADD CONSTRAINT ck_zona_envio_precio
    CHECK (tipo IN ('CORREO_ARGENTINO', 'ZIPNOVA') OR costo_a_convenir OR precio IS NOT NULL);

ALTER TABLE pedido
    -- Id del envío en Zipnova (para la etiqueta y el seguimiento) y cuándo se dio de alta
    ADD COLUMN envio_externo_id      VARCHAR(40),
    ADD COLUMN envio_alta_en         TIMESTAMPTZ,
    -- Transporte que lleva el paquete, estado que informa Zipnova y su página pública de seguimiento
    ADD COLUMN envio_transportista   VARCHAR(80),
    ADD COLUMN envio_estado          VARCHAR(80),
    ADD COLUMN envio_seguimiento_url VARCHAR(300);
