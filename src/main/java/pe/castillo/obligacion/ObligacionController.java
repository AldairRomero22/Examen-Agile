package pe.castillo.obligacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

import pe.castillo.beneficiario.Beneficiario;
import pe.castillo.beneficiario.BeneficiarioFila;
import pe.castillo.beneficiario.BeneficiarioRepository;
import pe.castillo.categoria.Categoria;
import pe.castillo.categoria.CategoriaRepository;
import pe.castillo.config.Fechas;
import pe.castillo.config.NegocioException;
import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-03 registrar obligaciones (solo asistente; lo exige SecurityConfig).
 * HU-04 lista del mes, HU-05 urgentes, HU-06 detalle.
 * HU-10 poner en espera y liberar (solo gerente; lo exige SecurityConfig).
 */
@RestController
@RequestMapping("/api/obligaciones")
public class ObligacionController {

    /** HU-05: "Por vencer" cuando faltan 3 días o menos. */
    private static final int DIAS_POR_VENCER = 3;

    private final ObligacionRepository obligaciones;
    private final CategoriaRepository categorias;
    private final BeneficiarioRepository beneficiarios;
    private final UsuarioRepository usuarios;

    public ObligacionController(ObligacionRepository obligaciones,
                                CategoriaRepository categorias,
                                BeneficiarioRepository beneficiarios,
                                UsuarioRepository usuarios) {
        this.obligaciones = obligaciones;
        this.categorias = categorias;
        this.beneficiarios = beneficiarios;
        this.usuarios = usuarios;
    }

    /** HU-04: obligaciones cuyo vencimiento cae en el mes (AAAA-MM), del más próximo al más lejano. */
    @GetMapping
    public List<ObligacionResumen> listar(@RequestParam(required = false) String mes) {
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

    /** HU-06: detalle del pago con los datos para pagar. Si no existe: "Pago no encontrado". */
    @GetMapping("/{id}")
    public ObligacionDetalle detalle(@PathVariable Long id) {
        return detalleDe(buscar(id));
    }

    /**
     * HU-10 criterios 1 y 2: el gerente pone en espera un pago que no esté Pagado, con nota obligatoria.
     * Se guardan la nota, el usuario y la fecha. Un pago en espera deja de ser vencido o urgente.
     */
    @PostMapping("/{id}/espera")
    public ObligacionDetalle ponerEnEspera(@PathVariable Long id, @Valid @RequestBody EsperaForm form,
                                           Authentication auth) {
        Obligacion o = buscar(id);
        if ("PAGADO".equals(o.getEstadoPago())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Un pago Pagado no se puede poner en espera");
        }
        if (o.isEnEspera()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este pago ya está en espera");
        }
        o.ponerEnEspera(form.nota().trim(), usuarioId(auth), OffsetDateTime.now(Fechas.LIMA));
        return detalleDe(obligaciones.save(o));
    }

    /** HU-10 criterio 4: "Liberar pago"; vuelve al estado que le toca por su fecha. */
    @PostMapping("/{id}/liberar")
    public ObligacionDetalle liberar(@PathVariable Long id, Authentication auth) {
        Obligacion o = buscar(id);
        if (!o.isEnEspera()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este pago no está en espera");
        }
        o.liberar(usuarioId(auth), OffsetDateTime.now(Fechas.LIMA));
        return detalleDe(obligaciones.save(o));
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
                BeneficiarioFila.de(o.getBeneficiario()),
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
        Categoria categoria = categorias.findById(form.categoriaId())
                .filter(Categoria::isActivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "Elige una categoría válida", "categoriaId"));
        Beneficiario beneficiario = beneficiarios.findById(form.beneficiarioId())
                .filter(Beneficiario::isActivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "Elige un beneficiario válido", "beneficiarioId"));

        String observacion = form.observacion() == null || form.observacion().isBlank()
                ? null : form.observacion().trim();
        Long usuarioId = usuarioId(auth);

        Obligacion guardada = obligaciones.save(new Obligacion(
                form.concepto().trim(), categoria, beneficiario, form.monto(),
                form.fechaVencimiento(), observacion, usuarioId));
        return ObligacionResumen.de(guardada, Fechas.hoy());
    }
}
