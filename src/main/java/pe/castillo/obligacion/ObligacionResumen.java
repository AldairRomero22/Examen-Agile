package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Una obligación tal como la ven las pantallas (lista del mes, alertas, detalle del beneficiario).
 * diasParaVencer: días que faltan para el vencimiento (0 = vence hoy, negativo = días de atraso).
 */
public record ObligacionResumen(
        Long id,
        String concepto,
        String categoria,
        Long beneficiarioId,
        String beneficiario,
        LocalDate fechaVencimiento,
        BigDecimal monto,
        String estado,
        String estadoEtiqueta,
        long diasParaVencer) {

    public static ObligacionResumen de(Obligacion o, LocalDate hoy) {
        EstadoMostrado estado = EstadoMostrado.de(o, hoy);
        return new ObligacionResumen(
                o.getId(),
                o.getConcepto(),
                o.getCategoria().getNombre(),
                o.getBeneficiario().getId(),
                o.getBeneficiario().getNombre(),
                o.getFechaVencimiento(),
                o.getMonto(),
                estado.name(),
                estado.getEtiqueta(),
                ChronoUnit.DAYS.between(hoy, o.getFechaVencimiento()));
    }
}
