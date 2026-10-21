package pe.castillo.prestamo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglas de un préstamo de capital y su cronograma (pedido del Product Owner).
 * Revisa que el cronograma sea coherente: cada cuota = interés + amortización, cada saldo final = saldo inicial
 * − amortización, el saldo pasa de una fila a la siguiente, empieza en el monto y termina en 0, tantas filas como
 * el plazo, vencimientos en orden, y cuotas "Normal" y "Doble" del valor que corresponde.
 *
 * Sin dependencias de Spring, para poder probarlo solo. Las mismas reglas están en /js/prestamo-reglas.js.
 * Los errores impiden guardar; los avisos solo llaman la atención (por ejemplo, un interés que no calza con la tasa).
 */
public final class ValidadorPrestamo {

    /** Un error o aviso. fila = 0 para los datos del préstamo, 1..n para la cuota n. */
    public record Problema(int fila, String campo, String mensaje, boolean bloquea) { }

    public record Fila(Integer numero, LocalDate vencimiento, String tipo, BigDecimal saldoInicial,
                       BigDecimal interes, BigDecimal amortizacion, BigDecimal cuota, BigDecimal saldoFinal) { }

    public record Cronograma(String entidad, String nroCredito, BigDecimal monto, BigDecimal tasa, Integer plazo,
                             LocalDate fechaDesembolso, List<Fila> filas) { }

    static final BigDecimal CENTIMO = new BigDecimal("0.01");
    static final BigDecimal MONTO_MAXIMO = new BigDecimal("99999999.99");
    private static final BigDecimal TOLERANCIA_ULTIMA = new BigDecimal("1.00");
    private static final BigDecimal TOLERANCIA_DOBLE = new BigDecimal("0.05");
    private static final BigDecimal TOLERANCIA_INTERES = new BigDecimal("0.05");
    private static final BigDecimal TOLERANCIA_TOTAL = new BigDecimal("0.05");

    private ValidadorPrestamo() { }

