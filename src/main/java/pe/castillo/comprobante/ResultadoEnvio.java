package pe.castillo.comprobante;

import java.util.List;

/** HU-08 criterios 2 y 3: resultado de verificar el comprobante, con una marca por cada regla. */
public record ResultadoEnvio(
        Long comprobanteId,
        Long obligacionId,
        String resultado,           // PAGADO u OBSERVADO
        String resultadoEtiqueta,   // "Pagado" u "Observado"
        String insignia,            // AUTOMATICA o MANUAL
        String insigniaEtiqueta,    // "Verificado automáticamente" o "Verificado manualmente"
        List<ReglasVerificacion.Regla> reglas) {
}
