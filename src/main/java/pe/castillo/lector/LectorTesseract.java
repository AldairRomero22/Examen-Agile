package pe.castillo.lector;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Lector con OCR local (Tesseract). Es el lector por defecto mientras no esté el de IA.
 *
 * - Imagen (JPG/PNG): se pasa por Tesseract. Si faltan datos, se prueba un segundo modo de lectura.
 * - PDF: si trae texto (casi todos los de bancos), se lee ese texto, que es exacto. Si no (PDF escaneado),
 *   se dibuja la primera página y se aplica OCR.
 */
@Component
@ConditionalOnProperty(prefix = "castillo", name = "lector", havingValue = "tesseract", matchIfMissing = true)
public class LectorTesseract implements LectorVoucher {

    /** Modos de página de Tesseract: bloque de texto, y texto suelto (para vouchers con recuadros). */
    private static final int MODO_BLOQUE = 6;
    private static final int MODO_SUELTO = 11;

    private final OcrTesseract ocr;

    public LectorTesseract(@Value("${castillo.tesseract.comando:tesseract}") String comando,
                           @Value("${castillo.tesseract.idiomas:spa+eng}") String idiomas,
                           @Value("${castillo.tesseract.limite-segundos:90}") long limiteSegundos) {
        this.ocr = new OcrTesseract(comando, idiomas, limiteSegundos);
    }

    @Override
    public String nombre() {
        return "TESSERACT";
    }

    @Override
    public LecturaVoucher leer(byte[] archivo, String tipoMime) {
        if ("application/pdf".equals(tipoMime)) {
            return leerPdf(archivo);
        }
        BufferedImage imagen = decodificar(archivo);
        if (imagen == null) {
            return LecturaVoucher.VACIA;   // no es una imagen válida: "No parece un voucher de pago"
        }
        return leerImagen(imagen);
    }

    private LecturaVoucher leerImagen(BufferedImage imagen) {
        LecturaVoucher lectura = ExtractorDatosVoucher.extraer(ocr.reconocer(imagen, MODO_BLOQUE));
        if (!lectura.estaCompleta()) {
            lectura = lectura.completarCon(ExtractorDatosVoucher.extraer(ocr.reconocer(imagen, MODO_SUELTO)));
        }
        return lectura;
    }

    private LecturaVoucher leerPdf(byte[] archivo) {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            PDFTextStripper lector = new PDFTextStripper();
            lector.setSortByPosition(true);
            lector.setStartPage(1);
            lector.setEndPage(Math.min(2, documento.getNumberOfPages()));
            LecturaVoucher lectura = ExtractorDatosVoucher.extraer(lector.getText(documento));
            if (lectura.estaCompleta() || documento.getNumberOfPages() == 0) {
                return lectura;
            }
            // PDF escaneado o con texto incompleto: se dibuja la primera página (ancho ~1600 px) y se lee con OCR
            float anchoEnPuntos = Math.max(1f, documento.getPage(0).getMediaBox().getWidth());
            float dpi = Math.max(50f, Math.min(170f, 1600f / anchoEnPuntos * 72f));
            BufferedImage pagina = new PDFRenderer(documento).renderImageWithDPI(0, dpi, ImageType.GRAY);
            return lectura.completarCon(leerImagen(pagina));
        } catch (IOException e) {
            return LecturaVoucher.VACIA;   // PDF dañado o con contraseña: no se puede leer
        }
    }

    /**
     * Decodifica la imagen cuidando la memoria: una foto de 48 megapíxeles ocuparía casi 200 MB,
     * así que si es muy grande se lee reducida (se salta cada N-ésimo píxel).
     */
    private static BufferedImage decodificar(byte[] archivo) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(archivo))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > 400_000_000L) {
                    return null;   // dimensiones absurdas
                }
                ImageReadParam parametros = lector.getDefaultReadParam();
                int salto = (int) Math.ceil(Math.sqrt(pixeles / 12_000_000.0));
                if (salto > 1) {
                    parametros.setSourceSubsampling(salto, salto, 0, 0);
                }
                return lector.read(0, parametros);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