    public static List<Problema> revisar(Cronograma c, LocalDate hoy) {
        List<Problema> p = new ArrayList<>();

        // ---------- Datos del préstamo ----------
        String entidad = c.entidad() == null ? "" : c.entidad().trim();
        if (entidad.isEmpty()) {
            p.add(error(0, "entidad", "Escribe la entidad que dio el préstamo (por ejemplo BCP)"));
        } else if (entidad.length() < 2 || !entidad.matches(".*\\p{L}.*") || entidad.length() > 100) {
            p.add(error(0, "entidad", "La entidad debe tener letras (de 2 a 100 caracteres)"));
        }
        String nro = c.nroCredito() == null ? "" : c.nroCredito().trim();
        if (nro.isEmpty()) {
            p.add(error(0, "nroCredito", "Escribe el número de crédito"));
        } else if (!nro.matches("[A-Za-z0-9][A-Za-z0-9\\-]{2,39}")) {
            p.add(error(0, "nroCredito", "El número de crédito solo lleva letras, números y guiones (por ejemplo 102-30031-67)"));
        }
        BigDecimal monto = c.monto();
        if (monto == null) {
            p.add(error(0, "monto", "Escribe el monto del préstamo"));
        } else if (monto.signum() <= 0 || monto.compareTo(MONTO_MAXIMO) > 0 || monto.stripTrailingZeros().scale() > 2) {
            p.add(error(0, "monto", "El monto debe ser mayor que 0, con hasta 2 decimales"));
            monto = null;
        }
        BigDecimal tasa = c.tasa();
        if (tasa == null) {
            p.add(error(0, "tasa", "Escribe la tasa de interés anual (%)"));
        } else if (tasa.signum() <= 0 || tasa.compareTo(new BigDecimal("100")) > 0 || tasa.stripTrailingZeros().scale() > 4) {
            p.add(error(0, "tasa", "La tasa debe ser mayor que 0 % y como máximo 100 %"));
            tasa = null;
        }
        Integer plazo = c.plazo();
        if (plazo == null) {
            p.add(error(0, "plazo", "Escribe el plazo (número de cuotas)"));
        } else if (plazo < 1 || plazo > 360) {
            p.add(error(0, "plazo", "El plazo debe ser de 1 a 360 cuotas"));
            plazo = null;
        }
        LocalDate desembolso = c.fechaDesembolso();
        if (desembolso == null) {
            p.add(error(0, "fechaDesembolso", "Escribe la fecha de desembolso"));
        } else if (desembolso.isBefore(hoy.minusYears(30)) || desembolso.isAfter(hoy.plusYears(1))) {
            p.add(error(0, "fechaDesembolso", "La fecha de desembolso no es razonable"));
            desembolso = null;
        }

        // ---------- Cronograma ----------
        List<Fila> filas = c.filas() == null ? List.of() : c.filas();
        if (filas.isEmpty()) {
            p.add(error(0, "filas", "El cronograma no tiene cuotas"));
            return p;
        }
        if (plazo != null && filas.size() != plazo) {
            p.add(error(0, "plazo", "El plazo es de " + plazo + " cuotas, pero el cronograma tiene " + filas.size() + " filas"));
        }

        BigDecimal normal = cuotaNormal(filas);
        List<Problema> avisosInteres = new ArrayList<>();
        BigDecimal sumaAmortizacion = BigDecimal.ZERO;
        boolean montosCompletos = true;
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            int n = i + 1;
            if (f.numero() == null || f.numero() != n) {
                p.add(error(n, "numero", "Cuota " + n + ": el número de cuota debería ser " + n));
            }

            // vencimientos en orden
            if (f.vencimiento() == null) {
                p.add(error(n, "vencimiento", "Cuota " + n + ": falta la fecha de vencimiento"));
            } else if (i == 0 && desembolso != null && !f.vencimiento().isAfter(desembolso)) {
                p.add(error(n, "vencimiento", "Cuota 1: vence el " + dia(f.vencimiento())
                        + ", que no es posterior al desembolso (" + dia(desembolso) + ")"));
            } else if (i > 0 && filas.get(i - 1).vencimiento() != null) {
                LocalDate anterior = filas.get(i - 1).vencimiento();
                if (!f.vencimiento().isAfter(anterior)) {
                    p.add(error(n, "vencimiento", "Cuota " + n + ": vence el " + dia(f.vencimiento())
                            + ", que no es posterior a la cuota anterior (" + dia(anterior) + ")"));
                } else {
                    long dias = ChronoUnit.DAYS.between(anterior, f.vencimiento());
                    if (dias < 25 || dias > 35) {
                        p.add(aviso(n, "vencimiento", "Cuota " + n + ": vence " + dias
                                + " días después de la anterior; lo usual es un mes"));
                    }
                }
            }

            // importes completos y válidos
            boolean completa = true;
            for (Object[] campo : new Object[][] {
                    {"saldoInicial", f.saldoInicial(), "el saldo inicial"}, {"interes", f.interes(), "el interés"},
                    {"amortizacion", f.amortizacion(), "la amortización"}, {"cuota", f.cuota(), "la cuota a pagar"},
                    {"saldoFinal", f.saldoFinal(), "el saldo final"}}) {
                BigDecimal v = (BigDecimal) campo[1];
                if (v == null) {
                    p.add(error(n, (String) campo[0], "Cuota " + n + ": falta " + campo[2]));
                    completa = false;
                } else if (v.signum() < 0 || v.compareTo(MONTO_MAXIMO) > 0 || v.stripTrailingZeros().scale() > 2) {
                    p.add(error(n, (String) campo[0], "Cuota " + n + ": " + campo[2] + " debe ser un importe de 0 o más, con hasta 2 decimales"));
                    completa = false;
                }
            }
            if (!completa) {
                montosCompletos = false;
                continue;
            }
            sumaAmortizacion = sumaAmortizacion.add(f.amortizacion());

            // cuota = interés + amortización
            BigDecimal suma = f.interes().add(f.amortizacion());
            if (distinto(f.cuota(), suma, CENTIMO)) {
                p.add(error(n, "cuota", "Cuota " + n + ": la cuota (" + soles(f.cuota()) + ") no es igual al interés + la amortización ("
                        + soles(f.interes()) + " + " + soles(f.amortizacion()) + " = " + soles(suma) + ")"));
            }
            // saldo final = saldo inicial − amortización
            BigDecimal resta = f.saldoInicial().subtract(f.amortizacion());
            if (distinto(f.saldoFinal(), resta, CENTIMO)) {
                p.add(error(n, "saldoFinal", "Cuota " + n + ": el saldo final (" + soles(f.saldoFinal())
                        + ") no es igual al saldo inicial − la amortización (" + soles(f.saldoInicial()) + " − "
                        + soles(f.amortizacion()) + " = " + soles(resta) + ")"));
            }
            // el saldo empieza en el monto y pasa de una cuota a la siguiente
            if (i == 0) {
                if (monto != null && distinto(f.saldoInicial(), monto, CENTIMO)) {
                    p.add(error(n, "saldoInicial", "Cuota 1: el saldo inicial (" + soles(f.saldoInicial())
                            + ") debe ser igual al monto del préstamo (" + soles(monto) + ")"));
                }
            } else {
                BigDecimal finalAnterior = filas.get(i - 1).saldoFinal();
                if (finalAnterior != null && distinto(f.saldoInicial(), finalAnterior, CENTIMO)) {
                    p.add(error(n, "saldoInicial", "Cuota " + n + ": el saldo inicial (" + soles(f.saldoInicial())
                            + ") debe ser igual al saldo final de la cuota " + i + " (" + soles(finalAnterior) + ")"));
                }
            }
            // tipo de cuota: Normal o Doble, con el valor que corresponde
            String tipo = tipoNormalizado(f.tipo());
            if (tipo == null) {
                p.add(error(n, "tipo", "Cuota " + n + ": el tipo de cuota debe ser «Normal» o «Doble»"));
            } else if (normal != null) {
                boolean ultima = i == filas.size() - 1;
                if ("NORMAL".equals(tipo) && distinto(f.cuota(), normal, ultima ? TOLERANCIA_ULTIMA : CENTIMO)) {
                    p.add(error(n, "cuota", "Cuota " + n + ": es «Normal», pero vale " + soles(f.cuota())
                            + "; la cuota normal es " + soles(normal)));
                } else if ("DOBLE".equals(tipo) && distinto(f.cuota(), normal.multiply(BigDecimal.valueOf(2)), TOLERANCIA_DOBLE)) {
                    p.add(error(n, "cuota", "Cuota " + n + ": es «Doble», pero vale " + soles(f.cuota())
                            + "; una cuota doble es " + soles(normal.multiply(BigDecimal.valueOf(2)))));
                }
            }
            // interés = saldo inicial × tasa anual / 12 (solo aviso: cada banco redondea a su manera)
            if (tasa != null) {
                BigDecimal esperado = f.saldoInicial().multiply(tasa)
                        .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
                if (distinto(f.interes(), esperado, TOLERANCIA_INTERES)) {
                    avisosInteres.add(aviso(n, "interes", "Cuota " + n + ": el interés (" + soles(f.interes()) + ") no calza con el "
                            + tasa.stripTrailingZeros().toPlainString() + " % anual sobre el saldo (" + soles(esperado) + ")"));
                }
            }
        }

