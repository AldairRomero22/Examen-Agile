package pe.castillo.categoria;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categorias;

    public CategoriaController(CategoriaRepository categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    public List<CategoriaDto> listar() {
        return categorias.findByActivoTrueOrderByIdAsc().stream()
                .map(c -> new CategoriaDto(c.getId(), c.getNombre()))
                .toList();
    }

    public record CategoriaDto(Long id, String nombre) { }
}
