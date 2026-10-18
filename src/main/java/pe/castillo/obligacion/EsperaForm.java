package pe.castillo.obligacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-10 criterio 1: nota obligatoria de hasta 200 caracteres. */
public record EsperaForm(
        @NotBlank(message = "Escribe una nota para el asistente")
        @Size(max = 200, message = "Máximo 200 caracteres")
        String nota) {
}
