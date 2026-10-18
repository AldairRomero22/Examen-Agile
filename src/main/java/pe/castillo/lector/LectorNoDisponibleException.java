package pe.castillo.lector;

/**
 * HU-07 criterio 5: el servicio de lectura no responde (Tesseract no instalado, tiempo agotado,
 * la IA caída...). La pantalla muestra "No se pudo leer el voucher, intenta de nuevo".
 */
public class LectorNoDisponibleException extends RuntimeException {

    public LectorNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public LectorNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
