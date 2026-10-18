package pe.castillo.usuario;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SesionController {

    private final UsuarioRepository usuarios;

    public SesionController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Las pantallas lo usan para saber quién inició sesión y qué botones mostrar según el rol. */
    @GetMapping("/me")
    public UsuarioActual me(Authentication auth) {
        Usuario u = usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow();
        return new UsuarioActual(u.getCorreo(), u.getNombre(), u.getRol().name());
    }

    public record UsuarioActual(String correo, String nombre, String rol) { }
}
