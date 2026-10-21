package pe.castillo.lector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que se leyó de un cronograma de préstamo (Excel, PDF o imagen). Lo que no se pudo leer viene en null.
 * Los valores se copian tal como están en el documento, aunque estén mal: el sistema los revisa después
 * y marca los errores (no los corrige solo).
 *
 * @param lector "EXCEL" o "IA"
 */
public record LecturaCronograma(String entidad, String nroCredito, BigDecimal monto, BigDecimal tasa, Integer plazo,
                                LocalDate fechaDesembolso, List<Fila> cuotas, String lector) {

    public record Fila(Integer numero, LocalDate vencimiento, String tipo, BigDecimal saldoInicial, BigDecimal interes,
                       BigDecimal amortizacion, BigDecimal cuota, BigDecimal saldoFinal) { }

    public boolean estaVacia() {
        return (cuotas == null || cuotas.isEmpty()) && monto == null && plazo == null && nroCredito == null;
    }
}
