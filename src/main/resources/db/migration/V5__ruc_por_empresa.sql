-- Pedido del Product Owner: cada pago guarda el RUC (empresa) o DNI (persona) de a quién se paga.
-- Un RUC pertenece a una sola empresa y una empresa tiene un solo RUC (lo controla el sistema al registrar).
-- Los pagos que ya existen toman el documento del beneficiario que tenían; los demás quedan sin documento.

ALTER TABLE obligacion ADD COLUMN beneficiario_documento VARCHAR(11);

UPDATE obligacion o
   SET beneficiario_documento = b.documento
  FROM beneficiario b
 WHERE b.id = o.beneficiario_id;

ALTER TABLE obligacion ADD CONSTRAINT ck_obligacion_documento
    CHECK (beneficiario_documento IS NULL OR beneficiario_documento ~ '^([0-9]{8}|[0-9]{11})$');

CREATE INDEX ix_obligacion_documento ON obligacion (beneficiario_documento) WHERE activo;
