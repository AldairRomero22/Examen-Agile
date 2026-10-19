package pe.castillo.lector;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el texto de un recibo, una factura o una cuota en los dos datos que pide una obligación:
 * el monto a pagar y la fecha de vencimiento. Son reglas generales (palabras clave), no una receta por empresa.
 *
 * Regla de oro: si no está seguro de un dato, lo deja en null. Un dato vacío lo escribe el asistente;
 * un dato mal leído pasaría por bueno. Además, todo lo leído se muestra editable en la pantalla.
 *
 * Es una clase sin dependencias de Spring, para poder probarla sola.
 */
public final class ExtractorDatosDocumento {

    private static final int CI = Pattern.CASE_INSENSITIVE;

    // ---------- monto ----------
    /** Monto con 2 decimales: 350.00 · 1,280.50 · 1.280,50. No debe ser parte de una fecha (15.10.25) ni de otro número. */
    private static final Pattern MONTO = Pattern.compile(
            "(?<![\\d.,/\\-])(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})(?![\\d/\\-]|[.,]\\d)");

    /** Etiquetas que dicen "esto es lo que hay que pagar", de la más a la menos clara. */
    private static final Pattern ETIQUETA_FUERTE = Pattern.compile(
            "t[o0]tal\\s+a\\s+pagar|[i1]mporte\\s+a\\s+pagar|m[o0]nt[o0]\\s+a\\s+pagar|t[o0]tal\\s+p[o0]r\\s+pagar|saldo\\s+a\\s+pagar"
            + "|pago\\s+t[o0]tal|[i1]mporte\\s+t[o0]tal\\s+a|t[o0]tal\\s+pendiente|t[o0]tal\\s+de\\s+la\\s+cuota|cuota\\s+a\\s+pagar", CI);
    private static final Pattern ETIQUETA_MEDIA = Pattern.compile(
            "[i1]mporte\\s+t[o0]tal|m[o0]nt[o0]\\s+t[o0]tal|t[o0]tal\\s+general|t[o0]tal\\s+venta|t[o0]tal\\s+comprobante|t[o0]tal\\s+documento"
            + "|t[o0]tal\\s+rec[i1]b[o0]|t[o0]tal\\s+factura|m[o0]nt[o0]\\s+de\\s+(?:la\\s+)?cuota|cuota\\s+mensual|[i1]mporte\\s+de\\s+(?:la\\s+)?cuota|valor\\s+cuota", CI);
    private static final Pattern ETIQUETA_DEBIL = Pattern.compile("(?<![a-z])t[o0]tal(?![a-z])", CI);
    /** Líneas con montos que NO son el total a pagar. */
    private static final Pattern NO_ES_EL_TOTAL = Pattern.compile(
            "sub\\s*-?total|igv|i\\.g\\.v|base\\s+imponible|descuento|detracc|retenc|percepc|gravad|exonerad|inafect"
            + "|valor\\s+(?:de\\s+)?venta|saldo\\s+anterior|deuda\\s+anterior|interes|mora|redondeo|icbper|\\bisc\\b"
            + "|seguro|comision|penalidad|capital\\b|amortiz", CI);

    // ---------- fecha de vencimiento ----------
    private static final Pattern ETIQUETA_VENCIMIENTO = Pattern.compile(
            "venc[i1]m[i1]ent[o0]|\\bvencim\\b|[uú]lt[i1]m[o0]\\s+d[ií]a\\s+de\\s+pag[o0]|\\bvence\\b|\\bvenc\\.|fecha\\s+l[i1]mite|l[i1]mite\\s+de\\s+pago|pagar\\s+hasta|pague\\s+hasta"
            + "|fecha\\s+m[a4]x[i1]ma\\s+de\\s+pago|fecha\\s+de\\s+pago\\s+l[i1]mite", CI);

    private ExtractorDatosDocumento() { }

    public static LecturaDocumento extraer(String texto) {
        if (texto == null || texto.isBlank()) {
            return LecturaDocumento.VACIA;
        }
        String[] lineas = ExtractorDatosVoucher.prepararLineas(texto);
        BigDecimal monto = reconciliar(buscarMonto(lineas), MontoEnLetras.leer(texto));
        return new LecturaDocumento(buscarVencimiento(lineas), monto, String.join("\n", lineas));
    }

