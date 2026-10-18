-- =====================================================================
-- Castillo · Sprint 1 · Tablas
-- Regla general: nada se borra; cada tabla tiene la columna "activo".
-- Los estados Por pagar / Por vencer / Vencido NO se guardan: se calculan
-- al consultar con la fecha de vencimiento (HU-05).
-- =====================================================================

-- HU-01 · Usuarios (dos cuentas creadas de antemano, sin registro público)
CREATE TABLE usuario (
    id          BIGSERIAL    PRIMARY KEY,
    correo      VARCHAR(120) NOT NULL,
    clave_hash  VARCHAR(100) NOT NULL,              -- BCrypt, nunca texto plano
    nombre      VARCHAR(100) NOT NULL,
    rol         VARCHAR(20)  NOT NULL CHECK (rol IN ('ASISTENTE', 'GERENTE')),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_usuario_correo ON usuario (lower(correo));

-- HU-03 · Cuatro categorías fijas (se insertan en V2)
CREATE TABLE categoria (
    id      BIGSERIAL   PRIMARY KEY,
    nombre  VARCHAR(40) NOT NULL UNIQUE,
    activo  BOOLEAN     NOT NULL DEFAULT TRUE
);

-- HU-02 · Beneficiarios
CREATE TABLE beneficiario (
    id          BIGSERIAL    PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,              -- nombre o razón social
    documento   VARCHAR(11)  NOT NULL,              -- RUC (11) o DNI (8), solo números
    banco       VARCHAR(60)  NOT NULL,
    cuenta      VARCHAR(30)  NOT NULL,              -- cuenta o CCI
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por  BIGINT       NOT NULL REFERENCES usuario (id),
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_beneficiario_documento UNIQUE (documento),
    CONSTRAINT ck_beneficiario_documento CHECK (documento ~ '^([0-9]{8}|[0-9]{11})$')
);

-- HU-03, HU-05, HU-10 · Obligaciones (gastos por pagar)
CREATE TABLE obligacion (
    id                 BIGSERIAL     PRIMARY KEY,
    concepto           VARCHAR(150)  NOT NULL,
    categoria_id       BIGINT        NOT NULL REFERENCES categoria (id),
    beneficiario_id    BIGINT        NOT NULL REFERENCES beneficiario (id),
    monto              NUMERIC(12,2) NOT NULL CHECK (monto > 0),   -- soles, 2 decimales
    fecha_vencimiento  DATE          NOT NULL,
    observacion        VARCHAR(200),

    -- Estado del pago. PENDIENTE se muestra como Por pagar / Por vencer / Vencido
    -- según la fecha de hoy.
    estado_pago        VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE'
                       CHECK (estado_pago IN ('PENDIENTE', 'EN_VERIFICACION', 'PAGADO', 'OBSERVADO')),
    tipo_verificacion  VARCHAR(15)   CHECK (tipo_verificacion IN ('AUTOMATICA', 'MANUAL')),
    pagado_en          TIMESTAMPTZ,

    -- HU-10 · En espera (lo decide el gerente)
    en_espera          BOOLEAN       NOT NULL DEFAULT FALSE,
    nota_espera        VARCHAR(200),
    espera_por         BIGINT        REFERENCES usuario (id),
    espera_en          TIMESTAMPTZ,
    liberado_por       BIGINT        REFERENCES usuario (id),
    liberado_en        TIMESTAMPTZ,

    activo             BOOLEAN       NOT NULL DEFAULT TRUE,
    registrado_por     BIGINT        NOT NULL REFERENCES usuario (id),
    registrado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_obligacion_espera_completa
        CHECK (NOT en_espera OR (nota_espera IS NOT NULL AND espera_por IS NOT NULL AND espera_en IS NOT NULL)),
    CONSTRAINT ck_obligacion_pagado_no_espera
        CHECK (NOT (en_espera AND estado_pago = 'PAGADO')),
    CONSTRAINT ck_obligacion_pagado_completo
        CHECK (estado_pago <> 'PAGADO' OR (pagado_en IS NOT NULL AND tipo_verificacion IS NOT NULL))
);
CREATE INDEX ix_obligacion_vencimiento  ON obligacion (fecha_vencimiento) WHERE activo;
CREATE INDEX ix_obligacion_beneficiario ON obligacion (beneficiario_id);

-- HU-07, HU-08, HU-09 · Comprobantes (vouchers). Cada subida es una fila nueva.
CREATE TABLE comprobante (
    id                     BIGSERIAL     PRIMARY KEY,
    obligacion_id          BIGINT        NOT NULL REFERENCES obligacion (id),
    archivo                BYTEA         NOT NULL,
    nombre_archivo         VARCHAR(255)  NOT NULL,
    tipo_mime              VARCHAR(50)   NOT NULL
                           CHECK (tipo_mime IN ('image/jpeg', 'image/png', 'application/pdf')),
    tamano_bytes           INTEGER       NOT NULL CHECK (tamano_bytes BETWEEN 1 AND 5242880),
    hash_sha256            VARCHAR(64)   NOT NULL,          -- lo calcula el sistema solo
    lector                 VARCHAR(20)   NOT NULL,          -- IA o TESSERACT

    -- Valor leído por el lector (nunca se edita)
    fecha_leida            DATE,
    nro_operacion_leido    VARCHAR(40),
    monto_leido            NUMERIC(12,2),

    -- Valor enviado a verificación
    fecha_enviada          DATE,
    nro_operacion_enviado  VARCHAR(40),
    monto_enviado          NUMERIC(12,2),

    -- "escrito a mano": solo permitido si el lector dejó ese campo vacío
    fecha_manual           BOOLEAN       NOT NULL DEFAULT FALSE,
    nro_operacion_manual   BOOLEAN       NOT NULL DEFAULT FALSE,
    monto_manual           BOOLEAN       NOT NULL DEFAULT FALSE,

    estado                 VARCHAR(15)   NOT NULL DEFAULT 'LEIDO'
                           CHECK (estado IN ('LEIDO', 'ENVIADO', 'APROBADO', 'RECHAZADO')),
    subido_por             BIGINT        NOT NULL REFERENCES usuario (id),
    subido_en              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Un dato leído no se puede cambiar: lo enviado debe ser igual a lo leído
    CONSTRAINT ck_fecha_manual   CHECK (NOT fecha_manual OR fecha_leida IS NULL),
    CONSTRAINT ck_nro_manual     CHECK (NOT nro_operacion_manual OR nro_operacion_leido IS NULL),
    CONSTRAINT ck_monto_manual   CHECK (NOT monto_manual OR monto_leido IS NULL),
    CONSTRAINT ck_fecha_no_editada
        CHECK (fecha_manual OR fecha_enviada IS NULL OR fecha_enviada = fecha_leida),
    CONSTRAINT ck_nro_no_editado
        CHECK (nro_operacion_manual OR nro_operacion_enviado IS NULL OR nro_operacion_enviado = nro_operacion_leido),
    CONSTRAINT ck_monto_no_editado
        CHECK (monto_manual OR monto_enviado IS NULL OR monto_enviado = monto_leido),
    -- Para enviarse a verificación deben estar los tres datos (HU-07 criterio 6)
    CONSTRAINT ck_enviado_completo
        CHECK (estado = 'LEIDO' OR (fecha_enviada IS NOT NULL AND nro_operacion_enviado IS NOT NULL AND monto_enviado IS NOT NULL))
);
CREATE INDEX ix_comprobante_obligacion ON comprobante (obligacion_id);
CREATE INDEX ix_comprobante_hash       ON comprobante (hash_sha256);
CREATE INDEX ix_comprobante_nro        ON comprobante (nro_operacion_enviado);

-- HU-08 reglas 3 y 4 + criterio 5: un nº de operación o un archivo aprobado
-- no puede usarse en ningún otro pago, y cada obligación tiene un solo comprobante aprobado.
CREATE UNIQUE INDEX ux_comprobante_nro_aprobado  ON comprobante (nro_operacion_enviado) WHERE estado = 'APROBADO';
CREATE UNIQUE INDEX ux_comprobante_hash_aprobado ON comprobante (hash_sha256)           WHERE estado = 'APROBADO';
CREATE UNIQUE INDEX ux_comprobante_un_aprobado   ON comprobante (obligacion_id)         WHERE estado = 'APROBADO';

-- HU-08 criterio 6 · Cada verificación queda registrada con fecha, hora y resultado
CREATE TABLE verificacion (
    id               BIGSERIAL    PRIMARY KEY,
    comprobante_id   BIGINT       NOT NULL REFERENCES comprobante (id),
    obligacion_id    BIGINT       NOT NULL REFERENCES obligacion (id),
    resultado        VARCHAR(10)  NOT NULL CHECK (resultado IN ('PAGADO', 'OBSERVADO')),
    regla_monto      BOOLEAN      NOT NULL,
    regla_fecha      BOOLEAN      NOT NULL,
    regla_operacion  BOOLEAN      NOT NULL,
    regla_archivo    BOOLEAN      NOT NULL,
    detalle          VARCHAR(500),             -- p. ej. "Monto: esperado S/ 1,280.00, recibido S/ 1,208.00"
    verificado_por   BIGINT       NOT NULL REFERENCES usuario (id),
    verificado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_verificacion_resultado
        CHECK ((resultado = 'PAGADO') = (regla_monto AND regla_fecha AND regla_operacion AND regla_archivo))
);
CREATE INDEX ix_verificacion_obligacion ON verificacion (obligacion_id);
