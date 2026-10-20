package pe.castillo.lector;

import java.io.IOException;
import java.math.BigDecimal;
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
 * Lector de recibos, facturas y cuotas con IA de visión (API de Anthropic, "Messages").
 * Se le envía la imagen o el PDF y devuelve el monto a pagar, el vencimiento y quién emite el documento.
 *
 * - Sin dependencias de Spring, para poder probarla sola (con un servidor falso).
 * - El documento viaja a Anthropic solo para leerlo; el sistema no lo guarda.
 * - Si algo falla (sin conexión, clave inválida, sin saldo, tiempo agotado) lanza LectorNoDisponibleException
 *   y quien la usa vuelve a Tesseract.
 * - Los datos que devuelve la IA se validan igual que cualquier dato escrito a mano: monto positivo con
 *   2 decimales, fecha real, RUC/DNI solo si son 8 u 11 dígitos. Lo dudoso se descarta (null).
 */
public final class ClienteIaDocumento {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION_API = "2023-06-01";

    static final String INSTRUCCION = """
            Eres un lector de recibos, boletas, facturas, cuotas y vouchers de pago de Perú. Lee el documento y responde SOLO con un objeto JSON, \
            sin texto adicional, con exactamente estos campos:
            {"monto": número o null, "fecha_vencimiento": "AAAA-MM-DD" o null, "emisor_nombre": texto o null, "emisor_ruc": texto o null, "concepto": texto o null, "categoria": texto o null}
            Reglas:
            - monto: el TOTAL A PAGAR final del documento en soles (con IGV y demás cargos), no un subtotal ni la deuda anterior. \
              Usa punto decimal y sin separador de miles. En una cuota de préstamo, el monto de la cuota.
            - fecha_vencimiento: la fecha límite de pago, no la de emisión ni la del periodo de consumo. \
              En un voucher o constancia de pago, la fecha de la operación.
            - emisor_nombre y emisor_ruc: a quién se le paga. En un recibo, boleta, factura o cuota es la empresa o persona \
              que cobra o emite el documento (arriba del documento), no el cliente. En un voucher o constancia de \
              transferencia o depósito es el destinatario o beneficiario del pago, no quien paga.
            - concepto: descripción corta (máximo 60 caracteres) de qué se paga, con el mes o periodo si aparece. \
              Ejemplos: "Recibo de luz - setiembre 2026", "Cuota de préstamo 5 de 36", "Factura F001-458 cemento".
            - categoria: exactamente una de estas cuatro: "Servicios" (luz, agua, teléfono, internet, alquiler), \
              "Impuestos" (SUNAT, arbitrios, tributos), "Proveedores" (facturas por compra de bienes o servicios a empresas), \
              "Préstamos de capital" (cuotas de préstamos o créditos). Si ninguna calza claramente, null.
            - Si un dato no aparece claramente, pon null. No inventes ni adivines.""";

    private final String clave;
    private final String modelo;
    private final URI direccion;
    private final Duration limite;
    private final HttpClient http;

    public ClienteIaDocumento(String clave, String modelo, String urlBase, long limiteSegundos) {
        this.clave = clave;
        this.modelo = modelo;
        this.direccion = URI.create(urlBase.replaceAll("/+$", "") + "/v1/messages");
        this.limite = Duration.ofSeconds(limiteSegundos);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * @param tipoMime image/jpeg, image/png o application/pdf
     * @return lo leído; "texto" trae el nombre y el RUC del emisor para reconocer a quién se paga
     */
    public LecturaDocumento leer(byte[] archivo, String tipoMime) {
        String cuerpo = armarPedido(archivo, tipoMime);
        HttpRequest pedido = HttpRequest.newBuilder(direccion)
                .timeout(limite)
                .header("x-api-key", clave)
                .header("anthropic-version", VERSION_API)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
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
            // no se registra el cuerpo completo: solo el estado y el tipo de error
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
        pedido.put("max_tokens", 400);
        pedido.put("messages", List.of(mensaje));
        try {
            return JSON.writeValueAsString(pedido);
        } catch (IOException e) {
            throw new LectorNoDisponibleException("No se pudo preparar el pedido a la IA", e);
        }
    }

    /** Convierte la respuesta de la API en una lectura validada. Visible para pruebas. */
    static LecturaDocumento interpretar(String respuestaApi) {
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
        if (m.isNumber() || (m.isTextual() && m.asText().matches("\\d{1,10}(\\.\\d{1,2})?"))) {
            BigDecimal candidato = m.isNumber() ? m.decimalValue() : new BigDecimal(m.asText());
            candidato = candidato.setScale(2, java.math.RoundingMode.HALF_UP);
            if (candidato.signum() > 0 && candidato.compareTo(new BigDecimal("9999999999.99")) <= 0) {
                monto = candidato;
            }
        }
        LocalDate fecha = null;
        JsonNode f = datos.path("fecha_vencimiento");
        if (f.isTextual()) {
            try {
                fecha = LocalDate.parse(f.asText().trim());
            } catch (DateTimeParseException e) {
                fecha = null;
            }
        }
        String nombre = limpio(datos.path("emisor_nombre"), 150);
        String ruc = limpio(datos.path("emisor_ruc"), 20);
        if (ruc != null) {
            ruc = ruc.replaceAll("\\D", "");
            if (ruc.length() != 8 && ruc.length() != 11) {
                ruc = null;
            }
        }
        // el texto lleva el RUC primero (lo busca CoincidenciaBeneficiario) y luego el nombre
        String textoEmisor = ((ruc != null ? "RUC " + ruc + "\n" : "") + (nombre != null ? nombre : "")).trim();
        String concepto = limpio(datos.path("concepto"), 150);
        String categoria = categoriaFija(limpio(datos.path("categoria"), 40));
        return new LecturaDocumento(fecha, monto, textoEmisor, nombre, ruc, concepto, categoria);
    }

    private static final List<String> CATEGORIAS = List.of("Servicios", "Impuestos", "Proveedores", "Préstamos de capital");

    /** Solo se acepta una de las cuatro categorías fijas (sin importar tildes ni mayúsculas); otra cosa se descarta. */
    private static String categoriaFija(String propuesta) {
        if (propuesta == null) {
            return null;
        }
        String buscada = ExtractorDatosDocumento.simplificar(propuesta);
        for (String c : CATEGORIAS) {
            if (ExtractorDatosDocumento.simplificar(c).equals(buscada)) {
                return c;
            }
        }
        return null;
    }

    private static String limpio(JsonNode nodo, int maximo) {
        if (!nodo.isTextual()) {
            return null;
        }
        String s = nodo.asText().replaceAll("\\p{Cntrl}", " ").trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) {
            return null;
        }
        return s.length() > maximo ? s.substring(0, maximo) : s;
    }

    private static String tipoDeError(String cuerpo) {
        try {
            return JSON.readTree(cuerpo).path("error").path("type").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