    /**
     * El monto en letras ("SON: QUINIENTOS SETENTA Y CINCO CON 50/100") es una segunda lectura del mismo dato.
     * Se usa cuando el número leído falta o parece haber perdido dígitos al inicio (75.50 en vez de 575.50);
     * si no, se respeta el número leído.
     */
    static BigDecimal reconciliar(BigDecimal leido, BigDecimal enLetras) {
        if (enLetras == null) {
            return leido;
        }
        if (leido == null) {
            return enLetras;
        }
        boolean perdioDigitos = enLetras.compareTo(leido) != 0 && enLetras.toPlainString().endsWith(leido.toPlainString());
        return perdioDigitos ? enLetras : leido;
    }

    // =====================================================================
    // Fecha de vencimiento
    // =====================================================================

    static LocalDate buscarVencimiento(String[] lineas) {
        for (int i = 0; i < lineas.length; i++) {
            Matcher etiqueta = ETIQUETA_VENCIMIENTO.matcher(lineas[i]);
            if (!etiqueta.find()) {
                continue;
            }
            // 1) la fecha va después de la etiqueta, en la misma línea
            LocalDate fecha = ExtractorDatosVoucher.primeraFechaDe(lineas[i].substring(etiqueta.end()));
            if (fecha != null) {
                return fecha;
            }
            // 2) la etiqueta está sola y la fecha en la línea de abajo (recibos con la etiqueta encima del valor)
            if (i + 1 < lineas.length) {
                fecha = ExtractorDatosVoucher.primeraFechaDe(lineas[i + 1]);
                if (fecha != null) {
                    return fecha;
                }
            }
        }
        return null;
    }

    // =====================================================================
    // Monto a pagar
    // =====================================================================

    static BigDecimal buscarMonto(String[] lineas) {
        BigDecimal mejor = null;
        int mejorPuntaje = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (NO_ES_EL_TOTAL.matcher(linea).find()) {
                continue;
            }
            int puntaje = puntajeDeEtiqueta(linea);
            if (puntaje == 0) {
                continue;
            }
            // el monto va después de la etiqueta en la misma línea; si no, en la línea de abajo
            BigDecimal valor = primerMontoDespuesDeEtiqueta(linea);
            if (valor == null && i + 1 < lineas.length && !NO_ES_EL_TOTAL.matcher(lineas[i + 1]).find()
                    && puntajeDeEtiqueta(lineas[i + 1]) == 0) {
                valor = primerMonto(lineas[i + 1]);
            }
            if (valor == null) {
                continue;
            }
            // gana la etiqueta más clara; a igual claridad, la primera si es fuerte, y la de mayor monto si es débil
            boolean mejora = puntaje > mejorPuntaje
                    || (puntaje == mejorPuntaje && puntaje == 1 && valor.compareTo(mejor) > 0);
            if (mejora) {
                mejor = valor;
                mejorPuntaje = puntaje;
            }
        }
        return mejor;
    }

    /** 3 = etiqueta fuerte ("Total a pagar"), 2 = media ("Importe total"), 1 = débil ("Total"), 0 = ninguna. */
    private static int puntajeDeEtiqueta(String linea) {
        if (ETIQUETA_FUERTE.matcher(linea).find()) {
            return 3;
        }
        if (ETIQUETA_MEDIA.matcher(linea).find()) {
            return 2;
        }
        return ETIQUETA_DEBIL.matcher(linea).find() ? 1 : 0;
    }

    private static BigDecimal primerMontoDespuesDeEtiqueta(String linea) {
        int desde = 0;
        for (Pattern p : new Pattern[] {ETIQUETA_FUERTE, ETIQUETA_MEDIA, ETIQUETA_DEBIL}) {
            Matcher m = p.matcher(linea);
            if (m.find()) {
                desde = m.end();
                break;
            }
        }
        return primerMonto(linea.substring(desde));
    }

    private static BigDecimal primerMonto(String texto) {
        Matcher m = MONTO.matcher(texto);
        while (m.find()) {
            BigDecimal valor = ExtractorDatosVoucher.aMonto(m.group(1));
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    /** Sin tildes ni mayúsculas ni signos: para comparar nombres. */
    static String simplificar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().replaceAll("[^A-Z0-9 ]", " ").replaceAll(" {2,}", " ").trim();
    }
}
