-- Las zonas de envío se pueden definir por provincia (más simple que rangos de códigos postales, que en Argentina
-- se superponen entre provincias). Lista de códigos de una letra del Correo, ej. "E,S,X". Los códigos postales de
-- la zona siguen sirviendo para lo local (ej. la propia ciudad).
ALTER TABLE zona_envio ADD COLUMN provincias VARCHAR(100);
