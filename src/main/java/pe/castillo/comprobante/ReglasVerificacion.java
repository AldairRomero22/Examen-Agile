package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * HU-08 criterio 1: las cuatro reglas de verificación. Sin dependencias de Spring, para poder probarla sola.
 *
 *  1. MONTO      el monto coincide con el de la obligación
 *  2. FECHA      la fecha de pago no es posterior a hoy
 *  3. OPERACION  el número de operación no se usó antes en otro pago
 *  4. ARCHIVO    el archivo (su huella SHA-256) no es el mismo que ya se usó en otro pago
 */
public final class ReglasVerificacion {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Resultado de una regla, listo para mostrar: lo esperado y lo recibido, y el mensaje si falló. */
    public record Regla(String codigo, String titulo, boolean cumple, String esperado, String recibido, String mensaje) { }

    private ReglasVerificacion() { }

    /** Calcula las cuatro reglas. "operacionUsada" y "archivoUsado" vienen de consultar la base. */
    public static List<Regla> evaluar(BigDecimal montoObligacion, BigDecimal montoEnviado, LocalDate fechaEnviada,
                                      String nroOperacion, boolean operacionUsada, boolean archivoUsado, LocalDate hoy) {
        return construir(montoObligacion.compareTo(montoEnviado) == 0,
                !fechaEnviada.isAfter(hoy),
                !operacionUsada,
                !archivoUsado,
                montoObligacion, montoEnviado, fechaEnviada, nroOperacion, hoy);
    }

    /** Arma las cuatro reglas a partir de si cumplieron o no (también sirve para mostrar una verificación ya guardada). */
    public static List<Regla> construir(boolean montoOk, boolean fechaOk, boolean operacionOk, boolean archivoOk,
                                        BigDecimal montoObligacion, BigDecimal montoEnviado, LocalDate fechaEnviada,
                                        String nroOperacion, LocalDate hoy) {
        String esperadoMonto = soles(montoObligacion);
        String recibidoMonto = soles(montoEnviado);
        String yaUsado = "Este comprobante ya se usó en otro pago";
        return List.of(
                new Regla("MONTO", "Monto", montoOk, esperadoMonto, recibidoMonto,
                        montoOk ? null : "Monto: esperado " + esperadoMonto + ", recibido " + recibidoMonto),
                new Regla("FECHA", "Fecha de pago", fechaOk,
                        "No posterior a hoy (" + DIA.format(hoy) + ")", DIA.format(fechaEnviada),
                        fechaOk ? null : "Fecha de pago: no puede ser posterior a hoy (hoy es " + DIA.format(hoy)
                                + ", recibido " + DIA.format(fechaEnviada) + ")"),
                new Regla("OPERACION", "Número de operación", operacionOk,
                        "No usado antes en otro pago", nroOperacion,
                        operacionOk ? null : "Número de operación " + nroOperacion + ": " + yaUsado),
                new Regla("ARCHIVO", "Archivo", archivoOk,
                        "No usado antes en otro pago", archivoOk ? "Archivo nuevo" : "Archivo repetido",
                        archivoOk ? null : "Archivo: " + yaUsado));
    }

    /** 1280 → "S/ 1,280.00" */
    public static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }

    public static boolean todasCumplen(List<Regla> reglas) {
        return reglas.stream().allMatch(Regla::cumple);
    }

    /** Mensajes de las reglas que fallaron, para guardarlos en el registro de la verificación. */
    public static String detalleDeFallas(List<Regla> reglas) {
        String texto = String.join(" · ", reglas.stream().filter(r -> !r.cumple()).map(Regla::mensaje).toList());
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
