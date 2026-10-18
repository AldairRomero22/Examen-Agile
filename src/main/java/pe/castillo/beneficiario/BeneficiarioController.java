package pe.castillo.beneficiario;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.castillo.config.Fechas;
import pe.castillo.config.NegocioException;
import pe.castillo.obligacion.ObligacionRepository;
import pe.castillo.obligacion.ObligacionResumen;
import pe.castillo.usuario.UsuarioRepository;

/**
 * HU-02. Registrar (solo asistente) y consultar (asistente y gerente) beneficiarios.
 * El permiso de registrar se exige en SecurityConfig.
 */
@RestController
@RequestMapping("/api/beneficiarios")
public class BeneficiarioController {

    private final BeneficiarioRepository beneficiarios;
    private final ObligacionRepository obligaciones;
    private final UsuarioRepository usuarios;

    public BeneficiarioController(BeneficiarioRepository beneficiarios,
                                  ObligacionRepository obligaciones,
                                  UsuarioRepository usuarios) {
        this.beneficiarios = beneficiarios;
        this.obligaciones = obligaciones;
        this.usuarios = usuarios;
    }

    /** Criterio 4: lista con nombre, documento, banco y estado. */
    @GetMapping
    public List<BeneficiarioFila> listar() {
        return beneficiarios.findByActivoTrueOrderByNombreAsc().stream()
                .map(BeneficiarioFila::de)
                .toList();
    }

    /** Criterio 4: todos los datos del beneficiario y los gastos asociados, cada uno con su estado. */
    @GetMapping("/{id}")
    public Detalle detalle(@PathVariable Long id) {
        Beneficiario b = beneficiarios.findById(id)
                .filter(Beneficiario::isActivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Beneficiario no encontrado"));
        LocalDate hoy = Fechas.hoy();
        List<ObligacionResumen> gastos = obligaciones
                .findByBeneficiarioIdAndActivoTrueOrderByFechaVencimientoAscIdAsc(id).stream()
                .map(o -> ObligacionResumen.de(o, hoy))
                .toList();
        return new Detalle(BeneficiarioFila.de(b), gastos);
    }

    /** Criterios 1 a 3. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiarioFila crear(@Valid @RequestBody BeneficiarioForm form, Authentication auth) {
        String documento = form.documento().trim();
        if (beneficiarios.existsByDocumento(documento)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este documento ya está registrado", "documento");
        }
        Long usuarioId = usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow().getId();
        Beneficiario nuevo = beneficiarios.save(new Beneficiario(
                form.nombre().trim(), documento, form.banco().trim(), form.cuenta().trim(), usuarioId));
        return BeneficiarioFila.de(nuevo);
    }

    public record Detalle(BeneficiarioFila beneficiario, List<ObligacionResumen> gastos) { }
}
