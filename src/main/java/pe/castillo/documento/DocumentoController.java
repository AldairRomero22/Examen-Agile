package pe.castillo.documento;

import java.io.IOException;
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

import pe.castillo.beneficiario.Beneficiario;
import pe.castillo.beneficiario.BeneficiarioRepository;
import pe.castillo.categoria.Categoria;
import pe.castillo.categoria.CategoriaRepository;
import pe.castillo.comprobante.ArchivoVoucher;
import pe.castillo.config.NegocioException;
import pe.castillo.lector.CoincidenciaBeneficiario;
import pe.castillo.lector.LectorDocumento;
import pe.castillo.lector.LectorNoDisponibleException;
import pe.castillo.lector.LecturaDocumento;
import pe.castillo.obligacion.Obligacion;
import pe.castillo.obligacion.ObligacionController;
import pe.castillo.obligacion.ObligacionRepository;

/**
 * Nueva obligación, opción "Leer desde un documento": recibe un recibo, factura o cuota (JPG, PNG o PDF, máx. 5 MB),
 * lo lee y devuelve los datos para rellenar el formulario. El archivo NO se guarda y no se crea nada:
 * el asistente revisa los datos y guarda la obligación como siempre. Solo asistente (lo exige SecurityConfig).
 */
@RestController
public class DocumentoController {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentoController.class);
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    private static final String FORMATO_NO_PERMITIDO = "Formato o tamaño no permitido. Usa JPG, PNG o PDF de hasta 5 MB";

    private final LectorDocumento lector;
    private final BeneficiarioRepository beneficiarios;
    private final ObligacionRepository obligaciones;
    private final CategoriaRepository categorias;

    public DocumentoController(LectorDocumento lector, BeneficiarioRepository beneficiarios,
                               ObligacionRepository obligaciones, CategoriaRepository categorias) {
        this.lector = lector;
        this.beneficiarios = beneficiarios;
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

        // beneficiario: por RUC/DNI que aparezca en el texto; si no, por nombre completo único
        List<Beneficiario> activos = beneficiarios.findByActivoTrueOrderByNombreAsc();
        Long beneficiarioId = CoincidenciaBeneficiario.buscar(lectura.texto(), activos.stream()
                .map(b -> new CoincidenciaBeneficiario.Candidato(b.getId(), b.getNombre(), b.getDocumento()))
                .toList());
        String beneficiarioNombre = null;
        Long categoriaId = null;
        String concepto = null;
        if (beneficiarioId != null) {
            for (Beneficiario b : activos) {
                if (b.getId().equals(beneficiarioId)) {
                    beneficiarioNombre = b.getNombre();
                }
            }
            // concepto y categoría sugeridos: los de su última obligación
            Optional<Obligacion> ultima = obligaciones
                    .findFirstByBeneficiarioIdAndActivoTrueOrderByFechaVencimientoDescIdDesc(beneficiarioId);
            if (ultima.isPresent()) {
                categoriaId = ultima.get().getCategoria().getId();
                concepto = ultima.get().getConcepto();
            }
        }
        // si no hay historial, se usa lo que la IA sugiere del documento (solo si el lector con IA está activo)
        if (concepto == null) {
            concepto = lectura.concepto();
        }
        if (categoriaId == null && lectura.categoria() != null) {
            for (Categoria c : categorias.findByActivoTrueOrderByIdAsc()) {
                if (c.getNombre().equalsIgnoreCase(lectura.categoria())) {
                    categoriaId = c.getId();
                }
            }
        }
        // emisor no registrado: se ofrece registrarlo con el nombre y el RUC leídos
        String emisorNombre = beneficiarioId == null ? lectura.emisorNombre() : null;
        String emisorDocumento = beneficiarioId == null ? lectura.emisorDocumento() : null;
        // ¿ya está registrada? se avisa desde ahora; igual el servidor la rechazaría al guardar
        String avisoDuplicada = null;
        if (beneficiarioId != null && lectura.monto() != null && lectura.fechaVencimiento() != null) {
            avisoDuplicada = ObligacionController.avisoDuplicada(obligaciones, beneficiarioId, concepto,
                    lectura.monto(), lectura.fechaVencimiento()).orElse(null);
        }
        return new DocumentoLeido(lectura.monto(), lectura.fechaVencimiento(),
                beneficiarioId, beneficiarioNombre, categoriaId, concepto, emisorNombre, emisorDocumento, avisoDuplicada);
    }
}
