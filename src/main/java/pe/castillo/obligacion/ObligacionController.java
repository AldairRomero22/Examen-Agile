package pe.castillo.obligacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.castillo.beneficiario.Beneficiario;
import pe.castillo.beneficiario.BeneficiarioRepository;
import pe.castillo.categoria.Categoria;
import pe.castillo.categoria.CategoriaRepository;
import pe.castillo.config.Fechas;
import pe.castillo.config.NegocioException;
import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-03: registrar obligaciones (solo asistente; lo exige SecurityConfig).
 * El listado por mes es provisional: la HU-04 le agrega totales y filtros.
 */
@RestController
@RequestMapping("/api/obligaciones")
public class ObligacionController {

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

    /** Obligaciones cuyo vencimiento cae en el mes (formato AAAA-MM), del más próximo al más lejano. */
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

    /**
     * Criterios 4 y 5: si la fecha ya pasó queda "Vencido"; si no, "Por pagar" (se calcula al consultar).
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
