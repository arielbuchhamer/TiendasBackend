-- Datos del envío que pide Zipnova y que la tienda guarda desde el checkout.
ALTER TABLE pedido
    -- Indicaciones para el repartidor (entre calles, timbre, referencias); van al transporte con piso y depto
    ADD COLUMN envio_indicaciones     VARCHAR(150),
    -- Forma de despacho de la opción elegida (ej. carrier_dropoff): Zipnova pide guardarla al cotizar y usarla al crear
    ADD COLUMN envio_logistica        VARCHAR(40),
    -- Fecha estimada de entrega que informó Zipnova al cotizar
    ADD COLUMN envio_entrega_estimada DATE;
