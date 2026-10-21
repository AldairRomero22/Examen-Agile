package pe.castillo.prestamo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que envía la pantalla al registrar un préstamo: sus datos y el cronograma completo.
 * Todo se revisa con ValidadorPrestamo (los mensajes salen de ahí).
 */
public record PrestamoForm(
        String entidad,
        String nroCredito,
        BigDecimal monto,
        BigDecimal tasa,
        Integer plazo,
        LocalDate fechaDesembolso,
        List<CuotaForm> cuotas) {

    public record CuotaForm(Integer numero, LocalDate vencimiento, String tipo, BigDecimal saldoInicial,
                            BigDecimal interes, BigDecimal amortizacion, BigDecimal cuota, BigDecimal saldoFinal) { }

    ValidadorPrestamo.Cronograma comoCronograma() {
        List<ValidadorPrestamo.Fila> filas = cuotas == null ? List.of() : cuotas.stream()
                .map(c -> c == null
                        ? new ValidadorPrestamo.Fila(null, null, null, null, null, null, null, null)
                        : new ValidadorPrestamo.Fila(c.numero(), c.vencimiento(), c.tipo(), c.saldoInicial(),
                                c.interes(), c.amortizacion(), c.cuota(), c.saldoFinal()))
                .toList();
        return new ValidadorPrestamo.Cronograma(entidad, nroCredito, monto, tasa, plazo, fechaDesembolso, filas);
    }
}
