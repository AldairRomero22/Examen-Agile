package pe.castillo.lector;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el monto escrito en letras que traen muchos recibos peruanos:
 * "SON: QUINIENTOS SETENTA Y CINCO CON 50/100 NUEVOS SOLES" = 575.50.
 * Sirve para confirmar (o corregir) el monto leído en números cuando el OCR se come un dígito.
 * Solo devuelve un valor si TODAS las palabras se reconocen; si hay una dudosa, devuelve null.
 * Es una clase sin dependencias de Spring, para poder probarla sola.
 */
public final class MontoEnLetras {

    private static final Pattern FRASE = Pattern.compile("\\bSON ((?:[A-Z]+ ){1,12}?)CON (\\d{1,2}) ?(?:/ ?)?100\\b");

    private static final Map<String, Integer> PALABRAS = Map.ofEntries(
            Map.entry("UN", 1), Map.entry("UNO", 1), Map.entry("UNA", 1), Map.entry("DOS", 2), Map.entry("TRES", 3),
            Map.entry("CUATRO", 4), Map.entry("CINCO", 5), Map.entry("SEIS", 6), Map.entry("SIETE", 7),
            Map.entry("OCHO", 8), Map.entry("NUEVE", 9), Map.entry("DIEZ", 10), Map.entry("ONCE", 11),
            Map.entry("DOCE", 12), Map.entry("TRECE", 13), Map.entry("CATORCE", 14), Map.entry("QUINCE", 15),
            Map.entry("DIECISEIS", 16), Map.entry("DIECISIETE", 17), Map.entry("DIECIOCHO", 18),
            Map.entry("DIECINUEVE", 19), Map.entry("VEINTE", 20), Map.entry("VEINTIUN", 21), Map.entry("VEINTIUNO", 21),
            Map.entry("VEINTIUNA", 21), Map.entry("VEINTIDOS", 22), Map.entry("VEINTITRES", 23),
            Map.entry("VEINTICUATRO", 24), Map.entry("VEINTICINCO", 25), Map.entry("VEINTISEIS", 26),
            Map.entry("VEINTISIETE", 27), Map.entry("VEINTIOCHO", 28), Map.entry("VEINTINUEVE", 29),
            Map.entry("TREINTA", 30), Map.entry("CUARENTA", 40), Map.entry("CINCUENTA", 50), Map.entry("SESENTA", 60),
            Map.entry("SETENTA", 70), Map.entry("OCHENTA", 80), Map.entry("NOVENTA", 90), Map.entry("CIEN", 100),
            Map.entry("CIENTO", 100), Map.entry("DOSCIENTOS", 200), Map.entry("TRESCIENTOS", 300),
            Map.entry("CUATROCIENTOS", 400), Map.entry("QUINIENTOS", 500), Map.entry("SEISCIENTOS", 600),
            Map.entry("SETECIENTOS", 700), Map.entry("OCHOCIENTOS", 800), Map.entry("NOVECIENTOS", 900));

    private MontoEnLetras() { }

    /** @return el monto, o null si el texto no trae la frase "SON ... CON NN/100" o no se entiende por completo */
    public static BigDecimal leer(String texto) {
        if (texto == null) {
            return null;
        }
        String plano = ExtractorDatosDocumento.simplificar(texto.replace('\n', ' ')) + " ";
        Matcher m = FRASE.matcher(plano);
        if (!m.find()) {
            return null;
        }
        long total = 0;
        long actual = 0;
        for (String palabra : m.group(1).trim().split(" ")) {
            if (palabra.equals("Y")) {
                continue;
            }
            if (palabra.equals("MIL")) {
                total += (actual == 0 ? 1 : actual) * 1_000L;
                actual = 0;
            } else if (palabra.equals("MILLON") || palabra.equals("MILLONES")) {
                total += (actual == 0 ? 1 : actual) * 1_000_000L;
                actual = 0;
            } else if (PALABRAS.containsKey(palabra)) {
                actual += PALABRAS.get(palabra);
            } else {
                return null;   // palabra no reconocida: mejor no usar este dato
            }
        }
        total += actual;
        if (total <= 0 && Integer.parseInt(m.group(2)) == 0) {
            return null;
        }
        int centavos = Integer.parseInt(m.group(2));
        if (centavos > 99) {
            return null;
        }
        return BigDecimal.valueOf(total).add(BigDecimal.valueOf(centavos, 2));
    }
}