        // Si muchos intereses no calzan, lo más probable es que la tasa esté mal escrita: un solo aviso
        if (avisosInteres.size() > 3 && tasa != null) {
            p.add(aviso(0, "tasa", "Los intereses de " + avisosInteres.size() + " cuotas no calzan con el "
                    + tasa.stripTrailingZeros().toPlainString() + " % anual. Revisa la tasa o los intereses."));
        } else {
            p.addAll(avisosInteres);
        }

        // ---------- El préstamo se paga completo ----------
        Fila ultima = filas.get(filas.size() - 1);
        if (ultima.saldoFinal() != null && distinto(ultima.saldoFinal(), BigDecimal.ZERO, CENTIMO)) {
            p.add(error(filas.size(), "saldoFinal", "Cuota " + filas.size() + ": el saldo final de la última cuota debe ser 0.00 (es "
                    + soles(ultima.saldoFinal()) + ")"));
        }
        if (montosCompletos && monto != null && distinto(sumaAmortizacion, monto, TOLERANCIA_TOTAL)) {
            p.add(error(0, "monto", "La suma de las amortizaciones (" + soles(sumaAmortizacion)
                    + ") no es igual al monto del préstamo (" + soles(monto) + ")"));
        }
        return p;
    }

    public static boolean hayErrores(List<Problema> problemas) {
        return problemas.stream().anyMatch(Problema::bloquea);
    }

    /** "Normal", "NORMAL", "Doble (Jul)", "doble" → NORMAL o DOBLE; otra cosa → null. */
    public static String tipoNormalizado(String tipo) {
        if (tipo == null) {
            return null;
        }
        String t = tipo.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("NORMAL")) {
            return "NORMAL";
        }
        if (t.startsWith("DOBLE")) {
            return "DOBLE";
        }
        return null;
    }

    /** La cuota normal: el valor que más se repite entre las cuotas "Normal" (sin contar la última). */
    static BigDecimal cuotaNormal(List<Fila> filas) {
        Map<BigDecimal, Integer> veces = new HashMap<>();
        for (int i = 0; i < filas.size() - 1; i++) {
            Fila f = filas.get(i);
            if (f.cuota() != null && "NORMAL".equals(tipoNormalizado(f.tipo()))) {
                veces.merge(f.cuota().setScale(2, RoundingMode.HALF_UP), 1, Integer::sum);
            }
        }
        if (veces.isEmpty() && filas.size() == 1 && filas.get(0).cuota() != null) {
            return filas.get(0).cuota().setScale(2, RoundingMode.HALF_UP);
        }
        return veces.entrySet().stream()
                .max(Map.Entry.<BigDecimal, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static boolean distinto(BigDecimal a, BigDecimal b, BigDecimal tolerancia) {
        return a.subtract(b).abs().compareTo(tolerancia) > 0;
    }

    private static Problema error(int fila, String campo, String mensaje) {
        return new Problema(fila, campo, mensaje, true);
    }

    private static Problema aviso(int fila, String campo, String mensaje) {
        return new Problema(fila, campo, mensaje, false);
    }

    static String soles(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "S/ " + f.format(v);
    }

    private static String dia(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
