package pe.castillo.beneficiario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-02: los cuatro campos son obligatorios; el formato (DNI/RUC, cuenta solo dígitos...) lo valida Reglas. */
public record BeneficiarioForm(
        @NotBlank(message = "Obligatorio")
        @Size(max = 150, message = "Máximo 150 caracteres")
        String nombre,

        @NotBlank(message = "Obligatorio")
        @Pattern(regexp = "\\d{8}|\\d{11}", message = "Debe tener 8 dígitos (DNI) u 11 (RUC), solo números")
        String documento,

        @NotBlank(message = "Obligatorio")
        @Size(max = 60, message = "Máximo 60 caracteres")
        String banco,

        @NotBlank(message = "Obligatorio")
        @Size(max = 20, message = "Solo números, de 10 a 20 dígitos (sin guiones ni espacios)")
        String cuenta,

        @NotNull(message = "Obligatorio")
        Long categoriaId) {
}
