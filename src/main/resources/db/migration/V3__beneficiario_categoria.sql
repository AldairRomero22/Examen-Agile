-- Cada beneficiario pertenece a una categoría (rubro): al registrar una obligación, elegir la categoría
-- muestra solo los beneficiarios de ese rubro.
ALTER TABLE beneficiario ADD COLUMN categoria_id BIGINT REFERENCES categoria (id);

-- Los beneficiarios que ya existen toman la categoría de su última obligación activa
-- (los que no tienen ninguna quedan sin categoría y solo salen cuando no se filtra).
UPDATE beneficiario b
   SET categoria_id = (SELECT o.categoria_id
                         FROM obligacion o
                        WHERE o.beneficiario_id = b.id AND o.activo = TRUE
                        ORDER BY o.fecha_vencimiento DESC, o.id DESC
                        LIMIT 1);
