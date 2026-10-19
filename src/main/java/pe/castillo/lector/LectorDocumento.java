package pe.castillo.lector;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lee un recibo, factura o cuota (JPG, PNG o PDF) y devuelve monto, fecha de vencimiento y el texto completo.
 * Usa el mismo Tesseract y PDFBox del lector de vouchers, con reglas generales (sin plantillas por empresa).
 */
@Component
public class LectorDocumento {

    private static final int MODO_BLOQUE = 6;
    private static final int MODO_SUELTO = 11;

    private static final System.Logger LOG = System.getLogger(LectorDocumento.class.getName());

    private final OcrTesseract ocr;
    /** null si no hay clave de IA: entonces se lee solo con Tesseract. */
    private final ClienteIaDocumento ia;

    public LectorDocumento(@Value("${castillo.tesseract.comando:tesseract}") String comando,
                           @Value("${castillo.tesseract.idiomas:spa+eng}") String idiomas,
                           @Value("${castillo.tesseract.limite-segundos:90}") long limiteSegundos,
                           @Value("${ANTHROPIC_API_KEY:}") String claveIa,
                           @Value("${castillo.ia.modelo:claude-haiku-5-5}") String modeloIa,
                           @Value("${castillo.ia.url:https://api.anthropic.com}") String urlIa,
                           @Value("${castillo.ia.limite-segundos:40}") long limiteIa) {
        this.ocr = new OcrTesseract(comando, idiomas, limiteSegundos);
        this.ia = claveIa == null || claveIa.isBlank() ? null : new ClienteIaDocumento(claveIa.trim(), modeloIa, urlIa, limiteIa);
    }

    /** Con clave de IA, la IA lee primero; si falla (sin saldo, sin conexión...), respalda Tesseract. */
    public LecturaDocumento leer(byte[] archivo, String tipoMime) {
        if (ia != null) {
            try {
                return ia.leer(archivo, tipoMime);
            } catch (LectorNoDisponibleException e) {
                LOG.log(System.Logger.Level.WARNING, "La IA no pudo leer el documento, se usa Tesseract: " + e.getMessage());
            }
        }
        return leerConTesseract(archivo, tipoMime);
    }

    private LecturaDocumento leerConTesseract(byte[] archivo, String tipoMime) {
        if ("application/pdf".equals(tipoMime)) {
            return leerPdf(archivo);
        }
        BufferedImage imagen = LectorTesseract.decodificar(archivo);
        return imagen == null ? LecturaDocumento.VACIA : leerImagen(imagen);
    }

    private LecturaDocumento leerImagen(BufferedImage imagen) {
        LecturaDocumento lectura = ExtractorDatosDocumento.extraer(ocr.reconocer(imagen, MODO_BLOQUE));
        if (!lectura.tieneMontoYFecha()) {
            lectura = lectura.completarCon(ExtractorDatosDocumento.extraer(ocr.reconocer(imagen, MODO_SUELTO)));
        }
        return lectura;
    }

    private LecturaDocumento leerPdf(byte[] archivo) {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            PDFTextStripper lector = new PDFTextStripper();
            lector.setSortByPosition(true);
            lector.setStartPage(1);
            lector.setEndPage(Math.min(3, documento.getNumberOfPages()));
            LecturaDocumento lectura = ExtractorDatosDocumento.extraer(lector.getText(documento));
            if (lectura.tieneMontoYFecha() || documento.getNumberOfPages() == 0) {
                return lectura;
            }
            // PDF escaneado o con texto incompleto: se dibuja la primera página y se lee con OCR
            float anchoEnPuntos = Math.max(1f, documento.getPage(0).getMediaBox().getWidth());
            float dpi = Math.max(50f, Math.min(170f, 1600f / anchoEnPuntos * 72f));
            BufferedImage pagina = new PDFRenderer(documento).renderImageWithDPI(0, dpi, ImageType.GRAY);
            return lectura.completarCon(leerImagen(pagina));
        } catch (IOException e) {
            return LecturaDocumento.VACIA;
        }
    }
}
