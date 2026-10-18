package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import pe.castillo.beneficiario.BeneficiarioFila;

/** HU-06: todo lo que muestra el detalle de un pago. */
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
        BeneficiarioFila beneficiario) {
}
