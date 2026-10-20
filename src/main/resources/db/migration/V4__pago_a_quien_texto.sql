-- El Product Owner pidió no gestionar beneficiarios: cada obligación guarda "a quién se paga" como texto,
-- escrito por el asistente o leído del documento (recibo, boleta, factura o voucher).
-- No se borra nada: la tabla beneficiario y la columna beneficiario_id quedan como historial.

ALTER TABLE obligacion ADD COLUMN beneficiario_nombre VARCHAR(150);

-- Las obligaciones que ya existen toman el nombre del beneficiario que tenían
UPDATE obligacion o
   SET beneficiario_nombre = b.nombre
  FROM beneficiario b
 WHERE b.id = o.beneficiario_id;

ALTER TABLE obligacion ALTER COLUMN beneficiario_nombre SET NOT NULL;
ALTER TABLE obligacion ALTER COLUMN beneficiario_id DROP NOT NULL;
