package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-07 criterio 3: respuesta tras subir el archivo. Un campo en null es un dato que el lector
 * no pudo leer: la pantalla deja ese campo para escribirlo a mano (marcado "escrito a mano").
 */
public record ComprobanteLeido(
        Long comprobanteId,
        String nombreArchivo,
        String lector,
        BigDecimal montoObligacion,
        LocalDate fecha,
        String nroOperacion,
        BigDecimal monto) {
}
