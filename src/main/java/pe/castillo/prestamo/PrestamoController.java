package pe.castillo.prestamo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.castillo.config.Fechas;
import pe.castillo.comprobante.ArchivoVoucher;
import pe.castillo.config.NegocioException;
import pe.castillo.lector.LecturaCronograma;
import pe.castillo.lector.LectorCronograma;
import pe.castillo.lector.LectorExcelCronograma;
import pe.castillo.lector.LectorNoDisponibleException;
import pe.castillo.usuario.UsuarioRepository;

/**
 * Pedido del Product Owner: apartado de préstamos de capital.
 * El asistente registra el préstamo con su cronograma (a mano o leído); el gerente y el asistente lo consultan.
 * Las cuotas no se pagan desde aquí: es solo para consultar el cronograma.
 */
@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    private static final String[] MESES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"};

    private static final Logger LOG = LoggerFactory.getLogger(PrestamoController.class);
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    private static final String FORMATO = "Usa un Excel (.xlsx), PDF, JPG o PNG de hasta 5 MB";

    private final PrestamoRepository prestamos;
    private final UsuarioRepository usuarios;
    private final LectorCronograma lector;

    public PrestamoController(PrestamoRepository prestamos, UsuarioRepository usuarios, LectorCronograma lector) {
        this.prestamos = prestamos;
        this.usuarios = usuarios;
        this.lector = lector;
    }

    /**
     * Lee el cronograma de un archivo para rellenar la pantalla (no guarda nada).
     * Excel: directo y sin IA. PDF o foto: con IA. Los valores se copian tal cual, con sus errores, para que se marquen.
     */
    @PostMapping(path = "/leer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LecturaCronograma leer(@RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona el archivo del cronograma", "archivo");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO, "archivo");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (java.io.IOException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se pudo recibir el archivo, intenta de nuevo", "archivo");
        }
        if (bytes.length > 4 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Es un Excel antiguo (.xls). Ábrelo y guárdalo como .xlsx para poder leerlo", "archivo");
        }
        LecturaCronograma lectura;
        if (LectorExcelCronograma.esXlsx(bytes)) {
            try {
                lectura = lector.leerExcel(bytes);
            } catch (LectorNoDisponibleException e) {
                throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No se pudo leer el Excel: " + e.getMessage() + ". Revisa que sea el cronograma o escribe los datos a mano", "archivo");
            }
        } else {
            String tipo = ArchivoVoucher.detectarTipo(bytes);
            if (tipo == null) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, FORMATO, "archivo");
            }
            if (!lector.leeImagenes()) {
                throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Para leer un PDF o una foto se necesita la IA. Sube el Excel o escribe los datos a mano", "archivo");
            }
            try {
                lectura = lector.leerConIa(bytes, tipo);
            } catch (LectorNoDisponibleException e) {
                LOG.warn("No se pudo leer el cronograma con IA: {}", e.getMessage());
                throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo leer el cronograma. Intenta de nuevo o escribe los datos a mano", "archivo");
            }
        }
        if (lectura.estaVacia()) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontró un cronograma de pagos en el archivo", "archivo");
        }
        return lectura;
    }

    public record CuotaVista(int numero, LocalDate vencimiento, String tipo, String tipoEtiqueta, BigDecimal saldoInicial,
                             BigDecimal interes, BigDecimal amortizacion, BigDecimal cuota, BigDecimal saldoFinal) { }

    public record PrestamoResumen(Long id, String entidad, String nroCredito, BigDecimal monto, BigDecimal tasa, int plazo,
                                  LocalDate fechaDesembolso, Integer proximaCuota, LocalDate proximoVencimiento,
                                  BigDecimal proximoMonto, BigDecimal saldoPendiente, int cuotasRestantes,
                                  BigDecimal totalIntereses) { }

    public record PrestamoDetalle(PrestamoResumen resumen, String registradoPor, OffsetDateTime registradoEn,
                                  List<CuotaVista> cuotas) { }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PrestamoResumen> listar() {
        LocalDate hoy = Fechas.hoy();
        return prestamos.findByActivoTrueOrderByFechaDesembolsoDescIdDesc().stream()
                .map(p -> resumen(p, hoy))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public PrestamoDetalle detalle(@PathVariable Long id) {
        Prestamo p = prestamos.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        String quien = usuarios.findById(p.getRegistradoPor())
                .map(u -> u.getNombre() + " (" + u.getCorreo() + ")").orElse("—");
        return new PrestamoDetalle(resumen(p, Fechas.hoy()), quien, p.getRegistradoEn(),
                p.getCuotas().stream().map(PrestamoController::vista).toList());
    }

    /** Registra el préstamo solo si el cronograma no tiene errores; si los tiene, los devuelve todos. */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody PrestamoForm form, Authentication auth) {
        List<ValidadorPrestamo.Problema> problemas = new ArrayList<>(
                ValidadorPrestamo.revisar(form.comoCronograma(), Fechas.hoy()));
        if (form.nroCredito() != null && prestamos.existsByNroCreditoIgnoreCaseAndActivoTrue(form.nroCredito().trim())) {
            problemas.add(0, new ValidadorPrestamo.Problema(0, "nroCredito",
                    "El crédito N.º " + form.nroCredito().trim() + " ya está registrado", true));
        }
        if (ValidadorPrestamo.hayErrores(problemas)) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("mensaje", "El préstamo no se guardó: corrige los errores marcados");
            cuerpo.put("problemas", problemas);
            return ResponseEntity.badRequest().body(cuerpo);
        }

        Long usuarioId = usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow().getId();
        Prestamo p = new Prestamo(form.entidad().trim().replaceAll("\\s+", " "), form.nroCredito().trim().toUpperCase(),
                form.monto(), form.tasa(), form.plazo(), form.fechaDesembolso(), usuarioId);
        for (PrestamoForm.CuotaForm c : form.cuotas()) {
            p.agregarCuota(new PrestamoCuota(c.numero(), c.vencimiento(), ValidadorPrestamo.tipoNormalizado(c.tipo()),
                    c.saldoInicial(), c.interes(), c.amortizacion(), c.cuota(), c.saldoFinal()));
        }
        Prestamo guardado = prestamos.save(p);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", guardado.getId());
        cuerpo.put("avisos", problemas);
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    private static PrestamoResumen resumen(Prestamo p, LocalDate hoy) {
        PrestamoCuota proxima = p.getCuotas().stream()
                .filter(c -> !c.getVencimiento().isBefore(hoy))
                .findFirst().orElse(null);
        int restantes = (int) p.getCuotas().stream().filter(c -> !c.getVencimiento().isBefore(hoy)).count();
        BigDecimal intereses = p.getCuotas().stream().map(PrestamoCuota::getInteres).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PrestamoResumen(p.getId(), p.getEntidad(), p.getNroCredito(), p.getMonto(),
                new BigDecimal(p.getTasaInteres().stripTrailingZeros().toPlainString()), p.getPlazo(), p.getFechaDesembolso(),
                proxima == null ? null : proxima.getNumero(), proxima == null ? null : proxima.getVencimiento(),
                proxima == null ? null : proxima.getCuota(), proxima == null ? BigDecimal.ZERO : proxima.getSaldoInicial(),
                restantes, intereses);
    }

    private static CuotaVista vista(PrestamoCuota c) {
        String etiqueta = "DOBLE".equals(c.getTipo())
                ? "Doble (" + MESES[c.getVencimiento().getMonthValue() - 1] + ")" : "Normal";
        return new CuotaVista(c.getNumero(), c.getVencimiento(), c.getTipo(), etiqueta, c.getSaldoInicial(),
                c.getInteres(), c.getAmortizacion(), c.getCuota(), c.getSaldoFinal());
    }
}
