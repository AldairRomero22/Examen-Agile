package pe.castillo.lector;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el texto que devolvió el OCR (o el texto de un PDF) en los tres datos del voucher:
 * fecha de pago, número de operación y monto.
 *
 * Regla de oro: si no está seguro de un dato, lo deja en null. Un campo vacío lo completa el asistente
 * a mano (queda marcado "escrito a mano"); un dato mal leído pasaría como "leído" sin que nadie lo corrija.
 *
 * Es una clase sin dependencias de Spring, para poder probarla sola.
 */
public final class ExtractorDatosVoucher {

    private static final int CI = Pattern.CASE_INSENSITIVE;

    // ---------- fecha ----------
    private static final Pattern FECHA_NUMERICA = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*([/\\-.])\\s*(\\d{1,2})\\s*[/\\-.]\\s*(\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern FECHA_ISO = Pattern.compile(
            "(?<!\\d)(\\d{4})\\s*[/\\-.]\\s*(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern FECHA_CON_MES = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[\\s\\-/]*(?:de\\s+)?([a-z0]{3,10})\\.?,?[\\s\\-/]*(?:de\\s+|del\\s+)?(\\d{4})(?!\\d)", CI);
    private static final Pattern FECHA_MES_PRIMERO = Pattern.compile(
            "(?<![a-z])([a-z0]{3,10})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)", CI);
    private static final Pattern NO_ES_FECHA_DE_PAGO = Pattern.compile("vencim|vence|caduc|nacim", CI);

    // ---------- monto ----------
    /** Número con miles opcionales: 1,280.00 · 1.280,00 · 300.00 · 85 */
    private static final String NUM = "(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    /** "S/ 1,280.00", "S/.50", "$ 50"; también "5/" cuando el OCR confunde la S. No debe ser parte de una fecha. */
    private static final Pattern MONTO_CON_SIMBOLO = Pattern.compile(
            "(?<![\\dA-Za-z/])(?:S\\s?/\\.?|\\$\\s?/?\\.?|5/\\.?|PEN)\\s*" + NUM + "(?!\\d)(?!\\s*/\\s*\\d)", CI);
    private static final Pattern MONTO_CON_SUFIJO = Pattern.compile(
            "(?<![\\d.,/])" + NUM + "\\s*(?:PEN|soles?)\\b", CI);
    private static final Pattern NUMERO_LIBRE = Pattern.compile(
            "(?<![\\d.,/])" + NUM + "(?!\\d)(?!\\s*/\\s*\\d)");
    private static final Pattern ETIQUETA_MONTO = Pattern.compile(
            "monto|importe|total|pagaste|yapeaste|plineaste|transferiste|enviaste|pago realizado|valor", CI);
    private static final Pattern NO_ES_EL_MONTO = Pattern.compile(
            "comisi|itf|saldo|disponible|limite|cambio|cargo|interes|propina|descuento", CI);

    // ---------- número de operación ----------
    private static final Pattern PALABRA_OPERACION = Pattern.compile(
            "operaci.{1,2}n|transacci.{1,2}n|referencia|constancia|movimiento", CI);
    private static final Pattern ANTES_ESPECIFICO = Pattern.compile(
            "\\b(nro|numero|n[°º]|no|codigo|cod|n)\\.?\\s*(de\\s*)?$", CI);
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9])([A-Za-z0-9][A-Za-z0-9\\-]{4,24})(?![A-Za-z0-9])");
    private static final Pattern SOLO_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9\\- ]{4,28}");

    private ExtractorDatosVoucher() { }

    public static LecturaVoucher extraer(String texto) {
        if (texto == null || texto.isBlank()) {
            return LecturaVoucher.VACIA;
        }
        String[] lineas = prepararLineas(texto);
        return new LecturaVoucher(buscarFecha(lineas), buscarOperacion(lineas), buscarMonto(lineas));
    }

    // =====================================================================
    // Preparación del texto
    // =====================================================================

    /** Sin tildes (el OCR las confunde), espacios normalizados y sin líneas vacías. */
    static String[] prepararLineas(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replace(' ', ' ').replace('\t', ' ').lines()
                .map(l -> l.replaceAll(" {2,}", " ").trim())
                .filter(l -> !l.isEmpty())
                .toArray(String[]::new);
    }

    private static boolean contiene(Pattern patron, String linea) {
        return patron.matcher(linea).find();
    }

    // =====================================================================
    // Fecha de pago
    // =====================================================================

    static LocalDate buscarFecha(String[] lineas) {
        LocalDate mejor = null;
        int mejorPuntaje = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (contiene(NO_ES_FECHA_DE_PAGO, linea)) {
                continue;
            }
            int puntaje = 1;
            if (linea.toLowerCase().contains("fecha")) {
                puntaje = 3;
            } else if (i > 0 && lineas[i - 1].toLowerCase().contains("fecha")) {
                puntaje = 2;
            }
            if (puntaje <= mejorPuntaje) {
                continue;   // ya hay una igual o mejor, y se prefiere la primera
            }
            LocalDate fecha = primeraFechaDe(linea);
            if (fecha != null) {
                mejor = fecha;
                mejorPuntaje = puntaje;
            }
        }
        return mejor;
    }

    private static LocalDate primeraFechaDe(String linea) {
        Matcher m = FECHA_NUMERICA.matcher(linea);
        while (m.find()) {
            boolean anioCorto = m.group(4).length() == 2;
            if (anioCorto && ".".equals(m.group(2))) {
                continue;   // "12.30.55" no es una fecha, es otra cosa
            }
            LocalDate f = fecha(m.group(4), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(1)));
            if (f != null) {
                return f;
            }
        }
        m = FECHA_ISO.matcher(linea);
        while (m.find()) {
            LocalDate f = fecha(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            if (f != null) {
                return f;
            }
        }
        m = FECHA_CON_MES.matcher(linea);
        while (m.find()) {
            int mes = numeroDeMes(m.group(2));
            if (mes > 0) {
                LocalDate f = fecha(m.group(3), mes, Integer.parseInt(m.group(1)));
                if (f != null) {
                    return f;
                }
            }
        }
        m = FECHA_MES_PRIMERO.matcher(linea);
        while (m.find()) {
            int mes = numeroDeMes(m.group(1));
            if (mes > 0) {
                LocalDate f = fecha(m.group(3), mes, Integer.parseInt(m.group(2)));
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static LocalDate fecha(String anioTexto, int mes, int dia) {
        try {
            int anio = Integer.parseInt(anioTexto);
            if (anioTexto.length() == 2) {
                anio += 2000;
            }
            if (anio < 2000 || anio > 2100) {
                return null;
            }
            return LocalDate.of(anio, mes, dia);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /** "oct", "octubre", "Oct.", "sept", "set" → 10, 10, 10, 9, 9. 0 si no es un mes. */
    static int numeroDeMes(String palabra) {
        String p = palabra.toLowerCase().replace('0', 'o');
        if (p.length() < 3) {
            return 0;
        }
        switch (p.substring(0, 3)) {
            case "ene": case "jan": return 1;
            case "feb": return 2;
            case "mar": return 3;
            case "abr": case "apr": return 4;
            case "may": return 5;
            case "jun": return 6;
            case "jul": return 7;
            case "ago": case "aug": return 8;
            case "set": case "sep": return 9;
            case "oct": return 10;
            case "nov": return 11;
            case "dic": case "dec": return 12;
            default: return 0;
        }
    }

    // =====================================================================
    // Monto
    // =====================================================================

    static BigDecimal buscarMonto(String[] lineas) {
        BigDecimal mejor = null;
        int mejorPuntaje = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (contiene(NO_ES_EL_MONTO, linea)) {
                continue;
            }
            boolean conEtiqueta = contiene(ETIQUETA_MONTO, linea)
                    || (i > 0 && contiene(ETIQUETA_MONTO, lineas[i - 1]) && !contiene(NO_ES_EL_MONTO, lineas[i - 1]));

            // 1) "S/ 1,280.00" o "1,280.00 PEN"
            BigDecimal conSimbolo = primerMonto(MONTO_CON_SIMBOLO, linea);
            if (conSimbolo == null) {
                conSimbolo = primerMonto(MONTO_CON_SUFIJO, linea);
            }
            if (conSimbolo != null) {
                int puntaje = conEtiqueta ? 4 : 2;
                if (puntaje > mejorPuntaje) {
                    mejor = conSimbolo;
                    mejorPuntaje = puntaje;
                }
                continue;
            }
            // 2) sin símbolo, pero en una línea con "Monto", "Importe", "Total"... (con decimales)
            if (contiene(ETIQUETA_MONTO, linea) && 3 > mejorPuntaje) {
                Matcher m = NUMERO_LIBRE.matcher(linea);
                while (m.find()) {
                    boolean conDecimales = m.group(1).matches(".*[.,]\\d{1,2}$");
                    BigDecimal valor = conDecimales ? aMonto(m.group(1)) : null;
                    if (valor != null) {
                        mejor = valor;
                        mejorPuntaje = 3;
                        break;
                    }
                }
            }
        }
        return mejor;
    }

    private static BigDecimal primerMonto(Pattern patron, String linea) {
        Matcher m = patron.matcher(linea);
        while (m.find()) {
            BigDecimal valor = aMonto(m.group(1));
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    /** "1,280.00" → 1280.00 · "1.280,50" → 1280.50 · "1,280" → 1280.00 · "85" → 85.00. null si no es razonable. */
    static BigDecimal aMonto(String token) {
        int comas = contar(token, ',');
        int puntos = contar(token, '.');
        int ultimo = Math.max(token.lastIndexOf(','), token.lastIndexOf('.'));
        String entero = token;
        String decimales = "";
        if (ultimo >= 0) {
            String tras = token.substring(ultimo + 1);
            boolean esDecimal = (comas > 0 && puntos > 0) || (comas + puntos == 1 && tras.length() <= 2);
            if (esDecimal) {
                entero = token.substring(0, ultimo);
                decimales = tras;
            }
        }
        entero = entero.replaceAll("[.,]", "");
        if (entero.isEmpty() || entero.length() > 9) {
            return null;
        }
        BigDecimal valor = new BigDecimal(entero + (decimales.isEmpty() ? "" : "." + decimales)).setScale(2);
        return valor.signum() > 0 ? valor : null;
    }

    private static int contar(String texto, char c) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    // =====================================================================
    // Número de operación
    // =====================================================================

    static String buscarOperacion(String[] lineas) {
        String mejor = null;
        int mejorPuntaje = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            Matcher palabra = PALABRA_OPERACION.matcher(linea);
            while (palabra.find()) {
                String antes = linea.substring(0, palabra.start());
                if (antes.toLowerCase().contains("fecha") || antes.toLowerCase().contains("hora")) {
                    continue;   // "Fecha de operación": la palabra pertenece a una fecha, no a un número
                }
                int puntaje = ANTES_ESPECIFICO.matcher(antes).find() ? 3 : 2;
                if (puntaje <= mejorPuntaje) {
                    continue;
                }
                String valor = primerTokenValido(linea.substring(palabra.end()));
                if (valor == null && i + 1 < lineas.length
                        && SOLO_TOKEN.matcher(lineas[i + 1]).matches()) {
                    // el valor está solo, en la línea de abajo
                    valor = primerTokenValido(lineas[i + 1]);
                }
                if (valor != null) {
                    mejor = valor;
                    mejorPuntaje = puntaje;
                }
            }
        }
        return mejor;
    }

    private static String primerTokenValido(String texto) {
        Matcher m = TOKEN.matcher(texto);
        while (m.find()) {
            String token = corregirDigitos(m.group(1).toUpperCase());
            long digitos = token.chars().filter(Character::isDigit).count();
            if (digitos >= 4 && token.length() >= 5) {
                return token;
            }
        }
        return null;
    }

    /** Si todo es número salvo letras que el OCR suele confundir (O, I, L), las pasa a dígitos. */
    private static String corregirDigitos(String token) {
        String letras = token.replaceAll("[0-9\\-]", "");
        if (!letras.isEmpty() && letras.matches("[OIL]+")) {
            return token.replace('O', '0').replace('I', '1').replace('L', '1');
        }
        return token;
    }
}
