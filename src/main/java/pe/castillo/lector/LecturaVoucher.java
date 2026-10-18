package pe.castillo.lector;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-07 criterio 3: lo que un lector logró extraer del voucher.
 * Un dato que el lector no pudo leer es null (no se inventa).
 */
public record LecturaVoucher(LocalDate fecha, String nroOperacion, BigDecimal monto) {

    public static final LecturaVoucher VACIA = new LecturaVoucher(null, null, null);

    /** HU-07 criterio 4: no se leyó ningún dato. */
    public boolean estaVacia() {
        return fecha == null && nroOperacion == null && monto == null;
    }

    public boolean estaCompleta() {
        return fecha != null && nroOperacion != null && monto != null;
    }

    /** Completa con los datos de otra lectura solo en los campos que esta dejó vacíos. */
    public LecturaVoucher completarCon(LecturaVoucher otra) {
        return new LecturaVoucher(
                fecha != null ? fecha : otra.fecha,
                nroOperacion != null ? nroOperacion : otra.nroOperacion,
                monto != null ? monto : otra.monto);
    }
}
