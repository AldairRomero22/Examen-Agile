package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU-03 criterio 1 y "solo mis deudas": concepto, categoría, a quién se paga, su RUC o DNI, monto y vencimiento obligatorios; observación opcional. */
public record ObligacionForm(
        @NotBlank(message = "Obligatorio")
        @Size(max = 150, message = "Máximo 150 caracteres")
        String concepto,

        @NotNull(message = "Obligatorio")
        Long categoriaId,

        /** A quién se paga (texto libre o leído del documento). */
        @NotBlank(message = "Obligatorio")
        @Size(max = 150, message = "Máximo 150 caracteres")
        String beneficiario,

        /** RUC (empresa, 11 dígitos) o DNI (persona, 8 dígitos) de a quién se paga. */
        @NotBlank(message = "Obligatorio")
        String ruc,

        @NotNull(message = "Obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El monto debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "Máximo S/ 99,999,999.99 y 2 decimales")
        BigDecimal monto,

        @NotNull(message = "Obligatorio")
        LocalDate fechaVencimiento,

        @Size(max = 200, message = "Máximo 200 caracteres")
        String observacion,

        /** "Confirmo que esta deuda es de la empresa": obligatorio (se marca solo si el documento leído está a su nombre). */
        Boolean confirmaEmpresa) {
}
