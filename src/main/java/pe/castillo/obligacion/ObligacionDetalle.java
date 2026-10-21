package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * HU-06: todo lo que muestra el detalle de un pago (beneficiario = a quién se paga, en texto).
 * HU-10: si está en espera, la nota del gerente, quién la puso y cuándo; y si fue liberado, quién y cuándo.
 */
public record ObligacionDetalle(
        Long id,
        String concepto,
        String categoria,
        LocalDate fechaVencimiento,
        BigDecimal monto,
        String observacion,
        String estado,
        String estadoEtiqueta,
        long diasParaVencer,
        String registradoPor,
        OffsetDateTime registradoEn,
        String beneficiario,
        String ruc,
        boolean enEspera,
        String notaEspera,
        String esperaPor,
        OffsetDateTime esperaEn,
        String liberadoPor,
        OffsetDateTime liberadoEn) {
}
