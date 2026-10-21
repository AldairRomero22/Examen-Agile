-- Pedido del Product Owner: las cuotas del cronograma se pagan como cualquier otro pago (subiendo el voucher).
-- Al registrar el pago de una cuota se crea su obligación y la cuota queda enlazada a ella.
-- Una cuota tiene como mucho un pago: el índice único lo garantiza.

ALTER TABLE prestamo_cuota ADD COLUMN obligacion_id BIGINT REFERENCES obligacion (id);

CREATE UNIQUE INDEX ux_prestamo_cuota_obligacion ON prestamo_cuota (obligacion_id) WHERE obligacion_id IS NOT NULL;
