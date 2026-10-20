package pe.castillo.config;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Reglas de validación de las obligaciones (y del nombre de a quién se paga).
 * Cada método devuelve el mensaje de error, o null si el valor es válido.
 * Las mismas reglas están en /js/reglas.js para avisar en pantalla antes de enviar.
 */
public final class Reglas {

    private static final Pattern NOMBRE = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} .,&'’()/\\-ªº°]*");
    private static final Pattern BANCO = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} .,&'’()/\\-]*");
    private static final Pattern CONCEPTO = Pattern.compile("[^\\p{Cntrl}]+");
    private static final Pattern LETRA = Pattern.compile(".*\\p{L}.*", Pattern.DOTALL);
    private static final Pattern CUENTA = Pattern.compile("\\d{10,20}");
    private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Reglas() { }

    /** Nombre o razón social: mínimo 3 caracteres, con al menos una letra, sin símbolos raros. */
    public static String nombre(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.length() < 3) {
            return "Escribe el nombre completo (mínimo 3 caracteres)";
        }
        if (!LETRA.matcher(v).matches()) {
            return "El nombre debe tener letras, no solo números o símbolos";
        }
        if (!NOMBRE.matcher(v).matches()) {
            return "El nombre tiene caracteres no permitidos";
        }
        return null;
    }

    /** DNI de 8 dígitos o RUC de 11 con prefijo y dígito verificador correctos. */
    public static String documento(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (!v.matches("\\d{8}|\\d{11}")) {
            return "Debe tener 8 dígitos (DNI) u 11 (RUC), solo números";
        }
        if (v.length() == 8) {
            return v.chars().distinct().count() == 1 ? "El DNI no es válido" : null;
        }
        return rucValido(v) ? null : "El RUC no es válido: revisa los números (debe empezar con 10, 15, 16, 17 o 20)";
    }

    static boolean rucValido(String ruc) {
        if (!ruc.matches("\\d{11}")) {
            return false;
        }
        String prefijo = ruc.substring(0, 2);
        if (!(prefijo.equals("10") || prefijo.equals("15") || prefijo.equals("16")
                || prefijo.equals("17") || prefijo.equals("20"))) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += (ruc.charAt(i) - '0') * PESOS_RUC[i];
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        return digito == ruc.charAt(10) - '0';
    }

    public static String banco(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.length() < 2) {
            return "Elige el banco";
        }
        if (!LETRA.matcher(v).matches() || !BANCO.matcher(v).matches()) {
            return "Escribe el nombre del banco con letras";
        }
        return null;
    }

    /** Cuenta o CCI: solo dígitos, entre 10 y 20. */
    public static String cuenta(String valor) {
        String v = valor == null ? "" : valor.trim();
        return CUENTA.matcher(v).matches() ? null
                : "Solo números, de 10 a 20 dígitos (sin guiones ni espacios)";
    }

    public static String concepto(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.length() < 3) {
            return "Describe el concepto (mínimo 3 caracteres)";
        }
        if (!LETRA.matcher(v).matches()) {
            return "El concepto debe tener letras, no solo números o símbolos";
        }
        if (!CONCEPTO.matcher(v).matches()) {
            return "El concepto tiene caracteres no permitidos";
        }
        return null;
    }

    /** Para comparar conceptos: sin tildes, sin mayúsculas, sin signos y con un solo espacio entre palabras. */
    public static String clave(String concepto) {
        if (concepto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(concepto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** Vencimiento: no más de 2 años atrás (queda Vencido) ni más de 5 años adelante. */
    public static String vencimiento(LocalDate fecha, LocalDate hoy) {
        LocalDate min = hoy.minusYears(2);
        LocalDate max = hoy.plusYears(5);
        if (fecha.isBefore(min)) {
            return "La fecha no puede ser anterior al " + min.format(DIA);
        }
        if (fecha.isAfter(max)) {
            return "La fecha no puede ser posterior al " + max.format(DIA);
        }
        return null;
    }
}
