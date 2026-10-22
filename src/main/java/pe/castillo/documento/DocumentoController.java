package pe.castillo.documento;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.castillo.categoria.Categoria;
import pe.castillo.categoria.CategoriaRepository;
import pe.castillo.comprobante.ArchivoVoucher;
import pe.castillo.config.Empresa;
import pe.castillo.config.NegocioException;
import pe.castillo.config.Reglas;
import pe.castillo.lector.CoincidenciaBeneficiario;
import pe.castillo.lector.LectorDocumento;
import pe.castillo.lector.LectorNoDisponibleException;
import pe.castillo.lector.LecturaDocumento;
import pe.castillo.obligacion.Obligacion;
import pe.castillo.obligacion.ObligacionController;
import pe.castillo.obligacion.ObligacionRepository;

/**
 * Nueva obligación, opción "Leer desde un documento": recibe un recibo, boleta, factura, cuota o voucher (JPG, PNG o PDF, máx. 5 MB),
 * lo lee y devuelve los datos para rellenar el formulario. El archivo NO se guarda y no se crea nada:
 * el asistente revisa los datos y guarda la obligación como siempre. Solo asistente (lo exige SecurityConfig).
 */
@RestController
public class DocumentoController {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentoController.class);
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    static final String ES_VOUCHER = "Este documento es un voucher (prueba de un pago ya hecho). Los vouchers se suben en "
            + "‘Subir comprobante’ del pago correspondiente.";
    static final String ES_BOLETA = "Este documento es una boleta (comprobante de una compra ya pagada). Aquí solo se "
            + "registran recibos, facturas o cuotas por pagar.";

    /**
     * Sin IA (solo Tesseract) el tipo se reconoce por el texto: frases típicas de un voucher o de una boleta de venta.
     * Si nada calza, null (se trata como documento de cobro).
     */
    static String tipoPorTexto(String texto) {
        String t = Reglas.clave(texto);
        if (t.isEmpty()) {
            return null;
        }
        boolean cobro = t.contains("vencimiento") || t.contains("vence") || t.contains("fecha limite");
        if (!cobro && (t.contains("yapeaste") || t.contains("plineaste") || t.contains("constancia de transferencia")
                || t.contains("operacion exitosa") || t.contains("transferencia exitosa")
                || t.contains("pago realizado exitosamente") || t.contains("constancia de deposito"))) {
            return "VOUCHER";
        }
        if (!cobro && t.contains("boleta de venta")) {
            return "BOLETA";
        }
        return null;
    }

    private static final String FORMATO_NO_PERMITIDO = "Formato o tamaño no permitido. Usa JPG, PNG o PDF de hasta 5 MB";

    private final LectorDocumento lector;
    private final ObligacionRepository obligaciones;
    private final CategoriaRepository categorias;
    private final Empresa empresa;

    public DocumentoController(LectorDocumento lector, ObligacionRepository obligaciones, CategoriaRepository categorias,
                               Empresa empresa) {
        this.empresa = empresa;
        this.lector = lector;
        this.obligaciones = obligaciones;
        this.categorias = categorias;
    }

    @PostMapping(path = "/api/documentos/leer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentoLeido leer(@RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona el documento", "archivo");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO_NO_PERMITIDO, "archivo");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se pudo recibir el archivo, intenta de nuevo", "archivo");
        }
        String tipo = ArchivoVoucher.detectarTipo(bytes);
        if (tipo == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO_NO_PERMITIDO, "archivo");
        }

        LecturaDocumento lectura;
        try {
            lectura = lector.leer(bytes, tipo);
        } catch (LectorNoDisponibleException e) {
            LOG.warn("No se pudo leer el documento: {}", e.getMessage(), e);
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo leer el documento. Intenta de nuevo o escribe los datos a mano");
        }

        // En "Nueva obligación" solo se leen documentos de cobro (recibo, factura o cuota).
        // Un voucher o una boleta prueban un pago ya hecho: no se rellena nada y se explica dónde van.
        String tipoDocumento = lectura.tipo() != null ? lectura.tipo() : tipoPorTexto(lectura.texto());
        if ("VOUCHER".equals(tipoDocumento)) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, ES_VOUCHER, "archivo");
        }
        if ("BOLETA".equals(tipoDocumento)) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, ES_BOLETA, "archivo");
        }

        // "Solo mis deudas": el documento debe estar a nombre de la empresa. Si está a nombre de otro, no se rellena nada.
        // Si no se pudo leer el titular (por ejemplo sin IA), se sigue y el asistente confirma a mano al guardar.
        String titular = lectura.titularNombre() == null ? null : lectura.titularNombre().trim().replaceAll("\\s+", " ");
        Boolean titularEsMio = titular == null || titular.isEmpty() ? null : empresa.esMia(titular);
        if (Boolean.FALSE.equals(titularEsMio)) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "Este documento está a nombre de «" + titular
                    + "», no de " + empresa.nombre() + ". Solo se registran deudas de la empresa.", "archivo");
        }

        // A quién se paga: si el nombre leído (o el texto del documento) coincide con alguien a quien ya se le pagó,
        // se usa ese mismo nombre para que quede escrito igual; si no, el nombre que leyó la IA.
        List<String> conocidos = obligaciones.nombresBeneficiarios();
        List<CoincidenciaBeneficiario.Candidato> candidatos = new ArrayList<>();
        for (int i = 0; i < conocidos.size(); i++) {
            candidatos.add(new CoincidenciaBeneficiario.Candidato((long) i, conocidos.get(i), null));
        }
        String aQuien = null;
        Long encontrado = CoincidenciaBeneficiario.buscar(lectura.texto(), candidatos);
        if (encontrado == null && lectura.emisorNombre() != null) {
            String leido = Reglas.clave(lectura.emisorNombre());
            for (int i = 0; i < conocidos.size() && encontrado == null; i++) {
                if (Reglas.clave(conocidos.get(i)).equals(leido)) {
                    encontrado = (long) i;
                }
            }
        }
        if (encontrado != null) {
            aQuien = conocidos.get(encontrado.intValue());
        } else if (lectura.emisorNombre() != null && Reglas.nombre(lectura.emisorNombre()) == null) {
            aQuien = lectura.emisorNombre().trim().replaceAll("\\s+", " ");
        }

        // RUC o DNI: si el leído ya está registrado, manda el nombre de esa empresa; si no se leyó, el ya registrado
        String ruc = lectura.emisorDocumento() != null && Reglas.documento(lectura.emisorDocumento()) == null
                ? lectura.emisorDocumento() : null;
        for (Object[] fila : obligaciones.nombresConDocumento()) {
            String nombre = (String) fila[0];
            String doc = (String) fila[1];
            if (doc == null) {
                continue;
            }
            if (doc.equals(ruc)) {
                aQuien = nombre;
                break;
            }
            if (ruc == null && aQuien != null && Reglas.empresa(nombre).equals(Reglas.empresa(aQuien))) {
                ruc = doc;
            }
        }

        // concepto y categoría sugeridos: los de su último pago registrado
        Long categoriaId = null;
        String concepto = null;
        if (aQuien != null) {
            Optional<Obligacion> ultima = obligaciones
                    .findFirstByBeneficiarioNombreIgnoreCaseAndActivoTrueOrderByFechaVencimientoDescIdDesc(aQuien);
            if (ultima.isPresent()) {
                categoriaId = ultima.get().getCategoria().getId();
                concepto = ultima.get().getConcepto();
            }
        }
        // el concepto que la IA lee del documento (con su periodo, p. ej. "setiembre 2026") manda sobre el del último
        // pago; ese solo se usa cuando el documento no da concepto (lector sin IA)
        if (lectura.concepto() != null && !lectura.concepto().isBlank()) {
            concepto = lectura.concepto();
        }
        if (categoriaId == null && lectura.categoria() != null) {
            for (Categoria c : categorias.findByActivoTrueOrderByIdAsc()) {
                if (c.getNombre().equalsIgnoreCase(lectura.categoria())) {
                    categoriaId = c.getId();
                }
            }
        }
        // ¿ya está registrada? se avisa desde ahora; igual el servidor la rechazaría al guardar
        String avisoDuplicada = ObligacionController.avisoDuplicada(obligaciones, aQuien, concepto,
                lectura.monto(), lectura.fechaVencimiento()).orElse(null);
        return new DocumentoLeido(lectura.monto(), lectura.fechaVencimiento(), aQuien, ruc, categoriaId, concepto, avisoDuplicada,
                titular, titularEsMio);
    }
}
