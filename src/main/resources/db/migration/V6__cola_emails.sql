-- Cola de emails salientes (patrón outbox). El email se guarda en la misma transacción que el cambio que lo
-- origina (ej. la aprobación del pedido) y una tarea programada lo envía después, fuera de la transacción y con
-- reintentos: si el proveedor está caído o la aplicación se reinicia, el email no se pierde.
-- Se guarda ya armado (asunto, html, texto): se envía exactamente lo que correspondía al momento del evento.
CREATE TABLE email_pendiente (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo            VARCHAR(40)   NOT NULL,
    pedido_id       BIGINT        REFERENCES pedido (id),
    -- Direcciones separadas por coma
    destinatarios   VARCHAR(1000) NOT NULL,
    responder_a     VARCHAR(160),
    asunto          VARCHAR(200)  NOT NULL,
    html            TEXT          NOT NULL,
    texto           TEXT          NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    intentos        INT           NOT NULL DEFAULT 0,
    proximo_intento TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ultimo_error    VARCHAR(500),
    -- Id del email en el proveedor (para rastrearlo en su panel)
    id_externo      VARCHAR(100),
    enviado_en      TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- La tarea de envío consulta cada pocos segundos solo los pendientes cuyo reintento ya venció
CREATE INDEX ix_email_pendiente_proximo ON email_pendiente (proximo_intento) WHERE estado = 'PENDIENTE';
