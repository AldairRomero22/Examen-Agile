package pe.castillo.obligacion;

import java.time.LocalDate;
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
        Obligacion o = obligaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        LocalDate hoy = Fechas.hoy();
        EstadoMostrado estado = EstadoMostrado.de(o, hoy);
        String registradoPor = usuarios.findById(o.getRegistradoPor())
                .map(u -> u.getNombre() + " (" + u.getCorreo() + ")")
                .orElse("—");
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
                registradoPor,
                o.getRegistradoEn(),
                BeneficiarioFila.de(o.getBeneficiario()));
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
        Long usuarioId = usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow().getId();

        Obligacion guardada = obligaciones.save(new Obligacion(
                form.concepto().trim(), categoria, beneficiario, form.monto(),
                form.fechaVencimiento(), observacion, usuarioId));
        return ObligacionResumen.de(guardada, Fechas.hoy());
    }
}
