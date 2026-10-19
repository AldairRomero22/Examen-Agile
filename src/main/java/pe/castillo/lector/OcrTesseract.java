package pe.castillo.lector;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Llama al programa "tesseract" (OCR) instalado en el equipo o en el contenedor.
 *
 * - Sin dependencias de Java: se ejecuta como proceso aparte (en Windows: instalar Tesseract y, si no
 *   está en el PATH, definir la variable TESSERACT_CMD con la ruta del .exe).
 * - Una lectura a la vez: en Render gratis hay 512 MB y dos lecturas juntas pueden agotar la memoria.
 * - Si el idioma español no está instalado, usa solo inglés (los números y fechas se leen igual).
 *
 * Es una clase sin dependencias de Spring, para poder probarla sola.
 */
public final class OcrTesseract {

    private static final System.Logger LOG = System.getLogger(OcrTesseract.class.getName());

    private final String comando;
    private final long limiteSegundos;
    private volatile String idiomas;
    private static final Semaphore turno = new Semaphore(1, true);   // compartido: una sola lectura a la vez en todo el sistema

    public OcrTesseract(String comando, String idiomas, long limiteSegundos) {
        this.comando = comando;
        this.idiomas = idiomas;
        this.limiteSegundos = limiteSegundos;
    }

    /**
     * Devuelve el texto que Tesseract reconoce en la imagen.
     *
     * @param modoPagina 6 = un bloque de texto (por defecto); 11 = texto suelto, útil si el voucher tiene recuadros
     */
    public String reconocer(BufferedImage imagen, int modoPagina) {
        BufferedImage lista = prepararImagen(imagen);
        boolean conTurno = false;
        Path png = null;
        try {
            conTurno = turno.tryAcquire(limiteSegundos, TimeUnit.SECONDS);
            if (!conTurno) {
                throw new LectorNoDisponibleException("El lector está ocupado con otro voucher");
            }
            png = Files.createTempFile("voucher-", ".png");
            ImageIO.write(lista, "png", png.toFile());
            try {
                return ejecutar(png, modoPagina, idiomas);
            } catch (IdiomaNoInstalado e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Tesseract no tiene el idioma ''{0}'': se usará solo inglés (eng)", idiomas);
                idiomas = "eng";
                return ejecutar(png, modoPagina, "eng");
            }
        } catch (IOException e) {
            throw new LectorNoDisponibleException("No se pudo preparar la imagen para Tesseract", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LectorNoDisponibleException("Lectura interrumpida", e);
        } finally {
            if (conTurno) {
                turno.release();
            }
            borrar(png);
        }
    }

    private String ejecutar(Path imagen, int modoPagina, String idiomasAUsar)
            throws IOException, InterruptedException {
        Path salida = Files.createTempFile("voucher-", ".txt");
        Path errores = Files.createTempFile("voucher-", ".err");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    comando, imagen.toString(), "stdout", "-l", idiomasAUsar, "--psm", String.valueOf(modoPagina));
            pb.environment().put("OMP_THREAD_LIMIT", "1");   // un solo hilo: la CPU de Render gratis es muy pequeña
            pb.redirectOutput(salida.toFile());
            pb.redirectError(errores.toFile());
            Process proceso;
            try {
                proceso = pb.start();
            } catch (IOException e) {
                throw new LectorNoDisponibleException(
                        "No se encontró Tesseract (comando \"" + comando + "\"). "
                        + "Instálalo o define TESSERACT_CMD con la ruta del programa", e);
            }
            if (!proceso.waitFor(limiteSegundos, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new LectorNoDisponibleException("Tesseract tardó más de " + limiteSegundos + " segundos");
            }
            if (proceso.exitValue() != 0) {
                String detalle = Files.readString(errores, StandardCharsets.UTF_8);
                if (!"eng".equals(idiomasAUsar)
                        && (detalle.contains("Failed loading language") || detalle.contains("Error opening data file"))) {
                    throw new IdiomaNoInstalado();
                }
                throw new LectorNoDisponibleException("Tesseract terminó con error: " + primeraLinea(detalle));
            }
            return Files.readString(salida, StandardCharsets.UTF_8);
        } finally {
            borrar(salida);
            borrar(errores);
        }
    }

    // =====================================================================
    // Preparación de la imagen (menos memoria y mejor lectura)
    // =====================================================================

    /**
     * Escala de grises; ancho entre ~900 y 1600 px (las capturas de celular son anchas y las fotos enormes);
     * y si la imagen es oscura (modo noche) se invierte, porque Tesseract lee mejor texto oscuro sobre fondo claro.
     */
    static BufferedImage prepararImagen(BufferedImage origen) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        double escala = 1.0;
        if (ancho > 1600) {
            escala = 1600.0 / ancho;
        } else if (ancho < 900) {
            escala = Math.min(2.0, 1200.0 / ancho);
        }
        if (alto * escala > 4500) {
            escala = 4500.0 / alto;
        }
        int nuevoAncho = Math.max(1, (int) Math.round(ancho * escala));
        int nuevoAlto = Math.max(1, (int) Math.round(alto * escala));

        BufferedImage gris = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gris.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);                      // los PNG con transparencia quedan sobre blanco
            g.fillRect(0, 0, nuevoAncho, nuevoAlto);
            g.drawImage(origen, 0, 0, nuevoAncho, nuevoAlto, null);
        } finally {
            g.dispose();
        }

        byte[] pixeles = ((DataBufferByte) gris.getRaster().getDataBuffer()).getData();
        long suma = 0;
        for (byte p : pixeles) {
            suma += p & 0xFF;
        }
        if (pixeles.length > 0 && suma / pixeles.length < 110) {
            for (int i = 0; i < pixeles.length; i++) {
                pixeles[i] = (byte) (255 - (pixeles[i] & 0xFF));
            }
        }
        return gris;
    }

    // =====================================================================

    private static String primeraLinea(String texto) {
        return texto.lines().filter(l -> !l.isBlank()).findFirst().orElse("sin detalle");
    }

    private static void borrar(Path archivo) {
        if (archivo != null) {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException e) {
                // un temporal que no se borra no afecta al resultado
            }
        }
    }

    /** Señal interna: el idioma pedido no está instalado. */
    private static final class IdiomaNoInstalado extends RuntimeException {
        IdiomaNoInstalado() {
            super(null, null, false, false);
        }
    }
}
