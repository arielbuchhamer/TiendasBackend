-- Paquete por defecto de la tienda: peso y medidas con los que se cotiza un producto que no tiene los suyos ni los
-- de su categoría. Así un envío cotizado (Correo o Zipnova) siempre tiene precio.
ALTER TABLE ajustes_envio
    ADD COLUMN peso_gramos INT CHECK (peso_gramos > 0),
    ADD COLUMN largo_cm    INT CHECK (largo_cm > 0),
    ADD COLUMN ancho_cm    INT CHECK (ancho_cm > 0),
    ADD COLUMN alto_cm     INT CHECK (alto_cm > 0);
