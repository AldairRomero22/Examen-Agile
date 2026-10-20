package pe.castillo.lector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HU-07: lector de vouchers que usa el sistema.
 * Con clave de IA (ANTHROPIC_API_KEY) lee primero con IA, que entiende Yape, Plin y cualquier banco.
 * Si la IA no responde (sin conexión, sin saldo...) o le falta algún dato, completa con Tesseract (OCR local).
 * Sin clave de IA, lee solo con Tesseract.
 */
@Component
public class LectorVoucherAutomatico implements LectorVoucher {

    private static final Logger LOG = LoggerFactory.getLogger(LectorVoucherAutomatico.class);

    /** null si no hay clave de IA. */
    private final ClienteIaVoucher ia;
    private final LectorTesseract tesseract;

    public LectorVoucherAutomatico(@Value("${castillo.tesseract.comando:tesseract}") String comando,
                                   @Value("${castillo.tesseract.idiomas:spa+eng}") String idiomas,
                                   @Value("${castillo.tesseract.limite-segundos:90}") long limiteSegundos,
                                   @Value("${ANTHROPIC_API_KEY:}") String claveIa,
                                   @Value("${castillo.ia.modelo:claude-haiku-5-5}") String modeloIa,
                                   @Value("${castillo.ia.url:https://api.anthropic.com}") String urlIa,
                                   @Value("${castillo.ia.limite-segundos:40}") long limiteIa) {
        this.tesseract = new LectorTesseract(comando, idiomas, limiteSegundos);
        this.ia = claveIa == null || claveIa.isBlank() ? null : new ClienteIaVoucher(claveIa.trim(), modeloIa, urlIa, limiteIa);
    }

    @Override
    public String nombre() {
        return ia != null ? "IA" : "TESSERACT";
    }

    @Override
    public LecturaVoucher leer(byte[] archivo, String tipoMime) {
        LecturaVoucher lectura = LecturaVoucher.VACIA;
        if (ia != null) {
            try {
                lectura = ia.leer(archivo, tipoMime);
                if (lectura.estaCompleta()) {
                    return lectura;
                }
            } catch (LectorNoDisponibleException e) {
                LOG.warn("La IA no pudo leer el voucher, se usa Tesseract: {}", e.getMessage());
            }
        }
        LecturaVoucher porOcr = tesseract.leer(archivo, tipoMime).leidaPor("TESSERACT");
        return lectura.estaVacia() ? porOcr : lectura.completarCon(porOcr);
    }
}
