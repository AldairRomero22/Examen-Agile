package pe.castillo.lector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Lee un cronograma de pagos desde un archivo Excel (.xlsx) sin IA y sin librerías extra: un .xlsx es un ZIP con XML.
 * Busca la fila de títulos (N.º, Vencimiento, Tipo de cuota, Saldo inicial, Interés, Amortización, Cuota, Saldo final)
 * y, arriba de ella, los datos del préstamo (Monto, Interés, Plazo, Fecha de desembolso, Crédito N.º).
 * Copia los valores tal como están: no corrige nada.
 */
public final class LectorExcelCronograma {

    private static final int MAX_FILAS = 400;
    private static final long MAX_DESCOMPRIMIDO = 30L * 1024 * 1024;
    private static final LocalDate BASE_EXCEL = LocalDate.of(1899, 12, 30);
    private static final Pattern CELDA = Pattern.compile("([A-Z]+)(\\d+)");
    private static final Pattern CREDITO = Pattern.compile("(?i)cr[eé]dito\\s*(?:n\\s*[°º.]*\\s*º?|nro\\.?|num(?:ero)?\\.?)?\\s*[:#]?\\s*([A-Za-z0-9][A-Za-z0-9\\-]{2,39})");
    private static final String[] ENTIDADES = {"BCP", "BBVA", "INTERBANK", "SCOTIABANK", "BANBIF", "MIBANCO", "BANCO DE LA NACION",
            "BANCO PICHINCHA", "CAJA TRUJILLO", "CAJA AREQUIPA", "CAJA HUANCAYO", "CAJA PIURA", "CAJA SULLANA", "CAJA CUSCO",
            "CAJA ICA", "FINANCIERA CONFIANZA", "COMPARTAMOS"};

    private LectorExcelCronograma() { }

    /** true si el archivo parece un .xlsx (ZIP que empieza con "PK"). */
    public static boolean esXlsx(byte[] archivo) {
        return archivo != null && archivo.length > 4 && archivo[0] == 'P' && archivo[1] == 'K';
    }

    public static LecturaCronograma leer(byte[] archivo) {
        Map<String, byte[]> partes = descomprimir(archivo);
        byte[] hoja = partes.get(primeraHoja(partes));
        if (hoja == null) {
            throw new LectorNoDisponibleException("El Excel no tiene hojas");
        }
        List<String> compartidos = textosCompartidos(partes.get("xl/sharedStrings.xml"));
        TreeMap<Integer, TreeMap<Integer, String>> grilla = grilla(hoja, compartidos);
        return interpretar(grilla);
    }

    // =====================================================================
    // Interpretar la grilla (fila -> columna -> texto)
    // =====================================================================

    static LecturaCronograma interpretar(TreeMap<Integer, TreeMap<Integer, String>> grilla) {
        // 1) fila de títulos del cronograma
        Integer filaTitulos = null;
        Map<String, Integer> columnas = new HashMap<>();
        for (Map.Entry<Integer, TreeMap<Integer, String>> fila : grilla.entrySet()) {
            Map<String, Integer> encontradas = columnasDe(fila.getValue());
            if (encontradas.containsKey("vencimiento") && encontradas.containsKey("cuota")) {
                filaTitulos = fila.getKey();
                columnas = encontradas;
                break;
            }
        }

        // 2) datos del préstamo (arriba de los títulos)
        BigDecimal monto = null;
        BigDecimal tasa = null;
        Integer plazo = null;
        LocalDate desembolso = null;
        String credito = null;
        String entidad = null;
        for (Map.Entry<Integer, TreeMap<Integer, String>> fila : grilla.entrySet()) {
            if (filaTitulos != null && fila.getKey() >= filaTitulos) {
                break;
            }
            for (Map.Entry<Integer, String> celda : fila.getValue().entrySet()) {
                String texto = celda.getValue();
                String t = simplificar(texto);
                if (credito == null) {
                    Matcher m = CREDITO.matcher(texto);
                    if (m.find() && m.group(1).matches(".*\\d.*")) {
                        credito = m.group(1).toUpperCase(Locale.ROOT);
                    } else if (t.matches("(n\\s*)?(de\\s*)?credito( n.*)?") || t.equals("nro de credito")) {
                        credito = textoALaDerecha(grilla, fila.getKey(), celda.getKey());
                    }
                }
                if (entidad == null) {
                    for (String e : ENTIDADES) {
                        if ((" " + t.toUpperCase(Locale.ROOT) + " ").contains(" " + e + " ")) {
                            entidad = e.equals("BCP") || e.equals("BBVA") ? e : capitalizar(e);
                        }
                    }
                }
                if (t.equals("monto") || t.startsWith("monto del prestamo") || t.equals("importe")) {
                    monto = importe(valorALaDerecha(grilla, fila.getKey(), celda.getKey()));
                } else if (t.startsWith("interes") || t.startsWith("tasa") || t.equals("tea") || t.equals("tem")) {
                    tasa = porcentaje(valorALaDerecha(grilla, fila.getKey(), celda.getKey()));
                } else if (t.startsWith("plazo") || t.startsWith("numero de cuotas") || t.startsWith("n de cuotas")) {
                    BigDecimal p = importe(valorALaDerecha(grilla, fila.getKey(), celda.getKey()));
                    plazo = p == null ? null : p.intValue();
                } else if (t.contains("desembolso")) {
                    desembolso = fecha(valorALaDerecha(grilla, fila.getKey(), celda.getKey()));
                }
            }
        }

        // 3) filas del cronograma
        List<LecturaCronograma.Fila> cuotas = new ArrayList<>();
        if (filaTitulos != null) {
            for (Map.Entry<Integer, TreeMap<Integer, String>> fila : grilla.tailMap(filaTitulos, false).entrySet()) {
                TreeMap<Integer, String> c = fila.getValue();
                LocalDate venc = fecha(c.get(columnas.get("vencimiento")));
                BigDecimal cuota = importe(c.get(columnas.get("cuota")));
                if (venc == null && cuota == null) {
                    if (!cuotas.isEmpty()) {
                        break;   // terminó la tabla
                    }
                    continue;
                }
                BigDecimal n = importe(columnas.containsKey("numero") ? c.get(columnas.get("numero")) : null);
                cuotas.add(new LecturaCronograma.Fila(
                        n == null ? cuotas.size() + 1 : n.intValue(), venc,
                        columnas.containsKey("tipo") ? c.get(columnas.get("tipo")) : null,
                        importe(c.get(columnas.get("saldoInicial"))), importe(c.get(columnas.get("interes"))),
                        importe(c.get(columnas.get("amortizacion"))), cuota, importe(c.get(columnas.get("saldoFinal")))));
                if (cuotas.size() >= MAX_FILAS) {
                    break;
                }
            }
        }
        return new LecturaCronograma(entidad, credito, monto, tasa, plazo, desembolso, cuotas, "EXCEL");
    }

