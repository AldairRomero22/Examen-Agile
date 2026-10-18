package pe.castillo.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InicioController {

    /** Abrir la URL raíz lleva al panel (o al login si no hay sesión). */
    @GetMapping("/")
    public String inicio() {
        return "redirect:/panel.html";
    }
}
