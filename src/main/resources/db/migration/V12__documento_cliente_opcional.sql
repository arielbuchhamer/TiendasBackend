-- El CUIT/DNI de la cuenta de cliente pasa a ser opcional. El pedido lo copia de la cuenta, así que también puede
-- quedar sin documento (el checkout anónimo lo sigue exigiendo en la validación).
ALTER TABLE cliente ALTER COLUMN documento DROP NOT NULL;
ALTER TABLE pedido ALTER COLUMN cliente_documento DROP NOT NULL;

-- Con un documento NULL la concatenación daría NULL y el cliente no aparecería en ninguna búsqueda
ALTER TABLE cliente ALTER COLUMN busqueda SET EXPRESSION AS (lower(email || ' ' || nombre || ' ' || apellido || ' '
    || coalesce(documento, '') || ' ' || coalesce(razon_social, '')));
