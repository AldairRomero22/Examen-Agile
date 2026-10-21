-- Pedido del Product Owner: apartado de préstamos de capital con su cronograma de pagos (solo consulta).
-- El asistente lo registra a mano o leyéndolo (PDF, imagen o Excel); el sistema revisa que el cronograma sea coherente.

CREATE TABLE prestamo (
    id                BIGSERIAL     PRIMARY KEY,
    entidad           VARCHAR(100)  NOT NULL,                 -- quién presta, p. ej. BCP
    nro_credito       VARCHAR(40)   NOT NULL,
    monto             NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    tasa_interes      NUMERIC(7,4)  NOT NULL CHECK (tasa_interes > 0 AND tasa_interes <= 100),   -- % anual
    plazo             INTEGER       NOT NULL CHECK (plazo BETWEEN 1 AND 360),                  -- n.º de cuotas
    fecha_desembolso  DATE          NOT NULL,
    activo            BOOLEAN       NOT NULL DEFAULT TRUE,
    registrado_por    BIGINT        NOT NULL REFERENCES usuario (id),
    registrado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Un número de crédito no se registra dos veces
CREATE UNIQUE INDEX ux_prestamo_credito ON prestamo (upper(nro_credito)) WHERE activo;

CREATE TABLE prestamo_cuota (
    id             BIGSERIAL     PRIMARY KEY,
    prestamo_id    BIGINT        NOT NULL REFERENCES prestamo (id),
    numero         INTEGER       NOT NULL CHECK (numero >= 1),
    vencimiento    DATE          NOT NULL,
    tipo           VARCHAR(10)   NOT NULL CHECK (tipo IN ('NORMAL', 'DOBLE')),
    saldo_inicial  NUMERIC(12,2) NOT NULL CHECK (saldo_inicial >= 0),
    interes        NUMERIC(12,2) NOT NULL CHECK (interes >= 0),
    amortizacion   NUMERIC(12,2) NOT NULL CHECK (amortizacion >= 0),
    cuota          NUMERIC(12,2) NOT NULL CHECK (cuota > 0),
    saldo_final    NUMERIC(12,2) NOT NULL CHECK (saldo_final >= 0),
    CONSTRAINT uq_prestamo_cuota UNIQUE (prestamo_id, numero)
);
CREATE INDEX ix_prestamo_cuota_vencimiento ON prestamo_cuota (vencimiento);
