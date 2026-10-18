package pe.castillo.lector;

/**
 * HU-07 criterio 3: el lector es intercambiable por configuración, sin tocar el resto del flujo.
 * Se elige con la variable de entorno LECTOR_VOUCHER (propiedad castillo.lector):
 *   tesseract (por ahora, OCR local)  ·  ia (visión por IA, se agrega después)
 */
public interface LectorVoucher {

    /** Nombre que se guarda en comprobante.lector: "TESSERACT" o "IA". */
    String nombre();

    /**
     * Extrae fecha de pago, número de operación y monto.
     *
     * @param tipoMime image/jpeg, image/png o application/pdf (ya validado)
     * @return lo leído; los datos que no se pudieron leer vienen en null
     * @throws LectorNoDisponibleException si el servicio de lectura no responde
     */
    LecturaVoucher leer(byte[] archivo, String tipoMime) throws LectorNoDisponibleException;
}
