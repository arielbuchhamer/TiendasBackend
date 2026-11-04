-- Cuentas de cliente (módulo opcional clientes.habilitada). Las da de alta el administrador; el cliente elige su
-- contraseña con un link de activación que recibe por email. Con el módulo apagado las tablas quedan vacías.
CREATE TABLE cliente (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Usuario del login; se guarda normalizado (trim + minúsculas), por eso alcanza un UNIQUE simple
    email          VARCHAR(160)  NOT NULL,
    nombre         VARCHAR(100)  NOT NULL,
    apellido       VARCHAR(100)  NOT NULL,
    documento      VARCHAR(20)   NOT NULL,
    razon_social   VARCHAR(160),
    telefono       VARCHAR(40),
    provincia      VARCHAR(100),
    localidad      VARCHAR(100),
    -- Solo para el administrador
    notas          VARCHAR(1000),
    -- NULL hasta que el cliente elige su contraseña con el link de activación
    password_hash  VARCHAR(100),
    estado         VARCHAR(20)   NOT NULL,
    invitado_en    TIMESTAMPTZ,
    ultimo_acceso  TIMESTAMPTZ,
    -- Texto de búsqueda del panel: un solo índice trigram en lugar de uno por columna
    busqueda       TEXT GENERATED ALWAYS AS (lower(email || ' ' || nombre || ' ' || apellido || ' ' || documento
                       || ' ' || coalesce(razon_social, ''))) STORED,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_cliente_email UNIQUE (email),
    CONSTRAINT ck_cliente_estado CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'BLOQUEADO'))
);
-- Listado del panel: por estado y más nuevos primero
CREATE INDEX ix_cliente_estado_creado ON cliente (estado, creado_en DESC);
CREATE INDEX ix_cliente_busqueda_trgm ON cliente USING gin (busqueda gin_trgm_ops);

-- Links de activación y de cambio de contraseña: de un solo uso y con vencimiento. Se guarda el SHA-256 del token;
-- el token en claro solo viaja en el email.
CREATE TABLE cliente_token (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id BIGINT      NOT NULL REFERENCES cliente (id) ON DELETE CASCADE,
    tipo       VARCHAR(20) NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    vence_en   TIMESTAMPTZ NOT NULL,
    usado_en   TIMESTAMPTZ,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cliente_token_hash UNIQUE (hash),
    CONSTRAINT ck_cliente_token_tipo CHECK (tipo IN ('ACTIVACION', 'RESETEO'))
);
-- Al generar un link nuevo se invalidan los anteriores del mismo cliente y tipo
CREATE INDEX ix_cliente_token_cliente ON cliente_token (cliente_id, tipo) WHERE usado_en IS NULL;
-- Limpieza periódica de los tokens vencidos
CREATE INDEX ix_cliente_token_vence ON cliente_token (vence_en);

-- Cliente que hizo el pedido (NULL en el checkout anónimo). Sin cascade: un cliente con pedidos no se borra.
ALTER TABLE pedido ADD COLUMN cliente_id BIGINT REFERENCES cliente (id);
-- "Mis pedidos" y el filtro por cliente del panel
CREATE INDEX ix_pedido_cliente_creado ON pedido (cliente_id, creado_en DESC) WHERE cliente_id IS NOT NULL;

-- Emails de cuenta (invitación, cambio de contraseña), como pedido_id rastrea los de un pedido
ALTER TABLE email_pendiente ADD COLUMN cliente_id BIGINT REFERENCES cliente (id) ON DELETE SET NULL;
