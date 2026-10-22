package pe.castillo.documento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que el sistema pudo leer de un documento. Lo que no se pudo leer viene en null.
 * beneficiario: a quién se paga (el nombre leído, o el de alguien a quien ya se le pagó si coincide).
 * ruc: su RUC o DNI (el leído del documento o, si no se leyó, el ya registrado para esa empresa).
 * avisoDuplicada trae un texto si ya existe una obligación igual (misma persona o empresa, monto y vencimiento).
 * titular: a nombre de quién está el documento (null si no se pudo leer). titularEsMio: true si es la empresa,
 * null si no se pudo leer (un documento a nombre de otro ni siquiera llega aquí: se rechaza).
 */
public record DocumentoLeido(
        BigDecimal monto,
        LocalDate fechaVencimiento,
        String beneficiario,
        String ruc,
        Long categoriaId,
        String concepto,
        String avisoDuplicada,
        String titular,
        Boolean titularEsMio) {
}
