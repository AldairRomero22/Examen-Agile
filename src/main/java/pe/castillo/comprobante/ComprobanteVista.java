package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-09: todo lo que muestra "Ver comprobante" (solo lectura, para asistente y gerente).
 * Los datos "enviados" son los verificados; los marcados "manual" son los que escribió el asistente.
 */
public record ComprobanteVista(
        Long id,
        Long obligacionId,
        String nombreArchivo,
        String tipoMime,
        int tamanoBytes,
        String lector,
        LocalDate fecha,
        boolean fechaManual,
        String nroOperacion,
        boolean nroOperacionManual,
        BigDecimal monto,
        boolean montoManual,
        BigDecimal montoObligacion,
        String insignia,
        String insigniaEtiqueta,
        String subidoPor,
        OffsetDateTime subidoEn,
        String resultado,
        String resultadoEtiqueta,
        String verificadoPor,
        OffsetDateTime verificadoEn,
        List<ReglasVerificacion.Regla> reglas) {
}
