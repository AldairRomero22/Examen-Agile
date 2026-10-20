package pe.castillo.documento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que el sistema pudo leer de un documento. Lo que no se pudo leer viene en null.
 * beneficiario: a quién se paga (el nombre leído, o el de alguien a quien ya se le pagó si coincide).
 * avisoDuplicada trae un texto si ya existe una obligación igual (misma persona o empresa, monto y vencimiento).
 */
public record DocumentoLeido(
        BigDecimal monto,
        LocalDate fechaVencimiento,
        String beneficiario,
        Long categoriaId,
        String concepto,
        String avisoDuplicada) {
}
