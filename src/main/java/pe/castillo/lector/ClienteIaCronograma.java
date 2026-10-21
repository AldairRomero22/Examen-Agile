package pe.castillo.lector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lee un cronograma de pagos de préstamo (PDF o foto) con IA de visión (API de Anthropic, "Messages").
 * Devuelve los datos del préstamo y todas las filas, copiados tal como están en el documento:
 * si el documento tiene errores, se copian igual para que el sistema los marque (no se corrigen).
 * Si algo falla lanza LectorNoDisponibleException.
 */
public final class ClienteIaCronograma {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION_API = "2023-06-01";

    static final String INSTRUCCION = """
            Eres un lector de cronogramas de pago de préstamos de Perú (bancos y cajas). Lee el documento y responde SOLO \
            con un objeto JSON, sin texto adicional, con exactamente esta forma:
            {"entidad": texto o null, "nro_credito": texto o null, "monto": número o null, "tasa": número o null, \
            "plazo": número o null, "fecha_desembolso": "AAAA-MM-DD" o null, "cuotas": [{"numero": número, \
            "vencimiento": "AAAA-MM-DD", "tipo": texto, "saldo_inicial": número, "interes": número, "amortizacion": número, \
            "cuota": número, "saldo_final": número}]}
            Reglas:
            - Copia cada valor EXACTAMENTE como aparece en el documento, aunque te parezca incorrecto o no cuadre. \
              No corrijas, no recalcules, no completes valores que no ves. Si un valor no se lee, pon null.
            - entidad: el banco o caja que presta (por ejemplo "BCP"); nro_credito: el número de crédito.
            - monto: el monto del préstamo; tasa: la tasa de interés anual en % (17.02 % -> 17.02); plazo: número de cuotas.
            - Las fechas en Perú son día/mes/año: 04/05/2026 es el 4 de mayo de 2026. Escríbelas como AAAA-MM-DD.
            - tipo: el tipo de cuota tal como dice (por ejemplo "Normal" o "Doble (Jul)").
            - Importes en soles con punto decimal y sin separador de miles ni "S/".
            - Incluye todas las filas del cronograma, en orden.""";

    private final String clave;
    private final String modelo;
    private final URI direccion;
    private final Duration limite;
    private final HttpClient http;

    public ClienteIaCronograma(String clave, String modelo, String urlBase, long limiteSegundos) {
        this.clave = clave;
        this.modelo = modelo;
        this.direccion = URI.create(urlBase.replaceAll("/+$", "") + "/v1/messages");
        this.limite = Duration.ofSeconds(Math.max(limiteSegundos, 90));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** @param tipoMime image/jpeg, image/png o application/pdf */
    public LecturaCronograma leer(byte[] archivo, String tipoMime) {
        HttpRequest pedido = HttpRequest.newBuilder(direccion)
                .timeout(limite)
                .header("x-api-key", clave)
                .header("anthropic-version", VERSION_API)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(armarPedido(archivo, tipoMime), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LectorNoDisponibleException("Lectura con IA interrumpida", e);
        } catch (IOException e) {
            throw new LectorNoDisponibleException("No se pudo conectar con el servicio de IA", e);
        }
        if (respuesta.statusCode() != 200) {
            throw new LectorNoDisponibleException("El servicio de IA respondió " + respuesta.statusCode());
        }
        return interpretar(respuesta.body());
    }

    String armarPedido(byte[] archivo, String tipoMime) {
        Map<String, Object> origen = new LinkedHashMap<>();
        origen.put("type", "base64");
        origen.put("media_type", tipoMime);
        origen.put("data", Base64.getEncoder().encodeToString(archivo));
        Map<String, Object> adjunto = new LinkedHashMap<>();
        adjunto.put("type", "application/pdf".equals(tipoMime) ? "document" : "image");
        adjunto.put("source", origen);
        Map<String, Object> texto = new LinkedHashMap<>();
        texto.put("type", "text");
        texto.put("text", INSTRUCCION);
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("role", "user");
        mensaje.put("content", List.of(adjunto, texto));
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("model", modelo);
        pedido.put("max_tokens", 8000);
        pedido.put("messages", List.of(mensaje));
        try {
            return JSON.writeValueAsString(pedido);
        } catch (IOException e) {
            throw new LectorNoDisponibleException("No se pudo preparar el pedido a la IA", e);
        }
    }

    /** Convierte la respuesta de la API en una lectura. Visible para pruebas. */
    static LecturaCronograma interpretar(String respuestaApi) {
        String texto;
        try {
            StringBuilder junto = new StringBuilder();
            for (JsonNode bloque : JSON.readTree(respuestaApi).path("content")) {
                if ("text".equals(bloque.path("type").asText())) {
                    junto.append(bloque.path("text").asText());
                }
            }
            texto = junto.toString();
        } catch (IOException e) {
            throw new LectorNoDisponibleException("Respuesta de la IA ilegible", e);
        }
        int ini = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (ini < 0 || fin <= ini) {
            throw new LectorNoDisponibleException("La IA no devolvió datos en el formato esperado");
        }
        JsonNode d;
        try {
            d = JSON.readTree(texto.substring(ini, fin + 1));
        } catch (IOException e) {
            throw new LectorNoDisponibleException("La IA no devolvió datos en el formato esperado", e);
        }
        List<LecturaCronograma.Fila> cuotas = new ArrayList<>();
        for (JsonNode c : d.path("cuotas")) {
            if (cuotas.size() >= 400) {
                break;
            }
            Integer n = entero(c.path("numero"));
            cuotas.add(new LecturaCronograma.Fila(n == null ? cuotas.size() + 1 : n,
                    LectorExcelCronograma.fecha(txt(c.path("vencimiento"))), limpio(c.path("tipo"), 20),
                    LectorExcelCronograma.importe(txt(c.path("saldo_inicial"))), LectorExcelCronograma.importe(txt(c.path("interes"))),
                    LectorExcelCronograma.importe(txt(c.path("amortizacion"))), LectorExcelCronograma.importe(txt(c.path("cuota"))),
                    LectorExcelCronograma.importe(txt(c.path("saldo_final")))));
        }
        String nro = limpio(d.path("nro_credito"), 40);
        if (nro != null) {
            nro = nro.replaceAll("\\s+", "").toUpperCase();
            if (!nro.matches("[A-Z0-9][A-Z0-9\\-]{2,39}")) {
                nro = null;
            }
        }
        return new LecturaCronograma(limpio(d.path("entidad"), 100), nro,
                LectorExcelCronograma.importe(txt(d.path("monto"))), LectorExcelCronograma.porcentaje(txt(d.path("tasa"))),
                entero(d.path("plazo")), LectorExcelCronograma.fecha(txt(d.path("fecha_desembolso"))), cuotas, "IA");
    }

    private static String txt(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        return n.isNumber() ? n.decimalValue().toPlainString() : n.asText();
    }

    private static Integer entero(JsonNode n) {
        String t = txt(n);
        return t != null && t.trim().matches("\\d{1,3}(\\.0+)?") ? (int) Double.parseDouble(t.trim()) : null;
    }

    private static String limpio(JsonNode n, int max) {
        String t = txt(n);
        if (t == null) {
            return null;
        }
        t = t.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return t.isEmpty() || t.equalsIgnoreCase("null") ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}
