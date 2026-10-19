package pe.castillo.lector;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que un lector logró extraer de un recibo, factura o cuota para rellenar una obligación nueva.
 * Un dato que no se pudo leer es null (no se inventa).
 *
 * @param texto            el texto completo leído; se usa solo en memoria para reconocer al beneficiario y no se guarda
 * @param emisorNombre     quién emite o cobra el documento (solo lo entrega el lector con IA)
 * @param emisorDocumento  RUC o DNI del emisor, solo dígitos (solo lo entrega el lector con IA)
 * @param concepto         descripción corta sugerida, por ejemplo "Recibo de luz - setiembre 2026" (solo IA)
 * @param categoria        una de las cuatro categorías fijas, por su nombre (solo IA)
 */
public record LecturaDocumento(LocalDate fechaVencimiento, BigDecimal monto, String texto,
                               String emisorNombre, String emisorDocumento, String concepto, String categoria) {

    public static final LecturaDocumento VACIA = new LecturaDocumento(null, null, "");

    public LecturaDocumento(LocalDate fechaVencimiento, BigDecimal monto, String texto) {
        this(fechaVencimiento, monto, texto, null, null, null, null);
    }

    public boolean tieneMontoYFecha() {
        return fechaVencimiento != null && monto != null;
    }

    /** Completa con otra lectura solo los campos que esta dejó vacíos y junta los textos. */
    public LecturaDocumento completarCon(LecturaDocumento otra) {
        return new LecturaDocumento(
                fechaVencimiento != null ? fechaVencimiento : otra.fechaVencimiento,
                monto != null ? monto : otra.monto,
                (texto + "\n" + otra.texto).trim(),
                emisorNombre != null ? emisorNombre : otra.emisorNombre,
                emisorDocumento != null ? emisorDocumento : otra.emisorDocumento,
                concepto != null ? concepto : otra.concepto,
                categoria != null ? categoria : otra.categoria);
    }
}
