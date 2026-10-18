package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-07 criterio 6: lo que manda la pantalla al pulsar "Enviar comprobante".
 * Solo se completan los campos que el lector dejó vacíos; si llega un valor distinto
 * de lo que el lector leyó, el servidor lo rechaza.
 */
public record EnvioForm(LocalDate fecha, String nroOperacion, BigDecimal monto) {
}
