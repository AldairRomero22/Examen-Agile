package pe.castillo.comprobante;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Ayudas para el archivo subido. Sin dependencias de Spring, para poder probarlas solas. */
public final class ArchivoVoucher {

    private ArchivoVoucher() { }

    /**
     * HU-07 criterio 2: JPG, PNG o PDF según los primeros bytes del archivo (no por el nombre ni por lo que
     * diga el navegador, que se pueden falsificar). Devuelve null si es otra cosa.
     */
    public static String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    /** Sin carpetas ni caracteres de control; máximo 255 caracteres. */
    public static String nombreSeguro(String original) {
        String nombre = original == null ? "" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (nombre.isEmpty()) {
            nombre = "comprobante";
        }
        return nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre;
    }

    /** HU-08 regla 4: la huella digital del contenido (64 caracteres), que el sistema calcula solo. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
