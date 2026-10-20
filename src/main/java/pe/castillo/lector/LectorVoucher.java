package pe.castillo.lector;

/**
 * HU-07 criterio 3: el lector es intercambiable sin tocar el resto del flujo.
 * El que usa el sistema es LectorVoucherAutomatico: IA si hay clave (ANTHROPIC_API_KEY) y Tesseract como respaldo.
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
