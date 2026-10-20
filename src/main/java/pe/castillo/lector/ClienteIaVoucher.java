package pe.castillo.lector;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU-07: lector de vouchers con IA de visión (API de Anthropic, "Messages"). Lee capturas de Yape, Plin,
 * banca por internet, agentes y PDF de bancos, y devuelve monto, fecha de la operación y número de operación.
 *
 * - Sin dependencias de Spring, para poder probarla sola (con un servidor falso).
 * - Usa la misma clave que la lectura de documentos (ANTHROPIC_API_KEY). El voucher viaja a Anthropic solo para leerlo.
 * - Si algo falla (sin conexión, clave inválida, sin saldo, tiempo agotado) lanza LectorNoDisponibleException
 *   y el sistema vuelve a Tesseract.
 * - Lo que devuelve la IA se valida: monto positivo con 2 decimales, fecha real, número de operación de 4 a 40
 *   letras o números. Lo dudoso se descarta (null) y entonces el voucher no se puede enviar.
 */
public final class ClienteIaVoucher {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION_API = "2023-06-01";

    static final String INSTRUCCION = """
            Eres un lector de comprobantes de pago (vouchers) de Perú: capturas de Yape o Plin, constancias de \
            transferencia o pago de banca por internet o app (BCP, BBVA, Interbank, Scotiabank, Banco de la Nación, cajas), \
            tickets de agente o ventanilla y PDF de bancos. Lee la imagen y responde SOLO con un objeto JSON, \
            sin texto adicional, con exactamente estos campos:
            {"monto": número o null, "fecha": "AAAA-MM-DD" o null, "nro_operacion": texto o null}
            Reglas:
            - monto: el importe pagado o transferido en soles (el número grande junto a "S/"). No uses comisiones, \
              saldos ni el ITF. Usa punto decimal y sin separador de miles.
            - fecha: la fecha en que se hizo el pago u operación. Si aparece como "4 oct. 2026" o "04/10/2026", \
              conviértela a AAAA-MM-DD (en Perú el formato es día/mes/año). No uses fechas de vencimiento de tarjetas.
            - nro_operacion: el número de operación, de constancia, de transacción o código de operación, solo letras \
              y números, sin espacios.
            - Si un dato no aparece claramente o la imagen no es un comprobante de pago, pon null. No inventes ni adivines.""";

    private static final java.util.regex.Pattern NRO = java.util.regex.Pattern.compile("[A-Z0-9\\-]{4,40}");

    private final String clave;
    private final String modelo;
    private final URI direccion;
    private final Duration limite;
    private final HttpClient http;

    public ClienteIaVoucher(String clave, String modelo, String urlBase, long limiteSegundos) {
        this.clave = clave;
        this.modelo = modelo;
        this.direccion = URI.create(urlBase.replaceAll("/+$", "") + "/v1/messages");
        this.limite = Duration.ofSeconds(limiteSegundos);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** @param tipoMime image/jpeg, image/png o application/pdf */
    public LecturaVoucher leer(byte[] archivo, String tipoMime) {
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
            throw new LectorNoDisponibleException("El servicio de IA respondió " + respuesta.statusCode()
                    + " " + tipoDeError(respuesta.body()));
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
        pedido.put("max_tokens", 300);
        pedido.put("messages", List.of(mensaje));
        try {
            return JSON.writeValueAsString(pedido);
        } catch (IOException e) {
            throw new LectorNoDisponibleException("No se pudo preparar el pedido a la IA", e);
        }
    }

    /** Convierte la respuesta de la API en una lectura validada. Visible para pruebas. */
    static LecturaVoucher interpretar(String respuestaApi) {
        String texto;
        try {
            JsonNode contenido = JSON.readTree(respuestaApi).path("content");
            StringBuilder junto = new StringBuilder();
            for (JsonNode bloque : contenido) {
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
        JsonNode datos;
        try {
            datos = JSON.readTree(texto.substring(ini, fin + 1));
        } catch (IOException e) {
            throw new LectorNoDisponibleException("La IA no devolvió datos en el formato esperado", e);
        }

        BigDecimal monto = null;
        JsonNode m = datos.path("monto");
        String montoTexto = m.isNumber() ? m.decimalValue().toPlainString()
                : (m.isTextual() ? m.asText().replace("S/", "").replace(",", "").trim() : null);
        if (montoTexto != null && montoTexto.matches("\\d{1,8}(\\.\\d{1,2})?")) {
            BigDecimal candidato = new BigDecimal(montoTexto).setScale(2, RoundingMode.HALF_UP);
            if (candidato.signum() > 0) {
                monto = candidato;
            }
        }

        LocalDate fecha = null;
        JsonNode f = datos.path("fecha");
        if (f.isTextual()) {
            try {
                fecha = LocalDate.parse(f.asText().trim());
            } catch (DateTimeParseException e) {
                fecha = null;
            }
        }

        String nro = null;
        JsonNode n = datos.path("nro_operacion");
        if (n.isTextual() || n.isNumber()) {
            String candidato = n.asText().replaceAll("\\s+", "").toUpperCase();
            if (NRO.matcher(candidato).matches()) {
                nro = candidato;
            }
        }
        return new LecturaVoucher(fecha, nro, monto, "IA");
    }

    private static String tipoDeError(String cuerpo) {
        try {
            return JSON.readTree(cuerpo).path("error").path("type").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
