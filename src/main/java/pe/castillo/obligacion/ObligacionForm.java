package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU-03 criterio 1: concepto, categoría, beneficiario, monto y vencimiento obligatorios; observación opcional. */
public record ObligacionForm(
        @NotBlank(message = "Obligatorio")
        @Size(max = 150, message = "Máximo 150 caracteres")
        String concepto,

        @NotNull(message = "Obligatorio")
        Long categoriaId,

        @NotNull(message = "Obligatorio")
        Long beneficiarioId,

        @NotNull(message = "Obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El monto debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "Máximo S/ 99,999,999.99 y 2 decimales")
        BigDecimal monto,

        @NotNull(message = "Obligatorio")
        LocalDate fechaVencimiento,

        @Size(max = 200, message = "Máximo 200 caracteres")
        String observacion) {
}