    /** Reconoce los títulos de las columnas del cronograma. */
    static Map<String, Integer> columnasDe(Map<Integer, String> fila) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> celda : fila.entrySet()) {
            String t = simplificar(celda.getValue());
            if (t.isEmpty()) {
                continue;
            }
            if (t.matches("n|no|nro|n o|numero|nº|n°|cuota n|n de cuota") && !c.containsKey("numero")) {
                c.put("numero", celda.getKey());
            } else if (t.startsWith("venc") || t.startsWith("fecha")) {
                c.putIfAbsent("vencimiento", celda.getKey());
            } else if (t.startsWith("tipo")) {
                c.putIfAbsent("tipo", celda.getKey());
            } else if (t.startsWith("saldo ini") || t.equals("saldo") || t.startsWith("capital ini")) {
                c.putIfAbsent("saldoInicial", celda.getKey());
            } else if (t.startsWith("saldo fin") || t.startsWith("saldo cap")) {
                c.putIfAbsent("saldoFinal", celda.getKey());
            } else if (t.startsWith("interes")) {
                c.putIfAbsent("interes", celda.getKey());
            } else if (t.startsWith("amort") || t.equals("capital")) {
                c.putIfAbsent("amortizacion", celda.getKey());
            } else if (t.startsWith("cuota") || t.startsWith("total cuota") || t.startsWith("monto cuota")) {
                c.putIfAbsent("cuota", celda.getKey());
            }
        }
        return c;
    }

    private static String valorALaDerecha(TreeMap<Integer, TreeMap<Integer, String>> g, int fila, int col) {
        for (Map.Entry<Integer, String> e : g.get(fila).tailMap(col, false).entrySet()) {
            if (!e.getValue().isBlank()) {
                return e.getValue();
            }
        }
        // a veces el valor está justo debajo de la etiqueta
        TreeMap<Integer, String> abajo = g.get(fila + 1);
        return abajo == null ? null : abajo.get(col);
    }

    private static String textoALaDerecha(TreeMap<Integer, TreeMap<Integer, String>> g, int fila, int col) {
        String v = valorALaDerecha(g, fila, col);
        return v == null || !v.trim().matches("[A-Za-z0-9][A-Za-z0-9\\-]{2,39}") ? null : v.trim().toUpperCase(Locale.ROOT);
    }

    // =====================================================================
    // Valores: importes, porcentajes y fechas (como número de Excel o como texto)
    // =====================================================================

    static BigDecimal importe(String v) {
        if (v == null) {
            return null;
        }
        String t = v.replace("S/.", "").replace("S/", "").replace("s/", "").replace(" ", "").replace(" ", "").trim();
        if (t.matches("-?\\d+,\\d{1,2}")) {
            t = t.replace(',', '.');
        }
        t = t.replace(",", "");
        if (!t.matches("-?\\d+(\\.\\d+)?(E-?\\d+)?")) {
            return null;
        }
        try {
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "17.02%", "17.02", 0.1702 (Excel guarda 17 % como 0.17) -> 17.02 */
    static BigDecimal porcentaje(String v) {
        if (v == null) {
            return null;
        }
        boolean conSigno = v.contains("%");
        BigDecimal n = importeExacto(v.replace("%", ""));
        if (n == null) {
            return null;
        }
        if (!conSigno && n.compareTo(BigDecimal.ONE) < 0) {
            n = n.multiply(BigDecimal.valueOf(100));
        }
        return n.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal importeExacto(String v) {
        String t = v.replace(" ", "").trim();
        if (t.matches("\\d+,\\d+")) {
            t = t.replace(',', '.');
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "04/04/2026", "2026-04-04" o número de serie de Excel (46116) -> fecha. */
    static LocalDate fecha(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim();
        Matcher m = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})").matcher(t);
        try {
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
            if (t.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(t.substring(0, 10));
            }
            if (t.matches("\\d{5}(\\.\\d+)?")) {
                double serie = Double.parseDouble(t);
                if (serie > 20000 && serie < 80000) {
                    return BASE_EXCEL.plusDays((long) serie);
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    static String simplificar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9°º]+", " ").replace("º", "°").replace("°", "").trim().replaceAll("\\s+", " ");
    }

    private static String capitalizar(String t) {
        StringBuilder sb = new StringBuilder();
        for (String p : t.toLowerCase(Locale.ROOT).split(" ")) {
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // =====================================================================
    // Abrir el .xlsx (ZIP con XML)
    // =====================================================================

    private static Map<String, byte[]> descomprimir(byte[] archivo) {
        Map<String, byte[]> partes = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archivo))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String nombre = e.getName();
                if (!nombre.startsWith("xl/")) {
                    continue;
                }
                byte[] datos = zip.readNBytes((int) Math.min(MAX_DESCOMPRIMIDO - total, Integer.MAX_VALUE - 8));
                total += datos.length;
                if (total >= MAX_DESCOMPRIMIDO) {
                    throw new LectorNoDisponibleException("El Excel es demasiado grande");
                }
                partes.put(nombre, datos);
            }
        } catch (IOException ex) {
            throw new LectorNoDisponibleException("No se pudo abrir el Excel", ex);
        }
        if (partes.isEmpty()) {
            throw new LectorNoDisponibleException("El archivo no es un Excel .xlsx");
        }
        return partes;
    }

    /** La primera hoja del libro (según workbook.xml y sus relaciones). */
    private static String primeraHoja(Map<String, byte[]> partes) {
        try {
            Document libro = xml(partes.get("xl/workbook.xml"));
            Document rels = xml(partes.get("xl/_rels/workbook.xml.rels"));
            Element hoja = (Element) libro.getElementsByTagNameNS("*", "sheet").item(0);
            String rid = hoja.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            NodeList r = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < r.getLength(); i++) {
                Element e = (Element) r.item(i);
                if (rid.equals(e.getAttribute("Id"))) {
                    String destino = e.getAttribute("Target");
                    return destino.startsWith("/") ? destino.substring(1) : "xl/" + destino;
                }
            }
        } catch (RuntimeException e) {
            // se prueba con el nombre de siempre
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static List<String> textosCompartidos(byte[] datos) {
        List<String> textos = new ArrayList<>();
        if (datos == null) {
            return textos;
        }
        NodeList si = xml(datos).getElementsByTagNameNS("*", "si");
        for (int i = 0; i < si.getLength(); i++) {
            NodeList t = ((Element) si.item(i)).getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < t.getLength(); k++) {
                sb.append(t.item(k).getTextContent());
            }
            textos.add(sb.toString());
        }
        return textos;
    }

    private static TreeMap<Integer, TreeMap<Integer, String>> grilla(byte[] hoja, List<String> compartidos) {
        TreeMap<Integer, TreeMap<Integer, String>> g = new TreeMap<>();
        NodeList celdas = xml(hoja).getElementsByTagNameNS("*", "c");
        for (int i = 0; i < celdas.getLength(); i++) {
            Element c = (Element) celdas.item(i);
            Matcher m = CELDA.matcher(c.getAttribute("r"));
            if (!m.matches()) {
                continue;
            }
            int fila = Integer.parseInt(m.group(2));
            int col = columna(m.group(1));
            String tipo = c.getAttribute("t");
            String valor;
            if ("inlineStr".equals(tipo)) {
                NodeList t = c.getElementsByTagNameNS("*", "t");
                valor = t.getLength() > 0 ? t.item(0).getTextContent() : "";
            } else {
                NodeList v = c.getElementsByTagNameNS("*", "v");
                valor = v.getLength() > 0 ? v.item(0).getTextContent() : "";
                if ("s".equals(tipo) && !valor.isEmpty()) {
                    int idx = Integer.parseInt(valor.trim());
                    valor = idx < compartidos.size() ? compartidos.get(idx) : "";
                }
            }
            if (!valor.isBlank()) {
                g.computeIfAbsent(fila, k -> new TreeMap<>()).put(col, valor.trim());
            }
        }
        return g;
    }

    private static int columna(String letras) {
        int n = 0;
        for (char ch : letras.toCharArray()) {
            n = n * 26 + (ch - 'A' + 1);
        }
        return n;
    }

    private static Document xml(byte[] datos) {
        if (datos == null) {
            throw new LectorNoDisponibleException("El Excel está incompleto");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // sin entidades externas (protección XXE)
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(datos));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new LectorNoDisponibleException("No se pudo leer el Excel", e);
        }
    }
}
