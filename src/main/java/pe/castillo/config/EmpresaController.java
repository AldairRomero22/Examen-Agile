package pe.castillo.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las pantallas muestran el nombre de la empresa en "Confirmo que esta deuda es de…". Solo lectura. */
@RestController
@RequestMapping("/api/empresa")
public class EmpresaController {

    private final Empresa empresa;

    public EmpresaController(Empresa empresa) {
        this.empresa = empresa;
    }

    @GetMapping
    public DatosEmpresa datos() {
        return new DatosEmpresa(empresa.nombre());
    }

    public record DatosEmpresa(String nombre) { }
}
