package pe.castillo.documento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que el sistema pudo leer de un documento. Lo que no se pudo leer viene en null.
 * emisorNombre y emisorDocumento solo vienen cuando el emisor NO es un beneficiario registrado
 * (para ofrecer registrarlo); si es uno registrado viene beneficiarioId.
 * avisoDuplicada trae un texto si ya existe una obligación igual (mismo beneficiario, monto y vencimiento).
 */
public record DocumentoLeido(
        BigDecimal monto,
        LocalDate fechaVencimiento,
        Long beneficiarioId,
        String beneficiarioNombre,
        Long categoriaId,
        String concepto,
        String emisorNombre,
        String emisorDocumento,
        String avisoDuplicada) {
}
