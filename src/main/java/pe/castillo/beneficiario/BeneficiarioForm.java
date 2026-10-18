package pe.castillo.beneficiario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-02 criterios 1 y 2: los cuatro campos son obligatorios; documento de 8 (DNI) u 11 (RUC) dígitos. */
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
        @Size(max = 30, message = "Máximo 30 caracteres")
        String cuenta) {
}
