package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.castillo.categoria.Categoria;
import pe.castillo.categoria.CategoriaRepository;
import pe.castillo.config.Fechas;
import pe.castillo.config.NegocioException;
import pe.castillo.config.Reglas;
import pe.castillo.config.ValidacionException;
import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-03 registrar obligaciones (solo asistente; lo exige SecurityConfig).
 * HU-04 lista del mes, HU-05 urgentes, HU-06 detalle.
 */
@RestController
@RequestMapping("/api/obligaciones")
public class ObligacionController {

    /** HU-05: "Por vencer" cuando faltan 3 días o menos. */
    private static final int DIAS_POR_VENCER = 3;

    private final ObligacionRepository obligaciones;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;

    public ObligacionController(ObligacionRepository obligaciones,
                                CategoriaRepository categorias,
                                UsuarioRepository usuarios) {
        this.obligaciones = obligaciones;
        this.categorias = categorias;
        this.usuarios = usuarios;
    }

    /**
     * HU-04: obligaciones cuyo vencimiento cae en el mes (AAAA-MM), del más próximo al más lejano.
     * Con mes=todos: todos los pagos de todos los meses (anteriores, actual y futuros), como pidió el Product Owner.
     */
    @GetMapping
    public List<ObligacionResumen> listar(@RequestParam(required = false) String mes) {
        if ("todos".equalsIgnoreCase(mes)) {
            LocalDate hoy = Fechas.hoy();
            return obligaciones.findByActivoTrueOrderByFechaVencimientoAscIdAsc().stream()
                    .map(o -> ObligacionResumen.de(o, hoy))
                    .toList();
        }
        YearMonth ym;
        try {
            ym = (mes == null || mes.isBlank()) ? YearMonth.from(Fechas.hoy()) : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Mes no válido (usa el formato AAAA-MM)");
        }
        LocalDate hoy = Fechas.hoy();
        return obligaciones
                .findByActivoTrueAndFechaVencimientoBetweenOrderByFechaVencimientoAscIdAsc(
                        ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .map(o -> ObligacionResumen.de(o, hoy))
                .toList();
    }

    /** HU-05: pagos Por vencer y Vencidos de cualquier mes, del más urgente al menos urgente. */
    @GetMapping("/urgentes")
    public List<ObligacionResumen> urgentes() {
        LocalDate hoy = Fechas.hoy();
        return obligaciones.buscarUrgentes(hoy.plusDays(DIAS_POR_VENCER)).stream()
                .map(o -> ObligacionResumen.de(o, hoy))
                .toList();
    }

    /** A quiénes ya se les registró algún pago: el formulario los sugiere mientras se escribe. */
    @GetMapping("/destinatarios")
    public List<Destinatario> destinatarios() {
        return obligaciones.nombresConDocumento().stream()
                .map(f -> new Destinatario((String) f[0], (String) f[1]))
                .toList();
    }

    /** A quién ya se le pagó y su RUC o DNI (null si se registró antes de pedirlo). */
    public record Destinatario(String nombre, String ruc) { }

    /** HU-06: detalle del pago. Si no existe: "Pago no encontrado". */
    @GetMapping("/{id}")
    public ObligacionDetalle detalle(@PathVariable Long id) {
        return detalleDe(buscar(id));
    }

    private Obligacion buscar(Long id) {
        return obligaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private Long usuarioId(Authentication auth) {
        return usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow().getId();
    }

    /** "Nombre (correo)" del usuario, o null si no hay usuario. */
    private String nombreUsuario(Long id) {
        if (id == null) {
            return null;
        }
        return usuarios.findById(id)
                .map(u -> u.getNombre() + " (" + u.getCorreo() + ")")
                .orElse("—");
    }

    private ObligacionDetalle detalleDe(Obligacion o) {
        LocalDate hoy = Fechas.hoy();
        EstadoMostrado estado = EstadoMostrado.de(o, hoy);
        return new ObligacionDetalle(
                o.getId(),
                o.getConcepto(),
                o.getCategoria().getNombre(),
                o.getFechaVencimiento(),
                o.getMonto(),
                o.getObservacion(),
                estado.name(),
                estado.getEtiqueta(),
                ChronoUnit.DAYS.between(hoy, o.getFechaVencimiento()),
                nombreUsuario(o.getRegistradoPor()),
                o.getRegistradoEn(),
                o.getBeneficiarioNombre(),
                o.getBeneficiarioDocumento(),
                o.isEnEspera(),
                o.isEnEspera() ? o.getNotaEspera() : null,
                o.isEnEspera() ? nombreUsuario(o.getEsperaPor()) : null,
                o.isEnEspera() ? o.getEsperaEn() : null,
                o.isEnEspera() ? null : nombreUsuario(o.getLiberadoPor()),
                o.isEnEspera() ? null : o.getLiberadoEn());
    }

    /**
     * HU-03 criterios 4 y 5: si la fecha ya pasó queda "Vencido"; si no, "Por pagar" (se calcula al consultar).
     * Guarda quién la registró y cuándo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObligacionResumen crear(@Valid @RequestBody ObligacionForm form, Authentication auth) {
        Map<String, String> errores = new LinkedHashMap<>();
        ValidacionException.agregar(errores, "concepto", Reglas.concepto(form.concepto()));
        ValidacionException.agregar(errores, "beneficiario", Reglas.nombre(form.beneficiario()));
        ValidacionException.agregar(errores, "ruc", Reglas.documento(form.ruc()));
        ValidacionException.agregar(errores, "fechaVencimiento", Reglas.vencimiento(form.fechaVencimiento(), Fechas.hoy()));
        ValidacionException.lanzarSiHay(errores);

        Categoria categoria = categorias.findById(form.categoriaId())
                .filter(Categoria::isActivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "Elige una categoría válida", "categoriaId"));
        // Los préstamos tienen cronograma de cuotas: se registran en su propio apartado, no como una obligación suelta
        if ("Préstamos de capital".equalsIgnoreCase(categoria.getNombre())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Los préstamos no se registran aquí. Regístralos en el apartado Préstamos, con su cronograma de cuotas", "categoriaId");
        }
        String aQuien = form.beneficiario().trim().replaceAll("\\s+", " ");
        String ruc = form.ruc().trim();

        // Un RUC es de una sola empresa y una empresa tiene un solo RUC (pedido del Product Owner).
        // Si la empresa ya existe escrita de otra forma ("HIDRANDINA S.A."), se guarda con el nombre ya registrado.
        aQuien = nombreSegunRuc(obligaciones, aQuien, ruc);

        // No se permite registrar dos veces lo mismo (ver avisoDuplicada)
        avisoDuplicada(obligaciones, aQuien, form.concepto(), form.monto(), form.fechaVencimiento())
                .ifPresent(mensaje -> {
                    throw new NegocioException(HttpStatus.CONFLICT, mensaje);
                });

        String observacion = form.observacion() == null || form.observacion().isBlank()
                ? null : form.observacion().trim();
        Long usuarioId = usuarioId(auth);

        Obligacion guardada = obligaciones.save(new Obligacion(
                form.concepto().trim().replaceAll("\\s+", " "), categoria, aQuien, ruc, form.monto(),
                form.fechaVencimiento(), observacion, usuarioId));
        return ObligacionResumen.de(guardada, Fechas.hoy());
    }

    /**
     * Mensaje si la obligación ya está registrada, o vacío si se puede guardar. Es duplicada si hay una activa para
     * la misma persona o empresa (a quién se paga, sin importar mayúsculas, tildes ni espacios) con (a) el mismo monto
     * y vencimiento, o (b) el mismo concepto en el mismo mes de vencimiento, aunque el monto cambie; o si
     * (c) ya hay un recibo del mismo servicio básico (luz, agua, internet, teléfono, gas) en ese mes, a quien sea.
     * "concepto" puede ser null (se revisa solo la regla a).
     */
    public static java.util.Optional<String> avisoDuplicada(ObligacionRepository repo, String aQuien,
                                                            String concepto, BigDecimal monto, LocalDate vencimiento) {
        if (aQuien == null || aQuien.isBlank() || monto == null || vencimiento == null) {
            return java.util.Optional.empty();
        }
        String quien = Reglas.empresa(aQuien);
        java.util.Optional<Obligacion> exacta = repo.findByMontoAndFechaVencimientoAndActivoTrue(monto, vencimiento).stream()
                .filter(o -> Reglas.empresa(o.getBeneficiarioNombre()).equals(quien))
                .findFirst();
        if (exacta.isPresent()) {
            return exacta.map(ObligacionController::mensajeDuplicada);
        }
        if (concepto == null || concepto.isBlank()) {
            return java.util.Optional.empty();
        }
        String clave = Reglas.clave(concepto);
        YearMonth mes = YearMonth.from(vencimiento);
        List<Obligacion> delMes = repo.findByActivoTrueAndFechaVencimientoBetweenOrderByFechaVencimientoAscIdAsc(
                mes.atDay(1), mes.atEndOfMonth());
        java.util.Optional<String> mismoMes = delMes.stream()
                .filter(o -> Reglas.empresa(o.getBeneficiarioNombre()).equals(quien))
                .filter(o -> Reglas.clave(o.getConcepto()).equals(clave))
                .findFirst()
                .map(ObligacionController::mensajeMismoMes);
        if (mismoMes.isPresent()) {
            return mismoMes;
        }
        // (c) Un solo recibo de cada servicio básico (luz, agua, internet, teléfono, gas) por mes, sea a quien sea
        String servicio = Reglas.servicio(concepto);
        if (servicio == null) {
            return java.util.Optional.empty();
        }
        return delMes.stream()
                .filter(o -> servicio.equals(Reglas.servicio(o.getConcepto())))
                .findFirst()
                .map(o -> mensajeServicioDelMes(o, servicio, mes));
    }

    /** Aviso cuando ya hay un recibo del mismo servicio (por ejemplo luz) en ese mes. */
    public static String mensajeServicioDelMes(Obligacion existente, String servicio, YearMonth mes) {
        String nombreMes = mes.format(java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
                java.util.Locale.forLanguageTag("es-PE")));
        return "Ya hay un recibo de " + servicio + " en " + nombreMes + ": «" + existente.getConcepto() + "» para "
                + existente.getBeneficiarioNombre() + " (S/ " + soles(existente) + ", vence " + dia(existente)
                + "). Solo se registra un recibo de " + servicio + " por mes.";
    }

    /**
     * Revisa que el RUC/DNI y la empresa vayan juntos: el RUC no puede estar registrado para otra empresa y la empresa
     * no puede tener otro RUC. Devuelve el nombre con el que se guarda (el ya registrado, si es la misma empresa).
     */
    public static String nombreSegunRuc(ObligacionRepository repo, String aQuien, String ruc) {
        String empresa = Reglas.empresa(aQuien);
        String tipo = ruc.length() == 11 ? "RUC" : "DNI";
        for (Obligacion o : repo.findByActivoTrueAndBeneficiarioDocumentoIsNotNull()) {
            boolean mismoRuc = ruc.equals(o.getBeneficiarioDocumento());
            boolean mismaEmpresa = Reglas.empresa(o.getBeneficiarioNombre()).equals(empresa);
            if (mismoRuc && !mismaEmpresa) {
                throw new NegocioException(HttpStatus.CONFLICT, "El " + tipo + " " + ruc + " ya está registrado para «"
                        + o.getBeneficiarioNombre() + "». Un " + tipo + " pertenece a una sola persona o empresa: "
                        + "revisa el número o el nombre.", "ruc");
            }
            if (mismaEmpresa && !mismoRuc) {
                String suyo = o.getBeneficiarioDocumento().length() == 11 ? "RUC" : "DNI";
                throw new NegocioException(HttpStatus.CONFLICT, "«" + o.getBeneficiarioNombre() + "» ya está registrada con el "
                        + suyo + " " + o.getBeneficiarioDocumento() + ". Una persona o empresa tiene un solo " + suyo
                        + ": revisa el número.", "ruc");
            }
        }
        for (Obligacion o : repo.findByActivoTrueAndBeneficiarioDocumentoIsNotNull()) {
            if (ruc.equals(o.getBeneficiarioDocumento())) {
                return o.getBeneficiarioNombre();
            }
        }
        return aQuien;
    }

    /** Texto del aviso cuando ya existe la misma obligación. */
    public static String mensajeDuplicada(Obligacion existente) {
        return "Esta obligación ya está registrada: «" + existente.getConcepto() + "» para "
                + existente.getBeneficiarioNombre() + " (S/ " + soles(existente) + " y vencimiento " + dia(existente)
                + "). Si es otro pago, revisa el monto o la fecha.";
    }

    /** Aviso cuando hay lo mismo en el mismo mes pero con otro monto. */
    public static String mensajeMismoMes(Obligacion existente) {
        return "Ya hay una obligación «" + existente.getConcepto() + "» para " + existente.getBeneficiarioNombre()
                + " en ese mes (S/ " + soles(existente) + ", vence " + dia(existente) + "). Si es un pago distinto,"
                + " cambia el concepto para diferenciarlo; si es el mismo, no lo registres otra vez.";
    }

    private static String soles(Obligacion o) {
        return o.getMonto().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String dia(Obligacion o) {
        return o.getFechaVencimiento().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
